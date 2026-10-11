package com.moodtools.hub

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionResumeConfirmationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `host optimism and historical callbacks never confirm a new launch`() {
        val session = temporary.newFolder("session")
        File(session, "host.txt").writeText("200 activity_resumed existing_session\n")
        File(session, "process-10.txt").writeText("100 activity_resumed main.Main\n200 application_onCreate_completed\n")
        assertFalse(sessionResumedSince(session, 150))
        File(session, "process-10.txt").appendText("201 activity_direct-resumed main.Main\n")
        assertTrue(sessionResumedSince(session, 150))
        assertFalse(sessionResumedSince(session, 202))
    }

    @Test fun `cleared session cannot confirm resume so next launch must be fresh`() {
        val absent = File(temporary.root, "cleared")
        assertFalse(sessionResumedSince(absent, 0))
        assertFalse(absent.exists())
    }
}
