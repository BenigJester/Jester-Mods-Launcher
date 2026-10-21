package com.moodtools.hub

import com.moodtools.hub.modules.LauncherLanguage
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DiagnosticTutorialTest {
    @Test fun `tutorial label and failure message cover every local language`() {
        LauncherLanguage.entries.filter { it != LauncherLanguage.English }.forEach { language ->
            listOf("Diagnostic Tutorial", "Could not open tutorial.", "Launch confirmation timed out").forEach { label ->
                val translated = LauncherLocalization.translate(label, language)
                assertTrue("$language: $label", translated.isNotBlank())
                assertNotEquals("$language: $label", label, translated)
            }
        }
    }

    @Test fun `all diagnostic surfaces use the shared themed tutorial action`() {
        val root = listOf(File("."), File("..")).first { File(it, "settings.gradle").isFile }
        fun source(name: String) = File(root, "app/src/main/java/com/moodtools/hub/modules/$name.kt").readText()
        val history = source("LaunchDiagnosticsUi")
        assertEquals(2, Regex("DiagnosticTutorialButton\\(palette\\)").findAll(history).count())
        assertTrue(source("GameHubScreen").substringAfter("private fun InstallerDiagnosticsPanel(")
            .substringBefore("private fun commonInstallerDiagnostics").contains("DiagnosticTutorialButton("))
        val button = source("DiagnosticTutorialButton")
        assertTrue(button.contains("https://youtube.com/shorts/Isl_ffWYpa4"))
        assertTrue(button.contains("Intent.ACTION_VIEW"))
        assertTrue(button.contains("palette.accentSecondary"))
        assertTrue(button.contains("LauncherLocalization.translate(\"Could not open tutorial.\")"))
    }
}
