package com.moodtools.hub

import com.moodtools.hub.modules.PlayStoreVersionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayStoreStatusFreshnessTest {
    @Test
    fun newerServerMetadataReplacesTheBrowseCache() {
        val cached = status(version = "1.0", checkedAt = 100)
        val updated = status(version = "2.0", checkedAt = 200)

        assertEquals(updated, newestPlayStoreStatus(cached, updated))
    }

    @Test
    fun olderServerCacheCannotReplaceNewerDeviceMetadata() {
        val cached = status(version = "2.0", checkedAt = 200)
        val older = status(version = "1.0", checkedAt = 100)

        assertEquals(cached, newestPlayStoreStatus(cached, older))
    }

    @Test
    fun richerMetadataWinsWhenChecksHaveTheSameTimestamp() {
        val cached = status(version = null, checkedAt = 200)
        val richer = status(version = "2.0", checkedAt = 200)

        assertEquals(richer, newestPlayStoreStatus(cached, richer))
    }

    @Test
    fun refreshedCompatibilityResultWinsForTheSameStoreCheck() {
        val cached = status(version = "2.0", checkedAt = 200)
        val refreshed = cached.copy(updateAvailable = true)

        assertEquals(refreshed, newestPlayStoreStatus(cached, refreshed))
    }

    private fun status(version: String?, checkedAt: Long) = PlayStoreVersionStatus(
        latestVersion = version,
        listingUpdatedAtEpochSeconds = 1_700_000_000,
        updateAvailable = false,
        checkedAtEpochSeconds = checkedAt,
        checkedDay = 1,
        stale = false
    )
}
