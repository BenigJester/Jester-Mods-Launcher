package com.moodtools.hub

import com.moodtools.hub.modules.LauncherLanguage
import org.junit.Assert.*
import org.junit.Test

class LaunchDiagnosticStatusTest {
    @Test fun `only uncompleted startup times out and intentional stops are not stalls`() {
        assertEquals("Startup in progress", launchDiagnosticStatus(false, false, false, 59_999))
        assertEquals("Possible startup stall", launchDiagnosticStatus(false, false, false, 60_000))
        assertEquals("Game activity opened", launchDiagnosticStatus(false, true, false, 90_000))
        assertEquals("Session stopped", launchDiagnosticStatus(false, false, true, 90_000))
        assertEquals("Crash or startup error recorded", launchDiagnosticStatus(true, true, true, 10))
        assertEquals("Guest process exited during startup", launchDiagnosticStatus(false, false, false, 10, true))
        assertEquals("Session stopped", launchDiagnosticStatus(false, false, true, 10, true))
        assertEquals("Game activity opened", launchDiagnosticStatus(false, true, false, 10, true))
    }
    @Test fun `every new diagnostic label has all supported translations`() {
        LauncherDiagnosticTranslations.entries.forEach { (source, translations) ->
            assertEquals(source, LauncherLanguage.entries.size - 1, translations.size)
            LauncherLanguage.entries.drop(1).forEach { language ->
                val translated = LauncherLocalization.translate(source, language)
                assertTrue("$source / $language", translated.isNotBlank() && translated != source)
            }
        }
    }
}
