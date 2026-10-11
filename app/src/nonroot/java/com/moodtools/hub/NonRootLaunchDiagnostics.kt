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
        val previous = LaunchSessionRecorder.current(game.packageName)
        val directory = File(context.filesDir, "menus/${game.packageName}")
        val metadata = JSONObject().apply {
            put("package", game.packageName)
            put("title", game.module.title)
            put("started", System.currentTimeMillis())
            put("launcher", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android", "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
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
        val session = LaunchSessionRecorder.begin(game.packageName, metadata.toString())
        // Keep the newest session, its predecessor, and the most recent failure.
        val sessions = session.parentFile!!.listFiles().orEmpty().filter(::isSessionDirectory)
            .sortedByDescending { it.name.substringBefore('-').toLongOrNull() ?: 0 }
        val lastFailure = sessions.firstOrNull { it != session && isFailureDirectory(it) }
        sessions.filter { it !in sessions.take(2) && it != lastFailure && it != previous }.forEach(File::deleteRecursively)
        session
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
            File(session, "launch-error.txt").writeText(LaunchSessionRecorder.exceptionFrames(error))
            event(session, "launch_failed ${error.javaClass.name}")
        }
    }

    fun stop(packageName: String) {
        stopSession(LaunchSessionRecorder.current(packageName))
    }

    fun stopSession(session: File?) { runCatching { session?.let { File(it, "stopped").writeText("user_stop") } } }

    private fun isSessionDirectory(file: File): Boolean = file.isDirectory &&
        file.name.matches(Regex("[0-9]+-[a-f0-9-]+")) && file.canonicalFile.parentFile == file.parentFile?.canonicalFile

    private fun isFailureDirectory(session: File) = session.listFiles().orEmpty().any {
        it.name.startsWith("failure-") || it.name.startsWith("exit-") ||
            it.name == "launch-error.txt" || it.name == "possible-stall"
    }

    @Synchronized override fun reports(context: Context): List<LaunchDiagnosticReport> {
        val sessions = runCatching { LaunchSessionRecorder.root().listFiles().orEmpty()
            .filter(File::isDirectory).flatMap { it.listFiles().orEmpty().filter(::isSessionDirectory) }
            .filter { !File(it, "superseded").exists() } }
            .getOrDefault(emptyList())
        // Calls run outside the UI and have a deadline. Never wait for a wedged guest process.
        if (Build.VERSION.SDK_INT >= 30 && readingExits.compareAndSet(false, true)) {
            val future = exitReader.submit {
                try { collectExitReasons(context.applicationContext, sessions) }
                finally { readingExits.set(false) }
            }
            runCatching { future.get(1500, TimeUnit.MILLISECONDS) }
            // Do not queue another reader while a device's system service is unresponsive.
        }
        return sessions.filter { !File(it, "superseded").exists() }
            .mapNotNull { session -> runCatching { report(session) }.getOrNull() }
            .sortedByDescending { it.startedAt }
    }

    private fun collectExitReasons(context: Context, sessions: List<File>) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        // Called from the real host process, not BlackBox's guest AMS proxy (which returns no exits).
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 32)
        for (session in sessions) {
            val started = session.name.substringBefore('-').toLongOrNull() ?: continue
            val pids = session.listFiles().orEmpty().mapNotNull {
                Regex("process-([0-9]+)\\.txt").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
            }.toSet()
            val nextStart = sessions.filter { it.parentFile == session.parentFile && it != session }
                .mapNotNull { it.name.substringBefore('-').toLongOrNull() }.filter { it > started }.minOrNull() ?: Long.MAX_VALUE
            for (exit in exits.filter { it.pid in pids && it.timestamp in started until nextStart }) {
                if (exit.reason !in setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE,
                        ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_INITIALIZATION_FAILURE)) continue
                val target = File(session, "exit-${exit.pid}-${exit.timestamp}.txt")
                if (target.exists()) continue
                val text = buildString {
                    appendLine("Exit reason: ${exit.reason}; status: ${exit.status}; PID: ${exit.pid}; timestamp: ${exit.timestamp}")
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

    private fun report(session: File): LaunchDiagnosticReport {
        val meta = JSONObject(File(session, "metadata.txt").readText())
        val files = session.listFiles().orEmpty().filter { it.extension == "txt" && it.name != "metadata.txt" }
            .sortedBy(File::getName).take(64)
        val events = files.filter { it.name == "host.txt" || it.name.startsWith("process-") }
            .flatMap { file -> file.readText().lineSequence().filter(String::isNotBlank).map { "${file.name}: $it" }.toList() }
        val failures = files.filter { it.name.startsWith("failure-") || it.name.startsWith("exit-") || it.name == "launch-error.txt" }
        val text = events.joinToString("\n")
        val resumed = Regex("activity_(?:direct-)?resumed ").containsMatchIn(text)
        val stopped = File(session, "stopped").exists()
        val started = meta.getLong("started")
        val status = launchDiagnosticStatus(failures.isNotEmpty(), resumed, stopped, System.currentTimeMillis() - started,
            text.contains("main_process_died"))
        if (status == "Possible startup stall") File(session, "possible-stall").apply { if (!exists()) writeText("suspected") }
        // A late successful resume supersedes a suspected stall.
        if (resumed) File(session, "possible-stall").delete()
        val reportText = buildString {
            appendLine("Jester Mods BlackBox session diagnostics")
            appendLine("Session: ${session.name}")
            appendLine("Status: $status")
            meta.keys().asSequence().sorted().forEach { key -> appendLine("$key: ${meta.get(key).toString().replace('\n', ' ')}") }
            appendLine("\n--- STARTUP TIMELINE (epoch milliseconds) ---")
            events.sortedBy { it.substringAfter(": ").substringBefore(' ').toLongOrNull() ?: 0 }.forEach(::appendLine)
            failures.forEach { appendLine("\n--- ${it.name} ---"); appendLine(it.readText().take(64 * 1024)) }
            files.filter { it.name.startsWith("stacks-") }.forEach {
                appendLine("\n--- STARTUP JAVA THREAD SNAPSHOT ---"); appendLine(it.readText().take(64 * 1024))
            }
            appendLine("\nActivity resumed does not prove the game reached its menu.")
            appendLine("Hook addresses require matching module/game symbols; no automatic blame assignment.")
            appendLine("No account text, exception messages, raw logcat or native memory dumps collected.")
        }.take(512 * 1024)
        val summary = buildString {
            appendLine("Jester Mods BlackBox diagnostics / ${session.name}")
            appendLine("Status: $status")
            for (key in listOf("package", "launcher", "device", "android", "game", "abis", "pageSize", "moduleNativeSha256")) {
                appendLine("$key: ${meta.opt(key)}")
            }
            failures.take(2).forEach { appendLine(it.readText().take(3500)) }
            appendLine("--- LAST STARTUP EVENTS ---")
            events.sortedBy { it.substringAfter(": ").substringBefore(' ').toLongOrNull() ?: 0 }
                .takeLast(12).forEach(::appendLine)
            appendLine("Share the full report for complete details; a stalled launch is not a confirmed freeze.")
        }.take(12_000)
        return LaunchDiagnosticReport(session.name, meta.getString("package"), meta.getString("title"), started,
            status, status in setOf("Crash or startup error recorded", "Possible startup stall", "Guest process exited during startup"),
            File(session, "acknowledged").exists(), reportText, summary)
    }

    override fun acknowledge(report: LaunchDiagnosticReport) {
        runCatching { reportDirectory(report)?.let { File(it, "acknowledged").writeText("seen") } }
    }

    @Synchronized override fun clear(context: Context, packageName: String) {
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
        if (!report.id.matches(Regex("[0-9]+-[a-f0-9-]+"))) return null
        return File(LaunchSessionRecorder.packageDirectory(report.packageName), report.id).takeIf(File::isDirectory)
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
