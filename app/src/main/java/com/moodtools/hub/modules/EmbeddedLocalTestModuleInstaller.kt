package com.moodtools.hub.modules

import android.content.Context
import com.moodtools.hub.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONObject

/** Installs the module carried by a debug launcher APK as a visible local TEST build. */
class EmbeddedLocalTestModuleInstaller(private val context: Context) {
    fun installIfConfigured(): String? {
        if (!BuildConfig.DEBUG || !BuildConfig.LOCAL_TEST_MODULE_ENABLED) return null
        require(BuildConfig.LOCAL_TEST_MODULE_PACKAGE.matches(PACKAGE_PATTERN)) {
            "The embedded local test package is invalid"
        }
        require(BuildConfig.LOCAL_TEST_MODULE_SHA256.matches(SHA256_PATTERN)) {
            "The embedded local test identity is invalid"
        }

        val menuRoot = File(context.filesDir, "menus").canonicalFile
        check(menuRoot.mkdirs() || menuRoot.isDirectory) { "Local test module storage is unavailable" }
        val target = safeChild(menuRoot, BuildConfig.LOCAL_TEST_MODULE_PACKAGE)
        if (installedMarkerMatches(target)) return BuildConfig.LOCAL_TEST_MODULE_PACKAGE

        // ModuleRepository deliberately requires the candidate directory name to match
        // package_name. Keep the transaction wrapper separate so the extracted candidate can
        // retain its exact package name while still being committed atomically below.
        val staging = safeChild(menuRoot, "${BuildConfig.LOCAL_TEST_MODULE_PACKAGE}.embedded-test-staging")
        val next = safeChild(staging, BuildConfig.LOCAL_TEST_MODULE_PACKAGE)
        val backup = safeChild(menuRoot, "${BuildConfig.LOCAL_TEST_MODULE_PACKAGE}.embedded-test-backup")
        deleteDirectory(staging)
        deleteDirectory(backup)
        check(staging.mkdirs()) { "Could not prepare embedded local test staging" }
        check(next.mkdirs()) { "Could not prepare embedded local test storage" }

        try {
            extractVerifiedBundle(next)
            writeMarker(next)
            check(ModuleRepository(context).run {
                val parsed = loadCandidate(next)
                parsed != null && parsed.packageName == BuildConfig.LOCAL_TEST_MODULE_PACKAGE
            }) { "The embedded local test configuration is invalid" }

            if (target.exists()) check(target.renameTo(backup)) {
                "Could not preserve the previous module installation"
            }
            if (!next.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                error("Could not activate the embedded local test module")
            }
            deleteDirectory(staging)
            deleteDirectory(backup)
            return BuildConfig.LOCAL_TEST_MODULE_PACKAGE
        } catch (error: Throwable) {
            deleteDirectory(staging)
            throw error
        }
    }

    private fun extractVerifiedBundle(destination: File) {
        val bundleDigest = MessageDigest.getInstance("SHA-256")
        val stagedArchive = File(context.cacheDir, "embedded-local-test-module.zip")
        try {
            context.assets.open(ASSET_PATH).use { asset ->
                FileOutputStream(stagedArchive).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var archiveBytes = 0L
                    while (true) {
                        val count = asset.read(buffer)
                        if (count < 0) break
                        archiveBytes += count
                        require(archiveBytes <= MAX_ARCHIVE_BYTES) { "The embedded local test archive is too large" }
                        bundleDigest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(bundleDigest.digest().joinToString("") { "%02x".format(it) } ==
                BuildConfig.LOCAL_TEST_MODULE_SHA256) {
                "The embedded local test archive failed verification"
            }
            ZipInputStream(stagedArchive.inputStream().buffered()).use { archive ->
                val seen = LinkedHashSet<String>()
                var totalBytes = 0L
                while (true) {
                    val entry = archive.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    if (entry.isDirectory || name.startsWith("META-INF/")) continue
                    require(name in ALLOWED_FILES && seen.add(name)) {
                        "The embedded local test archive contains an unexpected file"
                    }
                    val output = File(destination, name).canonicalFile
                    require(output.parentFile == destination.canonicalFile) {
                        "The embedded local test archive contains an unsafe path"
                    }
                    FileOutputStream(output).use { target ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = archive.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= MAX_EXTRACTED_BYTES) { "The embedded local test archive is too large" }
                            target.write(buffer, 0, count)
                        }
                        target.fd.sync()
                    }
                }
                require(seen.containsAll(REQUIRED_FILES)) { "The embedded local test archive is incomplete" }
            }
        } finally {
            if (stagedArchive.exists() && !stagedArchive.delete()) stagedArchive.deleteOnExit()
        }
    }

    private fun writeMarker(directory: File) {
        File(directory, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).writeText(
            JSONObject()
                .put("schema", 1)
                .put("source", "embedded-debug-launcher")
                .put("packageName", BuildConfig.LOCAL_TEST_MODULE_PACKAGE)
                .put("bundleSha256", BuildConfig.LOCAL_TEST_MODULE_SHA256)
                .toString(),
            Charsets.UTF_8
        )
    }

    private fun installedMarkerMatches(directory: File): Boolean = runCatching {
        if (!directory.isDirectory) return@runCatching false
        val marker = JSONObject(
            File(directory, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).readText(Charsets.UTF_8)
        )
        marker.optInt("schema") == 1 &&
            marker.optString("source") == "embedded-debug-launcher" &&
            marker.optString("packageName") == BuildConfig.LOCAL_TEST_MODULE_PACKAGE &&
            marker.optString("bundleSha256") == BuildConfig.LOCAL_TEST_MODULE_SHA256 &&
            REQUIRED_FILES.all { File(directory, it).isFile }
    }.getOrDefault(false)

    private fun safeChild(root: File, name: String): File = File(root, name).canonicalFile.also {
        require(it.parentFile == root && it.name == name) { "Local test module path escaped storage" }
    }

    private fun deleteDirectory(directory: File) {
        if (directory.exists()) check(directory.deleteRecursively()) { "Could not clean local test staging storage" }
    }

    companion object {
        private const val ASSET_PATH = "embedded-local-test/module.zip"
        private const val MAX_ARCHIVE_BYTES = 220L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 220L * 1024L * 1024L
        private val REQUIRED_FILES = setOf("config.json", "classes.dex", "libmenu_native.so")
        private val ALLOWED_FILES = REQUIRED_FILES + setOf("features.json", "local-test.json")
        private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_.]{3,200}")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
