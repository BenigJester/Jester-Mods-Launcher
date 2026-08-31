package com.moodtools.hub.modules

import android.content.Context
import android.graphics.drawable.Drawable

data class ModuleConfig(
    val packageName: String,
    val title: String,
    val supportedVersions: Set<String>,
    val supportedAbis: Set<String>,
    val entryPoint: String?,
    val dexFile: String,
    val nativeFile: String,
    val iconFile: String?,
    val nonRootMethod: NonRootMethod = NonRootMethod.INJECTION
)

enum class NonRootMethod(val jsonValue: String, val displayName: String) {
    INJECTION("injection", "Injection"),
    DIRECT_PATCH("direct_patch", "Patch");

    companion object {
        fun fromJson(value: String?, packageName: String? = null): NonRootMethod {
            if (value.isNullOrBlank()) {
                // Transition support for the one direct-patch module released before this field
                // became part of module metadata. Explicit JSON always wins.
                return if (packageName == LEGACY_DIRECT_PATCH_PACKAGE) DIRECT_PATCH else INJECTION
            }
            return entries.firstOrNull { it.jsonValue == value.trim().lowercase() }
                ?: throw IllegalArgumentException("Unsupported non-root method: $value")
        }

        private const val LEGACY_DIRECT_PATCH_PACKAGE = "com.ChillyRoom.DungeonShooter"
    }
}

data class LauncherMethodPresentation(
    val method: NonRootMethod,
    val badgeLabel: String,
    val setupLabel: String,
    val fieldLabel: String,
    val badgeDescription: String,
    val explanationTitle: String,
    val explanation: String
)

internal fun launcherMethodPresentation(
    configuredNonRootMethod: NonRootMethod,
    rootMode: Boolean
): LauncherMethodPresentation = if (rootMode) {
    LauncherMethodPresentation(
        method = NonRootMethod.INJECTION,
        badgeLabel = "INJECTION",
        setupLabel = "ROOT SETUP",
        fieldLabel = "Root method",
        badgeDescription = "Root method: Injection",
        explanationTitle = "How root injection works",
        explanation = "Jester Mods starts the installed game and injects the verified add-on through the root runtime. " +
            "The original game package and signing certificate stay unchanged."
    )
} else when (configuredNonRootMethod) {
    NonRootMethod.INJECTION -> LauncherMethodPresentation(
        method = NonRootMethod.INJECTION,
        badgeLabel = "INJECTION",
        setupLabel = "NON-ROOT SETUP",
        fieldLabel = "Non-root method",
        badgeDescription = "Non-root method: Injection",
        explanationTitle = "How non-root injection works",
        explanation = "Jester Mods opens the original game inside its managed runtime and loads the verified add-on " +
            "without rebuilding or replacing the installed game package."
    )
    NonRootMethod.DIRECT_PATCH -> LauncherMethodPresentation(
        method = NonRootMethod.DIRECT_PATCH,
        badgeLabel = "PATCH",
        setupLabel = "NON-ROOT SETUP",
        fieldLabel = "Non-root method",
        badgeDescription = "Non-root method: Patch",
        explanationTitle = "How patched install works",
        explanation = "Jester Mods builds a verified game package with the add-on embedded, then Android installs it " +
            "in place of the Play-signed app. The first replacement erases local game data; later patch updates preserve it."
    )
}

/** The action the primary Library button will perform for the current installation. */
enum class LibraryLaunchAction {
    PLAY,
    PATCH_AND_INSTALL,
    UPDATE_PATCHED_INSTALL
}

data class CatalogModule(
    val config: ModuleConfig,
    val slug: String,
    val build: Long,
    val version: String,
    val notes: String?,
    val icon: CatalogIcon?,
    val installSource: GameInstallSource,
    val category: String = "Other",
    val tags: Set<String> = emptySet(),
    val featured: Boolean = false,
    val popularity: Long = 0L,
    val publishedAtEpochSeconds: Long? = null,
    val updatedAtEpochSeconds: Long? = null,
    val updateStatus: ModuleUpdateStatus = ModuleUpdateStatus.READY,
    val statusChangedAtEpochSeconds: Long? = null,
    val features: CatalogModuleFeatures? = null,
    val downloadSizeByAbi: Map<String, Long> = emptyMap(),
    val privateScope: String? = null,
    val privateCatalogCapability: String? = null
) {
    /** Google Play remains an alternative even when this catalog entry supplies a game package. */
    val playStoreUrl: String
        get() = (installSource as? GameInstallSource.PlayStore)?.url
            ?: "https://play.google.com/store/apps/details?id=${config.packageName}"
}

enum class ModuleUpdateStatus(val catalogValue: String) {
    READY("ready"),
    UPDATING("updating");

    companion object {
        fun fromCatalog(value: String?): ModuleUpdateStatus = when (value?.trim()?.lowercase()) {
            null, "", READY.catalogValue -> READY
            UPDATING.catalogValue -> UPDATING
            else -> error("Unsupported add-on update status")
        }
    }
}

data class CatalogModuleFeatures(
    val path: String,
    val count: Int
)

data class ModuleFeatureGroup(
    val title: String,
    val features: List<String>
)

data class CatalogIcon(
    val path: String,
    val cachePath: String?,
    val sha256: String,
    val size: Long
)

data class PlayStoreVersionStatus(
    val latestVersion: String?,
    val listingUpdatedAtEpochSeconds: Long?,
    val updateAvailable: Boolean?,
    val checkedAtEpochSeconds: Long,
    val checkedDay: Long,
    val stale: Boolean = false
) {
    fun isSupportedBy(module: ModuleConfig): Boolean? =
        updateAvailable?.not() ?: latestVersion?.let { it in module.supportedVersions }
}

sealed interface GameInstallSource {
    data class PlayStore(val url: String) : GameInstallSource

    data class DirectDownload(
        val path: String,
        val versionCode: Long,
        val version: String,
        val size: Long,
        val sha256: String,
        val signingCertificateSha256: String,
        val format: GamePackageFormat = GamePackageFormat.APK
    ) : GameInstallSource
}

enum class GamePackageFormat(val catalogValue: String, val extension: String) {
    APK("apk", "apk"),
    APKS("apks", "apks")
}

enum class ModuleInstallStatus {
    UNSUPPORTED_DEVICE,
    GAME_NOT_INSTALLED,
    UNSUPPORTED_VERSION,
    UNSUPPORTED_ABI,
    AVAILABLE,
    INSTALLED,
    UPDATE_AVAILABLE,
    BROKEN_INSTALL
}

enum class LibraryGameStatus {
    RUNNING,
    READY,
    UPDATE_AVAILABLE,
    REPAIR_NEEDED,
    GAME_REQUIRED,
    UNSUPPORTED_VERSION,
    UNSUPPORTED_ABI
}

data class ModuleListing(
    val catalog: CatalogModule,
    val game: InstalledGame?,
    val installedBuild: Long,
    val installedComplete: Boolean,
    val deviceArchitectureSupported: Boolean = true,
    val playStoreVersionStatus: PlayStoreVersionStatus? = null,
    val privateAccessExpiresAtEpochSeconds: Long? = null
) {
    val playStoreVersionSupported: Boolean?
        get() = playStoreVersionStatus?.isSupportedBy(catalog.config)

    val playStoreUpdateInProgress: Boolean
        get() = playStoreVersionSupported == false &&
            catalog.updateStatus == ModuleUpdateStatus.UPDATING

    val playStoreOutdatedWarning: Boolean
        get() = playStoreVersionSupported == false && !playStoreUpdateInProgress

    val status: ModuleInstallStatus
        get() = when {
            !deviceArchitectureSupported -> ModuleInstallStatus.UNSUPPORTED_DEVICE
            game == null -> ModuleInstallStatus.GAME_NOT_INSTALLED
            !game.versionSupported -> ModuleInstallStatus.UNSUPPORTED_VERSION
            !game.abiSupported -> ModuleInstallStatus.UNSUPPORTED_ABI
            !installedComplete && installedBuild > 0 -> ModuleInstallStatus.BROKEN_INSTALL
            !installedComplete -> ModuleInstallStatus.AVAILABLE
            installedBuild < catalog.build -> ModuleInstallStatus.UPDATE_AVAILABLE
            else -> ModuleInstallStatus.INSTALLED
        }
}

data class InstalledGame(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val label: String,
    val icon: Drawable?,
    val module: ModuleConfig,
    val versionSupported: Boolean,
    val abi: String,
    val abiSupported: Boolean
) {
    val moduleSupported: Boolean
        get() = versionSupported && abiSupported
}

/** A persistent Library member backed by downloaded Jester support. */
data class LibraryGame(
    val module: ModuleConfig,
    val game: InstalledGame?,
    val listing: ModuleListing?,
    val installedBuild: Long,
    val installedComplete: Boolean,
    val localTest: Boolean = false,
    val lastLaunchedAtEpochMillis: Long = 0L,
    val running: Boolean = false,
    val launchAction: LibraryLaunchAction = LibraryLaunchAction.PLAY,
    val playStoreVersionStatus: PlayStoreVersionStatus? = listing?.playStoreVersionStatus,
    val privateScope: String? = listing?.catalog?.privateScope,
    val privateAccessExpiresAtEpochSeconds: Long? = listing?.privateAccessExpiresAtEpochSeconds
) {
    val packageName: String
        get() = module.packageName

    val title: String
        get() = module.title

    val playStoreUpdateInProgress: Boolean
        get() = playStoreVersionStatus?.isSupportedBy(module) == false &&
            listing?.catalog?.updateStatus == ModuleUpdateStatus.UPDATING

    val playStoreOutdatedWarning: Boolean
        get() = playStoreVersionStatus?.isSupportedBy(module) == false &&
            !playStoreUpdateInProgress

    val status: LibraryGameStatus
        get() = when {
            running && game?.moduleSupported == true && installedComplete -> LibraryGameStatus.RUNNING
            game == null -> LibraryGameStatus.GAME_REQUIRED
            !game.versionSupported -> LibraryGameStatus.UNSUPPORTED_VERSION
            !game.abiSupported -> LibraryGameStatus.UNSUPPORTED_ABI
            !installedComplete -> LibraryGameStatus.REPAIR_NEEDED
            localTest -> LibraryGameStatus.READY
            listing != null && installedBuild < listing.catalog.build -> LibraryGameStatus.UPDATE_AVAILABLE
            else -> LibraryGameStatus.READY
    }
}

internal fun installedModuleUpdates(games: List<LibraryGame>): List<LibraryGame> = games.filter { game ->
    val availableBuild = game.listing?.catalog?.build ?: return@filter false
    !game.localTest &&
        game.installedComplete &&
        game.installedBuild > 0L &&
        availableBuild > game.installedBuild
}

internal fun sortLibraryGames(games: List<LibraryGame>): List<LibraryGame> = games.sortedWith(
    compareBy<LibraryGame, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
)

/**
 * Uses a locally staged config for a catalog package while retaining catalog-only packages and
 * appending local-only test modules. The stable order of each input is preserved; presentation
 * layers apply their own explicit A-Z order.
 */
internal fun mergeCatalogAndLocalModuleConfigs(
    catalogConfigs: List<ModuleConfig>,
    localConfigs: List<ModuleConfig>
): List<ModuleConfig> {
    val localByPackage = localConfigs.associateBy(ModuleConfig::packageName)
    val catalogPackages = catalogConfigs.mapTo(hashSetOf(), ModuleConfig::packageName)
    return catalogConfigs.map { localByPackage[it.packageName] ?: it } +
        localConfigs.filter { it.packageName !in catalogPackages }
}

internal fun toggleLibrarySelection(selected: Set<String>, packageName: String): Set<String> =
    if (packageName in selected) selected - packageName else selected + packageName

internal fun toggleVisibleLibrarySelection(
    selected: Set<String>,
    visiblePackages: Set<String>
): Set<String> = if (visiblePackages.isNotEmpty() && visiblePackages.all(selected::contains)) {
    selected - visiblePackages
} else {
    selected + visiblePackages
}

interface MenuPlugin {
    fun onLaunch(context: PluginContext)
}

data class PluginContext(
    val hostContext: Context,
    val packageName: String,
    val moduleDirectory: String,
    val executionMode: String
)
