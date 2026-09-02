package com.moodtools.hub.networking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlayStoreVersionClientTest {
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

        val result = parsePlayStoreVersionResult("com.os.airforce", body)

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

        assertNull(parsePlayStoreVersionResult("com.os.airforce", body))
    }

    @Test
    fun parsesRequestedBatchAndIgnoresNoPackagesSilently() {
        val result = parsePlayStoreVersionResults(
            setOf("com.os.airforce", "com.playrix.township"),
            JSONObject()
                .put("ok", true)
                .put("schema", 1)
                .put("results", org.json.JSONArray().put(
                    JSONObject()
                        .put("ok", true)
                        .put("packageName", "com.os.airforce")
                        .put("version", "15.76")
                        .put("listingUpdatedAt", 1_787_824_746L)
                        .put("updateAvailable", false)
                        .put("checkedAt", 1_787_824_800L)
                        .put("stale", false)
                ))
        )

        assertEquals(setOf("com.os.airforce"), result.keys)
        assertEquals("15.76", result["com.os.airforce"]?.version)
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
            parsePlayStoreVersionResults(setOf("com.os.airforce"), body)
        }
    }
}
