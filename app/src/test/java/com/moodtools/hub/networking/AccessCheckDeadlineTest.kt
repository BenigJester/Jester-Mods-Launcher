package com.moodtools.hub.networking

import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AccessCheckDeadlineTest {
    @Test
    fun completedRequestReturnsItsResultAndPreservesFailures() {
        val deadline = AccessCheckDeadline(2_000)
        assertEquals("accepted", deadline.await { "accepted" })
        val failure = IllegalStateException("rejected")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            deadline.await<String> { throw failure }
        })
    }

    @Test
    fun stalledRequestTimesOutEvenWhenItIgnoresInterruption() {
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val startedAt = System.nanoTime()
        try {
            assertThrows(SocketTimeoutException::class.java) {
                AccessCheckDeadline(100).await {
                    try {
                        while (release.count > 0) {
                            try { release.await() } catch (_: InterruptedException) { }
                        }
                        "late response"
                    } finally {
                        finished.countDown()
                    }
                }
            }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) < 2_000)
        } finally {
            release.countDown()
        }
        assertTrue(finished.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun recoverySharesTheOriginalBudgetRatherThanStartingAnotherTimeout() {
        val deadline = AccessCheckDeadline(100)
        assertEquals("access", deadline.await { "access" })
        Thread.sleep(150)
        var recoveryStarted = false
        assertThrows(SocketTimeoutException::class.java) {
            deadline.await { recoveryStarted = true }
        }
        assertFalse(recoveryStarted)
    }
}
