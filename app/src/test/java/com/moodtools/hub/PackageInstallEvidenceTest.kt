package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PackageInstallEvidenceTest {
    @Test fun `installer result codes remain distinct from missing callbacks and confirmation errors`() {
        val categories = listOf("success", "failure", "blocked", "aborted", "invalid", "conflict", "storage", "incompatible", "timeout")
        categories.forEachIndexed { status, category ->
            val text = packageInstallEvidence(status, 42, PackageInstallObservation.RESULT)
            assertTrue(text.contains("status=$status category=$category sessionId=42"))
            assertFalse(text.contains("user_cancelled"))
            assertTrue(text.length < 320)
        }
        assertTrue(packageInstallEvidence(null, null, PackageInstallObservation.RESULT_NOT_OBSERVED)
            .contains("result_not_observed status=unavailable category=unavailable"))
        assertTrue(packageInstallEvidence(-1, 1, PackageInstallObservation.CONFIRMATION_MISSING)
            .contains("confirmation_missing status=-1 category=pending_user_action"))
        assertTrue(packageInstallEvidence(-1, -1, PackageInstallObservation.CONFIRMATION_OPEN_FAILED)
            .endsWith("sessionId=unavailable"))
        assertTrue(packageInstallEvidence(900, null, PackageInstallObservation.RESULT).contains("category=unknown"))
    }

    @Test fun `installer observations are appended without turning confirmation into a failure`() {
        fun source(path: String) = listOf(File(path), File("../$path")).first { it.isFile }.readText()
        val activity = source("app/src/main/java/com/moodtools/hub/LauncherActivity.kt")
        val callback = activity.substringAfter("PackageReplacementInstallEvents.results.collect { result ->")
            .substringBefore("private fun copySupportCode()")
        assertTrue(callback.contains("onPackageReplacementDiagnostic(packageInstallEvidence"))
        assertTrue(callback.contains("if (result.observation == PackageInstallObservation.CONFIRMATION_OPENED) return@collect"))
        assertTrue(activity.contains("PackageInstallObservation.SUCCESS_IDENTITY_NOT_VISIBLE"))
        assertTrue(activity.contains("PackageInstallObservation.RESULT_NOT_OBSERVED"))
        val observer = activity.substringAfter("fun onPackageReplacementDiagnostic(").substringBefore("fun onPackageReplacementProgress(")
        assertTrue(observer.contains("appendDiagnostic"))
        assertFalse(observer.contains("inProgress ="))
        val receiver = source("app/src/main/java/com/moodtools/hub/PackageReplacementInstallReceiver.kt")
        assertTrue(receiver.contains("intent.hasExtra(PackageInstaller.EXTRA_STATUS)"))
        assertTrue(receiver.contains("PackageInstallObservation.CONFIRMATION_MISSING"))
        assertTrue(receiver.contains("PackageInstallObservation.CONFIRMATION_OPEN_FAILED"))
    }
}
