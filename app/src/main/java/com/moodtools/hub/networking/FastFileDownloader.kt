package com.moodtools.hub.networking

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Streams a signed, fixed-size file without turning UI progress updates into the bottleneck.
 * Large files use independent byte ranges when the server supports them and safely fall back to
 * one connection otherwise. The caller still verifies the signed SHA-256 before activation.
 */
internal object FastFileDownloader {
    private const val BUFFER_BYTES = 256 * 1024
    private const val PROGRESS_BYTES = 512L * 1024L
    private const val PROGRESS_NANOS = 200L * 1_000_000L
    private const val PARALLEL_PART_BYTES = 8L * 1024L * 1024L
    private const val MAX_PARALLEL_PARTS = 4
    private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

    fun download(
        destination: File,
        expectedBytes: Long,
        openConnection: (range: LongRange?) -> HttpURLConnection,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ) {
        require(expectedBytes > 0L) { "Download size must be positive" }
        destination.parentFile?.mkdirs()
        destination.delete()

        val parts = min(
            MAX_PARALLEL_PARTS,
            ((expectedBytes + PARALLEL_PART_BYTES - 1L) / PARALLEL_PART_BYTES).toInt()
        )
        val downloadedInRanges = parts >= 2 && tryParallelDownload(
            destination,
            expectedBytes,
            parts,
            openConnection,
            onProgress
        )
        if (!downloadedInRanges) {
            destination.delete()
            downloadSingle(destination, expectedBytes, openConnection, onProgress)
        }
        require(destination.length() == expectedBytes) { "Download size verification failed" }
    }

    private fun downloadSingle(
        destination: File,
        expectedBytes: Long,
        openConnection: (LongRange?) -> HttpURLConnection,
        onProgress: (Long, Long) -> Unit
    ) {
        val reporter = ProgressReporter(expectedBytes, onProgress)
        val connection = openConnection(null)
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Download request failed: ${connection.responseCode}"
            }
            validateContentLength(connection, expectedBytes)
            var received = 0L
            BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
                FileOutputStream(destination).use { fileOutput ->
                    BufferedOutputStream(fileOutput, BUFFER_BYTES).use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            require(received <= expectedBytes) { "Download is larger than expected" }
                            output.write(buffer, 0, count)
                            reporter.add(count.toLong())
                        }
                        output.flush()
                        fileOutput.fd.sync()
                    }
                }
            }
            require(received == expectedBytes) { "Download ended before the expected size" }
            reporter.finish()
        } finally {
            connection.disconnect()
        }
    }

    private fun tryParallelDownload(
        destination: File,
        expectedBytes: Long,
        parts: Int,
        openConnection: (LongRange?) -> HttpURLConnection,
        onProgress: (Long, Long) -> Unit
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
                var connection: HttpURLConnection? = null
                try {
                    if (failure.get() != null) return@Thread
                    val range = start..end
                    connection = openConnection(range)
                    if (failure.get() != null) return@Thread
                    synchronized(connectionLock) { activeConnections += connection }
                    downloadRange(
                        destination,
                        expectedBytes,
                        range,
                        connection,
                        reporter,
                        failure
                    )
                } catch (error: Throwable) {
                    stopWorkers(error)
                } finally {
                    connection?.let { opened ->
                        synchronized(connectionLock) { activeConnections -= opened }
                        opened.disconnect()
                    }
                }
            }, "MoodToolsRangeDownload-$index").apply { start() }
        }

        try {
            workers.forEach(Thread::join)
        } catch (error: InterruptedException) {
            stopWorkers(error)
            workers.forEach(Thread::interrupt)
            throw error
        }
        if (failure.get() != null) {
            destination.delete()
            onProgress(0L, expectedBytes)
            return false
        }
        RandomAccessFile(destination, "rw").use { it.fd.sync() }
        reporter.finish()
        return true
    }

    private fun downloadRange(
        destination: File,
        expectedBytes: Long,
        range: LongRange,
        connection: HttpURLConnection,
        reporter: ProgressReporter,
        failure: AtomicReference<Throwable?>
    ) {
        require(connection.responseCode == HttpURLConnection.HTTP_PARTIAL) {
            "Download server does not support byte ranges"
        }
        val expectedPartBytes = range.last - range.first + 1L
        validateContentLength(connection, expectedPartBytes)
        val contentRange = connection.getHeaderField("Content-Range")?.trim().orEmpty()
        val match = CONTENT_RANGE.matchEntire(contentRange)
            ?: error("Download server returned an invalid byte range")
        require(match.groupValues[1].toLong() == range.first &&
            match.groupValues[2].toLong() == range.last &&
            match.groupValues[3].toLong() == expectedBytes) {
            "Download server returned the wrong byte range"
        }

        var received = 0L
        BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
            RandomAccessFile(destination, "rw").use { output ->
                output.seek(range.first)
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    failure.get()?.let { throw InterruptedException("Parallel download stopped") }
                    val count = input.read(buffer)
                    if (count < 0) break
                    received += count
                    require(received <= expectedPartBytes) { "Download range is larger than expected" }
                    output.write(buffer, 0, count)
                    reporter.add(count.toLong())
                }
            }
        }
        require(received == expectedPartBytes) { "Download range ended before the expected size" }
    }

    private fun validateContentLength(connection: HttpURLConnection, expectedBytes: Long) {
        val contentLength = connection.contentLengthLong
        require(contentLength < 0L || contentLength == expectedBytes) {
            "Download response size does not match the signed size"
        }
    }

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
        fun finish() {
            require(downloadedBytes == totalBytes) { "Download progress did not reach the expected size" }
            if (lastPublishedBytes != totalBytes) callback(totalBytes, totalBytes)
        }
    }
}
