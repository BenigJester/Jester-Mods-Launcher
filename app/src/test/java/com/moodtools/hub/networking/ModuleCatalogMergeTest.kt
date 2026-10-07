package com.moodtools.hub.networking

import com.moodtools.hub.modules.CatalogModule
import com.moodtools.hub.modules.GameInstallSource
import com.moodtools.hub.modules.ModuleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ModuleCatalogMergeTest {
    @Test
    fun publicPublicationReplacesCachedPrivateEntryWhilePreservingOtherVariants() {
        val public = module("mini-militia2")
        val cachedPrivate = public.copy(privateScope = "mini-militia2", build = 999)
        val otherPrivateVariant = public.copy(slug = "mini-militia-friends", privateScope = "friends")

        val merged = mergeCatalogs(listOf(public), listOf(cachedPrivate, otherPrivateVariant))

        assertEquals(listOf(public, otherPrivateVariant), merged)
        assertSame(public, merged.first())
        assertEquals(null, merged.first().privateScope)
    }

    @Test
    fun privateOnlyCacheRemainsAvailableOffline() {
        val private = module("mini-militia2").copy(privateScope = "mini-militia2")

        assertEquals(listOf(private), mergeCatalogs(emptyList(), listOf(private)))
        assertEquals(emptyList<CatalogModule>(), mergeCatalogs(emptyList(), emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicatesWithinThePreferredCatalogAreStillRejected() {
        val public = module("mini-militia2")
        mergeCatalogs(listOf(public, public), emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicatesWithinRetainedPrivateCatalogsAreStillRejected() {
        val private = module("private-only").copy(privateScope = "friends")
        mergeCatalogs(listOf(module("mini-militia2")), listOf(private, private))
    }

    private fun module(slug: String) = CatalogModule(
        config = ModuleConfig(
            packageName = "com.appsomniacs.da2",
            title = "Mini Militia",
            supportedVersions = setOf("5.6.0"),
            supportedAbis = setOf("arm64-v8a"),
            entryPoint = null,
            dexFile = "classes.dex",
            nativeFile = "libmenu_native.so",
            iconFile = null
        ),
        slug = slug,
        build = 148,
        version = "1.4.8",
        notes = null,
        icon = null,
        installSource = GameInstallSource.PlayStore(
            "https://play.google.com/store/apps/details?id=com.appsomniacs.da2"
        )
    )
}
