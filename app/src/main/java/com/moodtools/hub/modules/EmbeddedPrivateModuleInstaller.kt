package com.moodtools.hub.modules

import android.content.Context
import com.moodtools.hub.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONObject

/** Installs the build-bound private payload without exposing it through shared storage. */
class EmbeddedPrivateModuleInstaller(private val context: Context) {
    fun installIfConfigured(): String? {
        if (!BuildConfig.PRIVATE_MODULE_ENABLED) return null
        require(BuildConfig.PRIVATE_MODULE_PACKAGE.matches(PACKAGE_PATTERN)) {
            "The embedded module package is invalid"
        }
        require(BuildConfig.PRIVATE_MODULE_SCOPE.matches(SCOPE_PATTERN)) {
            "The embedded module scope is invalid"
        }
        require(BuildConfig.PRIVATE_MODULE_SHA256.matches(SHA256_PATTERN)) {
            "The embedded module identity is invalid"
        }

        val menuRoot = File(context.filesDir, "menus").canonicalFile
        check(menuRoot.mkdirs() || menuRoot.isDirectory) { "Private module storage is unavailable" }
        val target = safeChild(menuRoot, BuildConfig.PRIVATE_MODULE_PACKAGE)
        if (installedMarkerMatches(target)) return BuildConfig.PRIVATE_MODULE_PACKAGE

        val next = safeChild(menuRoot, "${BuildConfig.PRIVATE_MODULE_PACKAGE}.private-next")
        val backup = safeChild(menuRoot, "${BuildConfig.PRIVATE_MODULE_PACKAGE}.private-backup")
        deleteDirectory(next)
        deleteDirectory(backup)
        check(next.mkdirs()) { "Could not prepare private module storage" }

        try {
            extractVerifiedBundle(next)
            writeMarker(next)
            check(ModuleRepository(context).run {
                val parsed = loadCandidate(next)
                parsed != null && parsed.packageName == BuildConfig.PRIVATE_MODULE_PACKAGE
            }) { "The embedded module configuration is invalid" }

            if (target.exists()) check(target.renameTo(backup)) {
                "Could not preserve the previous module installation"
            }
            if (!next.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                error("Could not activate the private module")
            }
            deleteDirectory(backup)
            return BuildConfig.PRIVATE_MODULE_PACKAGE
        } catch (error: Throwable) {
            deleteDirectory(next)
            throw error
        }
    }

    private fun extractVerifiedBundle(destination: File) {
        val bundleDigest = MessageDigest.getInstance("SHA-256")
        val stagedArchive = File(context.cacheDir, "embedded-private-module.zip")
        try {
            context.assets.open(ASSET_PATH).use { asset ->
                FileOutputStream(stagedArchive).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var archiveBytes = 0L
                    while (true) {
                        val count = asset.read(buffer)
                        if (count < 0) break
                        archiveBytes += count
                        require(archiveBytes <= MAX_ARCHIVE_BYTES) {
                            "The embedded module archive is too large"
                        }
                        bundleDigest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(bundleDigest.digest().joinToString("") { "%02x".format(it) } ==
                BuildConfig.PRIVATE_MODULE_SHA256) {
                "The embedded module archive failed verification"
            }
            ZipInputStream(stagedArchive.inputStream().buffered()).use { archive ->
                val seen = LinkedHashSet<String>()
                var totalBytes = 0L
                while (true) {
                    val entry = archive.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    if (entry.isDirectory || name.startsWith("META-INF/")) continue
                    require(name in ALLOWED_FILES && seen.add(name)) {
                        "The embedded module archive contains an unexpected file"
                    }
                    val output = File(destination, name).canonicalFile
                    require(output.parentFile == destination.canonicalFile) {
                        "The embedded module archive contains an unsafe path"
                    }
                    FileOutputStream(output).use { target ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = archive.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= MAX_EXTRACTED_BYTES) {
                                "The embedded module archive is too large"
                            }
                            target.write(buffer, 0, count)
                        }
                        target.fd.sync()
                    }
                }
                require(seen.containsAll(REQUIRED_FILES)) {
                    "The embedded module archive is incomplete"
                }
            }
        } finally {
            if (stagedArchive.exists() && !stagedArchive.delete()) {
                stagedArchive.deleteOnExit()
            }
        }
    }

    private fun writeMarker(directory: File) {
        val marker = JSONObject()
            .put("schema", 1)
            .put("scope", BuildConfig.PRIVATE_MODULE_SCOPE)
            .put("packageName", BuildConfig.PRIVATE_MODULE_PACKAGE)
            .put("bundleSha256", BuildConfig.PRIVATE_MODULE_SHA256)
            .put("files", JSONObject().apply {
                REQUIRED_FILES.forEach { name -> put(name, sha256(File(directory, name).readBytes())) }
            })
            .toString()
        File(directory, ModuleRepository.EMBEDDED_PRIVATE_INSTALL_MARKER).writeText(marker, Charsets.UTF_8)
    }

    private fun installedMarkerMatches(directory: File): Boolean = runCatching {
        if (!directory.isDirectory) return@runCatching false
        val marker = JSONObject(
            File(directory, ModuleRepository.EMBEDDED_PRIVATE_INSTALL_MARKER).readText(Charsets.UTF_8)
        )
        marker.optInt("schema") == 1 &&
            marker.optString("scope") == BuildConfig.PRIVATE_MODULE_SCOPE &&
            marker.optString("packageName") == BuildConfig.PRIVATE_MODULE_PACKAGE &&
            marker.optString("bundleSha256") == BuildConfig.PRIVATE_MODULE_SHA256 &&
            REQUIRED_FILES.all { File(directory, it).isFile }
    }.getOrDefault(false)

    private fun safeChild(root: File, name: String): File = File(root, name).canonicalFile.also {
        require(it.parentFile == root && it.name == name) { "Private module path escaped storage" }
    }

    private fun deleteDirectory(directory: File) {
        if (directory.exists()) check(directory.deleteRecursively()) {
            "Could not clean private module staging storage"
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val ASSET_PATH = "embedded-private/module.zip"
        private const val MAX_ARCHIVE_BYTES = 220L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 220L * 1024L * 1024L
        private val REQUIRED_FILES = setOf("config.json", "classes.dex", "libmenu_native.so")
        private val ALLOWED_FILES = REQUIRED_FILES + "features.json"
        private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_.]{3,200}")
        private val SCOPE_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,63}")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
