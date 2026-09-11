package com.moodtools.hub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticExportProviderTest {
    @Test
    fun `accepts only generated diagnostic file names`() {
        assertTrue(isDiagnosticExportFileName("MoodTools-diagnostics-20260911-143025.txt"))
        assertTrue(isDiagnosticExportFileName("OtherworldLegends-diagnostics-20260911-143025.txt"))
        assertFalse(isDiagnosticExportFileName("../diagnostics.txt"))
        assertFalse(isDiagnosticExportFileName("MoodTools-diagnostics-20260911-143025.apk"))
    }
}
