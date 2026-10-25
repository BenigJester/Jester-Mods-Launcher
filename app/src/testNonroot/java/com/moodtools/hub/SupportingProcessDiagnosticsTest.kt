package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SupportingProcessDiagnosticsTest {
    private fun source(path: String): String = listOf(File(path), File("../$path"))
        .first { it.isFile }.readText()

    @Test fun `supporting files stay outside game attempt discovery and are exported separately`() {
        val report = source("app/src/nonroot/java/com/moodtools/hub/NonRootLaunchDiagnostics.kt")
        val discovery = report.substringAfter("private fun reportsForSession(").substringBefore("private fun report(")
        assertTrue(discovery.contains("process-([0-9]+)"))
        assertFalse(discovery.contains("support-resources"))
        assertTrue(report.contains("SUPPORTING PROCESS SAMPLES (context only; not separate game attempts)"))
        assertTrue(report.contains("(?:process|support-resources)-([0-9]+)"))
        assertTrue(report.contains("androidSecurityPatch"))
        assertTrue(report.contains("HELPER MAIN THREAD (shared context, not proof of cause)"))
        val summary = report.substringAfter("val summary = buildString")
        assertTrue(summary.contains("HELPER MAIN THREAD (shared context, not cause)"))
        assertTrue(report.contains("bbWindowConnectionCalls counts calls/failures"))
    }

    @Test fun `helper observation only uses existing one way client and remains bounded`() {
        val base = "third_party/BlackBox/Bcore/src/main/"
        val manager = source(base + "java/top/niunaijun/blackbox/core/system/BProcessManagerService.java")
            .substringAfter("private void observeDiagnosticProcesses(").substringBefore("private int getUsingBPidL(")
        assertTrue(manager.contains("app.bActivityThread.startSupportDiagnostics"))
        assertFalse(manager.contains("acquireContentProviderClient"))
        assertFalse(manager.contains("startProcessLocked"))
        assertTrue(source(base + "aidl/top/niunaijun/blackbox/core/IBActivityThread.aidl")
            .contains("oneway void startSupportDiagnostics"))
        val recorder = source(base + "java/top/niunaijun/blackbox/utils/SupportProcessRecorder.java")
        assertTrue(recorder.contains("MAX_SESSIONS = 4"))
        assertTrue(recorder.contains("WINDOW_MS = 10 * 60_000L"))
        assertFalse(recorder.contains("mkdir"))
    }
}
