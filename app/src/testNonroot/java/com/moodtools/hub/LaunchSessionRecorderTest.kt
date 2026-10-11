package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.niunaijun.blackbox.utils.LaunchSessionRecorder
import java.io.File

class LaunchSessionRecorderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `session writes are isolated persisted bounded and newline safe`() {
        val old = temporary.newFolder("old")
        val fresh = temporary.newFolder("fresh")
        LaunchSessionRecorder.hostEvent(old, "application_started")
        LaunchSessionRecorder.hostEvent(fresh, "launch_requested\nnot_a_new_event")
        assertTrue(File(old, "host.txt").readText().contains("application_started"))
        assertFalse(File(fresh, "host.txt").readText().contains("application_started"))
        assertEquals(1, File(fresh, "host.txt").readLines().size)
        repeat(1000) { LaunchSessionRecorder.hostEvent(fresh, "event " + "a".repeat(100)) }
        assertTrue(File(fresh, "host.txt").length() <= LaunchSessionRecorder.MAX_EVENT_BYTES)
    }
    @Test fun `recorder does not recreate a pruned session or accept path traversal`() {
        val absent = File(temporary.root, "pruned")
        LaunchSessionRecorder.hostEvent(absent, "late_process_event")
        assertFalse(absent.exists())
        for (name in listOf("..", ".", "pkg/../../other", "pkg..name")) {
            assertTrue(runCatching { LaunchSessionRecorder.packageDirectory(name) }.isFailure)
        }
    }
}
