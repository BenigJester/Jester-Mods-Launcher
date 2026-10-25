package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test

class DiagnosticFailureEvidenceTest {
    @Test fun `later shutdown capture survives long first stack without private text`() {
        val overview = diagnosticFailureEvidenceOverview(
            "Failure capture: first_observed\nFailure phase: unclassified\nFailure hint: unclassified\n" +
            "  at game.Loader.start:42\n".repeat(400) +
            "Failure capture: later_observed\nFailure phase: activity_teardown\nFailure hint: network_callback_cleanup\n" +
            "private-token\nFailure hint: secret-account\n")
        assertTrue(overview.contains("first_observed"))
        assertTrue(overview.contains("later_observed"))
        assertTrue(overview.contains("network_callback_cleanup"))
        assertTrue(overview.contains("does not establish why"))
        assertFalse(overview.contains("private-token"))
        assertFalse(overview.contains("secret-account"))
        assertFalse(overview.contains("game.Loader"))
    }

    @Test fun `legacy reports are unchanged and overviews are bounded`() {
        assertEquals("", diagnosticFailureEvidenceOverview("Exception causes:\ncause java.lang.Error\n"))
        val overview = diagnosticFailureEvidenceOverview("Failure capture: later_observed\n".repeat(100))
        assertEquals(9, overview.lines().count { it.startsWith("Failure capture:") })
    }
}
