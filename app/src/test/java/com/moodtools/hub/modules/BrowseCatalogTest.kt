package com.moodtools.hub.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseCatalogTest {
    @Test
    fun search_matchesTitlePackageCategoryAndTags() {
        val cooking = listing("Cooking Madness", "com.example.cooking", category = "Casual", tags = setOf("time-management"))
        val avatar = listing("Avatar World", "com.example.avatar", category = "Simulation", tags = setOf("sandbox"))

        assertEquals(listOf(cooking), browseCatalog(listOf(cooking, avatar), "cooking", BrowseFilter.ALL, null, BrowseSort.ALPHABETICAL).items)
        assertEquals(listOf(avatar), browseCatalog(listOf(cooking, avatar), "simulation sandbox", BrowseFilter.ALL, null, BrowseSort.ALPHABETICAL).items)
        assertEquals(listOf(avatar), browseCatalog(listOf(cooking, avatar), "com.example.avatar", BrowseFilter.ALL, null, BrowseSort.ALPHABETICAL).items)
    }

    @Test
    fun filtersUseDetectedGameCompatibilityAndFreshnessWithoutLibraryUpdates() {
        val now = 2_000_000_000L
        val missing = listing("Missing", "com.example.missing", publishedAt = now - 10)
        val ready = listing("Ready", "com.example.ready", game = game("com.example.ready"))
        val incompatible = listing(
            "Old game",
            "com.example.old",
            game = game("com.example.old", versionSupported = false)
        )
        val update = listing(
            "Update",
            "com.example.update",
            build = 3,
            installedBuild = 2,
            installedComplete = true,
            game = game("com.example.update")
        )
        val broken = listing(
            "Broken",
            "com.example.broken",
            installedBuild = 1,
            installedComplete = false,
            game = game("com.example.broken")
        )
        val all = listOf(missing, ready, incompatible, update, broken)

        assertEquals(setOf(ready, incompatible), browseCatalog(all, "", BrowseFilter.ON_DEVICE, null, BrowseSort.ALPHABETICAL, now).items.toSet())
        assertEquals(setOf(missing, ready), browseCatalog(all, "", BrowseFilter.COMPATIBLE, null, BrowseSort.ALPHABETICAL, now).items.toSet())
        assertEquals(listOf(missing), browseCatalog(all, "", BrowseFilter.NEW, null, BrowseSort.ALPHABETICAL, now).items)
        assertTrue(update !in browseCatalog(all, "", BrowseFilter.ALL, null, BrowseSort.ALPHABETICAL, now).items)
        assertTrue(broken !in browseCatalog(all, "", BrowseFilter.ALL, null, BrowseSort.ALPHABETICAL, now).items)
    }

    @Test
    fun recommendedSortPrioritizesDetectedGamesAndExcludesLibraryUpdates() {
        val missingFeatured = listing("Missing", "com.example.missing", featured = true, popularity = 100)
        val ready = listing("Ready", "com.example.ready", game = game("com.example.ready"))
        val update = listing(
            "Update",
            "com.example.update",
            build = 3,
            installedBuild = 2,
            installedComplete = true,
            game = game("com.example.update")
        )

        val result = browseCatalog(
            listOf(missingFeatured, ready, update),
            "",
            BrowseFilter.ALL,
            null,
            BrowseSort.RECOMMENDED
        )

        assertEquals(listOf(ready, missingFeatured), result.items)
        assertTrue(update !in result.items)
        assertTrue(ready.isRecommendedForDevice())
    }

    @Test
    fun installedModulesAreExcludedAndCategoriesAreStable() {
        val installed = listing(
            "Installed",
            "com.example.installed",
            category = "Puzzle",
            installedBuild = 1,
            installedComplete = true,
            game = game("com.example.installed")
        )
        val simulation = listing("Simulation", "com.example.sim", category = "Simulation")
        val casual = listing("Casual", "com.example.casual", category = "Casual")

        val result = browseCatalog(listOf(installed, simulation, casual), "", BrowseFilter.ALL, null, BrowseSort.ALPHABETICAL)

        assertEquals(listOf(casual, simulation), result.items)
        assertEquals(listOf("Casual", "Simulation"), result.categories)
    }

    @Test
    fun incompatibleDeviceIsExcludedFromCompatibleFilter() {
        val unsupported = listing("Unsupported", "com.example.unsupported").copy(
            deviceArchitectureSupported = false
        )

        assertEquals(ModuleInstallStatus.UNSUPPORTED_DEVICE, unsupported.status)
        assertTrue(
            unsupported !in browseCatalog(
                listOf(unsupported),
                "",
                BrowseFilter.COMPATIBLE,
                null,
                BrowseSort.ALPHABETICAL
            ).items
        )
    }

    private fun listing(
        title: String,
        packageName: String,
        category: String = "Other",
        tags: Set<String> = emptySet(),
        featured: Boolean = false,
        popularity: Long = 0,
        publishedAt: Long? = null,
        build: Long = 1,
        installedBuild: Long = 0,
        installedComplete: Boolean = false,
        game: InstalledGame? = null
    ) = ModuleListing(
        catalog = CatalogModule(
            config = ModuleConfig(
                packageName = packageName,
                title = title,
                supportedVersions = setOf("1.0"),
                supportedAbis = setOf("arm64-v8a"),
                entryPoint = "example.Main",
                dexFile = "classes.dex",
                nativeFile = "libmenu_native.so",
                iconFile = null
            ),
            slug = packageName.substringAfterLast('.'),
            build = build,
            version = "1.0",
            notes = null,
            icon = null,
            installSource = GameInstallSource.PlayStore("https://play.google.com/store/apps/details?id=$packageName"),
            category = category,
            tags = tags,
            featured = featured,
            popularity = popularity,
            publishedAtEpochSeconds = publishedAt
        ),
        game = game,
        installedBuild = installedBuild,
        installedComplete = installedComplete
    )

    private fun game(packageName: String, versionSupported: Boolean = true) = InstalledGame(
        packageName = packageName,
        versionName = "1.0",
        versionCode = 1,
        label = packageName,
        icon = null,
        module = ModuleConfig(packageName, packageName, setOf("1.0"), setOf("arm64-v8a"), "example.Main", "classes.dex", "libmenu_native.so", null),
        versionSupported = versionSupported,
        abi = "arm64-v8a",
        abiSupported = true
    )
}
