package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BlackBoxPreparationDiagnosticsTest {
    @Test fun `records start before entering operation and completion after return`() {
        val session = Files.createTempDirectory("preparation-diagnostics").toFile()
        try {
            val value = recordBlackBoxPreparationStage(session, "guest_switch") {
                val pending = File(session, "host.txt").readText()
                assertTrue(pending.contains("guest_switch started"))
                assertFalse(pending.contains("guest_switch completed"))
                42
            }
            assertEquals(42, value)
            val events = File(session, "host.txt").readLines()
            assertTrue(events.first().endsWith("guest_switch started"))
            assertTrue(events.last().endsWith("guest_switch completed"))
        } finally {
            session.deleteRecursively()
        }
    }

    @Test fun `records safe failure category and rethrows original exception`() {
        val session = Files.createTempDirectory("preparation-failure").toFile()
        try {
            val original = SecurityException("private /data/account@example.com/token")
            try {
                recordBlackBoxPreparationStage(session, "google_package_install package=com.google.android.gms") {
                    throw original
                }
                fail("Expected original failure")
            } catch (caught: SecurityException) {
                assertSame(original, caught)
            }
            val report = File(session, "host.txt").readText()
            assertTrue(report.contains("failed category=permission"))
            assertFalse(report.contains("completed"))
            for (privateText in listOf("private", "/data/", "account@", "token")) {
                assertFalse(report.contains(privateText))
            }
        } finally {
            session.deleteRecursively()
        }
    }

    @Test fun `missing diagnostic session does not prevent operation`() {
        assertEquals("result", recordBlackBoxPreparationStage(null, "guest_switch") { "result" })
    }
}
