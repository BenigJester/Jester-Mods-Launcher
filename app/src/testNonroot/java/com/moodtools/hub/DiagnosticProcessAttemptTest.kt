package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test

class DiagnosticProcessAttemptTest {
    @Test fun `launcher confirmation timeout is not a game crash and late resume supersedes it`() {
        val timeout = "Launch confirmation timeout\nException causes:\ncause com.moodtools.hub.LaunchConfirmationTimeoutException"
        val startup = DiagnosticProcessAttempt(11476, 100, "100 guest_attached main=true")
        val resumed = startup.copy(events = startup.events + "\n200 activity_resumed game.Screen")
        assertEquals("Launch confirmation timed out", diagnosticAttemptStatus(startup, listOf(timeout), false, false, 20000))
        assertEquals("Game activity opened", diagnosticAttemptStatus(resumed, listOf(timeout), false, false, 20000))
        assertEquals("Process exit recorded", diagnosticAttemptStatus(resumed,
            listOf(timeout, "Exit reason: 13; status: 0"), false, true, 20000))
        for (reason in listOf(4, 5, 7)) assertEquals("Crash or startup error recorded", diagnosticAttemptStatus(resumed,
            listOf(timeout, "Exit reason: $reason; status: 0"), false, true, 20000))
        assertEquals("Crash or startup error recorded", diagnosticAttemptStatus(resumed,
            listOf(timeout, "cause java.lang.NullPointerException"), false, true, 20000))
        assertEquals("Game ANR recorded", diagnosticAttemptStatus(resumed,
            listOf(timeout, "Exit reason: 6;\nANR context: after activity resume"), false, true, 20000))
    }

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
        assertEquals("Startup in progress", diagnosticAttemptStatus(attempts.last(), emptyList(), false, false, 160000))
        assertEquals("Possible startup stall", diagnosticAttemptStatus(attempts.last(), emptyList(), false, false, 160100))
        assertEquals("Background ANR recorded", diagnosticAttemptStatus(attempts.last(),
            listOf("Exit reason: 6;\nANR context: background"), false, true, 200000))
    }

    @Test fun `explicit helper identity excludes helpers and main attach alone is not a launch`() {
        val attempts = diagnosticProcessAttempts(mapOf(
            1 to "100 guest_attached main=true",
            2 to "200 guest_attached main=false\n210 activity_resumed helper.Screen"
        ), "")
        assertEquals(listOf(1), attempts.map { it.pid })
        assertEquals("Background process observed", diagnosticAttemptStatus(attempts.single(), emptyList(), false, false, 60100))
        assertEquals("Possible startup stall", diagnosticAttemptStatus(attempts.single(), emptyList(), false, false, 60100, 100))
    }

    @Test fun `Minecraft normal exit followed by background restart does not become a stalled launch`() {
        val attempts = diagnosticProcessAttempts(mapOf(
            19042 to "1792608134839 guest_attached main=true\n1792608135126 activity_resumed com.mojang.minecraftpe.MainActivity",
            2565 to "1792609179267 guest_attached main=true\n1792609179288 application_instantiated"
        ), "")
        val firstRequest = diagnosticAttemptLaunchRequest(attempts.first(), null, 1792608133931, true, "")
        val backgroundRequest = diagnosticAttemptLaunchRequest(attempts.last(), attempts.first().startedAt, 1792608133931, true, "")
        assertEquals(1792608133931L, firstRequest)
        assertNull(backgroundRequest)
        assertEquals("Process exit recorded", diagnosticAttemptStatus(attempts.first(),
            listOf("Exit reason: 10; status: 0"), false, true, 1792609400000, firstRequest))
        assertEquals("Background process observed", diagnosticAttemptStatus(attempts.last(),
            emptyList(), false, false, 1792609400000, backgroundRequest))
    }

    @Test fun `request attribution is per attempt for remote and BlackBox sessions`() {
        val first = DiagnosticProcessAttempt(1, 200, "200 guest_attached main=true")
        val restarted = DiagnosticProcessAttempt(2, 100000, "100000 guest_attached main=true")
        assertEquals(100L, diagnosticAttemptLaunchRequest(first, null, 100, false, "100 launch_requested"))
        assertNull(diagnosticAttemptLaunchRequest(restarted, 200, 100, false, "100 launch_requested"))
        assertEquals(99900L, diagnosticAttemptLaunchRequest(restarted, 200, 100, false,
            "100 launch_requested\n99900 resuming_existing_session"))
        assertEquals("Possible startup stall", diagnosticAttemptStatus(first, emptyList(), false, false, 60100, 100))
        assertEquals("Background process observed", diagnosticAttemptStatus(restarted, emptyList(), false, true, 200000))
        assertEquals("Session stopped", diagnosticAttemptStatus(restarted, emptyList(), true, false, 200000))
        for (reason in listOf(4, 5, 7)) assertEquals("Crash or startup error recorded",
            diagnosticAttemptStatus(restarted, listOf("Exit reason: $reason; status: 11"), false, true, 200000))
        assertEquals("Background ANR recorded", diagnosticAttemptStatus(restarted,
            listOf("Exit reason: 6;\nANR context: background"), false, true, 200000))
    }

    @Test fun `Activity creation after background warmup starts a fresh stall clock`() {
        val attempt = DiagnosticProcessAttempt(1, 100, "100 guest_attached main=true\n100000 activity_instantiate")
        assertEquals("Startup in progress", diagnosticAttemptStatus(attempt, emptyList(), false, false, 100001))
        assertEquals("Possible startup stall", diagnosticAttemptStatus(attempt, emptyList(), false, false, 160000))
        assertEquals("Game activity opened", diagnosticAttemptStatus(attempt.copy(events =
            attempt.events + "\n160001 activity_resumed game.Screen"), emptyList(), false, false, 200000))
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
