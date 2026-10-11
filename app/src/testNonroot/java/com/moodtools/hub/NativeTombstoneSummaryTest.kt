package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test
import top.niunaijun.blackbox.utils.LaunchSessionRecorder
import java.io.ByteArrayOutputStream

class NativeTombstoneSummaryTest {
    private fun variable(value: Long): ByteArray = ByteArrayOutputStream().apply {
        var remaining = value
        while (remaining and -128L != 0L) { write((remaining.toInt() and 127) or 128); remaining = remaining ushr 7 }
        write(remaining.toInt())
    }.toByteArray()
    private fun number(field: Int, value: Long) = variable((field * 8).toLong()) + variable(value)
    private fun message(field: Int, value: ByteArray) = variable((field * 8 + 2).toLong()) + variable(value.size.toLong()) + value
    private fun text(field: Int, value: String) = message(field, value.toByteArray())

    @Test fun `decodes selected crash fields without exposing native memory or logs`() {
        val signal = number(1, 11) + text(2, "SIGSEGV") + number(8, 1) + number(9, 0xdead)
        val frame = number(1, 0x1234) + text(6, "/private/path/libMenu.so") + text(8, "abcd")
        val thread = message(3, text(1, "pc") + number(2, 0x5678)) + message(4, frame)
        val bytes = number(6, 42) + message(10, signal) + message(16, number(1, 42) + message(2, thread)) +
            text(14, "SECRET_TOKEN") + message(18, text(1, "PRIVATE_LOG"))
        val report = NativeTombstoneSummary.decode(bytes)
        assertTrue(report.contains("SIGSEGV"))
        assertTrue(report.contains("0xdead"))
        assertTrue(report.contains("libMenu.so +0x1234"))
        assertTrue(report.contains("buildId=abcd"))
        assertTrue(report.contains("pc = 0x5678"))
        assertFalse(report.contains("SECRET_TOKEN"))
        assertFalse(report.contains("PRIVATE_LOG"))
        assertFalse(report.contains("/private/path"))
    }
    @Test fun `rejects truncated malformed and oversized traces`() {
        for (bytes in listOf(byteArrayOf(82, 127), byteArrayOf(0), ByteArray(2 * 1024 * 1024 + 1))) {
            assertTrue(runCatching { NativeTombstoneSummary.decode(bytes) }.isFailure)
        }
    }
    @Test fun `exception exports include frames but never secret messages`() {
        val error = IllegalStateException("password=SECRET", IllegalArgumentException("token=HIDDEN"))
        val report = LaunchSessionRecorder.exceptionFrames(error)
        assertTrue(report.contains("IllegalStateException"))
        assertTrue(report.contains("IllegalArgumentException"))
        assertFalse(report.contains("SECRET"))
        assertFalse(report.contains("HIDDEN"))
    }
}
