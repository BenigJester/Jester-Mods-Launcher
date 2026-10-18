package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test

class DiagnosticProcessAttemptTest {
    @Test fun `non-crash exits are retained without claiming a crash or stall`() {
        val attempt = DiagnosticProcessAttempt(1, 100, "100 guest_attached main=true")
        for (reason in listOf(0, 1, 2, 3, 8, 9, 10, 11, 12, 13, 14, 15, 16, 99)) {
            val exit = "Exit reason: $reason; status: 9; PID: 1; timestamp: 200"
            assertEquals("reason $reason", "Process exit recorded",
                diagnosticAttemptStatus(attempt, listOf(exit), false, true, 100000))
        }
        assertEquals("system low-memory kill", diagnosticExitCategory(3))
        assertEquals("self-exit", diagnosticExitCategory(1))
        assertTrue(diagnosticExitCategory(2).contains("not proof"))
        assertEquals("unknown", diagnosticExitCategory(99))
        for (reason in listOf(4, 5, 7)) {
            assertEquals("Crash or startup error recorded", diagnosticFailureStatus(
                listOf("Exit reason: 3;", "Exit reason: $reason;"), false))
        }
        assertEquals("Background ANR recorded", diagnosticFailureStatus(
            listOf("Exit reason: 2;", "Exit reason: 6;\nANR context: background"), false))
        assertEquals("Crash or startup error recorded", diagnosticFailureStatus(
            listOf("Exit reason: 1;", "cause java.lang.IllegalStateException"), false))
    }

    @Test fun `Township restart cannot inherit an earlier processes successful resume`() {
        val attempts = diagnosticProcessAttempts(mapOf(
            12613 to "100 guest_attached\n200 activity_direct-resumed com.playrix.township.GPlayActivity",
            9629 to "100000 guest_attached\n100100 activity_direct-created com.playrix.township.Launcher"
        ), "90000 main_process_died pid=12613")
        assertEquals(2, attempts.size)
        assertTrue(attempts.first().resumed)
        assertFalse(attempts.last().resumed)
        assertEquals("Startup in progress", diagnosticAttemptStatus(attempts.last(), emptyList(), false, false, 159999))
        assertEquals("Possible startup stall", diagnosticAttemptStatus(attempts.last(), emptyList(), false, false, 160000))
        assertEquals("Background ANR recorded", diagnosticAttemptStatus(attempts.last(),
            listOf("Exit reason: 6;\nANR context: background"), false, true, 200000))
    }

    @Test fun `explicit helper identity excludes helpers and main without an activity is monitored`() {
        val attempts = diagnosticProcessAttempts(mapOf(
            1 to "100 guest_attached main=true",
            2 to "200 guest_attached main=false\n210 activity_resumed helper.Screen"
        ), "")
        assertEquals(listOf(1), attempts.map { it.pid })
        assertEquals("Possible startup stall", diagnosticAttemptStatus(attempts.single(), emptyList(), false, false, 60100))
    }

    @Test fun `PID reuse resets resume evidence and after-resume ANR does not imply startup failure`() {
        val attempts = diagnosticProcessAttempts(mapOf(1 to
            "100 guest_attached main=true\n150 activity_resumed game.Screen\n1000 guest_attached main=true"), "")
        assertEquals(2, attempts.size)
        assertTrue(attempts.first().resumedBefore(200))
        assertFalse(attempts.first().resumedBefore(120))
        assertFalse(attempts.last().resumed)
        assertEquals("Game ANR recorded", diagnosticAttemptStatus(attempts.first(), listOf("Exit reason: 6;"), false, true, 200))
        assertEquals("ANR recorded", diagnosticAttemptStatus(attempts.last(), listOf("Exit reason: 6;"), false, true, 2000))
        assertEquals("Startup ANR recorded", diagnosticAttemptStatus(attempts.last(),
            listOf("Exit reason: 6;\nANR context: startup"), false, true, 2000))
        assertEquals("background", diagnosticAnrContext(true, true))
        assertEquals("after activity resume", diagnosticAnrContext(false, true))
        assertEquals("startup", diagnosticAnrContext(false, false))
    }

    @Test fun `unknown ANR context stays unknown and crash evidence still takes priority`() {
        assertEquals("ANR recorded", diagnosticFailureStatus(listOf("Exit reason: 6;\nANR context: unknown"), false))
        assertEquals("Crash or startup error recorded", diagnosticFailureStatus(
            listOf("Exit reason: 6;\nANR context: background", "cause java.lang.SecurityException"), true))
        assertNull(diagnosticFailureStatus(emptyList(), false))
        val resumed = DiagnosticProcessAttempt(1, 100, "100 guest_attached main=true\n200 activity_resumed game.Screen")
        assertEquals("Game activity opened", diagnosticAttemptStatus(resumed, emptyList(), false, false, 100000))
        assertEquals("Session stopped", diagnosticAttemptStatus(resumed, emptyList(), true, false, 100000))
    }
}
