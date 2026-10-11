package com.moodtools.hub.networking

import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** One shared budget across access validation and recovery; only network work runs here. */
internal class AccessCheckDeadline(timeoutMillis: Long = 12_000L) {
    private val expiresAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)

    fun <T> await(request: () -> T): T {
        val remaining = expiresAt - System.nanoTime()
        if (remaining <= 0L) throw SocketTimeoutException("Launcher access check timed out")
        val pending = requests.submit<T> { request() }
        try {
            return pending.get(remaining, TimeUnit.NANOSECONDS)
        } catch (_: TimeoutException) {
            throw SocketTimeoutException("Launcher access check timed out")
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } finally {
            // HttpURLConnection may ignore interruption until its socket timeout. The worker
            // only returns response data, so a late response cannot save access or change UI.
            pending.cancel(true)
        }
    }

    private companion object {
        val requests = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "launcher-access-request").apply { isDaemon = true }
        }
    }
}
