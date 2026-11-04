package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test

class AnrTraceSummaryTest {
    private fun decode(text: String, pid: Int = 42) = AnrTraceSummary.decode(text.trimIndent().toByteArray(), pid)

    @Test fun `keeps main and lock owner stacks only for the matching process`() {
        val report = decode("""
            ----- pid 99 at 2026-10-31 00:00:00 -----
            "main" prio=5 tid=1 Runnable
              at unrelated.Process.run(Process.java:1)
            ----- end 99 -----
            ----- pid 42 at 2026-10-31 00:00:00 -----
            Cmd line: account@example.com SECRET_TOKEN
            "main" prio=5 tid=1 Blocked
              at com.playrix.engine.Game.update(Game.java:42)
              - waiting to lock <0x1234> (a private.Account) held by thread 7
            "account@example.com" prio=5 tid=7 Native
              native: #00 pc 00001234 /data/private/libTownship.so (private_symbol+99)
              at top.niunaijun.blackbox.Proxy.call(Native method)
            "private-worker" prio=5 tid=8 Runnable
              at private.Unrelated.run(Secret.java:1)
            ----- end 42 -----
        """)
        assertTrue(report.contains("Thread 1 main: Blocked"))
        assertTrue(report.contains("waiting_for_thread=7"))
        assertTrue(report.contains("Thread 7 lock owner: Native"))
        assertTrue(report.contains("at com.playrix.engine.Game.update"))
        assertTrue(report.contains("libTownship.so +0x00001234"))
        for (secret in listOf("account@", "SECRET_TOKEN", "private", "/data/", "Unrelated", "unrelated.Process", "Game.java")) {
            assertFalse(secret, report.contains(secret))
        }
    }

    @Test fun `requires matching process and main thread rather than attributing another trace`() {
        assertEquals("ANR system trace has no matching process", decode("""
            ----- pid 99 at 2026-10-31 00:00:00 -----
            "main" prio=5 tid=1 Runnable
              at other.Process.run(Native method)
        """))
        assertEquals("ANR system trace has no main thread", decode("""
            ----- pid 42 at 2026-10-31 00:00:00 -----
            "worker" prio=5 tid=7 Waiting
        """))
        assertEquals("ANR system trace has no matching process", decode("private malformed trace"))
        assertEquals("ANR system trace exceeds size limit", AnrTraceSummary.decode(ByteArray(2 * 1024 * 1024 + 1), 42))
    }

    @Test fun `bounds frames and cyclic lock chains`() {
        val trace = buildString {
            appendLine("----- pid 42 at 2026-10-31 00:00:00 -----")
            appendLine("\"main\" prio=5 tid=1 Blocked")
            repeat(100) { appendLine("  at sample.Game.call$it(Native method)") }
            appendLine("- waiting to lock <0x123> (a sample.Lock) held by thread 2")
            appendLine("\"worker\" prio=5 tid=2 Blocked")
            appendLine("- waiting to lock <0x456> (a sample.Lock) held by thread 1")
        }
        val report = decode(trace)
        assertEquals(24, report.lineSequence().count { it.startsWith("at ") })
        assertEquals(2, report.lineSequence().count { it.startsWith("Thread ") })
    }

    @Test fun `exports fixed trigger categories without private description text`() {
        assertEquals("input_dispatch_timeout", diagnosticAnrTrigger("Input dispatching timed out /data/secret"))
        assertEquals("broadcast_timeout", diagnosticAnrTrigger("bg anr: Broadcast of Intent { act=SECRET_TOKEN }"))
        assertEquals("service_timeout", diagnosticAnrTrigger("executing service account@example.com"))
        assertEquals("provider_timeout", diagnosticAnrTrigger("ContentProvider not responding: /private"))
        assertEquals("unclassified_or_unavailable", diagnosticAnrTrigger(null))
        assertEquals("unclassified_or_unavailable", diagnosticAnrTrigger("SECRET_TOKEN"))
    }
}
