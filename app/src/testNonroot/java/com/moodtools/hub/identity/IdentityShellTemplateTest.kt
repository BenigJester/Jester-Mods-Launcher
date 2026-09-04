package com.moodtools.hub.identity

import com.moodtools.hub.soulpatch.BinaryXmlStringPool
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityShellTemplateTest {
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
            val branded = BinaryXmlStringPool.replaceSubstring(
                BinaryXmlStringPool.replaceExact(
                    manifest,
                    "__IDENTITY_SHELL_LABEL__",
                    "Example Game"
                ),
                "com.moodtools.identity.template",
                "com.example.game"
            )
            // A second rewrite proves both generated values are present in the rebuilt pool.
            BinaryXmlStringPool.replaceExact(branded, "Example Game", "Example Game 2")
            BinaryXmlStringPool.replaceSubstring(branded, "com.example.game", "com.example.other")
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
            assertFalse(
                "Identity shell must not start an automatic public logcat capture",
                dex.contains("_logcat.txt") || dex.contains("Download/logs")
            )
        }
    }
}
