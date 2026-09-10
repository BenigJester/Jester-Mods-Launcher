package com.moodtools.hub

import com.moodtools.hub.modules.PlayStoreVersionStatus
import com.moodtools.hub.modules.ModuleConfig
import com.moodtools.hub.networking.PlayStoreBuildObservation
import com.moodtools.hub.networking.shouldReportPlayStoreBuild
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayStoreStatusFreshnessTest {
    @Test
    fun playStoreCacheExpiresHourlyInsteadOfAtMidnight() {
        assertEquals(false, isPlayStoreCacheExpired(1_000L, 4_599L))
        assertEquals(true, isPlayStoreCacheExpired(1_000L, 4_600L))
    }

    @Test
    fun newerServerMetadataReplacesTheBrowseCache() {
        val cached = status(version = "1.0", checkedAt = 100)
        val updated = status(version = "2.0", checkedAt = 200)

        assertEquals(updated, newestPlayStoreStatus(cached, updated))
    }

    @Test
    fun newerCheckKeepsAConfirmedBuildForTheSameStoreRelease() {
        val cached = status(version = "2.0", checkedAt = 100).copy(latestVersionCode = 20_001L)
        val refreshed = status(version = "2.0", checkedAt = 200)

        assertEquals(
            refreshed.copy(latestVersionCode = 20_001L),
            newestPlayStoreStatus(cached, refreshed)
        )
    }

    @Test
    fun incompleteNewReleaseCannotReplaceAConfirmedCachedVersionAndBuild() {
        val cached = status(version = "2.0", checkedAt = 100).copy(latestVersionCode = 20_001L)
        val incomplete = status(version = "2.1", checkedAt = 200).copy(
            listingUpdatedAtEpochSeconds = 1_800_000_000,
            updateAvailable = true
        )

        assertEquals(
            incomplete.copy(latestVersion = "2.0", latestVersionCode = 20_001L),
            newestPlayStoreStatus(cached, incomplete)
        )
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
    fun buildNumberWinsWhenChecksHaveTheSameTimestamp() {
        val cached = status(version = "2.0", checkedAt = 200)
        val richer = cached.copy(latestVersionCode = 20_001L)

        assertEquals(richer, newestPlayStoreStatus(cached, richer))

        val advanced = richer.copy(latestVersionCode = 20_002L)
        assertEquals(advanced, newestPlayStoreStatus(richer, advanced))
    }

    @Test
    fun refreshedCompatibilityResultWinsForTheSameStoreCheck() {
        val cached = status(version = "2.0", checkedAt = 200)
        val refreshed = cached.copy(updateAvailable = true)

        assertEquals(refreshed, newestPlayStoreStatus(cached, refreshed))
    }

    @Test
    fun unchangedServerMetadataKeepsTheDisplayedCardStable() {
        val displayed = status(version = "2.0", checkedAt = 100).copy(stale = true)
        val refreshed = displayed.copy(checkedAtEpochSeconds = 200, checkedDay = 2, stale = false)

        assertEquals(displayed, stableDisplayedPlayStoreStatus(displayed, refreshed))
        assertEquals(
            refreshed.copy(updateAvailable = true),
            stableDisplayedPlayStoreStatus(displayed, refreshed.copy(updateAvailable = true))
        )
    }

    @Test
    fun compatibilityRequiresTheSupportedGameBuildWhenDeclared() {
        val module = ModuleConfig(
            packageName = "com.example.game",
            title = "Game",
            supportedVersions = setOf("2.0"),
            supportedAbis = setOf("arm64-v8a"),
            entryPoint = null,
            dexFile = "classes.dex",
            nativeFile = "libmenu_native.so",
            iconFile = null,
            supportedVersionCodes = setOf(20_001L)
        )

        assertEquals(true, status("2.0", 200).copy(latestVersionCode = 20_001L).isSupportedBy(module))
        assertEquals(false, status("2.0", 200).copy(latestVersionCode = 20_002L).isSupportedBy(module))
        assertEquals(true, status("2.0", 200).isSupportedBy(module))
    }

    @Test
    fun unavailablePlayStoreBuildCannotClaimIncompatibility() {
        val module = ModuleConfig(
            packageName = "com.example.game",
            title = "Game",
            supportedVersions = setOf("2.0", "2.1"),
            supportedAbis = setOf("arm64-v8a"),
            entryPoint = null,
            dexFile = "classes.dex",
            nativeFile = "libmenu_native.so",
            iconFile = null,
            supportedVersionCodes = setOf(20_001L, 20_002L)
        )
        val unavailableBuild = status("2.1", 200).copy(updateAvailable = true)

        assertEquals(null, unavailableBuild.versionCodeFor(module))
        assertEquals(null, unavailableBuild.isSupportedBy(module))
    }

    @Test
    fun availableUnsupportedPlayStoreVersionIsStrictWithoutABuild() {
        val module = ModuleConfig(
            packageName = "com.example.game",
            title = "Game",
            supportedVersions = setOf("2.0"),
            supportedAbis = setOf("arm64-v8a"),
            entryPoint = null,
            dexFile = "classes.dex",
            nativeFile = "libmenu_native.so",
            iconFile = null,
            supportedVersionCodes = setOf(20_001L)
        )

        assertEquals(false, status("2.1", 200).copy(updateAvailable = true).isSupportedBy(module))
    }

    @Test
    fun newerPlayStoreListingIsOutdatedWhenVersionNameAndCachedBuildStayTheSame() {
        val module = ModuleConfig(
            packageName = "com.example.game",
            title = "Game",
            supportedVersions = setOf("2.0"),
            supportedAbis = setOf("arm64-v8a"),
            entryPoint = null,
            dexFile = "classes.dex",
            nativeFile = "libmenu_native.so",
            iconFile = null,
            supportedVersionCodes = setOf(20_001L)
        )

        assertEquals(
            false,
            status("2.0", 200).copy(
                latestVersionCode = 20_001L,
                updateAvailable = true
            ).isSupportedBy(module)
        )
    }

    @Test
    fun confirmedCatalogReleaseSuppliesBuildMissingFromOlderDeviceCache() {
        val module = ModuleConfig(
            packageName = "com.example.game",
            title = "Game",
            supportedVersions = setOf("2.0"),
            supportedAbis = setOf("arm64-v8a"),
            entryPoint = null,
            dexFile = "classes.dex",
            nativeFile = "libmenu_native.so",
            iconFile = null,
            supportedVersionCodes = setOf(20_001L)
        )

        assertEquals(20_001L, status("2.0", 200).versionCodeFor(module))
        assertEquals(null, status("2.0", 200).copy(updateAvailable = true).versionCodeFor(module))
    }

    @Test
    fun officialInstalledBuildIsReportedOnlyWhileNewerAndUnacknowledged() {
        val observation = PlayStoreBuildObservation(
            "com.example.game",
            "8.5.1",
            80_513L,
            "com.android.vending",
            "a".repeat(64)
        )

        assertEquals(true, shouldReportPlayStoreBuild(observation, "8.5.1", 80_512L, true, null))
        assertEquals(false, shouldReportPlayStoreBuild(observation, "8.5.1", 80_513L, true, null))
        assertEquals(false, shouldReportPlayStoreBuild(observation, "8.5.1", 80_512L, true, observation.key))
        assertEquals(false, shouldReportPlayStoreBuild(observation, "8.5.0", 80_512L, true, null))
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
