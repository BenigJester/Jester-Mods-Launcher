package com.moodtools.hub.modules

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
