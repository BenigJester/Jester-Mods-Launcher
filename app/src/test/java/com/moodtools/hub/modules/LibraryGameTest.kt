package com.moodtools.hub.modules

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGameTest {
    @Test
    fun statusKeepsMissingGamesAndExplainsRecovery() {
        val module = module("com.example.missing", "Missing")
        val entry = LibraryGame(
            module = module,
            game = null,
            listing = null,
            installedBuild = 1,
            installedComplete = true
        )

        assertEquals(LibraryGameStatus.GAME_REQUIRED, entry.status)
    }

    @Test
    fun statusPrioritizesCompatibilityRepairAndUpdates() {
        val repair = libraryGame("Repair", installedComplete = false)
        val update = libraryGame("Update", installedBuild = 1, latestBuild = 2)
        val unsupported = libraryGame("Unsupported", versionSupported = false)

        assertEquals(LibraryGameStatus.REPAIR_NEEDED, repair.status)
        assertEquals(LibraryGameStatus.UPDATE_AVAILABLE, update.status)
        assertEquals(LibraryGameStatus.UNSUPPORTED_VERSION, unsupported.status)
    }

    @Test
    fun libraryIsAlwaysAlphabeticalRegardlessOfRunningOrRecentState() {
        val alpha = libraryGame("Alpha", lastLaunchedAt = 10)
        val beta = libraryGame("Beta", lastLaunchedAt = 20)
        val running = libraryGame("Running", lastLaunchedAt = 1, running = true)
        val neverZed = libraryGame("Zed")
        val neverAble = libraryGame("Able")

        assertEquals(
            listOf(neverAble, alpha, beta, running, neverZed),
            sortLibraryGames(listOf(neverZed, alpha, running, neverAble, beta))
        )
    }

    @Test
    fun localOnlyModulesRemainVisibleWhenTheLiveCatalogIsAvailable() {
        val catalog = module("com.example.catalog", "Catalog")
        val stagedCatalog = module("com.example.catalog", "Catalog local test")
        val avatar = module("com.pazugames.avatarworld", "Avatar World")

        assertEquals(
            listOf(stagedCatalog, avatar),
            mergeCatalogAndLocalModuleConfigs(
                catalogConfigs = listOf(catalog),
                localConfigs = listOf(stagedCatalog, avatar)
            )
        )
    }

    @Test
    fun libraryCarriesTheResolvedPrimaryLaunchAction() {
        val entry = libraryGame("Patch").copy(
            launchAction = LibraryLaunchAction.PATCH_AND_INSTALL
        )

        assertEquals(LibraryLaunchAction.PATCH_AND_INSTALL, entry.launchAction)
        assertEquals(LibraryGameStatus.READY, entry.status)
    }

    @Test
    fun libraryCarriesPrivateGrantIdentityAndExpiryFromItsListing() {
        val scope = "private.township"
        val expiry = 1_900_000_000L
        val regular = libraryGame("Private")
        val listing = regular.listing!!.copy(
            catalog = regular.listing.catalog.copy(privateScope = scope),
            privateAccessExpiresAtEpochSeconds = expiry
        )
        val privateGame = LibraryGame(
            module = regular.module,
            game = regular.game,
            listing = listing,
            installedBuild = regular.installedBuild,
            installedComplete = regular.installedComplete
        )

        assertEquals(scope, privateGame.privateScope)
        assertEquals(expiry, privateGame.privateAccessExpiresAtEpochSeconds)
    }

    @Test
    fun updatingCatalogStateSuppressesOnlyThePlayStoreOutdatedWarning() {
        val playStore = PlayStoreVersionStatus(
            latestVersion = "2.0",
            listingUpdatedAtEpochSeconds = null,
            updateAvailable = true,
            checkedAtEpochSeconds = 1,
            checkedDay = 1
        )
        val outdated = libraryGame("Outdated").copy(playStoreVersionStatus = playStore)
        val updatingListing = outdated.listing!!.copy(
            catalog = outdated.listing.catalog.copy(updateStatus = ModuleUpdateStatus.UPDATING),
            playStoreVersionStatus = playStore
        )
        val updating = outdated.copy(
            listing = updatingListing,
            playStoreVersionStatus = playStore
        )

        assertEquals(true, outdated.playStoreOutdatedWarning)
        assertEquals(false, outdated.playStoreUpdateInProgress)
        assertEquals(false, updating.playStoreOutdatedWarning)
        assertEquals(true, updating.playStoreUpdateInProgress)
        assertEquals(ModuleUpdateStatus.READY, ModuleUpdateStatus.fromCatalog(null))
        assertEquals(ModuleUpdateStatus.UPDATING, ModuleUpdateStatus.fromCatalog("updating"))
    }

    @Test
    fun listingRevisionDetectsUpdatesWhenGoogleDoesNotPublishAVersion() {
        val current = PlayStoreVersionStatus(
            latestVersion = null,
            listingUpdatedAtEpochSeconds = 1_787_824_746,
            updateAvailable = false,
            checkedAtEpochSeconds = 1_787_824_800,
            checkedDay = 1
        )
        val newer = current.copy(
            listingUpdatedAtEpochSeconds = 1_787_911_146,
            updateAvailable = true
        )
        val game = libraryGame("Revision check")

        assertEquals(true, current.isSupportedBy(game.module))
        assertEquals(false, newer.isSupportedBy(game.module))
    }

    @Test
    fun librarySelectionSupportsIndividualAndFilteredBatchToggles() {
        val first = toggleLibrarySelection(emptySet(), "com.example.first")
        val twoSelected = toggleVisibleLibrarySelection(
            first,
            setOf("com.example.first", "com.example.second")
        )

        assertEquals(setOf("com.example.first", "com.example.second"), twoSelected)
        assertEquals(
            emptySet<String>(),
            toggleVisibleLibrarySelection(
                twoSelected,
                setOf("com.example.first", "com.example.second")
            )
        )
        assertEquals(
            setOf("com.example.second"),
            toggleLibrarySelection(twoSelected, "com.example.first")
        )
    }

    @Test
    fun installedModuleUpdatePromptCollectsEveryEligibleLibraryUpdate() {
        val first = libraryGame("First", installedBuild = 1, latestBuild = 2)
        val second = libraryGame("Second", installedBuild = 4, latestBuild = 7)
        val third = libraryGame("Third", installedBuild = 2, latestBuild = 3)
        val current = libraryGame("Current", installedBuild = 5, latestBuild = 5)
        val incomplete = libraryGame(
            "Incomplete",
            installedBuild = 1,
            latestBuild = 2,
            installedComplete = false
        )
        val localTest = libraryGame("Local", installedBuild = 1, latestBuild = 2).copy(localTest = true)

        assertEquals(
            listOf(first, second, third),
            installedModuleUpdates(listOf(first, current, second, incomplete, localTest, third))
        )
    }

    private fun libraryGame(
        title: String,
        installedBuild: Long = 1,
        latestBuild: Long = installedBuild,
        installedComplete: Boolean = true,
        versionSupported: Boolean = true,
        lastLaunchedAt: Long = 0,
        running: Boolean = false
    ): LibraryGame {
        val packageName = "com.example.${title.lowercase()}"
        val module = module(packageName, title)
        val game = InstalledGame(
            packageName = packageName,
            versionName = "1.0",
            versionCode = 1,
            label = title,
            icon = null,
            module = module,
            versionSupported = versionSupported,
            abi = "arm64-v8a",
            abiSupported = true
        )
        val listing = ModuleListing(
            catalog = CatalogModule(
                config = module,
                slug = title.lowercase(),
                build = latestBuild,
                version = "1.0",
                notes = null,
                icon = null,
                installSource = GameInstallSource.PlayStore("https://example.com/$packageName")
            ),
            game = game,
            installedBuild = installedBuild,
            installedComplete = installedComplete
        )
        return LibraryGame(
            module = module,
            game = game,
            listing = listing,
            installedBuild = installedBuild,
            installedComplete = installedComplete,
            lastLaunchedAtEpochMillis = lastLaunchedAt,
            running = running
        )
    }

    private fun module(packageName: String, title: String) = ModuleConfig(
        packageName = packageName,
        title = title,
        supportedVersions = setOf("1.0"),
        supportedAbis = setOf("arm64-v8a"),
        entryPoint = "example.Main",
        dexFile = "classes.dex",
        nativeFile = "libmenu_native.so",
        iconFile = null
    )
}
