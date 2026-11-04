package com.moodtools.hub

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PackageReplacementLateResultTest {
    @Test fun `late success revives only an observation timeout`() {
        for (phase in PackageReplacementPhase.entries) {
            val expected = if (phase == PackageReplacementPhase.WAITING_FOR_LATE_INSTALL_RESULT)
                PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT else phase
            assertEquals(expected, phase.afterInstallerSuccess())
        }
    }

    @Test fun `recovered success still verifies installed identity and clears old failure`() {
        val root = listOf(File("."), File("..")).first { File(it, "settings.gradle").isFile }
        val text = root.resolve("app/src/main/java/com/moodtools/hub/LauncherActivity.kt").readText()
        val callback = text.substringAfter("PackageReplacementInstallEvents.results.collect { result ->")
            .substringBefore("private fun copySupportCode()")
        assertTrue(callback.indexOf("result.packageName != pendingPackageReplacement?.packageName") <
            callback.indexOf("afterInstallerSuccess()"))
        assertTrue(callback.indexOf("if (result.successful)") < callback.indexOf("afterInstallerSuccess()"))
        assertTrue(callback.indexOf("afterInstallerSuccess()") <
            callback.indexOf("schedulePackageReplacementReconciliation(installerReportedSuccess = true)"))
        val reconcile = text.substringAfter("private fun schedulePackageReplacementReconciliation(")
            .substringBefore("private fun finishPackageReplacementSuccessfully()")
        assertTrue(reconcile.contains("awaitingInstallerResult = !installerReportedSuccess"))
        assertTrue(reconcile.indexOf("ExecutionModeLaunchBridge.isPackageReplacementInstalled") <
            reconcile.indexOf("finishPackageReplacementSuccessfully()"))
        val failed = text.substringAfter("private fun finishPackageReplacementWithFailure(")
            .substringBefore("private fun cancelPackageReplacement()")
        assertTrue(failed.contains("awaitingInstallerResult: Boolean = false"))
        assertTrue(failed.contains("PackageReplacementPhase.WAITING_FOR_LATE_INSTALL_RESULT"))
        val cancel = text.substringAfter("private fun cancelPackageReplacement()")
            .substringBefore("private fun restorePackageReplacementRecovery()")
        assertTrue(cancel.contains("packageReplacementPhase = PackageReplacementPhase.IDLE"))
        val completed = text.substringAfter("fun onPackageReplacementInstalled(request:")
            .substringBefore("fun removeFromLibrary(")
        assertTrue(completed.contains("failed = false"))
        assertTrue(completed.contains("preparedRetryAvailable = false"))
        assertTrue(completed.contains("refreshGames(forceGameScan = true)"))
    }
}
