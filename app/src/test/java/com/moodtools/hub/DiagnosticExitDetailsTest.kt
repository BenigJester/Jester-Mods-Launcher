package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test

class DiagnosticExitDetailsTest {
    @Test fun `descriptions expose only fixed hints never private text`() {
        assertEquals("excessive_cpu_reported", diagnosticExitHint("Excessive background CPU secret@example.com"))
        assertEquals("background_anr_reported", diagnosticExitHint("bg anr: secret-url"))
        assertEquals("low_memory_mentioned", diagnosticExitHint("low memory /private/path"))
        assertEquals("unclassified_or_unavailable", diagnosticExitHint("token=secret"))
        assertEquals("unclassified_or_unavailable", diagnosticExitHint(null))
    }
}
