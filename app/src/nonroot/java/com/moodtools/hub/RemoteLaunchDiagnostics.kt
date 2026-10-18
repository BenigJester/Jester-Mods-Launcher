package com.moodtools.hub

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import top.niunaijun.blackbox.utils.LaunchSessionRecorder

/** Bounded, caller/signer-authorized report transfer. Never starts a game Activity. */
internal object RemoteLaunchDiagnostics {
    const val FACTORY = "com.moodtools.diagnostics.DiagnosticFactory"
    const val CONFIG = "assets/moodtools/diagnostics.json"
    private val beginWorker = java.util.concurrent.Executors.newSingleThreadExecutor { task -> Thread(task, "DiagnosticBegin").apply { isDaemon = true } }
    private val beginning = java.util.concurrent.atomic.AtomicBoolean()
    fun configuration(context: Context, method: String, factory: String = ""): ByteArray {
        val info = context.packageManager.getPackageInfo(context.packageName,
            if (android.os.Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        val signer = (if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo!!.apkContentsSigners else info.signatures!!).first().toByteArray()
        return JSONObject().put("launcher", context.packageName).put("factory", factory).put("method", method)
            .put("signer", MessageDigest.getInstance("SHA-256").digest(signer).joinToString("") { "%02x".format(it) })
            .toString().toByteArray()
    }
    private fun uri(packageName: String) = Uri.parse("content://$packageName.jester-diagnostics")
    private fun call(context: Context, pkg: String, method: String, extras: Bundle? = null): Bundle? =
        context.contentResolver.acquireUnstableContentProviderClient(uri(pkg))?.use { it.call(method, null, extras) }

    fun begin(context: Context, pkg: String, method: String) {
        // Reporting failures must never block launching an otherwise usable installation.
        if (!beginning.compareAndSet(false, true)) return
        val future = beginWorker.submit {
            try { runCatching {
            val app = context.packageManager.getPackageInfo(pkg, 0)
            val meta = JSONObject().put("title", context.packageManager.getApplicationLabel(app.applicationInfo!!))
                .put("launcher", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                .put("runtime", method).put("game", "${app.versionName} (${if (android.os.Build.VERSION.SDK_INT >= 28) app.longVersionCode else app.versionCode})")
                .put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                .put("android", "${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}")
                .put("abis", android.os.Build.SUPPORTED_ABIS.joinToString())
            call(context, pkg, "begin", Bundle().apply { putString("metadata", meta.toString()) })
            } } finally { beginning.set(false) }
        }
        runCatching { future.get(750, java.util.concurrent.TimeUnit.MILLISECONDS) }
    }
    private fun targets(context: Context): List<String> = File(context.filesDir, "menus").listFiles().orEmpty()
        .filter { it.isDirectory && it.name.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) }
        .mapNotNull { directory -> runCatching {
            val app = context.packageManager.getApplicationInfo(directory.name, PackageManager.GET_META_DATA)
            directory.name.takeIf { (android.os.Build.VERSION.SDK_INT >= 28 && app.appComponentFactory == FACTORY) ||
                (app.metaData?.getBoolean("com.moodtools.identity_shell") == true &&
                    (app.metaData?.getInt("com.moodtools.identity_shell_version", 0) ?: 0) >= 38) }
        }.getOrNull() }

    fun sync(context: Context) {
        for (pkg in targets(context)) runCatching {
            val cutoff = clearCutoff(context, pkg)
            if (cutoff > 0) call(context, pkg, "clear", Bundle().apply { putLong("before", cutoff) })
            val ids = call(context, pkg, "list")?.getStringArrayList("sessions").orEmpty().take(16)
            for (id in ids.filter { it.matches(Regex("[0-9]+-[a-f0-9-]+")) }) {
                val directory = File(LaunchSessionRecorder.packageDirectory(pkg), id)
                if ((id.substringBefore('-').toLongOrNull() ?: 0) <= cutoff) continue
                context.contentResolver.acquireUnstableContentProviderClient(uri(pkg))?.use { client ->
                    client.openFile(uri(pkg).buildUpon().appendPath(id).build(), "r", null)?.use { descriptor ->
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                            importSnapshot(input, directory, pkg) {
                                (id.substringBefore('-').toLongOrNull() ?: 0) > clearCutoff(context, pkg)
                            }
                        }
                    }
                }
            }
        }
    }
    internal fun importSnapshot(input: java.io.InputStream, directory: File, pkg: String,
                                              shouldImport: () -> Boolean = { true }) {
        val files = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            repeat(64) {
                val entry = zip.nextEntry ?: return@use
                require(!entry.isDirectory && entry.name.matches(Regex("(?:metadata|host|process-[0-9]+|failure-[0-9]+|resources-[0-9]+|exit-[0-9]+-[0-9]+)\\.(?:txt|pb)")))
                require(entry.name !in files)
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                val limit = if (entry.name.endsWith(".pb")) 2 * 1024 * 1024 else 64 * 1024
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    require(bytes.size() + count <= limit && total <= 8 * 1024 * 1024)
                    bytes.write(buffer, 0, count)
                }
                files[entry.name] = bytes.toByteArray()
            }
            require(zip.nextEntry == null)
        }
        val meta = JSONObject(requireNotNull(files["metadata.txt"]).toString(Charsets.UTF_8))
        require(meta.getString("package") == pkg && meta.optString("runtime") in setOf("direct_patch", "identity_shell"))
        synchronized(this) {
        if (!shouldImport()) return
        require(!java.nio.file.Files.isSymbolicLink(directory.toPath()))
        require(directory.mkdirs() || directory.isDirectory)
        for ((name, bytes) in files.filterKeys { it.endsWith(".txt") }) {
            require(!java.nio.file.Files.isSymbolicLink(File(directory, name).toPath()))
            val trace = files[name.removeSuffix(".txt") + ".pb"]
            val text = bytes.toString(Charsets.UTF_8) + if (trace == null) "" else "\n" +
                runCatching { NativeTombstoneSummary.decode(trace) }.getOrDefault("Native system trace unreadable or exceeds size limit")
            File(directory, name).writeText(text)
        }
        }
    }
    @Synchronized fun clear(context: Context, pkg: String) {
        require(pkg.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")))
        File(context.filesDir, "diagnostic-cleared/$pkg").apply { parentFile?.mkdirs(); writeText(System.currentTimeMillis().toString()) }
    }
    private fun clearCutoff(context: Context, pkg: String): Long = runCatching {
        File(context.filesDir, "diagnostic-cleared/$pkg").readText().toLong()
    }.getOrDefault(0)
}
