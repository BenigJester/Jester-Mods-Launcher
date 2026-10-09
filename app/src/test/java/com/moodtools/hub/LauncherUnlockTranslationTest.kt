package com.moodtools.hub

import com.moodtools.hub.modules.LauncherLanguage
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherUnlockTranslationTest {
    @Test
    fun unlockActionsAndInstructionsAreTranslatedInEveryLanguage() {
        val labels = listOf(
            "Use free 1-day Linkvertise access",
            "Complete the free Linkvertise route once to activate this launcher for one day. When the website confirms your route, tap Open launcher to come back here.",
            "Linkvertise Tutorial",
            "Could not open tutorial.",
            "Activate access key",
            "Hide details"
        )
        LauncherLanguage.entries.filter { it != LauncherLanguage.English }.forEach { language ->
            labels.forEach { label ->
                val translated = LauncherLocalization.translate(label, language)
                assertTrue("$language: $label", translated.isNotBlank())
                // Indonesian uses the same natural wording for the tutorial label.
                if (language != LauncherLanguage.Indonesian || label != "Linkvertise Tutorial") {
                    assertNotEquals("$language: $label", label, translated)
                }
            }
        }
    }
}
