package com.moodtools.hub.modules

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

class ModuleIntegrityVerifierTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val keys: KeyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.generateKeyPair()

    @Test
    fun verifiesPersistedSignedManifestAndExactPayloads() {
        val fixture = fixture()

        val verified = fixture.verifier.verify(fixture.directory, fixture.module, ABI)

        assertEquals(41L, verified.build)
        assertEquals("4.1.0", verified.version)
        assertEquals(ABI, verified.abi)
    }

    @Test
    fun verifiesSignedNonRootMethodMetadata() {
        val fixture = fixture(NonRootMethod.DIRECT_PATCH)

        fixture.verifier.verify(fixture.directory, fixture.module, ABI)

        assertEquals(NonRootMethod.DIRECT_PATCH, fixture.module.nonRootMethod)
    }

    @Test
    fun rejectsDexContentTamperingEvenWhenSizeIsUnchanged() {
        val fixture = fixture()
        File(fixture.directory, "classes.dex").writeBytes("signed-dex-X".toByteArray())

        assertFailure(fixture, "SHA-256")
    }

    @Test
    fun rejectsNativePayloadTruncation() {
        val fixture = fixture()
        File(fixture.directory, "libmenu_native.so").writeBytes(byteArrayOf(1, 2, 3))

        assertFailure(fixture, "wrong size")
    }

    @Test
    fun rejectsManifestSignatureTampering() {
        val fixture = fixture()
        val file = File(fixture.directory, ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE)
        val envelope = JSONObject(file.readText())
        envelope.put("signature", Base64.getEncoder().encodeToString(ByteArray(256) { 7 }))
        file.writeText(envelope.toString())

        assertFailure(fixture, "verification failed")
    }

    @Test
    fun rejectsConfigurationRebinding() {
        val fixture = fixture()
        val config = File(fixture.directory, "config.json")
        config.writeText(JSONObject(config.readText()).put("entry_point", "evil.Entry").toString())

        assertFailure(fixture, "configuration")
    }

    @Test
    fun rejectsMissingSignedInstallEvidence() {
        val fixture = fixture()
        File(fixture.directory, ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE).delete()

        assertFailure(fixture, "missing")
    }

    @Test
    fun acceptsLocalTestInstallWithoutSignedManifest() {
        val fixture = fixture()
        File(fixture.directory, ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE).delete()
        File(fixture.directory, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).writeText("{}")

        val verified = fixture.verifier.verify(fixture.directory, fixture.module, ABI)

        assertEquals(0L, verified.build)
        assertEquals("local-test", verified.version)
        assertEquals(ABI, verified.abi)
    }

    @Test
    fun verifiesDownloadedPrivateScopeAgainstSignedManifest() {
        val fixture = fixture(privateScope = "friends-zombie")

        val verified = fixture.verifier.verify(fixture.directory, fixture.module, ABI)

        assertEquals(41L, verified.build)
    }

    @Test
    fun rejectsPrivateManifestWithoutScopeMarker() {
        val fixture = fixture(privateScope = "friends-zombie")
        File(fixture.directory, ModuleRepository.PRIVATE_INSTALL_MARKER).delete()

        assertFailure(fixture, "missing")
    }

    private fun fixture(
        nonRootMethod: NonRootMethod? = null,
        privateScope: String? = null
    ): Fixture {
        val directory = temporary.newFolder(PACKAGE_NAME)
        val dexBytes = "signed-dex-1".toByteArray()
        val nativeBytes = "signed-native-payload".toByteArray()
        File(directory, "classes.dex").writeBytes(dexBytes)
        File(directory, "libmenu_native.so").writeBytes(nativeBytes)

        val supportedVersions = JSONArray().put("3.3.7")
        val supportedAbis = JSONArray().put(ABI)
        val config = JSONObject()
            .put("package_name", PACKAGE_NAME)
            .put("title", "Cooking Madness")
            .put("supported_versions", supportedVersions)
            .put("supported_abis", supportedAbis)
            .put("entry_point", "com.android.support.Main")
            .put("dex_file", "classes.dex")
            .put("native_file", "libmenu_native.so")
            .also { json ->
                nonRootMethod?.let { json.put("nonroot_method", it.jsonValue) }
            }
        File(directory, "config.json").writeText(config.toString())

        val signedModuleConfig = JSONObject()
            .put("packageName", PACKAGE_NAME)
            .put("title", "Cooking Madness")
            .put("supportedVersions", supportedVersions)
            .put("supportedAbis", supportedAbis)
            .put("entryPoint", "com.android.support.Main")
            .put("dexFile", "classes.dex")
            .put("nativeFile", "libmenu_native.so")
            .also { json ->
                nonRootMethod?.let { json.put("nonrootMethod", it.jsonValue) }
            }

        val payload = JSONObject()
            .put("schema", 1)
            .put("audience", "moodtools-standalone")
            .put("slug", "cooking-madness")
            .put("packageName", PACKAGE_NAME)
            .put("build", 41L)
            .put("version", "4.1.0")
            .put("minimumBootstrap", 1)
            .put("notes", "Signed fixture")
            .put("moduleConfig", signedModuleConfig)
            .put("files", JSONObject()
                .put("native", JSONObject().put(ABI, JSONObject()
                    .put("path", "/api/launcher-module-payload/cooking-madness/41/native/$ABI")
                    .put("sha256", sha256(nativeBytes))
                    .put("size", nativeBytes.size)))
                .put("dex", JSONObject()
                    .put("path", "/api/launcher-module-payload/cooking-madness/41/dex/classes.dex")
                    .put("sha256", sha256(dexBytes))
                    .put("size", dexBytes.size)))
            .also { json -> privateScope?.let { json.put("privateScope", it) } }
        val payloadBytes = payload.toString().toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private)
            update(payloadBytes)
            sign()
        }
        val envelope = JSONObject()
            .put("algorithm", "SHA256withRSA")
            .put("payload", Base64.getEncoder().encodeToString(payloadBytes))
            .put("signature", Base64.getEncoder().encodeToString(signature))
        File(directory, ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE).writeText(envelope.toString())
        privateScope?.let {
            File(directory, ModuleRepository.PRIVATE_INSTALL_MARKER).writeText(
                JSONObject()
                    .put("schema", 1)
                    .put("packageName", PACKAGE_NAME)
                    .put("scope", it)
                    .toString()
            )
        }

        return Fixture(
            directory = directory,
            module = ModuleConfig(
                packageName = PACKAGE_NAME,
                title = "Cooking Madness",
                supportedVersions = setOf("3.3.7"),
                supportedAbis = setOf(ABI),
                entryPoint = "com.android.support.Main",
                dexFile = "classes.dex",
                nativeFile = "libmenu_native.so",
                iconFile = null,
                nonRootMethod = nonRootMethod ?: NonRootMethod.INJECTION
            ),
            verifier = ModuleIntegrityVerifier(Base64.getEncoder().encodeToString(keys.public.encoded))
        )
    }

    private fun assertFailure(fixture: Fixture, message: String) {
        val result = runCatching {
            fixture.verifier.verify(fixture.directory, fixture.module, ABI)
        }
        assertTrue(result.isFailure)
        assertTrue(
            "Expected '${result.exceptionOrNull()?.message}' to contain '$message'",
            result.exceptionOrNull()?.message.orEmpty().contains(message, ignoreCase = true)
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private data class Fixture(
        val directory: File,
        val module: ModuleConfig,
        val verifier: ModuleIntegrityVerifier
    )

    companion object {
        private const val PACKAGE_NAME = "com.biglime.cookingmadness"
        private const val ABI = "arm64-v8a"
    }
}
