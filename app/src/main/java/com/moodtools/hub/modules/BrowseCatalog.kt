package com.moodtools.hub.modules

import java.util.Locale

internal enum class BrowseFilter(val label: String) {
    ALL("All"),
    ON_DEVICE("On device"),
    COMPATIBLE("Compatible"),
    NEW("New")
}

internal enum class BrowseSort(val label: String) {
    RECOMMENDED("Recommended"),
    RECENT("Recently updated"),
    ALPHABETICAL("A–Z"),
    POPULAR("Most popular")
}

internal data class BrowseCatalogResult(
    val items: List<ModuleListing>,
    val categories: List<String>
)

internal fun browseCatalog(
    listings: List<ModuleListing>,
    query: String,
    filter: BrowseFilter,
    category: String?,
    sort: BrowseSort,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1_000L
): BrowseCatalogResult {
    val browsable = listings.filter(ModuleListing::isVisibleInBrowse)
    val categories = browsable
        .map { it.catalog.category }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
    val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotEmpty)

    val filtered = browsable.filter { listing ->
        val searchable = buildString {
            append(listing.catalog.config.title)
            append(' ')
            append(listing.catalog.config.packageName)
            append(' ')
            append(listing.catalog.slug)
            append(' ')
            append(listing.catalog.category)
            append(' ')
            append(listing.catalog.tags.joinToString(" "))
        }.lowercase(Locale.ROOT)
        terms.all(searchable::contains) &&
            (category == null || listing.catalog.category.equals(category, ignoreCase = true)) &&
            when (filter) {
                BrowseFilter.ALL -> true
                BrowseFilter.ON_DEVICE -> listing.game != null
                BrowseFilter.COMPATIBLE -> listing.status != ModuleInstallStatus.UNSUPPORTED_DEVICE &&
                    listing.status != ModuleInstallStatus.UNSUPPORTED_VERSION &&
                    listing.status != ModuleInstallStatus.UNSUPPORTED_ABI
                BrowseFilter.NEW -> listing.catalog.publishedAtEpochSeconds?.let {
                    it <= nowEpochSeconds && nowEpochSeconds - it <= NEW_WINDOW_SECONDS
                } == true
            }
    }

    return BrowseCatalogResult(
        items = filtered.sortedWith(comparatorFor(sort)),
        categories = categories
    )
}

internal fun ModuleListing.isRecommendedForDevice(): Boolean =
    game != null

internal fun ModuleListing.isVisibleInBrowse(): Boolean = when (status) {
    ModuleInstallStatus.INSTALLED,
    ModuleInstallStatus.UPDATE_AVAILABLE,
    ModuleInstallStatus.BROKEN_INSTALL -> false
    else -> true
}

private fun comparatorFor(sort: BrowseSort): Comparator<ModuleListing> {
    val title = Comparator<ModuleListing> { left, right ->
        left.catalog.config.title.compareTo(right.catalog.config.title, ignoreCase = true)
    }
    val recent = compareByDescending<ModuleListing> {
        it.catalog.updatedAtEpochSeconds ?: it.catalog.publishedAtEpochSeconds ?: 0L
    }
    val popular = compareByDescending<ModuleListing> { it.catalog.popularity }
    val featured = compareByDescending<ModuleListing> { it.catalog.featured }
    return when (sort) {
        BrowseSort.RECOMMENDED -> compareBy<ModuleListing> { recommendationPriority(it) }
            .then(featured)
            .then(popular)
            .then(recent)
            .then(title)
        BrowseSort.RECENT -> recent.then(featured).then(title)
        BrowseSort.ALPHABETICAL -> title
        BrowseSort.POPULAR -> popular.then(featured).then(recent).then(title)
    }
}

private fun recommendationPriority(listing: ModuleListing): Int = when (listing.status) {
    ModuleInstallStatus.AVAILABLE -> 0
    ModuleInstallStatus.GAME_NOT_INSTALLED -> 1
    ModuleInstallStatus.UNSUPPORTED_VERSION -> 2
    ModuleInstallStatus.UNSUPPORTED_ABI -> 3
    ModuleInstallStatus.UNSUPPORTED_DEVICE -> 4
    ModuleInstallStatus.INSTALLED,
    ModuleInstallStatus.UPDATE_AVAILABLE,
    ModuleInstallStatus.BROKEN_INSTALL -> 5
}

private const val NEW_WINDOW_SECONDS = 30L * 24L * 60L * 60L
