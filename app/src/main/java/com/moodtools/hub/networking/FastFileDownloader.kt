package com.moodtools.hub.networking

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection

/**
 * Streams a fixed-size file through exactly one active HTTP connection at a time without turning
 * UI progress updates into the bottleneck. Transient failures resume the same sequential transfer
 * from its first unfinished byte. The caller still verifies the signed SHA-256 before activation.
 */
internal object FastFileDownloader {
    private const val BUFFER_BYTES = 256 * 1024
    private const val PROGRESS_BYTES = 512L * 1024L
    private const val PROGRESS_NANOS = 200L * 1_000_000L
    private const val MAX_REQUEST_ATTEMPTS = 6
    private const val INITIAL_RETRY_DELAY_MILLIS = 500L
    private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

    fun download(
        destination: File,
        expectedBytes: Long,
        openConnection: (range: LongRange?) -> HttpURLConnection,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        onDiagnostic: (message: String) -> Unit = {}
    ) {
        require(expectedBytes > 0L) { "Download size must be positive" }
        destination.parentFile?.mkdirs()
        if (destination.length() > expectedBytes) destination.delete()
        downloadSingle(destination, expectedBytes, openConnection, onProgress, onDiagnostic)
        require(destination.length() == expectedBytes) { "Download size verification failed" }
    }

    private fun downloadSingle(
        destination: File,
        expectedBytes: Long,
        openConnection: (LongRange?) -> HttpURLConnection,
        onProgress: (Long, Long) -> Unit,
        onDiagnostic: (String) -> Unit
    ) {
        var cursor = destination.takeIf(File::isFile)?.length() ?: 0L
        val reporter = ProgressReporter(expectedBytes, cursor, onProgress)
        var attempt = 1

        if (cursor > 0L) {
            onDiagnostic("Resuming saved download at byte $cursor of $expectedBytes")
        }

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
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw HttpDownloadException(responseCode)
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

    private fun validateRangeResponse(
        connection: HttpURLConnection,
        range: LongRange,
        expectedBytes: Long
    ) {
        val responseCode = connection.responseCode
        if (isRetryableStatus(responseCode)) {
            throw RetryableDownloadException("Temporary HTTP response $responseCode")
        }
        if (responseCode != HttpURLConnection.HTTP_PARTIAL) {
            throw HttpDownloadException(responseCode)
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

    private fun isRetryable(error: Exception): Boolean = when (error) {
        is HttpDownloadException -> isRetryableStatus(error.status)
        is IOException, is RetryableDownloadException -> true
        else -> false
    }

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

    internal class HttpDownloadException(val status: Int) : IOException(
        "Download request failed with HTTP $status"
    )

    private class RetryableDownloadException(message: String) : IOException(message)

    private class DownloadFailureException(message: String, cause: Throwable) : IOException(message, cause)

    private class ProgressReporter(
        private val totalBytes: Long,
        initialBytes: Long,
        private val callback: (Long, Long) -> Unit
    ) {
        private var downloadedBytes = initialBytes
        private var lastPublishedBytes = initialBytes
        private var lastPublishedAt = System.nanoTime()

        init {
            if (initialBytes > 0L) callback(initialBytes, totalBytes)
        }

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
