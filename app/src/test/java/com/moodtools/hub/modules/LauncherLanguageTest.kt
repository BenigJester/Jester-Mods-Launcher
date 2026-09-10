package com.moodtools.hub.modules

import com.moodtools.hub.LauncherLocalization
import com.moodtools.hub.LauncherAdditionalTranslations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLanguageTest {
    @Test
    fun preferenceOrderMatchesOfflineTranslator() {
        assertEquals(LauncherLanguage.English, LauncherLanguage.fromPreference(0))
        assertEquals(LauncherLanguage.Filipino, LauncherLanguage.fromPreference(1))
        assertEquals(LauncherLanguage.Korean, LauncherLanguage.fromPreference(2))
        assertEquals(LauncherLanguage.Japanese, LauncherLanguage.fromPreference(3))
        assertEquals(LauncherLanguage.ChineseSimplified, LauncherLanguage.fromPreference(4))
        assertEquals(LauncherLanguage.Spanish, LauncherLanguage.fromPreference(5))
        assertEquals(LauncherLanguage.Vietnamese, LauncherLanguage.fromPreference(6))
        assertEquals(LauncherLanguage.Indonesian, LauncherLanguage.fromPreference(7))
        assertEquals(LauncherLanguage.Portuguese, LauncherLanguage.fromPreference(8))
        assertEquals(LauncherLanguage.English, LauncherLanguage.fromPreference(99))
    }

    @Test
    fun themePreferenceUsesMidnightAsSafeFallback() {
        assertEquals(LauncherTheme.Midnight, LauncherTheme.fromPreference(0))
        assertEquals(LauncherTheme.Aurora, LauncherTheme.fromPreference(1))
        assertEquals(LauncherTheme.Royal, LauncherTheme.fromPreference(2))
        assertEquals(LauncherTheme.Ember, LauncherTheme.fromPreference(3))
        assertEquals(LauncherTheme.Ocean, LauncherTheme.fromPreference(4))
        assertEquals(LauncherTheme.Sakura, LauncherTheme.fromPreference(5))
        assertEquals(LauncherTheme.Obsidian, LauncherTheme.fromPreference(6))
        assertEquals(LauncherTheme.Midnight, LauncherTheme.fromPreference(-1))
    }

    @Test
    fun launcherChromeTranslatesOfflineForEveryLanguage() {
        LauncherLanguage.entries.drop(1).forEach { language ->
            val translated = LauncherLocalization.translate("Settings", language)
            assertTrue(translated.isNotBlank())
            assertNotEquals("Settings", translated)
        }
        assertEquals("Unknown server title", LauncherLocalization.translate("Unknown server title", LauncherLanguage.Japanese))
    }

    @Test
    fun launcherScreensAndDynamicLabelsTranslateOffline() {
        val screenText = listOf(
            "A NEW ERA AWAITS",
            "Your support code",
            "Search update activity",
            "ADD ADD-ON",
            "Original game needed",
            "How exact-package shell works",
            "Support code copied"
        )
        LauncherLanguage.entries.drop(1).forEach { language ->
            screenText.forEach { text ->
                assertTrue(LauncherLocalization.translate(text, language).isNotBlank())
            }
            assertNotEquals(
                "Choose your preferred language for Jester Mods.",
                LauncherLocalization.translate("Choose your preferred language for Jester Mods.", language)
            )
            assertNotEquals(
                "Your choice is saved offline on this device.",
                LauncherLocalization.translate("Your choice is saved offline on this device.", language)
            )
            assertNotEquals("Game version 3.0.4", LauncherLocalization.translate("Game version 3.0.4", language))
            assertNotEquals("Sort: Recommended", LauncherLocalization.translate("Sort: Recommended", language))
            assertNotEquals("Copied support code", LauncherLocalization.translate("Copied support code", language))
            assertNotEquals("Help", LauncherLocalization.translate("Help", language))
            assertNotEquals("Open issue page", LauncherLocalization.translate("Open issue page", language))
        }
    }

    @Test
    fun previouslyHardcodedLauncherStatusAndAccessibilityTextTranslateOffline() {
        val staticText = listOf(
            "Preparing download",
            "Account identity",
            "Installation permission is needed",
            "Settings, launcher update available",
            "Limited access public add-on. In-game eligibility requirements may apply."
        )
        LauncherLanguage.entries.drop(1).forEach { language ->
            staticText.forEach { text ->
                assertNotEquals(text, LauncherLocalization.translate(text, language))
            }
            val progress = LauncherLocalization.translate("Download 73 percent complete", language)
            assertNotEquals("Download 73 percent complete", progress)
            assertTrue(progress.contains("73"))

            val selection = LauncherLocalization.translate("Select Soul Knight", language)
            assertNotEquals("Select Soul Knight", selection)
            assertTrue(selection.contains("Soul Knight"))
        }
    }

    @Test
    fun generatedLauncherTranslationsPreserveEveryDynamicValue() {
        LauncherAdditionalTranslations.sourceText.forEach { source ->
            val placeholders = Regex("\\{(\\d+)}").findAll(source)
                .map { it.groupValues[1].toInt() }
                .toSet()
            val rendered = placeholders.fold(source) { text, index ->
                text.replace("{$index}", "VALUE_$index")
            }
            LauncherLanguage.entries.drop(1).forEach { language ->
                val translated = LauncherLocalization.translate(rendered, language)
                assertTrue("Blank $language translation for $source", translated.isNotBlank())
                placeholders.forEach { index ->
                    assertTrue(
                        "$language translation lost placeholder {$index} for $source",
                        translated.contains("VALUE_$index")
                    )
                }
            }
        }
    }
}
