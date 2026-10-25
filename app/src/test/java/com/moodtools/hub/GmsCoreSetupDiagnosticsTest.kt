package com.moodtools.hub

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class GmsCoreSetupDiagnosticsTest {
    @Test fun `setup snapshot distinguishes selected preference from required routing and companion availability`() {
        for (supported in listOf(false, true)) for (selected in listOf(false, true)) {
            for (state in GmsCoreSupport.State.entries) {
                val snapshot = GmsCoreSupport.setupDiagnostics(supported, selected, state)
                assertTrue(snapshot.contains("gmscoreSupported: $supported"))
                assertTrue(snapshot.contains("gmscoreSelected: $selected"))
                assertTrue(snapshot.contains("gmscoreRequired: ${supported && selected}"))
                assertTrue(snapshot.contains("gmscoreState: ${state.name}"))
                assertEquals(6, snapshot.size)
                assertTrue(snapshot.last().contains("does not confirm account sign-in"))
            }
        }
    }

    @Test fun `failure details identify actual companion problem and offer optional route in each language`() {
        for (state in GmsCoreSupport.State.entries.filter { it != GmsCoreSupport.State.READY }) {
            val detail = GmsCoreSupport.setupFailureDetail(state)
            assertTrue(detail.startsWith(state.label + "\n"))
            assertTrue(detail.contains("Google account support"))
            assertTrue(detail.endsWith(GmsCoreSupport.OPTIONAL_SETUP_GUIDANCE))
            assertEquals(detail, TransferFailurePolicy.present(IllegalArgumentException(detail),
                TransferFailureOperation.PACKAGE_SETUP, fallbackDetail = detail).detail)
            for (line in detail.lines()) {
                val translations = LauncherGmsCoreTranslations.entries.getValue(line)
                assertEquals(9, translations.size)
                assertTrue(translations.all { it.isNotBlank() && it != line })
            }
        }
    }

    @Test fun `setup snapshot is captured before preparation and exported rather than requeried at sharing time`() {
        fun source(path: String): String {
            val root = listOf(File("src/main/java/com/moodtools/hub"), File("app/src/main/java/com/moodtools/hub"))
                .first(File::isDirectory)
            return File(root, path).readText()
        }
        val authorize = source("LauncherActivity.kt").substringAfter("fun authorizePackageReplacement(")
            .substringBefore("fun onPackageReplacementProgress(")
        assertTrue(authorize.indexOf("GmsCoreSupport.setupDiagnostics(") >= 0)
        assertTrue(authorize.indexOf("current.copy(gmsCoreDiagnostics = snapshot)") <
            authorize.indexOf("withContext(Dispatchers.Main) { onAuthorized() }"))
        assertTrue(authorize.contains("!BuildConfig.IS_ROOT_MODE && kind == PackageReplacementKind.DIRECT_PATCH"))
        val report = source("modules/GameHubScreen.kt").substringAfter("private fun PackageSetupDialog(")
            .substringBefore("UnifiedInstallerDialog(")
        assertTrue(report.contains("state.gmsCoreDiagnostics.forEach(::appendLine)"))
        assertFalse(report.contains("GmsCoreSupport.state("))
        val ui = source("modules/GmsCoreSetupUi.kt")
        assertTrue(ui.contains("Text(GmsCoreSupport.OPTIONAL_SETUP_GUIDANCE, color = palette.muted)"))
        assertTrue(ui.contains("https://youtu.be/kHWNryjx3dQ"))
        assertTrue(ui.contains("Text(\"Google Login Tutorial\")"))
        val tutorialTranslations = LauncherGmsCoreTranslations.entries.getValue("Google Login Tutorial")
        assertEquals(9, tutorialTranslations.size)
        assertTrue(tutorialTranslations.all { it.isNotBlank() && it != "Google Login Tutorial" })
    }
}
