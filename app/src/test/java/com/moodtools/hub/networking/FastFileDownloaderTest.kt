package com.moodtools.hub.networking

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
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

    private fun deterministicBytes(size: Int) = ByteArray(size) { index ->
        ((index * 31 + index / 251) and 0xff).toByte()
    }

    private fun temporaryFile(): File = File.createTempFile("fast-download-", ".part").apply {
        delete()
    }

    private class FakeDownloadConnection(
        private val payload: ByteArray,
        private val range: LongRange?
    ) : HttpURLConnection(URL("https://jester.moodtools.workers.dev/test")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false

        override fun getResponseCode(): Int = if (range == null) HTTP_OK else HTTP_PARTIAL

        override fun getContentLengthLong(): Long = range?.let { it.last - it.first + 1L }
            ?: payload.size.toLong()

        override fun getHeaderField(name: String?): String? = when {
            name.equals("Content-Range", ignoreCase = true) && range != null ->
                "bytes ${range.first}-${range.last}/${payload.size}"
            else -> null
        }

        override fun getInputStream(): InputStream = range?.let {
            ByteArrayInputStream(payload, it.first.toInt(), (it.last - it.first + 1L).toInt())
        } ?: ByteArrayInputStream(payload)
    }
}
