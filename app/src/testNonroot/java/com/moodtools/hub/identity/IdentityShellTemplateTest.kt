package com.moodtools.hub.identity

import com.moodtools.hub.soulpatch.BinaryXmlStringPool
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityShellTemplateTest {
    @Test
    fun `native library selection is limited to the requested abi`() {
        assertTrue(isNativeLibraryForAbi("lib/arm64-v8a/libgame.so", "arm64-v8a"))
        assertFalse(isNativeLibraryForAbi("lib/armeabi-v7a/libgame.so", "arm64-v8a"))
        assertFalse(isNativeLibraryForAbi("lib/arm64-v8a/nested/libgame.so", "arm64-v8a"))
        assertFalse(isNativeLibraryForAbi("assets/libgame.so", "arm64-v8a"))
        assertFalse(
            shouldCopyNativeLibrary(
                "lib/arm64-v8a/libblackbox.so",
                "arm64-v8a",
                setOf("lib/arm64-v8a/libblackbox.so")
            )
        )
        assertTrue(
            shouldCopyNativeLibrary(
                "lib/arm64-v8a/libgame.so",
                "arm64-v8a",
                setOf("lib/arm64-v8a/libblackbox.so")
            )
        )
    }

    @Test
    fun `template contains replaceable identity and branding resources`() {
        val template = File(
            "build/generated/identityShellTemplateAssets/nonroot/identity-shell/template.apk"
        )
        assertTrue("Generated identity-shell template is missing", template.isFile)
        ZipFile(template).use { archive ->
            val manifest = archive.getInputStream(
                requireNotNull(archive.getEntry("AndroidManifest.xml"))
            ).readBytes()
            val branded = BinaryXmlStringPool.replaceManifestPackageVersion(
                BinaryXmlStringPool.replaceSubstring(
                    BinaryXmlStringPool.replaceExact(
                        BinaryXmlStringPool.replaceExact(
                            BinaryXmlStringPool.replaceExact(
                                manifest,
                                "__IDENTITY_SHELL_LABEL__",
                                "Example Game"
                            ),
                            "__IDENTITY_LAUNCHER_PACKAGE__",
                            "com.example.launcher"
                        ),
                        "__IDENTITY_PAYLOAD_AUTHORITY__",
                        "com.example.launcher.identity-payload"
                    ),
                    "com.moodtools.identity.template",
                    "com.example.game"
                ),
                "__IDENTITY_GAME_VERSION__",
                "8.5.1",
                80512
            )
            // A second rewrite proves both generated values are present in the rebuilt pool.
            BinaryXmlStringPool.replaceExact(branded, "Example Game", "Example Game 2")
            BinaryXmlStringPool.replaceSubstring(branded, "com.example.game", "com.example.other")
            BinaryXmlStringPool.replaceExact(
                branded,
                "com.example.launcher.identity-payload",
                "com.example.debug.identity-payload"
            )
            BinaryXmlStringPool.replaceExact(branded, "8.5.1", "8.5.2")
            assertEquals(
                BinaryXmlStringPool.ManifestPackageVersion("8.5.1", 80512),
                BinaryXmlStringPool.manifestPackageVersion(branded)
            )
            val resourceTable = archive.getInputStream(
                requireNotNull(archive.getEntry("resources.arsc"))
            ).readBytes().toString(Charsets.ISO_8859_1)
            listOf(
                "identity_shell_icon",
                "identity_shell_icon_foreground_bitmap",
                "identity_shell_icon_background_bitmap"
            ).forEach { name ->
                assertTrue("Missing compiled drawable/$name", resourceTable.contains(name))
            }
            val dex = archive.getInputStream(
                requireNotNull(archive.getEntry("classes.dex"))
            ).readBytes().toString(Charsets.ISO_8859_1)
            assertTrue("Identity-shell launch guard was stripped", dex.contains("IdentityLaunchGuard"))
            assertTrue(
                "Identity-shell shared launch-mode directory was stripped",
                dex.contains("getDataFilesDir")
            )
            assertTrue(
                "Identity-shell compatibility-only entry was stripped",
                dex.contains("loadNativeForIdentityShellCompatibility")
            )
            assertTrue(
                "Exact-package native-free compatibility marker was stripped",
                dex.contains("protocol-15 native-free guest")
            )
            assertFalse(
                "Identity shell must not start an automatic public logcat capture",
                dex.contains("_logcat.txt") || dex.contains("Download/logs")
            )
        }
    }
}
