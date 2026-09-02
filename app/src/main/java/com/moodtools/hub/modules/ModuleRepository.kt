package com.moodtools.hub.modules

import android.content.Context
import com.moodtools.hub.networking.SignedEnvelopeVerifier
import org.json.JSONObject
import java.io.File

class ModuleRepository(private val context: Context) {
    private val menuDirectory: File
        get() = File(context.filesDir, "menus")

    fun loadModules(): List<ModuleConfig> {
        if (!menuDirectory.isDirectory) return emptyList()

        return menuDirectory.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.mapNotNull { directory -> parseConfig(directory) }
            ?.toList()
            .orEmpty()
    }

    private fun parseConfig(directory: File): ModuleConfig? {
        return runCatching {
            val json = JSONObject(
                File(directory, "config.json").readText(Charsets.UTF_8).removePrefix("\uFEFF")
            )
            val supported = buildSet {
                val versions = json.optJSONArray("supported_versions") ?: return@buildSet
                for (index in 0 until versions.length()) add(versions.getString(index))
            }
            val supportedAbis = buildSet {
                val abis = json.optJSONArray("supported_abis")
                if (abis == null) {
                    // Existing launcher modules were built as ARM64-only payloads. Treat missing
                    // metadata as arm64-v8a so older staged modules fail closed on 32-bit games.
                    add("arm64-v8a")
                } else {
                    for (index in 0 until abis.length()) {
                        val abi = abis.getString(index).trim()
                        if (abi.isNotEmpty()) add(abi)
                    }
                }
            }

            val packageName = json.optString(
                "package_name",
                json.optString("target_package")
            )
            require(packageName.isNotBlank()) { "Module package name is required" }
            require(packageName == directory.name) { "Module directory must match package_name" }

            ModuleConfig(
                packageName = packageName,
                title = json.optString("title", json.optString("game_name", packageName)),
                supportedVersions = supported,
                supportedAbis = supportedAbis,
                entryPoint = json.optString(
                    "entry_point",
                    json.optString("entry_class")
                ).takeIf { it.isNotBlank() },
                dexFile = json.optString("dex_file", "classes.dex"),
                nativeFile = json.optString("native_file", "libmenu_native.so"),
                iconFile = json.optString("icon_file").takeIf { it.isNotBlank() },
                nonRootMethod = NonRootMethod.fromJson(
                    json.optString("nonroot_method").takeIf { it.isNotBlank() },
                    packageName
                ),
                catalogSlug = json.optString("module_slug").takeIf { it.matches(SLUG) }
            ).also {
                require(it.supportedAbis.isNotEmpty()) { "Module supported_abis must not be empty" }
                require(it.supportedAbis.all { abi -> abi in SUPPORTED_ABI_NAMES }) {
                    "Module contains an unsupported ABI name"
                }
                require(it.dexFile == "classes.dex") { "Launcher modules must use classes.dex" }
                require(it.nativeFile == "libmenu_native.so") {
                    "Launcher modules must use libmenu_native.so"
                }
                require(File(directory, it.dexFile).isFile) { "Module DEX is missing" }
                require(File(directory, it.nativeFile).isFile) { "Module native library is missing" }
            }
        }.getOrNull()
    }

    internal fun loadCandidate(directory: File): ModuleConfig? = parseConfig(directory)

    fun directoryFor(module: ModuleConfig): File = File(menuDirectory, module.packageName)

    fun isInstalled(packageName: String): Boolean {
        val directory = File(menuDirectory, packageName)
        return directory.isDirectory &&
            (
                File(directory, ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE).isFile ||
                    File(directory, LOCAL_TEST_INSTALL_MARKER).isFile ||
                    File(directory, EMBEDDED_PRIVATE_INSTALL_MARKER).isFile
                ) &&
            parseConfig(directory) != null
    }

    fun isLocalTest(packageName: String): Boolean {
        if (!PACKAGE_NAME.matches(packageName)) return false
        return File(File(menuDirectory, packageName), LOCAL_TEST_INSTALL_MARKER).isFile
    }

    fun embeddedPrivateScope(packageName: String): String? {
        if (!PACKAGE_NAME.matches(packageName)) return null
        return runCatching {
            val directory = File(menuDirectory, packageName).canonicalFile
            require(directory.parentFile == menuDirectory.canonicalFile)
            val markerFile = File(directory, EMBEDDED_PRIVATE_INSTALL_MARKER)
            require(markerFile.isFile && markerFile.canonicalFile.parentFile == directory)
            val marker = JSONObject(markerFile.readText(Charsets.UTF_8))
            require(marker.optInt("schema") == 1)
            require(marker.optString("packageName") == packageName)
            marker.getString("scope").also { require(it.matches(PRIVATE_SCOPE)) }
        }.getOrNull()
    }

    fun downloadedPrivateScope(packageName: String): String? {
        if (!PACKAGE_NAME.matches(packageName)) return null
        return runCatching {
            val directory = File(menuDirectory, packageName).canonicalFile
            require(directory.parentFile == menuDirectory.canonicalFile)
            val markerFile = File(directory, PRIVATE_INSTALL_MARKER)
            require(markerFile.isFile && markerFile.canonicalFile.parentFile == directory)
            val marker = JSONObject(markerFile.readText(Charsets.UTF_8))
            require(marker.optInt("schema") == 1)
            require(marker.optString("packageName") == packageName)
            marker.getString("scope").also { require(it.matches(PRIVATE_SCOPE)) }
        }.getOrNull()
    }

    fun privateScope(packageName: String): String? =
        embeddedPrivateScope(packageName) ?: downloadedPrivateScope(packageName)

    fun embeddedPrivateModules(): Map<String, String> {
        if (!menuDirectory.isDirectory) return emptyMap()
        return menuDirectory.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.mapNotNull { directory ->
                embeddedPrivateScope(directory.name)?.let { scope -> directory.name to scope }
            }
            ?.toMap()
            .orEmpty()
    }

    fun privateModules(): Map<String, String> {
        if (!menuDirectory.isDirectory) return emptyMap()
        return menuDirectory.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.mapNotNull { directory ->
                privateScope(directory.name)?.let { scope -> directory.name to scope }
            }
            ?.toMap()
            .orEmpty()
    }

    fun installedBuild(packageName: String): Long {
        val update = File(File(menuDirectory, packageName), "update.json")
        return runCatching {
            if (update.isFile) JSONObject(update.readText()).optLong("build", 0L) else 0L
        }.getOrDefault(0L)
    }

    fun installedSlug(packageName: String): String? {
        if (!PACKAGE_NAME.matches(packageName)) return null
        val directory = File(menuDirectory, packageName)
        parseConfig(directory)?.catalogSlug?.let { return it }
        return runCatching {
            val manifest = File(directory, ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE)
            require(manifest.isFile && manifest.canonicalFile.parentFile == directory.canonicalFile)
            SignedEnvelopeVerifier.payload(JSONObject(manifest.readText(Charsets.UTF_8)))
                .getString("slug")
                .also { require(it.matches(SLUG)) }
        }.getOrNull()
    }

    fun isInLibrary(packageName: String): Boolean {
        if (!PACKAGE_NAME.matches(packageName)) return false
        val directory = File(menuDirectory, packageName)
        return directory.isDirectory && (
            File(directory, ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE).isFile ||
                File(directory, LOCAL_TEST_INSTALL_MARKER).isFile ||
                File(directory, EMBEDDED_PRIVATE_INSTALL_MARKER).isFile ||
                File(directory, "update.json").isFile ||
                File(directory, "config.json").isFile
            )
    }

    /** Removes only launcher-owned support. The original Android game is never touched. */
    fun removeFromLibrary(packageName: String) {
        require(PACKAGE_NAME.matches(packageName)) { "Invalid module package name" }
        val root = menuDirectory.canonicalFile
        val directory = File(root, packageName).canonicalFile
        require(directory.parentFile == root && directory.name == packageName) {
            "Module directory escaped the launcher repository"
        }
        if (!directory.exists()) return
        require(directory.isDirectory) { "Module repository entry is not a directory" }
        check(directory.deleteRecursively()) { "Could not remove launcher support for $packageName" }
    }

    companion object {
        const val LOCAL_TEST_INSTALL_MARKER = "local-test.json"
        const val EMBEDDED_PRIVATE_INSTALL_MARKER = "embedded-private.json"
        const val PRIVATE_INSTALL_MARKER = "private-scope.json"
        private val PACKAGE_NAME = Regex("^[A-Za-z0-9_.]+$")
        private val PRIVATE_SCOPE = Regex("[a-z0-9][a-z0-9._-]{2,63}")
        private val SLUG = Regex("[a-z0-9][a-z0-9-]{0,63}")
        private val SUPPORTED_ABI_NAMES = setOf(
            "armeabi-v7a",
            "arm64-v8a",
            "x86",
            "x86_64"
        )
    }
}
