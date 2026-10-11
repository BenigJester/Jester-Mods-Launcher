package com.moodtools.hub

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticClearTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `clearing removes only the explicit session and is idempotent`() {
        val parent = temporary.newFolder("diagnostics")
        val target = File(parent, "123-abc").apply { mkdirs() }
        File(target, "crash.txt").writeText("crash")
        val other = File(parent, "124-def").apply { mkdirs() }
        val unrelated = File(parent, "game-save").apply { mkdirs() }
        deleteDiagnosticSession(target, parent)
        deleteDiagnosticSession(target, parent)
        assertFalse(target.exists())
        assertTrue(other.exists())
        assertTrue(unrelated.exists())
    }

    @Test fun `clearing rejects broad directories and targets outside diagnostic parent`() {
        val parent = temporary.newFolder("diagnostics")
        val outside = temporary.newFolder("123-abc")
        for (target in listOf(parent, outside, File(parent, "../123-abc"), File(parent, "game-save"))) {
            assertTrue(runCatching { deleteDiagnosticSession(target, parent) }.isFailure)
        }
        assertTrue(parent.exists())
        assertTrue(outside.exists())
    }
}
