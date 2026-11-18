package com.moodtools.hub.modules

import com.moodtools.hub.networking.APKPureDownload
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogModulePlayStoreTest {
    @Test
    fun directGameDownloadStillProvidesCanonicalPlayStoreLink() {
        val module = catalogModule(
            GameInstallSource.DirectDownload(
                path = "/api/launcher-game-download/example/100.apks",
                versionCode = 100,
                version = "1.0.0",
                size = 1024,
                sha256 = "a".repeat(64),
                signingCertificateSha256 = "b".repeat(64),
                format = GamePackageFormat.APKS
            )
        )

        assertEquals(
            "https://play.google.com/store/apps/details?id=com.example.game",
            module.playStoreUrl
        )
    }

    @Test
    fun explicitPlayStoreSourceKeepsItsVerifiedUrl() {
        val url = "https://play.google.com/store/apps/details?id=com.example.game&hl=en"

        assertEquals(url, catalogModule(GameInstallSource.PlayStore(url)).playStoreUrl)
    }

    @Test
    fun playStoreGameActionRequiresADetectedListing() {
        val catalog = catalogModule(
            GameInstallSource.PlayStore(
                "https://play.google.com/store/apps/details?id=com.example.game"
            )
        )
        val undetected = ModuleListing(
            catalog = catalog,
            game = null,
            installedBuild = 0,
            installedComplete = false
        )
        val detected = undetected.copy(
            playStoreVersionStatus = PlayStoreVersionStatus(
                latestVersion = "1.0.0",
                listingUpdatedAtEpochSeconds = null,
                updateAvailable = false,
                checkedAtEpochSeconds = 1,
                checkedDay = 1
            )
        )

        assertEquals(false, undetected.configuredGameSourceAvailable)
        assertEquals(true, detected.configuredGameSourceAvailable)
    }

    @Test
    fun companionDownloadRemainsAvailableWithoutPlayStoreDetection() {
        val listing = ModuleListing(
            catalog = catalogModule(
                GameInstallSource.DirectDownload(
                    path = "/api/launcher-game-download/example/100.apk",
                    versionCode = 100,
                    version = "1.0.0",
                    size = 1024,
                    sha256 = "a".repeat(64),
                    signingCertificateSha256 = "b".repeat(64)
                )
            ),
            game = null,
            installedBuild = 0,
            installedComplete = false
        )

        assertEquals(true, listing.configuredGameSourceAvailable)
    }

    @Test
    fun unsupportedStoreReleaseNeverOffersAnUpdateAndSearchUsesSupportedVersion() {
        val catalog = catalogModule(GameInstallSource.PlayStore("https://play.google.com/store/apps/details?id=com.example.game"))
        val listing = ModuleListing(
            catalog = catalog.copy(config = catalog.config.copy(supportedVersionCodes = setOf(100))),
            game = InstalledGame("com.example.game", "2.0.0", 200, "Example Game", null,
                catalog.config, false, "arm64-v8a", true),
            installedBuild = 0,
            installedComplete = false,
            playStoreVersionStatus = PlayStoreVersionStatus(
                latestVersion = "2.0.0", latestVersionCode = 200,
                listingUpdatedAtEpochSeconds = null, updateAvailable = true,
                checkedAtEpochSeconds = 1, checkedDay = 1
            )
        )
        assertEquals(true, listing.installedGameNewerThanSupported)
        assertEquals(false, listing.configuredGameSourceAvailable)
        assertEquals("https://www.google.com/search?q=Example+Game+1.0.0+APK", listing.supportedGameSearchUrl)
    }

    @Test
    fun apkPureDownloadMustMatchOneExactSupportedReleasePair() {
        val base = catalogModule(
            GameInstallSource.PlayStore("https://play.google.com/store/apps/details?id=com.example.game")
        )
        val catalog = base.copy(config = base.config.copy(supportedVersionCodes = setOf(100L)))
        val download = APKPureDownload(
            url = "https://d.apkpure.net/b/APK/com.example.game?versionCode=100&nc=arm64-v8a&sv=23",
            version = "1.0.0",
            versionCode = 100,
            size = 1024,
            format = GamePackageFormat.APK,
            supportedAbis = setOf("arm64-v8a")
        )
        val listing = ModuleListing(catalog, null, 0, false).copy(
            playStoreVersionStatus = PlayStoreVersionStatus(
                latestVersion = "1.0.0",
                latestVersionCode = 100,
                listingUpdatedAtEpochSeconds = null,
                updateAvailable = false,
                checkedAtEpochSeconds = 1,
                checkedDay = 1,
                apkPureDownload = download
            )
        )

        assertEquals(download, listing.apkPureDownload)
        assertEquals(null, listing.copy(deviceAbis = setOf("armeabi-v7a")).apkPureDownload)
        assertEquals(null, listing.copy(
            playStoreVersionStatus = listing.playStoreVersionStatus?.copy(
                apkPureDownload = download.copy(versionCode = 101)
            )
        ).apkPureDownload)
    }

    private fun catalogModule(source: GameInstallSource) = CatalogModule(
        config = ModuleConfig(
            packageName = "com.example.game",
            title = "Example Game",
            supportedVersions = setOf("1.0.0"),
            supportedAbis = setOf("arm64-v8a"),
            entryPoint = null,
            dexFile = "classes.dex",
            nativeFile = "libmenu_native.so",
            iconFile = null
        ),
        slug = "example",
        build = 1,
        version = "1.0.0",
        notes = null,
        icon = null,
        installSource = source
    )
}
