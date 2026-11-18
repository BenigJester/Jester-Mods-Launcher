package com.moodtools.hub.networking

import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class APKPureRedirectTest {
    @Test fun homepageRedirectIsUnavailableWithoutLeakingTheUrl() {
        for (address in listOf("https://apkpure.com", "https://apkpure.com/?token=private", "https://apkpure.net/")) {
            val error = assertThrows(DownloadValidationException::class.java) {
                requireAPKPureDownloadTarget(URL(address))
            }
            assertEquals(DownloadCheck.SOURCE_UNAVAILABLE, error.check)
            assertEquals("check=source_unavailable", error.message)
            assertTrue(isUnavailableAPKPureDownload(java.io.IOException("transfer failed", error)))
        }
    }

    @Test fun otherHostsAndInsecureTargetsRemainRejected() {
        for (address in listOf("http://data.winudf.com/file", "https://other.example/file",
            "https://data.winudf.com.other.example/file", "https://apkpure.com/unexpected")) {
            val error = assertThrows(DownloadValidationException::class.java) {
                requireAPKPureDownloadTarget(URL(address))
            }
            assertEquals(DownloadCheck.REDIRECT_URL, error.check)
            assertFalse(isUnavailableAPKPureDownload(error))
        }
        requireAPKPureDownloadTarget(URL("https://data.winudf.com/XAPK/file"))
    }
}
