package com.moodtools.hub

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.moodtools.hub.modules.InstalledGame
import org.json.JSONObject
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.utils.LaunchSessionRecorder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object NonRootLaunchDiagnostics : LaunchDiagnosticsBackend {
    private val exitReader = Executors.newSingleThreadExecutor { task ->
        Thread(task, "DiagnosticExitReader").apply { isDaemon = true }
    }
    private val readingExits = AtomicBoolean(false)

    fun install() { LaunchDiagnostics.backend = this }

    fun begin(context: Context, game: InstalledGame, verifiedModule: com.moodtools.hub.modules.VerifiedModuleInstall? = null): File? = runCatching {
        val directory = File(context.filesDir, "menus/${game.packageName}")
        val metadata = JSONObject().apply {
            put("package", game.packageName)
            put("title", game.module.title)
            put("started", System.currentTimeMillis())
            put("launcher", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android", "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            put("androidSecurityPatch", Build.VERSION.SECURITY_PATCH)
            put("androidBuild", Build.ID)
            put("abis", Build.SUPPORTED_ABIS.joinToString())
            put("game", "${game.versionName} (${game.versionCode}) / ${game.abi}")
            put("moduleNativeSha256", hash(File(directory, game.module.nativeFile)))
            put("moduleConfigSha256", hash(File(directory, "config.json")))
            put("method", game.module.rootMethod.jsonValue)
            put("moduleVersion", verifiedModule?.let { "${it.version} (${it.build})" } ?: "unavailable")
            put("pageSize", runCatching { android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE) }.getOrDefault(-1))
            put("googleHostPackages", listOf("com.google.android.gsf", "com.google.android.gms", "com.android.vending")
                .joinToString { pkg -> "$pkg=" + runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrDefault("missing") })
            put("hostInternetPermission", context.checkSelfPermission("android.permission.INTERNET"))
            if (Build.VERSION.SDK_INT >= 26) put("webView", runCatching { android.webkit.WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: "unavailable")
        }
        // Each launch gets isolated files; history is removed only by the explicit Clear action.
        LaunchSessionRecorder.begin(game.packageName, metadata.toString())
    }.getOrNull()

    private fun hash(file: File): String = if (!file.isFile) "unavailable" else
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    fun event(session: File?, stage: String) { LaunchSessionRecorder.hostEvent(session, stage) }

    fun failure(session: File?, error: Throwable) {
        if (session == null) return
        runCatching {
            val timeout = error is LaunchConfirmationTimeoutException
            File(session, "launch-error.txt").writeText(
                (if (timeout) "Launch confirmation timeout\n" else "") + LaunchSessionRecorder.exceptionFrames(error))
            event(session, if (timeout) "launch_confirmation_timeout" else "launch_failed ${error.javaClass.name}")
        }
    }

    fun stop(packageName: String) {
        stopSession(LaunchSessionRecorder.current(packageName))
    }

    fun stopSession(session: File?) { runCatching { session?.let { File(it, "stopped").writeText("user_stop") } } }

    private fun isSessionDirectory(file: File): Boolean = file.isDirectory &&
        file.name.matches(Regex("[0-9]+-[a-f0-9-]+")) && file.canonicalFile.parentFile == file.parentFile?.canonicalFile

    @Synchronized override fun reports(context: Context): List<LaunchDiagnosticReport> {
        fun sessions() = runCatching { LaunchSessionRecorder.root().listFiles().orEmpty()
            .filter(File::isDirectory).flatMap { it.listFiles().orEmpty().filter(::isSessionDirectory) }
            .filter { !File(it, "superseded").exists() } }
            .getOrDefault(emptyList())
        // Calls run outside the UI and have a deadline. Never wait for a wedged guest process.
        if (readingExits.compareAndSet(false, true)) {
            val future = exitReader.submit {
                try {
                    RemoteLaunchDiagnostics.sync(context.applicationContext)
                    // Remote packages collect their own UID's exits; the host only reads its own.
                    if (Build.VERSION.SDK_INT >= 30) collectExitReasons(context.applicationContext, sessions().filter {
                        runCatching { !JSONObject(File(it, "metadata.txt").readText()).has("runtime") }.getOrDefault(false)
                    })
                }
                finally { readingExits.set(false) }
            }
            runCatching { future.get(1500, TimeUnit.MILLISECONDS) }
            // Do not queue another reader while a device's system service is unresponsive.
        }
        return sessions().filter { !File(it, "superseded").exists() }
            .flatMap { session -> runCatching { reportsForSession(session) }.getOrDefault(emptyList()) }
            .sortedByDescending { it.startedAt }
    }

    private fun collectExitReasons(context: Context, sessions: List<File>) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        // Called from the real host process, not BlackBox's guest AMS proxy (which returns no exits).
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 32)
        for (session in sessions) {
            val started = session.name.substringBefore('-').toLongOrNull() ?: continue
            val pids = session.listFiles().orEmpty().mapNotNull {
                Regex("(?:process|support-resources)-([0-9]+)\\.txt").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
            }.toSet()
            val nextStart = sessions.filter { it.parentFile == session.parentFile && it != session }
                .mapNotNull { it.name.substringBefore('-').toLongOrNull() }.filter { it > started }.minOrNull() ?: Long.MAX_VALUE
            for (exit in exits.filter { it.pid in pids && it.timestamp in started until nextStart }) {
                val target = File(session, "exit-${exit.pid}-${exit.timestamp}.txt")
                if (target.exists() && target.readText().contains("Exit evidence schema: 2")) continue
                val text = buildString {
                    appendLine("Exit reason: ${exit.reason}; status: ${exit.status}; PID: ${exit.pid}; timestamp: ${exit.timestamp}")
                    appendLine("Exit category: ${diagnosticExitCategory(exit.reason)}")
                    appendLine("Exit evidence schema: 2")
                    appendLine("System last-sampled memory: pssKb=${exit.pss}; rssKb=${exit.rss}; importance=${exit.importance}")
                    appendLine("System description hint: ${diagnosticExitHint(exit.description)}")
                    appendLine("Memory values are historical samples; zero means unavailable, not zero usage.")
                    if (exit.reason == ApplicationExitInfo.REASON_ANR) {
                        val process = File(session, "process-${exit.pid}.txt")
                        val attempts = diagnosticProcessAttempts(mapOf(exit.pid to
                            if (process.isFile) process.readText() else ""), File(session, "host.txt").takeIf(File::isFile)?.readText().orEmpty())
                        val attempt = attempts.lastOrNull { it.startedAt <= exit.timestamp }
                        // Only retain a fixed category, never Android's raw description (it can contain intent extras).
                        val context = if (attempt == null && !exit.description.orEmpty().startsWith("bg anr:")) "unknown" else
                            diagnosticAnrContext(exit.description.orEmpty().startsWith("bg anr:"), attempt?.resumedBefore(exit.timestamp) == true)
                        appendLine("ANR context: $context")
                    }
                    if (exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE && Build.VERSION.SDK_INT >= 31) {
                        val trace = runCatching { exit.traceInputStream?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (output.size() <= 2 * 1024 * 1024) {
                                val count = input.read(buffer, 0, minOf(buffer.size, 2 * 1024 * 1024 + 1 - output.size()))
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        } }.getOrNull()
                        appendLine(if (trace == null) "Native system trace unavailable" else
                            runCatching { NativeTombstoneSummary.decode(trace) }.getOrDefault("Native system trace unreadable or exceeds size limit"))
                    }
                }
                target.writeText(text)
            }
        }
    }

    private fun reportsForSession(session: File): List<LaunchDiagnosticReport> {
        val files = session.listFiles().orEmpty()
        val processes = files.mapNotNull { file ->
            Regex("process-([0-9]+)\\.txt").matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to file.readText() }
        }.toMap()
        val host = File(session, "host.txt").takeIf(File::isFile)?.readText().orEmpty()
        val attempts = diagnosticProcessAttempts(processes, host)
        val metadata = JSONObject(File(session, "metadata.txt").readText())
        return if (attempts.isEmpty()) listOf(report(session, null, null)) else
            attempts.mapIndexed { index, attempt -> report(session, attempt, attempts.getOrNull(index + 1)?.startedAt,
                diagnosticAttemptLaunchRequest(attempt, attempts.getOrNull(index - 1)?.startedAt,
                    metadata.getLong("started"), metadata.optString("runtime") in setOf("direct_patch", "identity_shell"), host)) }
    }

    private fun report(session: File, attempt: DiagnosticProcessAttempt?, nextAttempt: Long?, launchRequestedAt: Long? = null): LaunchDiagnosticReport {
        val meta = JSONObject(File(session, "metadata.txt").readText())
        val runtime = when (meta.optString("runtime")) {
            "direct_patch" -> "Patch"
            "identity_shell" -> "Identity shell"
            else -> "BlackBox"
        }
        val files = session.listFiles().orEmpty().filter { it.extension == "txt" && it.name != "metadata.txt" }
            .sortedWith(compareBy<File> { file ->
                if (attempt != null && (file.name.startsWith("exit-${attempt.pid}-") ||
                            file.name == "failure-${attempt.pid}.txt" || file.name == "process-${attempt.pid}.txt")) 0 else 1
            }.thenBy(File::getName)).take(64)
        val events = files.filter { it.name == "host.txt" || it.name.startsWith("process-") }
            .flatMap { file -> file.readText().lineSequence().filter(String::isNotBlank).map { "${file.name}: $it" }.toList() }
        val failures = files.filter { it.name.startsWith("failure-") || it.name.startsWith("exit-") || it.name == "launch-error.txt" }
        val text = events.joinToString("\n")
        val resumed = attempt?.resumed ?: Regex("activity_(?:direct-)?resumed ").containsMatchIn(text)
        val stopped = File(session, "stopped").exists()
        val started = attempt?.startedAt ?: meta.getLong("started")
        val resources = files.filter { it.name.startsWith("resources-") &&
            (attempt == null || it.name == "resources-${attempt.pid}.txt") }.flatMap { file ->
            runCatching {
                file.readText().lineSequence()
                    .filter { (it.substringBefore(' ').toLongOrNull() ?: 0) in started until (nextAttempt ?: Long.MAX_VALUE) }
                    .map { "${file.name}: $it" }.toList()
            }.getOrDefault(emptyList())
        }.sortedBy { it.substringAfter(": ").substringBefore(' ').toLongOrNull() ?: 0 }
        val supportingResources = files.filter { it.name.matches(Regex("support-resources-[0-9]+\\.txt")) }
            .take(8).flatMap { file ->
                runCatching { file.readText().lineSequence().filter {
                    (it.substringBefore(' ').toLongOrNull() ?: 0) in started until (nextAttempt ?: Long.MAX_VALUE)
                }.map { "${file.name}: $it" }.toList() }.getOrDefault(emptyList())
            }
        val attemptFailures = if (attempt == null) failures.filterNot {
            it.name.startsWith("exit-") && File(session, "support-resources-${it.name.removePrefix("exit-").substringBefore('-')}.txt").exists()
        } else failures.filter { file ->
            when {
                file.name == "failure-${attempt.pid}.txt" -> attempt.events.contains("failure ") || nextAttempt == null
                file.name == "launch-error.txt" -> file.lastModified() >= started && file.lastModified() < (nextAttempt ?: Long.MAX_VALUE)
                file.name.startsWith("exit-${attempt.pid}-") -> file.name.substringAfterLast('-').removeSuffix(".txt").toLongOrNull()
                    ?.let { it >= started && it < (nextAttempt ?: Long.MAX_VALUE) } == true
                else -> false
            }
        }
        val died = attempt?.let { Regex("(?m)^([0-9]+) main_process_died pid=${it.pid}$").findAll(
            File(session, "host.txt").takeIf(File::isFile)?.readText().orEmpty()).any { death ->
                death.groupValues[1].toLong() >= started && death.groupValues[1].toLong() < (nextAttempt ?: Long.MAX_VALUE)
            } } ?: text.contains("main_process_died")
        val menuEvents = files.filter { it.name.startsWith("menu-") &&
            (attempt == null || it.name == "menu-${attempt.pid}.txt") }.mapNotNull {
            runCatching { it.readText().take(1024) }.getOrNull()
        }.joinToString("\n").lineSequence().filter {
            (it.substringBefore(' ').toLongOrNull() ?: 0) in started until (nextAttempt ?: Long.MAX_VALUE)
        }.joinToString("\n")
        val menuState = diagnosticMenuState((attempt?.events ?: events.joinToString("\n") { it.substringAfter(": ") }) + "\n" + menuEvents)
        val baseStatus = if (attempt == null) diagnosticFailureStatus(attemptFailures.map { it.readText() }, resumed) ?: launchDiagnosticStatus(false, resumed, stopped,
            System.currentTimeMillis() - started, died) else diagnosticAttemptStatus(attempt,
            attemptFailures.map { it.readText() }, stopped || nextAttempt != null, died, System.currentTimeMillis(), launchRequestedAt)
        val status = diagnosticStatusWithMenu(baseStatus, menuState)
        val prioritizedSupport = com.moodtools.hub.prioritizedSupportingResources(supportingResources)
        val binderLimitReported = attemptFailures.any { it.readText().contains("System description hint: binder_limit_reported") }
        val suffix = attempt?.let { "@${it.pid}-$started" }.orEmpty()
        if (status == "Possible startup stall") File(session, "possible-stall$suffix").apply { if (!exists()) writeText("suspected") }
        // Reclassification also clears old background-only false alarms without clearing evidence.
        if (status != "Possible startup stall") File(session, "possible-stall$suffix").delete()
        val reportText = buildString {
            appendLine("Jester Mods $runtime session diagnostics")
            appendLine("Session: ${session.name}")
            appendLine("Status: $status")
            if (attempt != null) appendLine("Selected process attempt: PID ${attempt.pid}; started: $started")
            appendLine("Menu window verification: ${menuState ?: "unavailable"}")
            appendLine("attached confirms a window attachment, not that its button is visible or unobscured. unsupported/probe_error are not attachment failures.")
            meta.keys().asSequence().sorted().forEach { key -> appendLine("$key: ${meta.get(key).toString().replace('\n', ' ')}") }
            val (selectedEvents, otherEvents) = events.partition { diagnosticEventBelongsToAttempt(it, attempt, nextAttempt) }
            appendLine(if (attempt == null) "\n--- SESSION TIMELINE (no main-process attempt identified) ---"
                else "\n--- SELECTED ATTEMPT TIMELINE (epoch milliseconds; host events are shared context) ---")
            selectedEvents.sortedBy { it.substringAfter(": ").substringBefore(' ').toLongOrNull() ?: 0 }.forEach(::appendLine)
            appendLine(if (attempt == null) "\n--- SESSION FAILURE / EXIT FILES ---" else "\n--- SELECTED ATTEMPT FAILURE / EXIT FILES ---")
            appendLine("Failure files are retained per PID and may contain multiple captures; compare their timestamps with the selected attempt.")
            if (attemptFailures.isEmpty()) appendLine("No matching failure or exit file recorded.")
            attemptFailures.forEach { appendLine("\n--- ${it.name} ---"); appendLine(diagnosticExitEvidence(it.readText()).take(64 * 1024)) }
            appendLine("\n--- OTHER SESSION HISTORY (not selected-attempt evidence) ---")
            appendLine("May include older or later attempts and other processes. Their exits do not explain this attempt.")
            otherEvents.sortedBy { it.substringAfter(": ").substringBefore(' ').toLongOrNull() ?: 0 }.forEach(::appendLine)
            (failures - attemptFailures.toSet()).forEach {
                appendLine("\n--- OTHER SESSION EVIDENCE: ${it.name} ---"); appendLine(diagnosticExitEvidence(it.readText()).take(64 * 1024))
            }
            files.filter { it.name.startsWith("stacks-") }.forEach {
                appendLine("\n--- SESSION JAVA THREAD SNAPSHOT (may be from another attempt) ---"); appendLine(it.readText().take(64 * 1024))
            }
            files.filter { it.name.matches(Regex("support-stacks-[0-9]+\\.txt")) }.take(4).forEach {
                appendLine("\n--- ${it.name}: HELPER MAIN THREAD (shared context, not proof of cause) ---")
                appendLine(it.readText().take(16 * 1024))
            }
            appendLine("\n--- ROLLING SESSION RESOURCES (15-second target interval; latest 24 per process) ---")
            resources.forEach(::appendLine)
            appendLine("\n--- SUPPORTING PROCESS SAMPLES (context only; not separate game attempts) ---")
            if (supportingResources.isEmpty()) appendLine("Unavailable; missing samples do not mean zero usage.")
            supportingResources.forEach(::appendLine)
            files.filter { it.name.matches(Regex("(?:support-)?connections-[0-9]+\\.txt")) }.take(8).forEach {
                appendLine("\n--- ${it.name}: SAMPLED CONNECTION CALLERS (process-lifetime context) ---")
                appendLine(it.readText().take(16 * 1024))
            }
            appendLine("Callsites are sampled on calls 1, 2, 4, then every 256 calls per operation; latest 16 retained, up to three code frames each. Samples may precede this attempt or serve another game. They do not prove a leak or Binder allocation ownership.")
            appendLine("Supporting capture runs for up to 10 minutes per observation, at most four sessions per process. Shared helpers may serve other games; these samples do not identify the cause.")
            appendLine("Supporting activityResumed is unavailable because its activity lifecycle is not tracked.")
            appendLine("bbWindowConnectionCalls counts calls/failures since this process's observation window began, not live connections or exclusive activity for this game. bbTrackedReceivers counts BroadcastManager-owned registrations only; -1 is unavailable, not zero. It excludes other receivers and unknown leaks from older code.")
            appendLine("CPU is cumulative process CPU milliseconds; divide its delta by elapsedMs delta for core usage (may exceed one core).")
            appendLine("mainPendingMs measures an outstanding main-thread probe, not game rendering or a confirmed freeze; -1 means unavailable.")
            appendLine("activityResumed is the last observed activity lifecycle state, not proof of Android foreground importance.")
            appendLine("Binder counters describe this process only: local objects, remote proxies, death links and cumulative sent/received transactions; -1 means unavailable.")
            appendLine("These counters are not system_server's per-UID proxy count or an OEM kill threshold. Reason 9 alone does not identify CPU, memory or Binder exhaustion.")
            appendLine("bbConnectionCalls are process-lifetime calls/failures observed at selected BlackBox Java hook boundaries, not live connection counts. lastFailure uses fixed categories only.")
            appendLine("Native Binder calls, bypassed hooks and unsampled UID processes are not covered; not_observed means no tracked operation was seen. Counts are not proof of a leak or its cause.")
            appendLine("\nActivity resumed does not prove the game reached its menu.")
            appendLine("Hook addresses require matching module/game symbols; no automatic blame assignment.")
            appendLine("No account text, exception messages, raw logcat or native memory dumps collected.")
        }.take(512 * 1024)
        val summary = buildString {
            appendLine("Jester Mods $runtime diagnostics / ${session.name}")
            appendLine("Status: $status")
            if (attempt != null) appendLine("Process attempt: PID ${attempt.pid}; started: $started")
            appendLine("Menu window verification: ${menuState ?: "unavailable"}")
            for (key in listOf("package", "launcher", "device", "android", "game", "abis", "pageSize", "moduleNativeSha256", "gmscoreEnabled", "gmscoreSelected", "gmscoreState", "gmscoreProfile")) {
                appendLine("$key: ${meta.opt(key)}")
            }
            if (binderLimitReported && prioritizedSupport.isNotEmpty()) {
                appendLine("--- HIGHEST SAMPLED HELPER BINDER COUNT (context only, not proof of cause) ---")
                appendLine(prioritizedSupport.first())
                appendLine("Latest sample per helper; not system_server's per-UID count or a kill threshold.")
                val pid = prioritizedSupport.first().substringBefore(": ")
                    .removePrefix("support-resources-").removeSuffix(".txt")
                files.firstOrNull { it.name == "support-connections-$pid.txt" }?.let { file ->
                    latestConnectionSiteSamples(file.readText()).takeIf(String::isNotEmpty)?.let { sites ->
                        appendLine("--- LATEST TWO SAMPLED CALLSITES FOR THIS HELPER (context, not cause) ---")
                        appendLine(sites)
                        appendLine("Process-lifetime samples; may precede this attempt. Not live connections or allocation ownership.")
                    }
                }
            }
            events.filter { line ->
                (line.contains(" window_add_rejected ") || line.contains(" window_add_exception ")) &&
                    (attempt == null || line.startsWith("process-${attempt.pid}.txt:")) &&
                    (line.substringAfter(": ").substringBefore(' ').toLongOrNull() ?: 0) in started until (nextAttempt ?: Long.MAX_VALUE)
            }.takeLast(3).forEach(::appendLine)
            attemptFailures.filter { it.name.startsWith("failure-") }.forEach { file ->
                diagnosticFailureEvidenceOverview(file.readText()).takeIf(String::isNotEmpty)?.let(::appendLine)
            }
            attemptFailures.take(2).forEach { appendLine(compactDiagnosticExcerpt(diagnosticExitEvidence(it.readText())).take(3500)) }
            files.filter { it.name.matches(Regex("support-stacks-[0-9]+\\.txt")) }.take(2).forEach {
                appendLine("--- ${it.name}: HELPER MAIN THREAD (shared context, not cause) ---")
                appendLine(compactDiagnosticExcerpt(it.readText().take(16 * 1024)).take(1600))
            }
            appendLine("--- LAST RESOURCE SAMPLES ---")
            resources.takeLast(3).forEach(::appendLine)
            appendLine("--- SUPPORTING PROCESSES (shared context, not proof of cause) ---")
            prioritizedSupport.drop(if (binderLimitReported) 1 else 0).forEach(::appendLine)
            appendLine("--- LAST STARTUP EVENTS ---")
            events.filter { diagnosticEventBelongsToAttempt(it, attempt, nextAttempt) }
                .sortedBy { it.substringAfter(": ").substringBefore(' ').toLongOrNull() ?: 0 }
                .takeLast(12).forEach(::appendLine)
            appendLine("Share the full report for complete details; a stalled launch is not a confirmed freeze.")
        }.take(12_000)
        return LaunchDiagnosticReport(session.name + suffix, meta.getString("package"), meta.getString("title"), started,
            status, status in setOf("Crash or startup error recorded", "Launch confirmation timed out", "Possible startup stall", "Guest process exited during startup",
                "Background ANR recorded", "Game ANR recorded", "Startup ANR recorded", "ANR recorded", "Menu window not attached"),
            File(session, "acknowledged$suffix").exists() || File(session, "acknowledged").lastModified() >= started, reportText, summary)
    }

    override fun acknowledge(report: LaunchDiagnosticReport) {
        val suffix = report.id.substringAfter('@', "").let { if (it.isEmpty()) "" else "@$it" }
        runCatching { reportDirectory(report)?.let { File(it, "acknowledged$suffix").writeText("seen") } }
    }

    @Synchronized override fun clear(context: Context, packageName: String) {
        RemoteLaunchDiagnostics.clear(context, packageName)
        synchronized(LaunchSessionRecorder::class.java) {
            val parent = LaunchSessionRecorder.packageDirectory(packageName)
            require(!java.nio.file.Files.isSymbolicLink(parent.toPath()) &&
                parent.canonicalFile.parentFile == LaunchSessionRecorder.root().canonicalFile)
            val sessions = parent.listFiles().orEmpty().filter(::isSessionDirectory)
            sessions.forEach { deleteDiagnosticSession(it, parent) }
            // Remove the stale AtomicFile pointer; live guest pins do not recreate deleted sessions.
            android.util.AtomicFile(File(parent, "current")).delete()
        }
    }

    private fun reportDirectory(report: LaunchDiagnosticReport): File? {
        if (!report.id.matches(Regex("[0-9]+-[a-f0-9-]+(?:@[0-9]+-[0-9]+)?"))) return null
        return File(LaunchSessionRecorder.packageDirectory(report.packageName), report.id.substringBefore('@')).takeIf(File::isDirectory)
    }

    override fun share(context: Context, report: LaunchDiagnosticReport) {
        val directory = File(context.cacheDir, "diagnostic-exports").apply { mkdirs() }
        directory.listFiles().orEmpty().filter { it.name.endsWith(".txt") }
            .sortedByDescending(File::lastModified).drop(7).forEach(File::delete)
        val file = File(directory, "Jester-diagnostics-${report.id}-${System.nanoTime()}.txt").apply { writeText(report.text) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.launcher-updates", file)
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = android.content.ClipData.newRawUri("Diagnostics", uri) }
        context.startActivity(Intent.createChooser(intent, LauncherLocalization.translate("Share full report")))
    }
}
