package com.moodtools.hub.networking

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Streams a signed, fixed-size file without turning UI progress updates into the bottleneck.
 * Large files use independent byte ranges. Transient failures resume only the unfinished bytes;
 * a server that genuinely does not support ranges safely falls back to one resumable stream.
 * The caller still verifies the signed SHA-256 before activation.
 */
internal object FastFileDownloader {
    private const val BUFFER_BYTES = 256 * 1024
    private const val PROGRESS_BYTES = 512L * 1024L
    private const val PROGRESS_NANOS = 200L * 1_000_000L
    private const val PARALLEL_PART_BYTES = 8L * 1024L * 1024L
    private const val MAX_PARALLEL_PARTS = 4
    private const val MAX_REQUEST_ATTEMPTS = 4
    private const val INITIAL_RETRY_DELAY_MILLIS = 250L
    private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

    fun download(
        destination: File,
        expectedBytes: Long,
        openConnection: (range: LongRange?) -> HttpURLConnection,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        onDiagnostic: (message: String) -> Unit = {},
        allowParallelRanges: Boolean = true
    ) {
        require(expectedBytes > 0L) { "Download size must be positive" }
        destination.parentFile?.mkdirs()
        destination.delete()

        val parts = if (allowParallelRanges) {
            min(
                MAX_PARALLEL_PARTS,
                ((expectedBytes + PARALLEL_PART_BYTES - 1L) / PARALLEL_PART_BYTES).toInt()
            )
        } else {
            1
        }
        val downloadedInRanges = parts >= 2 && tryParallelDownload(
            destination,
            expectedBytes,
            parts,
            openConnection,
            onProgress,
            onDiagnostic
        )
        if (!downloadedInRanges) {
            destination.delete()
            downloadSingle(destination, expectedBytes, openConnection, onProgress, onDiagnostic)
        }
        require(destination.length() == expectedBytes) { "Download size verification failed" }
    }

    private fun downloadSingle(
        destination: File,
        expectedBytes: Long,
        openConnection: (LongRange?) -> HttpURLConnection,
        onProgress: (Long, Long) -> Unit,
        onDiagnostic: (String) -> Unit
    ) {
        RandomAccessFile(destination, "rw").use { it.setLength(0L) }
        val reporter = ProgressReporter(expectedBytes, onProgress)
        var cursor = 0L
        var attempt = 1

        while (cursor < expectedBytes) {
            val requestedRange = if (cursor > 0L) cursor..(expectedBytes - 1L) else null
            var connection: HttpURLConnection? = null
            try {
                connection = openConnection(requestedRange)
                val responseCode = connection.responseCode
                if (requestedRange == null) {
                    if (isRetryableStatus(responseCode)) {
                        throw RetryableDownloadException("Temporary HTTP response $responseCode")
                    }
                    require(responseCode == HttpURLConnection.HTTP_OK) {
                        "Download request failed with HTTP $responseCode"
                    }
                    validateContentLength(connection, expectedBytes)
                } else if (responseCode == HttpURLConnection.HTTP_OK) {
                    // The endpoint ignored the resume range. Its body is still usable, but only as
                    // a clean full restart; never append a full response behind partial bytes.
                    RandomAccessFile(destination, "rw").use { it.setLength(0L) }
                    cursor = 0L
                    reporter.reset()
                    validateContentLength(connection, expectedBytes)
                    onDiagnostic("Server ignored a resume range; restarted the stream from byte 0")
                } else {
                    validateRangeResponse(connection, requestedRange, expectedBytes)
                }

                BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
                    RandomAccessFile(destination, "rw").use { output ->
                        output.seek(cursor)
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (cursor < expectedBytes) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(cursor + count <= expectedBytes) {
                                "Download is larger than expected"
                            }
                            output.write(buffer, 0, count)
                            cursor += count
                            reporter.add(count.toLong())
                        }
                    }
                }
                if (cursor < expectedBytes) {
                    throw RetryableDownloadException("Download stream ended early")
                }
            } catch (error: Exception) {
                if (!isRetryable(error) || attempt >= MAX_REQUEST_ATTEMPTS) {
                    destination.delete()
                    throw DownloadFailureException(
                        "Single-stream download failed after $attempt attempt(s) at byte " +
                            "$cursor of $expectedBytes (${failureKind(error)})",
                        error
                    )
                }
                onDiagnostic(
                    "Retrying stream at byte $cursor of $expectedBytes after attempt $attempt " +
                        "(${failureKind(error)})"
                )
                connection?.disconnect()
                connection = null
                retryDelay(attempt)
                attempt++
            } finally {
                connection?.disconnect()
            }
        }

        RandomAccessFile(destination, "rw").use { it.fd.sync() }
        reporter.finish()
    }

    private fun tryParallelDownload(
        destination: File,
        expectedBytes: Long,
        parts: Int,
        openConnection: (LongRange?) -> HttpURLConnection,
        onProgress: (Long, Long) -> Unit,
        onDiagnostic: (String) -> Unit
    ): Boolean {
        RandomAccessFile(destination, "rw").use { it.setLength(expectedBytes) }
        val reporter = ProgressReporter(expectedBytes, onProgress)
        val failure = AtomicReference<Throwable?>(null)
        val activeConnections = mutableSetOf<HttpURLConnection>()
        val connectionLock = Any()
        val partSize = (expectedBytes + parts - 1L) / parts

        fun stopWorkers(error: Throwable) {
            if (failure.compareAndSet(null, error)) {
                synchronized(connectionLock) {
                    activeConnections.forEach(HttpURLConnection::disconnect)
                }
            }
        }

        val workers = (0 until parts).mapNotNull { index ->
            val start = index * partSize
            val end = min(expectedBytes - 1L, start + partSize - 1L)
            if (start > end) return@mapNotNull null
            Thread({
                var cursor = start
                var attempt = 1
                while (cursor <= end && failure.get() == null) {
                    var connection: HttpURLConnection? = null
                    try {
                        val requestedRange = cursor..end
                        connection = openConnection(requestedRange)
                        synchronized(connectionLock) { activeConnections += connection }
                        if (failure.get() != null) return@Thread
                        validateRangeResponse(connection, requestedRange, expectedBytes)
                        BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
                            RandomAccessFile(destination, "rw").use { output ->
                                output.seek(cursor)
                                val buffer = ByteArray(BUFFER_BYTES)
                                while (cursor <= end) {
                                    failure.get()?.let {
                                        throw InterruptedException("Parallel download stopped")
                                    }
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    require(cursor + count - 1L <= end) {
                                        "Download range is larger than expected"
                                    }
                                    output.write(buffer, 0, count)
                                    cursor += count
                                    reporter.add(count.toLong())
                                }
                            }
                        }
                        if (cursor <= end) {
                            throw RetryableDownloadException("Download range ended early")
                        }
                    } catch (error: Exception) {
                        if (failure.get() != null) return@Thread
                        if (error is RangeUnavailableException) {
                            stopWorkers(error)
                            return@Thread
                        }
                        if (!isRetryable(error) || attempt >= MAX_REQUEST_ATTEMPTS) {
                            stopWorkers(
                                DownloadFailureException(
                                    "Range ${index + 1}/$parts failed after $attempt attempt(s) " +
                                        "at byte $cursor of ${end + 1L} (${failureKind(error)})",
                                    error
                                )
                            )
                            return@Thread
                        }
                        onDiagnostic(
                            "Retrying range ${index + 1}/$parts at byte $cursor of ${end + 1L} " +
                                "after attempt $attempt (${failureKind(error)})"
                        )
                        connection?.let { opened ->
                            synchronized(connectionLock) { activeConnections -= opened }
                            opened.disconnect()
                        }
                        connection = null
                        try {
                            retryDelay(attempt)
                        } catch (interrupted: InterruptedException) {
                            stopWorkers(interrupted)
                            Thread.currentThread().interrupt()
                            return@Thread
                        }
                        attempt++
                    } finally {
                        connection?.let { opened ->
                            synchronized(connectionLock) { activeConnections -= opened }
                            opened.disconnect()
                        }
                    }
                }
            }, "MoodToolsRangeDownload-$index").apply { start() }
        }

        try {
            workers.forEach(Thread::join)
        } catch (error: InterruptedException) {
            stopWorkers(error)
            workers.forEach(Thread::interrupt)
            Thread.currentThread().interrupt()
            throw error
        }

        val terminalFailure = failure.get()
        if (terminalFailure != null) {
            destination.delete()
            reporter.reset()
            if (terminalFailure is RangeUnavailableException) {
                onDiagnostic("Server does not support parallel byte ranges; using one stream")
                return false
            }
            throw terminalFailure
        }
        RandomAccessFile(destination, "rw").use { it.fd.sync() }
        reporter.finish()
        return true
    }

    private fun validateRangeResponse(
        connection: HttpURLConnection,
        range: LongRange,
        expectedBytes: Long
    ) {
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            throw RangeUnavailableException()
        }
        if (isRetryableStatus(responseCode)) {
            throw RetryableDownloadException("Temporary HTTP response $responseCode")
        }
        require(responseCode == HttpURLConnection.HTTP_PARTIAL) {
            "Range request failed with HTTP $responseCode"
        }
        val expectedPartBytes = range.last - range.first + 1L
        validateContentLength(connection, expectedPartBytes)
        val contentRange = connection.getHeaderField("Content-Range")?.trim().orEmpty()
        val match = CONTENT_RANGE.matchEntire(contentRange)
            ?: error("Download server returned an invalid byte range")
        require(
            match.groupValues[1].toLong() == range.first &&
                match.groupValues[2].toLong() == range.last &&
                match.groupValues[3].toLong() == expectedBytes
        ) { "Download server returned the wrong byte range" }
    }

    private fun validateContentLength(connection: HttpURLConnection, expectedBytes: Long) {
        val contentLength = connection.contentLengthLong
        require(contentLength < 0L || contentLength == expectedBytes) {
            "Download response size does not match the signed size"
        }
    }

    private fun isRetryable(error: Exception): Boolean =
        error is IOException || error is RetryableDownloadException

    private fun isRetryableStatus(status: Int): Boolean =
        status == HttpURLConnection.HTTP_CLIENT_TIMEOUT ||
            status == 425 || status == 429 || status in 500..599

    private fun failureKind(error: Throwable): String = when (error) {
        is RetryableDownloadException -> error.message ?: "temporary response"
        is IOException -> error.javaClass.simpleName.ifBlank { "network I/O" }
        else -> error.javaClass.simpleName.ifBlank { "download validation" }
    }

    @Throws(InterruptedException::class)
    private fun retryDelay(failedAttempt: Int) {
        Thread.sleep(INITIAL_RETRY_DELAY_MILLIS shl (failedAttempt - 1).coerceAtMost(2))
    }

    private class RangeUnavailableException : Exception("Download server does not support byte ranges")

    private class RetryableDownloadException(message: String) : IOException(message)

    private class DownloadFailureException(message: String, cause: Throwable) : IOException(message, cause)

    private class ProgressReporter(
        private val totalBytes: Long,
        private val callback: (Long, Long) -> Unit
    ) {
        private var downloadedBytes = 0L
        private var lastPublishedBytes = 0L
        private var lastPublishedAt = System.nanoTime()

        @Synchronized
        fun add(bytes: Long) {
            downloadedBytes = Math.addExact(downloadedBytes, bytes)
            require(downloadedBytes <= totalBytes) { "Download progress exceeded the expected size" }
            val now = System.nanoTime()
            if (downloadedBytes == totalBytes ||
                downloadedBytes - lastPublishedBytes >= PROGRESS_BYTES ||
                now - lastPublishedAt >= PROGRESS_NANOS
            ) {
                callback(downloadedBytes, totalBytes)
                lastPublishedBytes = downloadedBytes
                lastPublishedAt = now
            }
        }

        @Synchronized
        fun reset() {
            if (downloadedBytes > 0L || lastPublishedBytes > 0L) callback(0L, totalBytes)
            downloadedBytes = 0L
            lastPublishedBytes = 0L
            lastPublishedAt = System.nanoTime()
        }

        @Synchronized
        fun finish() {
            require(downloadedBytes == totalBytes) { "Download progress did not reach the expected size" }
            if (lastPublishedBytes != totalBytes) callback(totalBytes, totalBytes)
        }
    }
}
