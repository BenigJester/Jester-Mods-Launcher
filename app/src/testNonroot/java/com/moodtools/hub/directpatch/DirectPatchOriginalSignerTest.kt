package com.moodtools.hub.directpatch

import com.moodtools.hub.LauncherGmsCoreTranslations
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DirectPatchOriginalSignerTest {
    @Test fun `Google login still requires original signer and disabling it does not invent one`() {
        requireOriginalGameSigner("", "")
        requireOriginalGameSigner("google-sdk-routing-v2", "a".repeat(40))
        for (invalid in listOf("", "a".repeat(39), "A".repeat(40), "a".repeat(64), "not-a-signature")) {
            val failure = assertThrows(IllegalArgumentException::class.java) {
                requireOriginalGameSigner("google-sdk-routing-v2", invalid)
            }
            assertEquals(ORIGINAL_GAME_INFORMATION_MISSING, failure.message)
            assertTrue(failure.message!!.contains("Do not uninstall or clear game data"))
        }
    }

    @Test fun `preflight precedes compatibility and patch output work and rewrite retains defense`() {
        val path = "src/nonroot/java/com/moodtools/hub/directpatch/DirectPackagePatchManager.kt"
        val source = listOf(File(path), File("app/$path")).first(File::isFile).readText()
        val prepare = source.substringAfter("fun prepare(").substringBefore("private fun rewriteBase(")
        val check = prepare.indexOf("requireOriginalGameSigner(gmsProfile, originalSigner)")
        assertTrue(check >= 0)
        for (later in listOf("ModuleCompatibility.fromNative", "val signingMaterial = signingMaterial()",
                "recreateDirectory(outputDirectory)", "rewriteBase(")) {
            assertTrue(later, prepare.indexOf(later) > check)
        }
        assertTrue(source.substringAfter("private fun rewriteBase(")
            .contains("requireOriginalGameSigner(gmsProfile, originalSigner)"))
        // Preserve the existing no-guess rule and the explicit legacy migration path.
        val resolver = source.substringAfter("private fun originalGameSigner(")
            .substringBefore("private fun expectedGameNativeSha256(")
        assertTrue(resolver.contains("if (!alreadyPatched)"))
        assertTrue(resolver.contains("LegacyGmsPatchMigration.PROFILE"))
        assertTrue(resolver.contains("return \"\""))
    }

    @Test fun `save safe guidance is translated in all nine local languages`() {
        val translations = LauncherGmsCoreTranslations.entries.getValue(ORIGINAL_GAME_INFORMATION_MISSING)
        assertEquals(9, translations.size)
        assertTrue(translations.all { it.isNotBlank() && it != ORIGINAL_GAME_INFORMATION_MISSING })
    }
}
