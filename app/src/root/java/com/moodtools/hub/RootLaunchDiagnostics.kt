package com.moodtools.hub

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.moodtools.hub.modules.InstalledGame
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Root has no virtual guest lifecycle: only confirmed Android crash records become reports. */
internal object RootLaunchDiagnostics : LaunchDiagnosticsBackend {
    override val crashOnly = true
    private var directory: File? = null
    private var lastRead = -15_000L

    fun install(context: Context) {
        directory = File(context.filesDir, "root-crash-diagnostics").apply { mkdirs() }
        LaunchDiagnostics.backend = this
    }

    @Synchronized fun begin(context: Context, game: InstalledGame, payloadSha256: String?) {
        // Preserve a previous crash before advancing the session boundary. Never clear system logs.
        runCatching {
            install(context)
            lastRead = -15_000L
            reports(context)
            val root = directory ?: return
            val now = System.currentTimeMillis()
            val session = File(root, "$now-${UUID.randomUUID()}").apply { mkdirs() }
            File(session, "metadata.json").writeText(JSONObject().apply {
                put("package", game.packageName); put("title", game.module.title); put("started", now)
                put("game", "${game.versionName} (${game.versionCode})")
                put("launcher", BuildConfig.VERSION_NAME); put("android", Build.VERSION.SDK_INT)
                put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("abi", game.abi); put("rootMethod", game.module.rootMethod.toString())
                put("payloadSha256", payloadSha256 ?: "unavailable")
            }.toString())
            val sameGame = sessions().filter { (_, meta) -> meta.optString("package") == game.packageName }
            val keep = sameGame.take(2).map { it.first }.toMutableSet()
            sameGame.firstOrNull { File(it.first, "crash.txt").isFile }?.let { keep += it.first }
            sameGame.filter { it.first !in keep }.forEach { (old, _) -> old.deleteRecursively() }
        }
    }

    private fun sessions(): List<Pair<File, JSONObject>> = directory?.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.matches(Regex("[0-9]+-[a-f0-9-]+")) && it.canonicalFile.parentFile == directory?.canonicalFile }
        .mapNotNull { file -> runCatching { file to JSONObject(File(file, "metadata.json").readText()) }.getOrNull() }
        .sortedByDescending { it.second.optLong("started") }

    @Synchronized override fun reports(context: Context): List<LaunchDiagnosticReport> {
        val sessions = sessions()
        if (sessions.isEmpty()) return emptyList()
        if (SystemClock.elapsedRealtime() - lastRead >= 15_000) {
            lastRead = SystemClock.elapsedRealtime()
            // ponytail: finite crash buffer only; rotated/OEM-suppressed records need device-level capture.
            val log = readCrashBuffer()
            if (log != null) sessions.forEach { (file, meta) ->
                val start = meta.getLong("started")
                val end = sessions.filter { it.second.optString("package") == meta.optString("package") }
                    .map { it.second.optLong("started") }.filter { it > start }.minOrNull() ?: Long.MAX_VALUE
                val crash = rootCrashSummary(log, meta.getString("package"), start, end)
                if (crash.isNotBlank()) {
                    val output = File(file, "crash.txt")
                    if (!output.exists()) output.writeText(crash)
                }
            }
        }
        return sessions.mapNotNull { (file, meta) ->
            val crash = File(file, "crash.txt").takeIf(File::isFile)?.readText() ?: return@mapNotNull null
            val text = buildString {
                appendLine("Jester Mods ROOT crash diagnostics / ${file.name}")
                meta.keys().asSequence().sorted().forEach { appendLine("$it: ${meta.get(it).toString().replace('\n', ' ')}") }
                appendLine("\n$crash")
                appendLine("\nOnly game-specific crash structure is retained. No exception messages, raw logs or memory dumps.")
                appendLine("Missing/rotated crash records cannot be recovered. No stall detection in Root mode.")
                appendLine("Native PCs require matching game/module symbols; this report does not identify a bad hook automatically.")
            }
            LaunchDiagnosticReport(file.name, meta.getString("package"), meta.getString("title"), meta.getLong("started"),
                "Game crash recorded", true, File(file, "acknowledged").exists(), text)
        }
    }

    /** Drain concurrently so a full pipe cannot block su; root failure never blocks game launch. */
    private fun readCrashBuffer(): String? = runCatching {
        val process = ProcessBuilder("su", "-c", "logcat -b crash -d -t 2000 -v epoch AndroidRuntime:E DEBUG:F '*:S'")
            .redirectErrorStream(true).start()
        val reader = Executors.newSingleThreadExecutor { task -> Thread(task, "root-crash-reader").apply { isDaemon = true } }
        try {
            val result = reader.submit<String> {
                process.inputStream.bufferedReader().use { input ->
                    val output = StringBuilder()
                    val buffer = CharArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        val remaining = 1024 * 1024 - output.length
                        if (remaining > 0) output.append(buffer, 0, minOf(count, remaining))
                    }
                    output.toString()
                }
            }
            if (!process.waitFor(3, TimeUnit.SECONDS) || process.exitValue() != 0) null
            else result.get(1, TimeUnit.SECONDS)
        } finally {
            process.destroyForcibly()
            process.inputStream.close()
            reader.shutdownNow()
        }
    }.getOrNull()

    @Synchronized override fun acknowledge(report: LaunchDiagnosticReport) {
        sessions().firstOrNull { it.first.name == report.id && it.second.optString("package") == report.packageName }
            ?.first?.let { File(it, "acknowledged").writeText("seen") }
    }

    @Synchronized override fun clear(context: Context, packageName: String) {
        val parent = directory ?: return
        sessions().filter { it.second.optString("package") == packageName }
            .forEach { deleteDiagnosticSession(it.first, parent) }
        // No session metadata remains, so old system crash records cannot reappear on refresh.
    }

    override fun share(context: Context, report: LaunchDiagnosticReport) {
        val exports = File(context.cacheDir, "diagnostic-exports").apply { mkdirs() }
        exports.listFiles().orEmpty().filter { it.name.endsWith(".txt") }
            .sortedByDescending(File::lastModified).drop(7).forEach(File::delete)
        val file = File(exports, "Jester-root-crash-${UUID.randomUUID()}.txt").apply { writeText(report.text) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.launcher-updates", file)
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = android.content.ClipData.newRawUri("Diagnostics", uri) }
        context.startActivity(Intent.createChooser(intent, LauncherLocalization.translate("Share full report")))
    }
}
