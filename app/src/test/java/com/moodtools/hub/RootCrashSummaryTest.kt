package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test

class RootCrashSummaryTest {
    private fun line(time: String, pid: Int, tag: String, body: String) = "$time $pid $pid F $tag: $body\n"

    @Test fun `Java reports exclude other apps old sessions and private messages`() {
        val log = buildString {
            append(line("100.100", 10, "AndroidRuntime", "FATAL EXCEPTION: private-thread"))
            append(line("100.101", 10, "AndroidRuntime", "Process: com.test.game, PID: 10"))
            append(line("100.102", 10, "AndroidRuntime", "java.lang.IllegalStateException: secret account"))
            append(line("100.103", 10, "AndroidRuntime", "Caused by: java.lang.NullPointerException: token"))
            append(line("100.104", 10, "AndroidRuntime", "at com.test.Hook.open(Hook.java:42)"))
            append(line("100.100", 20, "AndroidRuntime", "FATAL EXCEPTION: main"))
            append(line("100.101", 20, "AndroidRuntime", "Process: com.test.game2, PID: 20"))
            append(line("100.102", 20, "AndroidRuntime", "java.lang.Error: other app"))
        }
        val report = rootCrashSummary(log, "com.test.game", 100000, 101000)
        assertTrue(report.contains("java.lang.IllegalStateException"))
        assertTrue(report.contains("java.lang.NullPointerException"))
        assertTrue(report.contains("Hook.java:42"))
        for (privateText in listOf("secret", "token", "private-thread", "game2", "other app")) assertFalse(report.contains(privateText))
        assertEquals("", rootCrashSummary(log, "com.test.game", 101000, 102000))
        assertEquals("", rootCrashSummary(log, "com.test.game", 99000, 100000))
    }

    @Test fun `native reports preserve addresses library build ID but no dump or abort text`() {
        val log = listOf("*** *** ***", "Cmdline: com.test.game:worker", "pid: 88, tid: 90, name: secret >>> com.test.game:worker <<<",
            "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x00000008",
            "x0 0000000000000000 x1 0000000000000008",
            "#00 pc 00001234 /data/user/0/private/lib/libmenu_native.so (secret+12) (BuildId: abcd)",
            "Abort message: 'private message'", "memory near x0: secret bytes")
            .joinToString("") { line("200.100", 55, "DEBUG", it) }
        val report = rootCrashSummary(log, "com.test.game", 200000, 201000)
        for (expected in listOf("SIGSEGV", "0x00000008", "tid: 90", "x0", "pc 00001234 libmenu_native.so BuildId: abcd")) assertTrue(report, report.contains(expected))
        for (secret in listOf("private", "secret", "memory near", "Abort")) assertFalse(report.contains(secret))
        assertEquals("", rootCrashSummary(log, "com.test.gam", 200000, 201000))
    }

    @Test fun `a process identity alone is not a crash`() {
        assertEquals("", rootCrashSummary(line("100.100", 10, "AndroidRuntime", "Process: com.test.game, PID: 10"),
            "com.test.game", 100000, 101000))
    }
}
