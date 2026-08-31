package com.moodtools.hub

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessTimeFormatterTest {
    @Test
    fun formatsTheSharedLauncherAndPrivateModuleCountdown() {
        assertEquals("Expired", formatRemainingAccessPrimary(0L))
        assertEquals("1m", formatRemainingAccessPrimary(1L))
        assertEquals("59m", formatRemainingAccessPrimary(59L * 60_000L))
        assertEquals("1h 0m", formatRemainingAccessPrimary(60L * 60_000L))
        assertEquals("2d 3h", formatRemainingAccessPrimary((2L * 24L + 3L) * 60L * 60_000L))
    }
}
