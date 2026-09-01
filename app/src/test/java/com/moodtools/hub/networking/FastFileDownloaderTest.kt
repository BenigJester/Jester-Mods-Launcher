package com.moodtools.hub.networking

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class FastFileDownloaderTest {
    @Test
    fun downloadsLargeFilesInExactlyOneStream() {
        val payload = deterministicBytes(17 * 1024 * 1024 + 37)
        val destination = temporaryFile()
        val requestedRanges = Collections.synchronizedList(mutableListOf<LongRange?>())
        val progress = Collections.synchronizedList(mutableListOf<Long>())
        try {
            FastFileDownloader.download(
                destination = destination,
                expectedBytes = payload.size.toLong(),
                openConnection = { range ->
                    requestedRanges += range
                    FakeDownloadConnection(payload, range)
                },
                onProgress = { downloaded, _ -> progress += downloaded }
            )

            assertArrayEquals(payload, destination.readBytes())
            assertEquals(listOf<LongRange?>(null), requestedRanges)
            assertEquals(progress.sorted(), progress)
            assertEquals(payload.size.toLong(), progress.last())
            assertTrue(progress.size < payload.size / (64 * 1024))
        } finally {
            destination.delete()
        }
    }

    @Test
    fun restartsTheSingleStreamWhenTheServerIgnoresAResumeRange() {
        val payload = deterministicBytes(9 * 1024 * 1024 + 11)
        val destination = temporaryFile()
        val requestedRanges = Collections.synchronizedList(mutableListOf<LongRange?>())
        val fullAttempts = AtomicInteger()
        val interruptedAt = 640 * 1024
        try {
            FastFileDownloader.download(
                destination = destination,
                expectedBytes = payload.size.toLong(),
                openConnection = { range ->
                    requestedRanges += range
                    if (range == null) {
                        val failAfter = if (fullAttempts.getAndIncrement() == 0) interruptedAt else null
                        FakeDownloadConnection(payload, null, failAfter)
                    } else {
                        // Simulate a server returning a full 200 response to the resume request.
                        FakeDownloadConnection(payload, null)
                    }
                }
            )

            assertArrayEquals(payload, destination.readBytes())
            assertEquals(
                listOf<LongRange?>(null, interruptedAt.toLong()..payload.lastIndex.toLong()),
                requestedRanges
            )
            assertEquals(1, fullAttempts.get())
        } finally {
            destination.delete()
        }
    }

    @Test
    fun resumesOnlyTheUnfinishedBytesOfABrokenLargeStream() {
        val payload = deterministicBytes(17 * 1024 * 1024 + 37)
        val destination = temporaryFile()
        val requestedRanges = Collections.synchronizedList(mutableListOf<LongRange?>())
        val diagnostics = Collections.synchronizedList(mutableListOf<String>())
        val fullAttempts = AtomicInteger()
        val interruptedAt = 768 * 1024
        try {
            FastFileDownloader.download(
                destination = destination,
                expectedBytes = payload.size.toLong(),
                openConnection = { range ->
                    requestedRanges += range
                    val failAfter = if (range == null && fullAttempts.getAndIncrement() == 0) {
                        interruptedAt
                    } else null
                    FakeDownloadConnection(payload, range, failAfter)
                },
                onDiagnostic = { diagnostics += it }
            )

            assertArrayEquals(payload, destination.readBytes())
            assertEquals(
                listOf<LongRange?>(null, interruptedAt.toLong()..payload.lastIndex.toLong()),
                requestedRanges
            )
            assertEquals(1, fullAttempts.get())
            assertTrue(diagnostics.any {
                it.contains("Retrying stream at byte $interruptedAt of ${payload.size}")
            })
        } finally {
            destination.delete()
        }
    }

    @Test
    fun resumesASmallSingleStreamDownload() {
        val payload = deterministicBytes(2 * 1024 * 1024 + 19)
        val destination = temporaryFile()
        val requestedRanges = Collections.synchronizedList(mutableListOf<LongRange?>())
        val progress = Collections.synchronizedList(mutableListOf<Long>())
        val fullAttempts = AtomicInteger()
        try {
            FastFileDownloader.download(
                destination = destination,
                expectedBytes = payload.size.toLong(),
                openConnection = { range ->
                    requestedRanges += range
                    val failAfter = if (range == null && fullAttempts.getAndIncrement() == 0) {
                        640 * 1024
                    } else {
                        null
                    }
                    FakeDownloadConnection(payload, range, failAfter)
                },
                onProgress = { downloaded, _ -> progress += downloaded }
            )

            assertArrayEquals(payload, destination.readBytes())
            assertEquals(null, requestedRanges[0])
            assertEquals((640 * 1024).toLong(), requestedRanges[1]?.first)
            assertEquals(payload.lastIndex.toLong(), requestedRanges[1]?.last)
            assertEquals(progress.sorted(), progress)
            assertEquals(payload.size.toLong(), progress.last())
        } finally {
            destination.delete()
        }
    }

    @Test
    fun resumesAnOtherworldSizedSingleStreamDownload() {
        // The published compressed Otherworld Legends native payload is 7,695,424 bytes. Keep its
        // production-sized exact-byte resume behavior covered without depending on a private
        // module artifact in source control.
        val payload = deterministicBytes(7_695_424)
        val destination = temporaryFile()
        val requestedRanges = Collections.synchronizedList(mutableListOf<LongRange?>())
        val progress = Collections.synchronizedList(mutableListOf<Long>())
        val diagnostics = Collections.synchronizedList(mutableListOf<String>())
        val fullAttempts = AtomicInteger()
        val interruptedAt = 2_359_296
        try {
            FastFileDownloader.download(
                destination = destination,
                expectedBytes = payload.size.toLong(),
                openConnection = { range ->
                    requestedRanges += range
                    val failAfter = if (range == null && fullAttempts.getAndIncrement() == 0) {
                        interruptedAt
                    } else {
                        null
                    }
                    FakeDownloadConnection(payload, range, failAfter)
                },
                onProgress = { downloaded, _ -> progress += downloaded },
                onDiagnostic = { diagnostics += it }
            )

            assertArrayEquals(payload, destination.readBytes())
            assertEquals(listOf(null, interruptedAt.toLong()..payload.lastIndex.toLong()), requestedRanges)
            assertEquals(progress.sorted(), progress)
            assertEquals(payload.size.toLong(), progress.last())
            assertTrue(diagnostics.any {
                it.contains("Retrying stream at byte $interruptedAt of ${payload.size}")
            })
        } finally {
            destination.delete()
        }
    }

    @Test
    fun doesNotHideInvalidRangeMetadataWithAFullRestart() {
        val payload = deterministicBytes(9 * 1024 * 1024 + 11)
        val destination = temporaryFile()
        val fullAttempts = AtomicInteger()
        val interruptedAt = 640 * 1024
        try {
            val error = assertThrows(IOException::class.java) {
                FastFileDownloader.download(
                    destination = destination,
                    expectedBytes = payload.size.toLong(),
                    openConnection = { range ->
                        val failAfter = if (range == null && fullAttempts.getAndIncrement() == 0) {
                            interruptedAt
                        } else null
                        FakeDownloadConnection(
                            payload,
                            range,
                            failAfterBytes = failAfter,
                            wrongContentRange = range != null
                        )
                    }
                )
            }

            assertTrue(error.cause?.message.orEmpty().contains("range", ignoreCase = true))
            assertEquals(1, fullAttempts.get())
            assertEquals(interruptedAt.toLong(), destination.length())
        } finally {
            destination.delete()
        }
    }

    @Test
    fun preservesAndResumesProgressAcrossSeparateDownloadAttempts() {
        val payload = deterministicBytes(4 * 1024 * 1024 + 29)
        val destination = temporaryFile()
        val requestedRanges = mutableListOf<LongRange?>()
        val interruptedAt = 640 * 1024
        var requests = 0
        try {
            assertThrows(IOException::class.java) {
                FastFileDownloader.download(
                    destination = destination,
                    expectedBytes = payload.size.toLong(),
                    openConnection = { range ->
                        requestedRanges += range
                        when (requests++) {
                            0 -> FakeDownloadConnection(payload, range, interruptedAt)
                            else -> HttpStatusConnection(401)
                        }
                    }
                )
            }
            assertEquals(interruptedAt.toLong(), destination.length())

            FastFileDownloader.download(
                destination = destination,
                expectedBytes = payload.size.toLong(),
                openConnection = { range ->
                    requestedRanges += range
                    FakeDownloadConnection(payload, range)
                }
            )

            assertArrayEquals(payload, destination.readBytes())
            assertEquals(interruptedAt.toLong(), requestedRanges.last()?.first)
        } finally {
            destination.delete()
        }
    }

    @Test
    fun cancellationStopsTheStreamAndKeepsResumableProgress() {
        val payload = deterministicBytes(4 * 1024 * 1024 + 17)
        val destination = temporaryFile()
        var cancelled = false
        try {
            assertThrows(FastFileDownloader.DownloadCancelledException::class.java) {
                FastFileDownloader.download(
                    destination = destination,
                    expectedBytes = payload.size.toLong(),
                    openConnection = { range -> FakeDownloadConnection(payload, range) },
                    onProgress = { downloaded, _ ->
                        if (downloaded >= 512L * 1024L) cancelled = true
                    },
                    isCancelled = { cancelled }
                )
            }

            assertTrue(destination.length() >= 512L * 1024L)
            assertTrue(destination.length() < payload.size.toLong())
            assertArrayEquals(
                payload.copyOf(destination.length().toInt()),
                destination.readBytes()
            )
        } finally {
            destination.delete()
        }
    }

    private fun deterministicBytes(size: Int) = ByteArray(size) { index ->
        ((index * 31 + index / 251) and 0xff).toByte()
    }

    private fun temporaryFile(): File = File.createTempFile("fast-download-", ".part").apply {
        delete()
    }

    private class FakeDownloadConnection(
        private val payload: ByteArray,
        private val range: LongRange?,
        private val failAfterBytes: Int? = null,
        private val wrongContentRange: Boolean = false
    ) : HttpURLConnection(URL("https://jester.moodtools.workers.dev/test")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false

        override fun getResponseCode(): Int = if (range == null) HTTP_OK else HTTP_PARTIAL

        override fun getContentLengthLong(): Long = range?.let { it.last - it.first + 1L }
            ?: payload.size.toLong()

        override fun getHeaderField(name: String?): String? = when {
            name.equals("Content-Range", ignoreCase = true) && range != null ->
                if (wrongContentRange) {
                    "bytes ${range.first + 1L}-${range.last}/${payload.size}"
                } else {
                    "bytes ${range.first}-${range.last}/${payload.size}"
                }
            else -> null
        }

        override fun getInputStream(): InputStream = (range?.let {
            ByteArrayInputStream(payload, it.first.toInt(), (it.last - it.first + 1L).toInt())
        } ?: ByteArrayInputStream(payload)).let { input ->
            failAfterBytes?.let { FailingInputStream(input, it) } ?: input
        }
    }

    private class HttpStatusConnection(
        private val status: Int
    ) : HttpURLConnection(URL("https://jester.moodtools.workers.dev/test")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode(): Int = status
    }

    private class FailingInputStream(
        private val delegate: InputStream,
        private val failAfterBytes: Int
    ) : InputStream() {
        private var delivered = 0

        override fun read(): Int {
            if (delivered >= failAfterBytes) throw IOException("Simulated connection loss")
            return delegate.read().also { if (it >= 0) delivered++ }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (delivered >= failAfterBytes) throw IOException("Simulated connection loss")
            val allowed = minOf(length, failAfterBytes - delivered)
            return delegate.read(buffer, offset, allowed).also { count ->
                if (count > 0) delivered += count
            }
        }
    }
}
