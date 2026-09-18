package com.moodtools.hub.networking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class APKPureVersionClientTest {
    @Test
    fun parsesListingRevisionWhenGoogleDoesNotPublishVersion() {
        val body = JSONObject()
            .put("ok", true)
            .put("packageName", "com.os.airforce")
            .put("version", JSONObject.NULL)
            .put("listingUpdatedAt", 1_787_824_746L)
            .put("updateAvailable", false)
            .put("checkedAt", 1_787_824_800L)
            .put("stale", false)

        val result = parseAPKPureVersionResult("com.os.airforce", body)

        assertNull(result?.version)
        assertEquals(1_787_824_746L, result?.listingUpdatedAtEpochSeconds)
        assertEquals(false, result?.updateAvailable)
    }

    @Test
    fun rejectsResponseWithoutVersionOrListingRevision() {
        val body = JSONObject()
            .put("ok", true)
            .put("packageName", "com.os.airforce")
            .put("version", JSONObject.NULL)
            .put("listingUpdatedAt", JSONObject.NULL)
            .put("checkedAt", 1_787_824_800L)

        assertNull(parseAPKPureVersionResult("com.os.airforce", body))
    }

    @Test
    fun parsesRequestedBatchAndIgnoresNoPackagesSilently() {
        val result = parseAPKPureVersionResults(
            setOf("com.os.airforce", "com.playrix.township"),
            JSONObject()
                .put("ok", true)
                .put("schema", 1)
                .put("results", org.json.JSONArray().put(
                    JSONObject()
                        .put("ok", true)
                        .put("packageName", "com.os.airforce")
                        .put("version", "15.76")
                        .put("versionCode", 157_600L)
                        .put("listingUpdatedAt", 1_787_824_746L)
                        .put("updateAvailable", false)
                        .put("checkedAt", 1_787_824_800L)
                        .put("stale", false)
                ))
        )

        assertEquals(setOf("com.os.airforce"), result.keys)
        assertEquals("15.76", result["com.os.airforce"]?.version)
        assertEquals(157_600L, result["com.os.airforce"]?.versionCode)
    }

    @Test
    fun rejectsInvalidAPKPureBuildNumber() {
        val body = JSONObject()
            .put("ok", true)
            .put("packageName", "com.os.airforce")
            .put("version", "15.76")
            .put("versionCode", 0)
            .put("checkedAt", 1_787_824_800L)

        assertNull(parseAPKPureVersionResult("com.os.airforce", body))
    }

    @Test
    fun rejectsUnexpectedPackageInBatch() {
        val body = JSONObject()
            .put("ok", true)
            .put("schema", 1)
            .put("results", org.json.JSONArray().put(
                JSONObject()
                    .put("ok", true)
                    .put("packageName", "com.example.unexpected")
                    .put("version", "1.0")
                    .put("checkedAt", 1_787_824_800L)
            ))

        assertThrows(IllegalArgumentException::class.java) {
            parseAPKPureVersionResults(setOf("com.os.airforce"), body)
        }
    }

    @Test
    fun acceptsOnlyExactAPKPureDownloadEndpoints() {
        val download = JSONObject()
            .put("url", "https://d.apkpure.net/b/XAPK/com.os.airforce?versionCode=1576&nc=arm64-v8a&sv=24")
            .put("version", "15.76")
            .put("versionCode", 1_576L)
            .put("size", 785_410_663L)
            .put("format", "xapk")
            .put("supportedAbis", org.json.JSONArray().put("arm64-v8a"))

        assertNotNull(parseAPKPureDownload("com.os.airforce", download))
        download.put("url", "https://example.com/b/XAPK/com.os.airforce?versionCode=1576")
        assertNull(parseAPKPureDownload("com.os.airforce", download))
    }

    @Test
    fun recognizesAPKPurePackageNamedBaseApk() {
        assertEquals(true, isBaseApkEntry("com.ctugames.km2.apk", "com.ctugames.km2"))
        assertEquals(true, isBaseApkEntry("splits/base-master.apk", "com.ctugames.km2"))
        assertEquals(false, isBaseApkEntry("config.arm64_v8a.apk", "com.ctugames.km2"))
    }

    @Test
    fun rejectsApkSetWhenItsOnlyAbiDoesNotRunOnDevice() {
        val error = assertThrows(GameInstallCompatibilityException::class.java) {
            requireCompatibleApkSet(
                listOf("com.ctugames.km2.apk", "config.armeabi_v7a.apk", "config.en.apk"),
                setOf("arm64-v8a")
            )
        }

        assertEquals(true, error.message!!.contains("32-bit ARM"))
        requireCompatibleApkSet(
            listOf("base.apk", "config.arm64_v8a.apk"),
            setOf("arm64-v8a")
        )
    }
}
