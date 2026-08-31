package com.moodtools.hub.networking

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
