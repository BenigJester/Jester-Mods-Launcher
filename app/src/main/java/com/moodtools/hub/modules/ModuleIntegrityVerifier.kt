package com.moodtools.hub.modules

import com.moodtools.hub.BuildConfig
import com.moodtools.hub.networking.SignedEnvelopeVerifier
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

data class VerifiedModuleInstall(
    val build: Long,
    val version: String,
    val abi: String
)

/** Authenticates the installed module and hashes its exact launch payloads without using the network. */
class ModuleIntegrityVerifier(
    private val publicKeyDerBase64: String = BuildConfig.UPDATE_PUBLIC_KEY_DER_BASE64
) {
    fun verify(
        moduleDirectory: File,
        module: ModuleConfig,
        abi: String,
        bootstrap: Int = 1
    ): VerifiedModuleInstall {
        require(module.packageName.matches(PACKAGE_PATTERN)) { "Invalid module package name" }
        require(moduleDirectory.name == module.packageName) { "Module directory does not match its package" }
        require(abi == "arm64-v8a" || abi == "armeabi-v7a") { "Unsupported game ABI" }
        require(bootstrap >= 1) { "Invalid launcher bootstrap" }

        if (File(moduleDirectory, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).isFile) {
            return verifyLocalTest(moduleDirectory, module, abi)
        }
        if (File(moduleDirectory, ModuleRepository.EMBEDDED_PRIVATE_INSTALL_MARKER).isFile) {
            return verifyEmbeddedPrivate(moduleDirectory, module, abi)
        }

        val signedManifest = regularFile(moduleDirectory, SIGNED_MANIFEST_FILE)
        require(signedManifest.length() in 2..MAX_MANIFEST_BYTES) { "Signed module manifest size is invalid" }
        val envelope = JSONObject(signedManifest.readText(Charsets.UTF_8))
        val payload = SignedEnvelopeVerifier.payload(envelope, publicKeyDerBase64)
        require(payload.getInt("schema") == 1) { "Unsupported module manifest schema" }
        require(payload.getString("audience") == "moodtools-standalone") {
            "Signed manifest has the wrong audience"
        }
        require(payload.getString("packageName") == module.packageName) {
            "Signed manifest belongs to another package"
        }
        val signedPrivateScope = payload.optString("privateScope").takeIf(String::isNotBlank)
        signedPrivateScope?.let { require(it.matches(PRIVATE_SCOPE_PATTERN)) }
        val privateMarkerFile = File(moduleDirectory, ModuleRepository.PRIVATE_INSTALL_MARKER)
        if (signedPrivateScope == null) {
            require(!privateMarkerFile.exists()) { "A public module contains a private scope marker" }
        } else {
            val marker = JSONObject(regularFile(moduleDirectory, ModuleRepository.PRIVATE_INSTALL_MARKER)
                .readText(Charsets.UTF_8))
            require(marker.optInt("schema") == 1 && marker.optString("packageName") == module.packageName &&
                marker.optString("scope") == signedPrivateScope) {
                "The private module scope does not match its signed manifest"
            }
        }
        require(payload.optInt("minimumBootstrap", 1) <= bootstrap) {
            "Module requires a newer launcher bootstrap"
        }

        val slug = payload.getString("slug").also { require(it.matches(SLUG_PATTERN)) }
        module.catalogSlug?.let { require(it == slug) { "Module belongs to another catalog publication" } }
        val build = payload.getLong("build").also { require(it > 0L) }
        val version = payload.getString("version").trim().also {
            require(it.isNotEmpty() && it.length <= 64)
        }
        val signedConfig = payload.getJSONObject("moduleConfig")
        require(signedConfig.getString("packageName") == module.packageName)
        require(signedConfig.getString("dexFile") == DEX_FILE)
        require(signedConfig.getString("nativeFile") == NATIVE_FILE)
        val title = signedConfig.getString("title")
        require(title.isNotBlank() && title.length <= 120)
        val entryPoint = signedConfig.getString("entryPoint")
        require(entryPoint.isNotBlank() && entryPoint.length <= 240)
        val supportedVersions = stringSet(signedConfig.getJSONArray("supportedVersions"))
        val supportedAbis = stringSet(signedConfig.getJSONArray("supportedAbis"))
        val nonRootMethod = NonRootMethod.fromJson(
            signedConfig.optString("nonrootMethod").takeIf { it.isNotBlank() },
            module.packageName
        )
        val nonRootMethods = NonRootMethod.choicesFromJson(
            signedConfig.optJSONArray("nonrootMethods")?.let { values ->
                List(values.length()) { index -> values.getString(index) }
            },
            nonRootMethod
        )
        require(supportedVersions.isNotEmpty() && supportedAbis.isNotEmpty())
        require(supportedAbis.all { it == "arm64-v8a" || it == "armeabi-v7a" })
        require(abi in supportedAbis) { "Signed module does not support the installed game ABI" }

        require(module.title == title && module.entryPoint == entryPoint)
        require(module.supportedVersions == supportedVersions && module.supportedAbis == supportedAbis)
        require(module.nonRootMethod == nonRootMethod)
        require(module.nonRootMethods == nonRootMethods)
        require(module.dexFile == DEX_FILE && module.nativeFile == NATIVE_FILE && module.iconFile == null)

        val expectedConfig = JSONObject()
            .put("package_name", module.packageName)
            .put("module_slug", slug)
            .put("title", title)
            .put("supported_versions", signedConfig.getJSONArray("supportedVersions"))
            .put("supported_abis", signedConfig.getJSONArray("supportedAbis"))
            .put("entry_point", entryPoint)
            .put("dex_file", DEX_FILE)
            .put("native_file", NATIVE_FILE)
            .also {
                if (signedConfig.has("nonrootMethod")) {
                    it.put("nonroot_method", nonRootMethod.jsonValue)
                }
                if (signedConfig.has("nonrootMethods")) {
                    it.put(
                        "nonroot_methods",
                        JSONArray(nonRootMethods.map(NonRootMethod::jsonValue))
                    )
                }
            }
            .toString()
        val configFile = regularFile(moduleDirectory, CONFIG_FILE)
        val installedConfig = configFile.readText(Charsets.UTF_8)
        val legacyConfig = JSONObject(expectedConfig).apply { remove("module_slug") }.toString()
        require(configFile.length() in 2..MAX_CONFIG_BYTES &&
            (installedConfig == expectedConfig || installedConfig == legacyConfig)) {
            "Module configuration does not match its signed manifest"
        }

        val files = payload.getJSONObject("files")
        val native = files.getJSONObject("native").getJSONObject(abi)
        val dex = files.getJSONObject("dex")
        require(native.getString("path") ==
            "/api/launcher-module-payload/$slug/$build/native/$abi")
        require(dex.getString("path") ==
            "/api/launcher-module-payload/$slug/$build/dex/classes.dex")
        verifyFile(regularFile(moduleDirectory, NATIVE_FILE), native, MAX_NATIVE_BYTES)
        verifyFile(regularFile(moduleDirectory, DEX_FILE), dex, MAX_DEX_BYTES)

        return VerifiedModuleInstall(build, version, abi)
    }

    private fun verifyLocalTest(
        moduleDirectory: File,
        module: ModuleConfig,
        abi: String
    ): VerifiedModuleInstall {
        val marker = regularFile(moduleDirectory, ModuleRepository.LOCAL_TEST_INSTALL_MARKER)
        require(marker.length() <= MAX_LOCAL_TEST_MARKER_BYTES) {
            "Local test marker size is invalid"
        }

        require(module.packageName.matches(PACKAGE_PATTERN)) { "Invalid module package name" }
        require(module.title.isNotBlank() && module.title.length <= 120) {
            "Local test module title is invalid"
        }
        val entryPoint = module.entryPoint
        require(entryPoint?.isNotBlank() == true && entryPoint.length <= 240) {
            "Local test entry point is invalid"
        }
        require(module.supportedVersions.isNotEmpty() && module.supportedVersions.size <= 100) {
            "Local test supported versions are invalid"
        }
        require(module.supportedVersions.all { it.isNotBlank() && it.length <= 120 }) {
            "Local test supported versions are invalid"
        }
        require(module.supportedAbis.isNotEmpty() && module.supportedAbis.size <= 4) {
            "Local test supported ABIs are invalid"
        }
        require(module.supportedAbis.all { it == "arm64-v8a" || it == "armeabi-v7a" }) {
            "Local test contains an unsupported ABI"
        }
        require(abi in module.supportedAbis) {
            "Local test module does not support the installed game ABI"
        }
        require(module.dexFile == DEX_FILE && module.nativeFile == NATIVE_FILE && module.iconFile == null)

        val configFile = regularFile(moduleDirectory, CONFIG_FILE)
        require(configFile.length() in 2..MAX_CONFIG_BYTES) {
            "Local test module configuration size is invalid"
        }
        val config = JSONObject(configFile.readText(Charsets.UTF_8))
        val configuredMethod = NonRootMethod.fromJson(
            config.optString("nonroot_method").takeIf { it.isNotBlank() },
            module.packageName
        )
        require(configuredMethod == module.nonRootMethod) {
            "Local test module configuration does not match its non-root method"
        }
        require(NonRootMethod.choicesFromJson(
            config.optJSONArray("nonroot_methods")?.let { values ->
                List(values.length()) { index -> values.getString(index) }
            },
            configuredMethod
        ) == module.nonRootMethods) {
            "Local test module configuration does not match its non-root method choices"
        }
        require(config.optString("package_name", config.optString("target_package")) == module.packageName) {
            "Local test module configuration belongs to another package"
        }
        require(config.optString("entry_point", config.optString("entry_class")) == entryPoint) {
            "Local test module configuration does not match its entry point"
        }
        require(config.optString("dex_file", DEX_FILE) == DEX_FILE)
        require(config.optString("native_file", NATIVE_FILE) == NATIVE_FILE)

        val native = regularFile(moduleDirectory, NATIVE_FILE)
        val dex = regularFile(moduleDirectory, DEX_FILE)
        require(native.length() in 1..MAX_NATIVE_BYTES) {
            "Local test native payload size is invalid"
        }
        require(dex.length() in 1..MAX_DEX_BYTES) {
            "Local test DEX payload size is invalid"
        }

        return VerifiedModuleInstall(0L, "local-test", abi)
    }

    private fun verifyEmbeddedPrivate(
        moduleDirectory: File,
        module: ModuleConfig,
        abi: String
    ): VerifiedModuleInstall {
        require(abi in module.supportedAbis)
        val markerFile = regularFile(moduleDirectory, ModuleRepository.EMBEDDED_PRIVATE_INSTALL_MARKER)
        require(markerFile.length() in 2..MAX_PRIVATE_MARKER_BYTES)
        val marker = JSONObject(markerFile.readText(Charsets.UTF_8))
        require(marker.optInt("schema") == 1)
        require(marker.optString("scope").matches(PRIVATE_SCOPE_PATTERN))
        require(marker.optString("packageName") == module.packageName)
        require(marker.optString("bundleSha256").matches(SHA256_PATTERN))
        if (BuildConfig.PRIVATE_MODULE_ENABLED &&
            module.packageName == BuildConfig.PRIVATE_MODULE_PACKAGE) {
            require(marker.optString("scope") == BuildConfig.PRIVATE_MODULE_SCOPE)
            require(marker.optString("bundleSha256") == BuildConfig.PRIVATE_MODULE_SHA256)
        }
        val hashes = marker.getJSONObject("files")
        listOf(CONFIG_FILE, DEX_FILE, NATIVE_FILE).forEach { name ->
            val expected = hashes.getString(name)
            require(expected.matches(SHA256_PATTERN) && sha256(regularFile(moduleDirectory, name)) == expected) {
                "Embedded private module file $name failed verification"
            }
        }
        return VerifiedModuleInstall(0L, "embedded-private", abi)
    }

    private fun regularFile(directory: File, name: String): File {
        val file = File(directory, name)
        require(file.isFile && !Files.isSymbolicLink(file.toPath())) { "Module file $name is missing" }
        require(file.canonicalFile.parentFile == directory.canonicalFile) { "Module file path is unsafe" }
        return file
    }

    private fun verifyFile(file: File, signed: JSONObject, maximumBytes: Long) {
        val expectedSize = signed.getLong("size")
        val expectedHash = signed.getString("sha256")
        require(expectedSize in 1..maximumBytes && file.length() == expectedSize) {
            "Module file ${file.name} has the wrong size"
        }
        require(expectedHash.matches(SHA256_PATTERN) && sha256(file) == expectedHash) {
            "Module file ${file.name} failed SHA-256 verification"
        }
    }

    private fun stringSet(array: JSONArray): Set<String> {
        require(array.length() in 1..100)
        val values = LinkedHashSet<String>()
        for (index in 0 until array.length()) {
            val value = array.getString(index)
            require(value.isNotBlank() && value.length <= 120 && values.add(value)) {
                "Signed module list contains an invalid or duplicate value"
            }
        }
        return values
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val SIGNED_MANIFEST_FILE = "signed-manifest.json"
        private const val CONFIG_FILE = "config.json"
        private const val DEX_FILE = "classes.dex"
        private const val NATIVE_FILE = "libmenu_native.so"
        private const val MAX_MANIFEST_BYTES = 256L * 1024L
        private const val MAX_CONFIG_BYTES = 64L * 1024L
        private const val MAX_DEX_BYTES = 100L * 1024L * 1024L
        private const val MAX_NATIVE_BYTES = 100L * 1024L * 1024L
        private const val MAX_LOCAL_TEST_MARKER_BYTES = 4L * 1024L
        private const val MAX_PRIVATE_MARKER_BYTES = 4L * 1024L
        private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_.]{3,200}")
        private val SLUG_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}")
        private val PRIVATE_SCOPE_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,63}")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
