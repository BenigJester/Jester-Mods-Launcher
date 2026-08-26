package com.moodtools.hub.networking

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun downloadsLargeFilesInVerifiedRanges() {
        val payload = deterministicBytes(17 * 1024 * 1024 + 37)
        val destination = temporaryFile()
        val requestedRanges = Collections.synchronizedList(mutableListOf<LongRange>())
        val progress = Collections.synchronizedList(mutableListOf<Long>())
        try {
            FastFileDownloader.download(
                destination = destination,
                expectedBytes = payload.size.toLong(),
                openConnection = { range ->
                    requireNotNull(range)
                    requestedRanges += range
                    FakeDownloadConnection(payload, range)
                },
                onProgress = { downloaded, _ -> progress += downloaded }
            )

            assertArrayEquals(payload, destination.readBytes())
            val sortedRanges = requestedRanges.sortedBy(LongRange::first)
            assertEquals(3, sortedRanges.size)
            assertEquals(0L, sortedRanges.first().first)
            assertEquals(payload.lastIndex.toLong(), sortedRanges.last().last)
            sortedRanges.zipWithNext().forEach { (before, after) ->
                assertEquals(before.last + 1L, after.first)
            }
            assertEquals(payload.size.toLong(), sortedRanges.sumOf { it.last - it.first + 1L })
            assertEquals(payload.size.toLong(), progress.last())
            assertTrue(progress.size < payload.size / (64 * 1024))
        } finally {
            destination.delete()
        }
    }

    @Test
    fun downloadsLargeFilesInOneStreamWhenParallelRangesAreDisabled() {
        val payload = deterministicBytes(19 * 1024 * 1024 + 37)
        val destination = temporaryFile()
        val requestedRanges = Collections.synchronizedList(mutableListOf<LongRange?>())
        try {
            FastFileDownloader.download(
                destination = destination,
                expectedBytes = payload.size.toLong(),
                openConnection = { range ->
                    requestedRanges += range
                    FakeDownloadConnection(payload, range)
                },
                allowParallelRanges = false
            )

            assertArrayEquals(payload, destination.readBytes())
            assertEquals(listOf<LongRange?>(null), requestedRanges)
        } finally {
            destination.delete()
        }
    }

    @Test
    fun fallsBackToOneStreamWhenRangesAreUnavailable() {
        val payload = deterministicBytes(9 * 1024 * 1024 + 11)
        val destination = temporaryFile()
        val rangeAttempts = AtomicInteger()
        val fullAttempts = AtomicInteger()
        try {
            FastFileDownloader.download(
                destination = destination,
                expectedBytes = payload.size.toLong(),
                openConnection = { range ->
                    if (range == null) {
                        fullAttempts.incrementAndGet()
                        FakeDownloadConnection(payload, null)
                    } else {
                        rangeAttempts.incrementAndGet()
                        FakeDownloadConnection(payload, null)
                    }
                }
            )

            assertArrayEquals(payload, destination.readBytes())
            assertTrue(rangeAttempts.get() in 1..2)
            assertEquals(1, fullAttempts.get())
        } finally {
            destination.delete()
        }
    }

    @Test
    fun resumesOnlyTheUnfinishedBytesOfABrokenRange() {
        val payload = deterministicBytes(17 * 1024 * 1024 + 37)
        val destination = temporaryFile()
        val requestedRanges = Collections.synchronizedList(mutableListOf<LongRange>())
        val diagnostics = Collections.synchronizedList(mutableListOf<String>())
        val firstPartAttempts = AtomicInteger()
        val fullAttempts = AtomicInteger()
        try {
            FastFileDownloader.download(
                destination = destination,
                expectedBytes = payload.size.toLong(),
                openConnection = { range ->
                    if (range == null) {
                        fullAttempts.incrementAndGet()
                        FakeDownloadConnection(payload, null)
                    } else {
                        requestedRanges += range
                        val failAfter = if (range.first == 0L && firstPartAttempts.getAndIncrement() == 0) {
                            768 * 1024
                        } else {
                            null
                        }
                        FakeDownloadConnection(payload, range, failAfter)
                    }
                },
                onDiagnostic = { diagnostics += it }
            )

            assertArrayEquals(payload, destination.readBytes())
            val firstPartRequests = requestedRanges.filter { it.first < payload.size / 3 }
                .sortedBy(LongRange::first)
            assertEquals(2, firstPartRequests.size)
            assertEquals(0L, firstPartRequests[0].first)
            assertEquals((768 * 1024).toLong(), firstPartRequests[1].first)
            assertEquals(firstPartRequests[0].last, firstPartRequests[1].last)
            assertEquals(0, fullAttempts.get())
            assertTrue(diagnostics.any { it.contains("Retrying range 1/3") })
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
        // The published compressed Otherworld Legends native payload is 7,695,424 bytes. Keep this
        // regression below the 8 MiB parallel threshold so the production-sized medium-file
        // path and its exact-byte resume behavior remain covered without depending on a private
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
        try {
            val error = assertThrows(IOException::class.java) {
                FastFileDownloader.download(
                    destination = destination,
                    expectedBytes = payload.size.toLong(),
                    openConnection = { range ->
                        if (range == null) fullAttempts.incrementAndGet()
                        FakeDownloadConnection(payload, range, wrongContentRange = range != null)
                    }
                )
            }

            assertTrue(error.message.orEmpty().contains("Range"))
            assertEquals(0, fullAttempts.get())
            assertFalse(destination.exists())
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
