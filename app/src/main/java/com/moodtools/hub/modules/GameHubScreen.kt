package com.moodtools.hub.modules

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodtools.hub.BuildConfig
import com.moodtools.hub.PackageReplacementKind
import com.moodtools.hub.formatRemainingAccessPrimary
import com.moodtools.hub.networking.CatalogIconClient
import com.moodtools.hub.networking.LauncherChangelogEntry
import com.moodtools.hub.networking.ModuleChangelog
import com.moodtools.hub.networking.ModuleChangelogEntry
import com.moodtools.hub.networking.ModuleFeaturesClient
import java.text.SimpleDateFormat
import java.text.DateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF090B10)
private val SurfaceDark = Color(0xFF12161D)
private val SurfaceRaised = Color(0xFF191F28)
private val Accent = Color(0xFF80E4C6)
private val AccentBlue = Color(0xFF8CB9FF)
private val Muted = Color(0xFFAAB3BF)
private val Hairline = Color(0xFF2A313C)
private val Danger = Color(0xFFFFB4AB)
private val PrivateGold = Color(0xFFFFD99A)
private val PrivateViolet = Color(0xFFC7B4FF)
private val LimitedAmber = Color(0xFFFFCB78)
private val LimitedSky = Color(0xFF9DCBFF)

/** Launcher-owned update state. The module itself no longer needs to own an updater screen. */
data class ModuleUpdateUiState(
    val visible: Boolean = false,
    val inProgress: Boolean = false,
    val cancelling: Boolean = false,
    val cancelled: Boolean = false,
    val stage: SecureTransferStage = SecureTransferStage.READY,
    val stageProgress: Float? = null,
    val title: String = "",
    val packageName: String = "",
    val targetVersion: String = "",
    val actionLabel: String = "Add-on",
    val intent: ModuleTransferIntent = ModuleTransferIntent.TRANSFER,
    val headline: String? = null,
    val detail: String? = null,
    val updateAvailable: Boolean = false,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val verificationUrl: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val changelog: List<ModuleChangelogEntry> = emptyList(),
    val diagnostics: List<String> = emptyList()
)

enum class ModuleTransferIntent {
    TRANSFER,
    UPDATE_CHECK
}

internal fun moduleTransferPrimaryActionLabel(state: ModuleUpdateUiState): String = when {
    state.updateAvailable -> "Update"
    state.intent == ModuleTransferIntent.UPDATE_CHECK -> "Check again"
    else -> "Try again"
}

enum class SecureTransferStage {
    READY,
    PREPARING,
    DOWNLOADING,
    VERIFYING,
    ACTIVATING,
    WAITING_FOR_ANDROID,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class GameDataResetUiState(
    val inProgress: Boolean = false,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val headline: String? = null,
    val detail: String? = null
)

data class GameInstallUiState(
    val visible: Boolean = false,
    val inProgress: Boolean = false,
    val installing: Boolean = false,
    val cancelling: Boolean = false,
    val downloaded: Boolean = false,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val cancelled: Boolean = false,
    val stage: GameInstallStage = GameInstallStage.IDLE,
    val title: String = "",
    val packageName: String = "",
    val targetVersion: String = "",
    val packageFormat: String = "",
    val headline: String? = null,
    val detail: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val stageProgress: Float? = null,
    val diagnostics: List<String> = emptyList()
)

enum class GameInstallStage {
    IDLE,
    DOWNLOADING,
    VERIFYING,
    PREPARING_INSTALLER,
    WAITING_FOR_ANDROID,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class LauncherUpdateUiState(
    val available: Boolean = false,
    val screenOpen: Boolean = false,
    val inProgress: Boolean = false,
    val installing: Boolean = false,
    val cancelling: Boolean = false,
    val cancelled: Boolean = false,
    val stage: SecureTransferStage = SecureTransferStage.READY,
    val stageProgress: Float? = null,
    val downloaded: Boolean = false,
    val failed: Boolean = false,
    val build: Long = 0L,
    val version: String? = null,
    val notes: String? = null,
    val headline: String? = null,
    val detail: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val changelog: List<LauncherChangelogEntry> = emptyList(),
    val diagnostics: List<String> = emptyList()
)

data class InstalledModuleUpdatesUiState(
    val open: Boolean = false,
    val packageNames: List<String> = emptyList(),
    val previewUpdates: List<LibraryGame> = emptyList(),
    val inProgress: Boolean = false,
    val cancelling: Boolean = false,
    val cancelled: Boolean = false,
    val updatingAll: Boolean = false,
    val itemStates: Map<String, InstalledModuleUpdateItemUiState> = emptyMap()
)

enum class InstalledModuleUpdateItemStatus {
    AVAILABLE,
    QUEUED,
    DOWNLOADING,
    INSTALLED,
    FAILED
}

data class InstalledModuleUpdateItemUiState(
    val status: InstalledModuleUpdateItemStatus = InstalledModuleUpdateItemStatus.AVAILABLE,
    val stage: SecureTransferStage = SecureTransferStage.READY,
    val stageProgress: Float? = null,
    val detail: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val diagnostics: List<String> = emptyList()
)

data class ChangelogUiState(
    val open: Boolean = false,
    val loading: Boolean = false,
    val launcherEntries: List<LauncherChangelogEntry> = emptyList(),
    val moduleHistories: List<ModuleChangelog> = emptyList(),
    val selectedModuleHistory: ModuleChangelog? = null,
    val selectedModulePackage: String? = null,
    val moduleHistoryLoadingPackage: String? = null,
    val moduleHistoryError: String? = null,
    val error: String? = null
)

data class AccountIdentityUiState(
    val open: Boolean = false,
    val grantPassIdentity: String = "",
    val deviceId: String = "",
    val recoveryId: String = "",
    val installationId: String = "",
    val proofKeyId: String = "",
    val flavor: String = "",
    val accessVersion: Int = 0,
    val error: String? = null
)

/** Visible launch progress so root staging/injection never looks like an app freeze. */
data class LaunchUiState(
    val inProgress: Boolean = false,
    val headline: String? = null,
    val detail: String? = null,
    val gameLaunched: Boolean = false,
    val failed: Boolean = false
)

/** Visible progress for direct-patch and exact-package shell preparation/install. */
data class PackageSetupUiState(
    val visible: Boolean = false,
    val inProgress: Boolean = false,
    val cancelling: Boolean = false,
    val cancelled: Boolean = false,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val stage: SecureTransferStage = SecureTransferStage.READY,
    val stageProgress: Float? = null,
    val title: String = "",
    val packageName: String = "",
    val kind: PackageReplacementKind = PackageReplacementKind.DIRECT_PATCH,
    val headline: String? = null,
    val detail: String? = null,
    val diagnostics: List<String> = emptyList()
)

internal fun libraryPrimaryActionLabel(
    status: LibraryGameStatus,
    launchAction: LibraryLaunchAction,
    launch: LaunchUiState
): String = when {
    launch.inProgress && launchAction == LibraryLaunchAction.SHELL_AND_INSTALL -> "Preparing shell…"
    launch.inProgress && launchAction == LibraryLaunchAction.RESTORE_OFFICIAL_FOR_SHELL -> "Checking migration…"
    launch.inProgress && launchAction != LibraryLaunchAction.PLAY -> "Preparing patch…"
    status == LibraryGameStatus.RUNNING -> "Resume"
    status !in setOf(
        LibraryGameStatus.READY,
        LibraryGameStatus.UPDATE_AVAILABLE
    ) -> "View requirements"
    launch.inProgress -> "Launching…"
    launchAction == LibraryLaunchAction.PATCH_AND_INSTALL -> "Patch & install"
    launchAction == LibraryLaunchAction.UPDATE_PATCHED_INSTALL -> "Update patched game"
    launchAction == LibraryLaunchAction.RESTORE_OFFICIAL_FOR_SHELL -> "Restore official game"
    launchAction == LibraryLaunchAction.SHELL_AND_INSTALL -> "Create & install shell"
    launch.gameLaunched -> "Play again"
    else -> "Play"
}

data class DirectPatchPromptUiState(
    val visible: Boolean = false,
    val title: String = "",
    val replacesOriginal: Boolean = false,
    val kind: PackageReplacementKind = PackageReplacementKind.DIRECT_PATCH,
    val restoresOfficialGame: Boolean = false
)

private const val SCREEN_TRANSITION_MS = 220
private const val SCREEN_TRANSITION_DEFER_MS = 280L
private const val ADD_ON_DETAILS_MIN_VISIBLE_MS = 1_500L
private const val LOADING_STATE_MIN_VISIBLE_MS = 360L
private const val ICON_BITMAP_DEFER_MS = 40L
private const val BROWSE_RESULT_CACHE_LIMIT = 36
private const val BROWSE_LOAD_MORE_KEY_PREFIX = "catalog-load-more:"

private sealed class LauncherPage(val key: String, val rank: Int) {
    object Library : LauncherPage("library", 0)
    data class Module(val game: LibraryGame) : LauncherPage("module:${game.moduleIdentity}", 1)
    object Browse : LauncherPage("browse", 2)
    data class Download(val listing: ModuleListing) : LauncherPage("download:${listing.catalog.slug}", 3)
    object LauncherUpdate : LauncherPage("launcher-update", 4)
    object Changelog : LauncherPage("changelog", 5)
    object AccountIdentity : LauncherPage("account-identity", 6)
}

internal enum class LauncherOverlay {
    LAUNCHER_UPDATE,
    DIRECT_PATCH,
    INSTALLED_ADD_ON_UPDATES,
    ADD_ON_TRANSFER,
    GAME_COMPANION_INSTALL,
    PACKAGE_SETUP
}

/**
 * Keeps independent background operations from stacking competing windows. Update experiences
 * retain their own surfaces; the companion installer waits behind them and resumes visibly once
 * the higher-priority window has closed.
 */
internal fun selectLauncherOverlay(
    launcherUpdateOpen: Boolean,
    directPatchOpen: Boolean,
    installedAddOnUpdatesOpen: Boolean,
    addOnTransferOpen: Boolean,
    gameCompanionInstallOpen: Boolean,
    packageSetupOpen: Boolean
): LauncherOverlay? = when {
    launcherUpdateOpen -> LauncherOverlay.LAUNCHER_UPDATE
    installedAddOnUpdatesOpen -> LauncherOverlay.INSTALLED_ADD_ON_UPDATES
    addOnTransferOpen -> LauncherOverlay.ADD_ON_TRANSFER
    gameCompanionInstallOpen -> LauncherOverlay.GAME_COMPANION_INSTALL
    directPatchOpen -> LauncherOverlay.DIRECT_PATCH
    packageSetupOpen -> LauncherOverlay.PACKAGE_SETUP
    else -> null
}

private class LauncherScreenCache {
    val warmedPages = mutableStateMapOf<String, Boolean>()
    val featureDetails = mutableStateMapOf<String, FeatureDetailsCacheEntry>()
    val iconBitmaps = mutableStateMapOf<String, BitmapCacheEntry>()
    val browseResults = BrowseResultCache()
}

private data class FeatureDetailsCacheEntry(
    val groups: List<ModuleFeatureGroup>? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val retryNonce: Int = 0
)

private data class BitmapCacheEntry(val bitmap: ImageBitmap?)

private data class BrowseCacheKey(
    val catalogRevision: Int,
    val query: String,
    val filter: BrowseFilter,
    val category: String?,
    val sort: BrowseSort
)

private class BrowseResultCache {
    private val results = object : LinkedHashMap<BrowseCacheKey, BrowseCatalogResult>(
        BROWSE_RESULT_CACHE_LIMIT,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BrowseCacheKey, BrowseCatalogResult>?): Boolean =
            size > BROWSE_RESULT_CACHE_LIMIT
    }

    @Synchronized
    fun getOrPut(key: BrowseCacheKey, producer: () -> BrowseCatalogResult): BrowseCatalogResult =
        results[key] ?: producer().also { results[key] = it }
}

private fun browseCatalogRevision(listings: List<ModuleListing>): Int {
    var revision = listings.size
    listings.forEach { listing ->
        revision = 31 * revision + listing.catalog.slug.hashCode()
        revision = 31 * revision + listing.catalog.build.hashCode()
        revision = 31 * revision + listing.catalog.config.packageName.hashCode()
        revision = 31 * revision + listing.catalog.config.title.hashCode()
        revision = 31 * revision + listing.catalog.category.hashCode()
        revision = 31 * revision + listing.catalog.tags.hashCode()
        revision = 31 * revision + listing.catalog.updatedAtEpochSeconds.hashCode()
        revision = 31 * revision + listing.catalog.publishedAtEpochSeconds.hashCode()
        revision = 31 * revision + listing.catalog.popularity.hashCode()
        revision = 31 * revision + listing.catalog.featured.hashCode()
        revision = 31 * revision + listing.status.hashCode()
        revision = 31 * revision + listing.installedBuild.hashCode()
        revision = 31 * revision + listing.installedComplete.hashCode()
        revision = 31 * revision + listing.deviceArchitectureSupported.hashCode()
        revision = 31 * revision + listing.playStoreVersionStatus?.latestVersion.hashCode()
        revision = 31 * revision + listing.playStoreVersionStatus?.listingUpdatedAtEpochSeconds.hashCode()
        revision = 31 * revision + listing.playStoreVersionStatus?.updateAvailable.hashCode()
        revision = 31 * revision + listing.playStoreVersionStatus?.stale.hashCode()
        revision = 31 * revision + listing.catalog.updateStatus.hashCode()
        revision = 31 * revision + listing.catalog.statusChangedAtEpochSeconds.hashCode()
        listing.game?.let { game ->
            revision = 31 * revision + game.versionCode.hashCode()
            revision = 31 * revision + game.versionName.hashCode()
            revision = 31 * revision + game.abi.hashCode()
            revision = 31 * revision + game.moduleSupported.hashCode()
        }
    }
    return revision
}

@Composable
fun GameHubScreen(
    state: StateFlow<List<LibraryGame>>,
    libraryLoading: StateFlow<Boolean>,
    catalogRefreshing: StateFlow<Boolean>,
    availableModules: StateFlow<List<ModuleListing>>,
    browserOpen: StateFlow<Boolean>,
    downloadListing: StateFlow<ModuleListing?>,
    selectedGame: StateFlow<LibraryGame?>,
    updateState: StateFlow<ModuleUpdateUiState>,
    gameDataResetState: StateFlow<GameDataResetUiState>,
    gameInstallState: StateFlow<GameInstallUiState>,
    launcherUpdateState: StateFlow<LauncherUpdateUiState>,
    installedModuleUpdatesState: StateFlow<InstalledModuleUpdatesUiState>,
    changelogState: StateFlow<ChangelogUiState>,
    accountIdentityState: StateFlow<AccountIdentityUiState>,
    launchState: StateFlow<LaunchUiState>,
    packageSetupState: StateFlow<PackageSetupUiState>,
    directPatchPromptState: StateFlow<DirectPatchPromptUiState>,
    onOpenGame: (LibraryGame) -> Unit,
    onBack: () -> Unit,
    onUpdate: (LibraryGame) -> Unit,
    onVerify: (String) -> Unit,
    onLaunch: (LibraryGame) -> Unit,
    onSelectNonRootMethod: (LibraryGame, NonRootMethod) -> Unit,
    onConfirmDirectPatch: () -> Unit,
    onDismissDirectPatch: () -> Unit,
    onCancelPackageSetup: () -> Unit,
    onDismissPackageSetup: () -> Unit,
    onRetryPackageSetup: () -> Unit,
    onRemoveFromLibrary: (LibraryGame) -> Unit,
    onClearGameData: (LibraryGame) -> Unit,
    onRemoveMultipleFromLibrary: (List<LibraryGame>) -> Unit,
    onRefreshCatalog: () -> Unit,
    onBrowse: () -> Unit,
    onCloseBrowser: () -> Unit,
    onOpenDownload: (ModuleListing) -> Unit,
    onCloseDownload: () -> Unit,
    onInstall: (ModuleListing) -> Unit,
    onAcquireGame: (ModuleListing) -> Unit,
    onCancelGameInstall: () -> Unit,
    onDismissGameInstall: () -> Unit,
    onOpenGameStore: (ModuleListing) -> Unit,
    onOpenLauncherUpdate: () -> Unit,
    onCloseLauncherUpdate: () -> Unit,
    onInstallLauncherUpdate: () -> Unit,
    onCancelLauncherUpdate: () -> Unit,
    onCancelModuleTransfer: () -> Unit,
    onDismissModuleTransfer: () -> Unit,
    onDismissInstalledModuleUpdates: () -> Unit,
    onCancelInstalledModuleUpdates: () -> Unit,
    onReviewInstalledModuleUpdate: (LibraryGame) -> Unit,
    onUpdateInstalledModule: (LibraryGame) -> Unit,
    onUpdateAllInstalledModules: () -> Unit,
    onOpenChangelog: () -> Unit,
    onCloseChangelog: () -> Unit,
    onOpenAccountIdentity: () -> Unit,
    onCloseAccountIdentity: () -> Unit,
    onRetryChangelog: () -> Unit,
    onOpenModuleChangelog: (String) -> Unit,
    onCloseModuleChangelog: () -> Unit
) {
    val screenCache = remember { LauncherScreenCache() }
    val games by state.collectAsStateWithLifecycle()
    val loadingLibrary by libraryLoading.collectAsStateWithLifecycle()
    val refreshingCatalog by catalogRefreshing.collectAsStateWithLifecycle()
    val listings by availableModules.collectAsStateWithLifecycle()
    val browsing by browserOpen.collectAsStateWithLifecycle()
    val pendingDownload by downloadListing.collectAsStateWithLifecycle()
    val selected by selectedGame.collectAsStateWithLifecycle()
    val update by updateState.collectAsStateWithLifecycle()
    val gameDataReset by gameDataResetState.collectAsStateWithLifecycle()
    val gameInstall by gameInstallState.collectAsStateWithLifecycle()
    val launcherUpdate by launcherUpdateState.collectAsStateWithLifecycle()
    val installedModuleUpdatePrompt by installedModuleUpdatesState.collectAsStateWithLifecycle()
    val changelog by changelogState.collectAsStateWithLifecycle()
    val accountIdentity by accountIdentityState.collectAsStateWithLifecycle()
    val launch by launchState.collectAsStateWithLifecycle()
    val packageSetup by packageSetupState.collectAsStateWithLifecycle()
    val directPatchPrompt by directPatchPromptState.collectAsStateWithLifecycle()
    var browseQuery by rememberSaveable { mutableStateOf("") }
    var browseFilter by rememberSaveable { mutableStateOf(BrowseFilter.ALL.name) }
    var browseCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var browseSort by rememberSaveable { mutableStateOf(BrowseSort.RECOMMENDED.name) }
    var browseVisibleCount by rememberSaveable { mutableStateOf(BROWSE_PAGE_SIZE) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var libraryManaging by rememberSaveable { mutableStateOf(false) }
    val page = when {
        changelog.open -> LauncherPage.Changelog
        accountIdentity.open -> LauncherPage.AccountIdentity
        pendingDownload != null -> LauncherPage.Download(pendingDownload!!)
        browsing -> LauncherPage.Browse
        selected != null -> LauncherPage.Module(selected!!)
        else -> LauncherPage.Library
    }

    BackHandler(
        enabled = launcherUpdate.screenOpen || changelog.open || accountIdentity.open || selected != null ||
            browsing || pendingDownload != null || libraryManaging
    ) {
        when {
            launcherUpdate.screenOpen -> onCloseLauncherUpdate()
            changelog.open && changelog.selectedModulePackage != null ->
                onCloseModuleChangelog()
            changelog.open -> onCloseChangelog()
            accountIdentity.open -> onCloseAccountIdentity()
            pendingDownload != null -> onCloseDownload()
            browsing -> onCloseBrowser()
            libraryManaging -> libraryManaging = false
            else -> onBack()
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF111B1B), Ink, Color(0xFF0B0E14))
                        )
                    )
            ) {
                AnimatedContent(
                    targetState = page,
                    contentKey = LauncherPage::key,
                    transitionSpec = { launcherPageTransitionSpec(initialState, targetState) },
                    label = "launcher-page",
                    modifier = Modifier.fillMaxSize()
                ) { visiblePage ->
                    when (visiblePage) {
                        LauncherPage.Library -> {
                            LibraryScreen(
                                screenCache = screenCache,
                                games = games,
                                loading = loadingLibrary,
                                refreshing = refreshingCatalog,
                                query = libraryQuery,
                                managing = libraryManaging,
                                onQueryChange = { libraryQuery = it },
                                onManagingChange = { libraryManaging = it },
                                onOpenGame = onOpenGame,
                                onRemoveMultipleFromLibrary = onRemoveMultipleFromLibrary,
                                onRefresh = onRefreshCatalog,
                                onBrowse = onBrowse
                            )
                        }
                        is LauncherPage.Module -> {
                            DeferredScreenContent(
                                screenCache = screenCache,
                                contentKey = visiblePage.key,
                                minimumPlaceholderMillis = ADD_ON_DETAILS_MIN_VISIBLE_MS,
                                placeholder = { ModuleScreenPlaceholder(visiblePage.game) }
                            ) {
                                ModuleScreen(
                                    screenCache = screenCache,
                                    game = visiblePage.game,
                                    update = update,
                                    gameDataReset = gameDataReset,
                                    launch = launch,
                                    refreshing = refreshingCatalog,
                                    onBack = onBack,
                                    onUpdate = { onUpdate(visiblePage.game) },
                                    onVerify = onVerify,
                                    onLaunch = { onLaunch(visiblePage.game) },
                                    onSelectNonRootMethod = { method ->
                                        onSelectNonRootMethod(visiblePage.game, method)
                                    },
                                    onRemoveFromLibrary = { onRemoveFromLibrary(visiblePage.game) },
                                    onClearGameData = { onClearGameData(visiblePage.game) },
                                    onRefresh = onRefreshCatalog,
                                    onResolve = visiblePage.game.listing?.let { listing -> { onOpenDownload(listing) } }
                                )
                            }
                        }
                        LauncherPage.Browse -> {
                            DeferredScreenContent(
                                screenCache = screenCache,
                                contentKey = visiblePage.key,
                                contentReady = listings.isNotEmpty(),
                                placeholder = {
                                    ScreenTransitionPlaceholder(
                                        backLabel = "Library",
                                        title = "Browse add-ons",
                                        detail = "Preparing the add-on catalog…"
                                    )
                                }
                            ) {
                                ModuleBrowserScreen(
                                    screenCache = screenCache,
                                    listings = listings,
                                    query = browseQuery,
                                    filter = BrowseFilter.entries.firstOrNull { it.name == browseFilter } ?: BrowseFilter.ALL,
                                    category = browseCategory,
                                    sort = BrowseSort.entries.firstOrNull { it.name == browseSort } ?: BrowseSort.RECOMMENDED,
                                    visibleCount = browseVisibleCount,
                                    refreshing = refreshingCatalog,
                                    onQueryChange = {
                                        browseQuery = it
                                        browseVisibleCount = BROWSE_PAGE_SIZE
                                    },
                                    onFilterChange = {
                                        browseFilter = it.name
                                        browseVisibleCount = BROWSE_PAGE_SIZE
                                    },
                                    onCategoryChange = {
                                        browseCategory = it
                                        browseVisibleCount = BROWSE_PAGE_SIZE
                                    },
                                    onSortChange = {
                                        browseSort = it.name
                                        browseVisibleCount = BROWSE_PAGE_SIZE
                                    },
                                    onLoadMore = {
                                        browseVisibleCount = minOf(
                                            browseVisibleCount + BROWSE_PAGE_SIZE,
                                            listings.size
                                        )
                                    },
                                    onRefresh = onRefreshCatalog,
                                    onBack = onCloseBrowser,
                                    onOpenDownload = onOpenDownload
                                )
                            }
                        }
                        is LauncherPage.Download -> {
                            DeferredScreenContent(
                                screenCache = screenCache,
                                contentKey = visiblePage.key,
                                minimumPlaceholderMillis = ADD_ON_DETAILS_MIN_VISIBLE_MS,
                                placeholder = {
                                    ScreenTransitionPlaceholder(
                                        backLabel = "Browse add-ons",
                                        title = visiblePage.listing.catalog.config.title,
                                        detail = "Preparing install details…"
                                    )
                                }
                            ) {
                                ModuleDownloadScreen(
                                    screenCache = screenCache,
                                    listing = visiblePage.listing,
                                    update = update,
                                    gameInstall = gameInstall,
                                    refreshing = refreshingCatalog,
                                    onBack = onCloseDownload,
                                    onInstall = { onInstall(visiblePage.listing) },
                                    onAcquireGame = { onAcquireGame(visiblePage.listing) },
                                    onOpenGameStore = { onOpenGameStore(visiblePage.listing) },
                                    onRefresh = onRefreshCatalog,
                                    onDone = onCloseBrowser
                                )
                            }
                        }
                        LauncherPage.LauncherUpdate -> {
                            DeferredScreenContent(
                                screenCache = screenCache,
                                contentKey = visiblePage.key,
                                placeholder = {
                                    ScreenTransitionPlaceholder(
                                        backLabel = "Library",
                                        title = "Launcher update",
                                        detail = "Preparing update details…"
                                    )
                                }
                            ) {
                                LauncherUpdateScreen(
                                    update = launcherUpdate,
                                    onBack = onCloseLauncherUpdate,
                                    onInstall = onInstallLauncherUpdate
                                )
                            }
                        }
                        LauncherPage.Changelog -> {
                            DeferredScreenContent(
                                screenCache = screenCache,
                                contentKey = visiblePage.key,
                                contentReady = changelog.launcherEntries.isNotEmpty() ||
                                    changelog.moduleHistories.isNotEmpty(),
                                placeholder = {
                                    ScreenTransitionPlaceholder(
                                        backLabel = "Library",
                                        title = "Changelog",
                                        detail = "Preparing release history…"
                                    )
                                }
                            ) {
                                ChangelogScreen(
                                    state = changelog,
                                    launcherUpdate = launcherUpdate,
                                    onBack = onCloseChangelog,
                                    onRetry = onRetryChangelog,
                                    onOpenLauncherUpdate = onOpenLauncherUpdate,
                                    onOpenModuleChangelog = onOpenModuleChangelog,
                                    onCloseModuleChangelog = onCloseModuleChangelog
                                )
                            }
                        }
                        LauncherPage.AccountIdentity -> {
                            DeferredScreenContent(
                                screenCache = screenCache,
                                contentKey = visiblePage.key,
                                placeholder = {
                                    ScreenTransitionPlaceholder(
                                        backLabel = "Library",
                                        title = "Account identity",
                                        detail = "Preparing device access details…"
                                    )
                                }
                            ) {
                                AccountIdentityScreen(
                                    state = accountIdentity,
                                    onBack = onCloseAccountIdentity
                                )
                            }
                        }
                    }
                }
                if (!launcherUpdate.screenOpen && !changelog.open && !accountIdentity.open) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(top = 12.dp, end = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccountIdentityIconButton(onClick = onOpenAccountIdentity)
                        ChangelogIconButton(
                            updateAvailable = launcherUpdate.available,
                            onClick = onOpenChangelog
                        )
                    }
                }
            }
        }
        val installedUpdates = installedModuleUpdatePrompt.previewUpdates.ifEmpty {
            installedModuleUpdatePrompt.packageNames.mapNotNull { packageName ->
                games.firstOrNull { it.packageName == packageName }
            }
        }
        val activeOverlay = selectLauncherOverlay(
            launcherUpdateOpen = launcherUpdate.screenOpen,
            directPatchOpen = directPatchPrompt.visible,
            installedAddOnUpdatesOpen = installedModuleUpdatePrompt.open && installedUpdates.isNotEmpty(),
            addOnTransferOpen = update.visible,
            gameCompanionInstallOpen = gameInstall.visible,
            packageSetupOpen = packageSetup.visible
        )
        when (activeOverlay) {
            LauncherOverlay.LAUNCHER_UPDATE -> LauncherUpdateDialog(
                update = launcherUpdate,
                onDismiss = onCloseLauncherUpdate,
                onInstall = onInstallLauncherUpdate,
                onCancel = onCancelLauncherUpdate
            )
            LauncherOverlay.DIRECT_PATCH -> DirectPatchInstallDialog(
                state = directPatchPrompt,
                onConfirm = onConfirmDirectPatch,
                onDismiss = onDismissDirectPatch
            )
            LauncherOverlay.INSTALLED_ADD_ON_UPDATES -> InstalledModuleUpdatesDialog(
                screenCache = screenCache,
                updates = installedUpdates,
                state = installedModuleUpdatePrompt,
                onDismiss = onDismissInstalledModuleUpdates,
                onCancel = onCancelInstalledModuleUpdates,
                onReview = onReviewInstalledModuleUpdate,
                onUpdate = onUpdateInstalledModule,
                onUpdateAll = onUpdateAllInstalledModules
            )
            LauncherOverlay.ADD_ON_TRANSFER -> ModuleTransferDialog(
                state = update,
                canRetry = pendingDownload?.game != null || selected?.listing?.game != null,
                onRetry = {
                    if (update.intent == ModuleTransferIntent.UPDATE_CHECK) {
                        selected?.let(onUpdate) ?: pendingDownload?.let(onInstall)
                    } else {
                        (pendingDownload ?: selected?.listing)?.let(onInstall)
                    }
                },
                onCancel = onCancelModuleTransfer,
                onDismiss = onDismissModuleTransfer
            )
            LauncherOverlay.GAME_COMPANION_INSTALL -> GameInstallDialog(
                state = gameInstall,
                canRetry = pendingDownload != null,
                onRetry = { pendingDownload?.let(onAcquireGame) },
                onCancel = onCancelGameInstall,
                onDismiss = onDismissGameInstall
            )
            LauncherOverlay.PACKAGE_SETUP -> PackageSetupDialog(
                state = packageSetup,
                canRetry = selected?.game != null,
                alternativeMethod = selected
                    ?.takeIf {
                        it.module.offersNonRootMethodChoice && it.installedNonRootMethod == null
                    }
                    ?.module
                    ?.nonRootMethods
                    ?.firstOrNull { it != selected?.module?.effectiveNonRootMethod },
                onTryAlternative = { method ->
                    selected?.let { onSelectNonRootMethod(it, method) }
                },
                onRetry = onRetryPackageSetup,
                onCancel = onCancelPackageSetup,
                onDismiss = onDismissPackageSetup
            )
            null -> Unit
        }
    }
}

@Composable
private fun DirectPatchInstallDialog(
    state: DirectPatchPromptUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = state.title.ifBlank { "game" }
    val identityShell = state.kind == PackageReplacementKind.IDENTITY_SHELL
    if (state.restoresOfficialGame) {
        LegacyPatchMigrationDialog(title, onConfirm, onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = SurfaceRaised,
        titleContentColor = Color.White,
        textContentColor = Muted,
        title = {
            Column {
                Text(
                    when {
                        identityShell -> "Install $title compatibility shell"
                        state.replacesOriginal -> "Install patched $title"
                        else -> "Update patched $title"
                    },
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    when {
                        identityShell -> "The original game has been preserved unchanged"
                        state.replacesOriginal -> "Two Android confirmations are required"
                        else -> "Your patched game is ready to update"
                    },
                    color = AccentBlue,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.replacesOriginal) {
                    DirectPatchDialogStep(
                        number = "1",
                        headline = "Remove the current installation",
                        detail = if (identityShell) {
                            "Android will uninstall the Play-signed game after Jester Mods preserves its untouched APK set. Local game data is still erased, so back up anything you need."
                        } else {
                            "Android will uninstall the Play-signed game. This permanently erases its local app data, so back up anything you need before continuing."
                        },
                        warning = true
                    )
                    DirectPatchDialogStep(
                        number = "2",
                        headline = if (identityShell) "Install the game-branded shell" else "Install the verified patched game",
                        detail = if (identityShell) {
                            "The home-screen name and icon come from the original game. Opening it starts the preserved game payload directly."
                        } else {
                            "Jester Mods will open Android's installer automatically after the uninstall finishes."
                        }
                    )
                } else {
                    DirectPatchDialogStep(
                        number = "1",
                        headline = "Install in place",
                        detail = "Android will update the existing Jester-patched game while preserving its local app data."
                    )
                }
                Text(
                    if (identityShell) {
                        "The shell, original game backup, branding, and add-on have already passed verification."
                    } else {
                        "The prepared APK set and embedded add-on have already passed verification."
                    },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Ink)
            ) {
                Text(
                    if (state.replacesOriginal) "Continue to uninstall" else "Continue to install",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, Hairline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
            ) { Text("Not now") }
        }
    )
}

@Composable
private fun LegacyPatchMigrationDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = SurfaceRaised,
        titleContentColor = Color.White,
        textContentColor = Muted,
        title = {
            Column {
                Text("Restore the official $title", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(
                    "A previous Jester-patched installation was detected",
                    color = PrivateGold,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DirectPatchDialogStep(
                    number = "1",
                    headline = "Back up anything important",
                    detail = "Android must remove the previous patched installation before an original game package can be restored. Its local app data will be erased.",
                    warning = true
                )
                DirectPatchDialogStep(
                    number = "2",
                    headline = "Remove the legacy patch",
                    detail = "Jester Mods will open Android's uninstall confirmation. The add-on stays safely in your Library."
                )
                DirectPatchDialogStep(
                    number = "3",
                    headline = "Choose the original game source",
                    detail = "After uninstalling, this add-on's Browse requirements will open. Download its companion game when offered, or choose Google Play."
                )
                Text(
                    "The patched APK will never be stored inside the new shell as an original game payload.",
                    color = Accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PrivateGold, contentColor = Ink)
            ) {
                Text("Restore official game", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, Hairline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
            ) { Text("Not now") }
        }
    )
}

@Composable
private fun DirectPatchDialogStep(
    number: String,
    headline: String,
    detail: String,
    warning: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (warning) Danger.copy(alpha = 0.10f) else SurfaceDark)
            .border(
                BorderStroke(1.dp, if (warning) Danger.copy(alpha = 0.35f) else Hairline),
                RoundedCornerShape(18.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (warning) Danger.copy(alpha = 0.18f) else AccentBlue.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = if (warning) Danger else AccentBlue, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(headline, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(detail, color = if (warning) Danger else Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun launcherPageTransitionSpec(initialPage: LauncherPage, targetPage: LauncherPage): ContentTransform {
    val direction = if (targetPage.rank >= initialPage.rank) 1 else -1
    return (
        fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 35)) +
            slideInHorizontally(animationSpec = tween(durationMillis = SCREEN_TRANSITION_MS)) { fullWidth ->
                direction * maxOf(fullWidth / 10, 24)
            }
        ) togetherWith (
        fadeOut(animationSpec = tween(durationMillis = 110)) +
            slideOutHorizontally(animationSpec = tween(durationMillis = SCREEN_TRANSITION_MS)) { fullWidth ->
                -direction * maxOf(fullWidth / 18, 18)
            }
        )
}

@Composable
private fun rememberMinimumVisibleState(
    requestedVisible: Boolean,
    minVisibleMillis: Long
): Boolean {
    var visible by remember { mutableStateOf(requestedVisible) }
    var shownAtMillis by remember {
        mutableStateOf(if (requestedVisible) SystemClock.uptimeMillis() else 0L)
    }
    LaunchedEffect(requestedVisible, minVisibleMillis) {
        if (requestedVisible) {
            shownAtMillis = SystemClock.uptimeMillis()
            visible = true
            return@LaunchedEffect
        }
        if (!visible) {
            shownAtMillis = 0L
            return@LaunchedEffect
        }
        val visibleForMillis = SystemClock.uptimeMillis() - shownAtMillis
        val remainingMillis = minVisibleMillis - visibleForMillis
        if (remainingMillis > 0L) {
            delay(remainingMillis)
        }
        visible = false
        shownAtMillis = 0L
    }
    return visible
}

@Composable
private fun DeferredScreenContent(
    screenCache: LauncherScreenCache,
    contentKey: String,
    contentReady: Boolean = false,
    minimumPlaceholderMillis: Long = 0L,
    placeholder: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    var ready by remember(contentKey) {
        mutableStateOf(
            minimumPlaceholderMillis <= 0L &&
                (screenCache.warmedPages[contentKey] == true || contentReady)
        )
    }
    LaunchedEffect(contentKey, contentReady, minimumPlaceholderMillis) {
        if (minimumPlaceholderMillis > 0L) {
            ready = false
            delay(minimumPlaceholderMillis)
            screenCache.warmedPages[contentKey] = true
            ready = true
            return@LaunchedEffect
        }
        if (screenCache.warmedPages[contentKey] == true || contentReady) {
            screenCache.warmedPages[contentKey] = true
            ready = true
            return@LaunchedEffect
        }
        ready = false
        delay(SCREEN_TRANSITION_DEFER_MS)
        screenCache.warmedPages[contentKey] = true
        ready = true
    }
    AnimatedContent(
        targetState = ready,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 160, delayMillis = 25)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 120))
        },
        label = "deferred-screen-content",
        modifier = Modifier.fillMaxSize()
    ) { showContent ->
        if (showContent) content() else placeholder()
    }
}

@Composable
private fun ScreenTransitionPlaceholder(
    backLabel: String,
    title: String,
    detail: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 18.dp, vertical = 24.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.34f))
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF172633), SurfaceRaised, Color(0xFF0C1118))
                        )
                    )
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(AccentBlue.copy(alpha = 0.3f), Accent.copy(alpha = 0.1f), SurfaceDark)
                            )
                        )
                        .border(1.dp, AccentBlue.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    InstallerOrbitAnimation()
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "PREPARING YOUR EXPERIENCE",
                    color = AccentBlue,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    detail,
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = AccentBlue,
                    trackColor = Hairline
                )
                Spacer(Modifier.height(11.dp))
                Text(
                    "Opening from $backLabel",
                    color = Accent.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ModuleScreenPlaceholder(game: LibraryGame) = ScreenTransitionPlaceholder(
    backLabel = "Library",
    title = game.title,
    detail = "Preparing add-on controls, compatibility, and launch status…"
)

@Composable
private fun AccountIdentityIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(46.dp)
            .scale(if (pressed) 0.92f else 1f)
            .clip(CircleShape)
            .background(SurfaceRaised.copy(alpha = 0.98f))
            .semantics { contentDescription = "Account identity" }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Account identity",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(23.dp)) {
            val stroke = 2.1.dp.toPx()
            drawCircle(
                AccentBlue,
                radius = size.minDimension * 0.22f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.32f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
            )
            drawArc(
                AccentBlue,
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.19f, size.height * 0.46f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.62f, size.height * 0.56f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawCircle(
                Accent,
                radius = size.minDimension * 0.12f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.72f)
            )
        }
    }
}

@Composable
private fun ChangelogIconButton(
    updateAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(46.dp)
            .scale(if (pressed) 0.92f else 1f)
            .clip(CircleShape)
            .background(SurfaceRaised.copy(alpha = 0.98f))
            .semantics {
                contentDescription = if (updateAvailable) {
                    "Changelog, launcher update available"
                } else {
                    "Changelog"
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = if (updateAvailable) "Changelog, launcher update available" else "Changelog",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 2.2.dp.toPx()
            listOf(0.25f, 0.5f, 0.75f).forEach { y ->
                drawCircle(Accent, radius = stroke * 0.75f, center = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * y))
                drawLine(Accent, androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * y), androidx.compose.ui.geometry.Offset(size.width * 0.86f, size.height * y), stroke, StrokeCap.Round)
            }
        }
        if (updateAvailable) {
            Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(7.dp).clip(CircleShape).background(Danger))
        }
    }
}

@Composable
private fun AccountIdentityScreen(
    state: AccountIdentityUiState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var copiedLabel by rememberSaveable { mutableStateOf<String?>(null) }
    fun copy(label: String, value: String) {
        if (value.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        copiedLabel = label
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "‹  Library",
                color = Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onBack)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text("My Information", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Use this page when you need to identify this phone for access, recovery, or help.", color = Muted)
        }

        if (state.error != null) {
            item { ChangelogMessageCard(state.error) }
        } else {

        item {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Accent.copy(alpha = 0.18f), AccentBlue.copy(alpha = 0.10f), SurfaceRaised)
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.24f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AccountIdentityIconButton(onClick = {}, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your support code", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("One code for this phone, whether you use Root or Non-root.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(16.dp))
                IdentityCodeBlock(
                    label = "Code to send",
                    value = state.grantPassIdentity,
                    actionLabel = "Copy code",
                    onCopy = { copy("support code", state.grantPassIdentity) }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Use this code when you need to connect this phone to a pass or request help. It does not reveal your private access key and cannot unlock another phone.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                copiedLabel?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Copied $it", color = Accent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            IdentitySectionTitle("WHAT THIS CODE DOES")
            IdentityInfoCard(
                title = "Adds access to this phone",
                detail = "This lets a pass be linked to the device you are holding."
            )
            Spacer(Modifier.height(10.dp))
            IdentityInfoCard(
                title = "Works across launcher modes",
                detail = "Root and Non-root use the same device identity, so you do not need two separate passes."
            )
            Spacer(Modifier.height(10.dp))
            IdentityInfoCard(
                title = "Safe to share when needed",
                detail = "It is a device lookup code, not your private access key."
            )
        }

        item { IdentitySectionTitle("DEVICE DETAILS") }
        item {
            IdentityDetailCard("Phone code", state.deviceId) { copy("phone code", state.deviceId) }
        }
        item {
            IdentityDetailCard("Restore code", state.recoveryId) { copy("restore code", state.recoveryId) }
        }
        item {
            IdentityDetailCard("App security code", state.proofKeyId) { copy("app security code", state.proofKeyId) }
        }
        item {
            IdentityDetailCard("Launcher type", "${state.flavor.ifBlank { "unknown" }} · pass v${state.accessVersion}") {
                copy("launcher type", "${state.flavor} pass v${state.accessVersion}")
            }
        }
        item {
            IdentityDetailCard("Install code", state.installationId) { copy("install code", state.installationId) }
        }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun IdentitySectionTitle(label: String) {
    Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun IdentityCodeBlock(
    label: String,
    value: String,
    actionLabel: String = "Copy",
    onCopy: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onCopy, border = BorderStroke(1.dp, Accent.copy(alpha = 0.6f))) {
            Text(actionLabel, color = Accent)
        }
    }
}

@Composable
private fun IdentityInfoCard(
    title: String,
    detail: String
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceRaised.copy(alpha = 0.78f))
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(28.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = Accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun IdentityDetailCard(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceRaised.copy(alpha = 0.92f))
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value.ifBlank { "Unavailable" }, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(10.dp))
        OutlinedButton(onClick = onCopy, border = BorderStroke(1.dp, Hairline)) {
            Text("Copy", color = Accent)
        }
    }
}

@Composable
private fun ChangelogScreen(
    state: ChangelogUiState,
    launcherUpdate: LauncherUpdateUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenLauncherUpdate: () -> Unit,
    onOpenModuleChangelog: (String) -> Unit,
    onCloseModuleChangelog: () -> Unit
) {
    val installedBuild = com.moodtools.hub.BuildConfig.VERSION_CODE.toLong()
    var launcherHistoryOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = launcherHistoryOpen) { launcherHistoryOpen = false }
    if (state.selectedModuleHistory != null || state.selectedModulePackage != null) {
        ModuleChangelogDetailScreen(
            history = state.selectedModuleHistory,
            loading = state.moduleHistoryLoadingPackage != null,
            error = state.moduleHistoryError,
            onBack = onCloseModuleChangelog,
            onRetry = { state.selectedModulePackage?.let(onOpenModuleChangelog) }
        )
        return
    }
    if (launcherHistoryOpen) {
        LauncherChangelogDetailScreen(
            entries = state.launcherEntries,
            installedBuild = installedBuild,
            onBack = { launcherHistoryOpen = false }
        )
        return
    }
    var moduleQuery by rememberSaveable { mutableStateOf("") }
    var visibleModules by rememberSaveable { mutableStateOf(CHANGELOG_PAGE_SIZE) }
    val filteredModules = remember(state.moduleHistories, moduleQuery) {
        val query = moduleQuery.trim()
        val matching = if (query.isEmpty()) state.moduleHistories else state.moduleHistories.filter { history ->
            history.title.contains(query, ignoreCase = true) ||
                history.slug.contains(query, ignoreCase = true) ||
                history.packageName.contains(query, ignoreCase = true) ||
                history.gameVersion.contains(query, ignoreCase = true) ||
                history.entries.any { entry ->
                    entry.version.contains(query, ignoreCase = true) ||
                        entry.notes.contains(query, ignoreCase = true)
                }
        }
        matching.sortedWith(
            compareBy<ModuleChangelog, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.slug }
        )
    }
    LaunchedEffect(moduleQuery) { visibleModules = CHANGELOG_PAGE_SIZE }
    RefreshableScreen(
        refreshing = state.loading,
        onRefresh = onRetry,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Text(
                "‹  Library",
                color = Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onBack)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text("Changelog", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Launcher releases and add-on update history.", color = Muted)
        }

        if (launcherUpdate.available) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Accent.copy(alpha = 0.12f)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Launcher update available", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Version ${launcherUpdate.version ?: "new"}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = onOpenLauncherUpdate, border = BorderStroke(1.dp, Accent)) {
                        Text("View update", color = Accent)
                    }
                }
            }
        }

        item { ChangelogSectionTitle("JESTER MODS") }
        if (state.launcherEntries.isEmpty() && !state.loading) {
            item {
                ChangelogMessageCard(state.error ?: "Launcher release history will appear after the next successful sync.")
            }
        }
        state.launcherEntries.firstOrNull()?.let { entry ->
            item(key = "launcher-latest-${entry.build}") {
                Text(
                    "LATEST RELEASE",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                ChangelogEntryCard(
                    title = "Jester Mods ${entry.version}",
                    meta = launcherChangelogMeta(entry, installedBuild),
                    notes = entry.notes.ifBlank { "Maintenance and reliability improvements." },
                    highlighted = true
                )
            }
        }
        if (state.launcherEntries.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = { launcherHistoryOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Hairline)
                ) {
                    Text("View full history", color = Accent)
                }
            }
        }

        if (state.moduleHistories.isNotEmpty()) {
            item { ChangelogSectionTitle("ADD-ONS") }
            item {
                OutlinedTextField(
                    value = moduleQuery,
                    onValueChange = { moduleQuery = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search update activity") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Hairline,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = Muted,
                        cursorColor = Accent
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Showing ${minOf(visibleModules, filteredModules.size)} of ${filteredModules.size} add-ons",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            filteredModules.take(visibleModules).forEach { history ->
                item(key = "module-title-${history.slug}") {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text(history.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Game version ${history.gameVersion}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(history.entries, key = { "${history.slug}-${it.build}" }) { entry ->
                    ChangelogEntryCard(
                        title = entry.version,
                        meta = "${entry.updateType.replaceFirstChar { it.uppercase() }} · Build ${entry.build}",
                        notes = entry.notes.ifBlank { "Add-on maintenance update." },
                        highlighted = entry.build == history.currentBuild
                    )
                }
                item(key = "module-history-${history.slug}") {
                    OutlinedButton(
                        onClick = { onOpenModuleChangelog(history.slug) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Hairline)
                    ) {
                        Text("View full history", color = Accent)
                    }
                }
            }
            if (visibleModules < filteredModules.size) {
                item {
                    OutlinedButton(
                        onClick = { visibleModules += CHANGELOG_PAGE_SIZE },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Hairline)
                    ) {
                        Text("Load ${minOf(CHANGELOG_PAGE_SIZE, filteredModules.size - visibleModules)} more", color = Accent)
                    }
                }
            }
        }

        if (state.loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    color = Accent,
                    trackColor = Hairline
                )
                Spacer(Modifier.height(6.dp))
                Text("Refreshing verified changelogs…", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        } else if (state.error != null) {
            item {
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Hairline)) {
                    Text("Try launcher history again", color = Accent)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LauncherChangelogDetailScreen(
    entries: List<LauncherChangelogEntry>,
    installedBuild: Long,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredEntries = remember(entries, query, installedBuild) {
        val normalized = query.trim()
        if (normalized.isEmpty()) entries else entries.filter { entry ->
            entry.version.contains(normalized, ignoreCase = true) ||
                entry.build.toString().contains(normalized, ignoreCase = true) ||
                entry.notes.contains(normalized, ignoreCase = true) ||
                launcherChangelogMeta(entry, installedBuild).contains(normalized, ignoreCase = true)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "‹  Changelog",
                color = Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onBack)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Launcher history",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text("Every verified Jester Mods release.", color = Muted)
        }
        item {
            ChangelogSearchField(
                value = query,
                onValueChange = { query = it },
                label = "Search launcher history"
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Showing ${filteredEntries.size} of ${entries.size} releases",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (filteredEntries.isEmpty()) {
            item { ChangelogMessageCard("No launcher releases match “${query.trim()}”.") }
        } else {
            items(filteredEntries, key = { "launcher-full-${it.build}" }) { entry ->
                LauncherChangelogCompactCard(entry = entry, installedBuild = installedBuild)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ModuleChangelogDetailScreen(
    history: ModuleChangelog?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    var query by rememberSaveable(history?.packageName) { mutableStateOf("") }
    val filteredEntries = remember(history, query) {
        val entries = history?.entries.orEmpty()
        val normalized = query.trim()
        if (normalized.isEmpty()) entries else entries.filter { entry ->
            entry.version.contains(normalized, ignoreCase = true) ||
                entry.build.toString().contains(normalized, ignoreCase = true) ||
                entry.updateType.contains(normalized, ignoreCase = true) ||
                entry.notes.contains(normalized, ignoreCase = true)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "‹  All changelogs",
                color = Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onBack)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(history?.title ?: "Add-on history", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                history?.let { "Game version ${it.gameVersion} · ${it.packageName}" }
                    ?: "Loading verified release history…",
                color = Muted
            )
        }
        if (loading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    color = Accent,
                    trackColor = Hairline
                )
            }
        }
        if (error != null) {
            item {
                ChangelogMessageCard(error)
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Hairline)) {
                    Text("Try again", color = Accent)
                }
            }
        }
        history?.let { moduleHistory ->
            item {
                ChangelogSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Search add-on history"
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Showing ${filteredEntries.size} of ${moduleHistory.entries.size} releases",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (filteredEntries.isEmpty()) {
                item { ChangelogMessageCard("No add-on releases match “${query.trim()}”.") }
            } else {
                items(filteredEntries, key = { "full-${moduleHistory.packageName}-${it.build}" }) { entry ->
                    ModuleChangelogCompactCard(
                        entry = entry,
                        highlighted = entry.build == moduleHistory.currentBuild
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChangelogSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(80)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Hairline,
            focusedLabelColor = Accent,
            unfocusedLabelColor = Muted,
            cursorColor = Accent
        )
    )
}

@Composable
private fun ChangelogSectionTitle(label: String) {
    Text(label, color = AccentBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun ChangelogMessageCard(message: String) {
    Text(
        message,
        color = Muted,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(SurfaceRaised.copy(alpha = 0.88f)).padding(16.dp)
    )
}

@Composable
private fun ChangelogEntryCard(title: String, meta: String, notes: String, highlighted: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(if (highlighted) Accent.copy(alpha = 0.09f) else SurfaceRaised.copy(alpha = 0.88f))
            .padding(16.dp)
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(meta, color = if (highlighted) Accent else AccentBlue, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        ChangelogRundown(notes, fallback = "Maintenance and reliability improvements.")
    }
}

@Composable
private fun LauncherChangelogCompactCard(entry: LauncherChangelogEntry, installedBuild: Long) {
    var expanded by rememberSaveable(entry.build) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (entry.build >= installedBuild) Accent.copy(alpha = 0.07f) else SurfaceRaised.copy(alpha = 0.72f))
            .animateContentSize()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Jester Mods ${entry.version}", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    launcherChangelogMeta(entry, installedBuild),
                    color = if (entry.build >= installedBuild) Accent else AccentBlue,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                if (expanded) "Hide" else "Details",
                color = Accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            ChangelogRundown(entry.notes, fallback = "Maintenance and reliability improvements.")
        }
    }
}

@Composable
private fun ModuleChangelogCompactCard(entry: ModuleChangelogEntry, highlighted: Boolean) {
    var expanded by rememberSaveable(entry.build) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (highlighted) Accent.copy(alpha = 0.09f) else SurfaceRaised.copy(alpha = 0.72f))
            .animateContentSize()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.version, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${entry.updateType.replaceFirstChar { it.uppercase() }} · Build ${entry.build}" +
                        if (highlighted) " · Current" else "",
                    color = if (highlighted) Accent else AccentBlue,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                if (expanded) "Hide" else "Details",
                color = Accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            ChangelogRundown(entry.notes, fallback = "Add-on maintenance update.")
        }
    }
}

@Composable
private fun ChangelogRundown(notes: String, fallback: String) {
    val groups = remember(notes, fallback) { changelogRundown(notes, fallback) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        groups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${group.category.label}:",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                group.items.forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•", color = Accent, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            item,
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private fun launcherChangelogMeta(entry: LauncherChangelogEntry, installedBuild: Long): String = buildString {
    append(formatChangelogDate(entry.publishedAtEpochSeconds))
    append(" · Build ${entry.build}")
    when {
        entry.build == installedBuild -> append(" · Installed")
        entry.build > installedBuild -> append(" · Available")
    }
}

private fun formatChangelogDate(epochSeconds: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochSeconds * 1_000L))

private const val CHANGELOG_PAGE_SIZE = 20

@Composable
private fun LauncherUpdateScreen(
    update: LauncherUpdateUiState,
    onBack: () -> Unit,
    onInstall: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "‹  Launcher",
                color = if (update.inProgress || update.installing) Muted else Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(
                    enabled = !update.inProgress && !update.installing,
                    onClick = onBack
                ).padding(vertical = 8.dp, horizontal = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text("Launcher update", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Version ${update.version ?: "new"} is available for Jester Mods.", color = Muted)
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceRaised.copy(alpha = 0.9f)).padding(18.dp)
            ) {
                Text("CHANGES SINCE YOUR VERSION", color = AccentBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val entries = update.changelog.ifEmpty {
                    listOf(
                        LauncherChangelogEntry(
                            build = update.build,
                            version = update.version ?: "New version",
                            notes = update.notes.orEmpty(),
                            publishedAtEpochSeconds = 0L
                        )
                    )
                }
                entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("${entry.version} · Build ${entry.build}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    ChangelogRundown(
                        entry.notes,
                        fallback = "Includes launcher improvements and reliability fixes."
                    )
                }
                if (update.totalBytes > 0L) {
                    Spacer(Modifier.height(18.dp))
                    DownloadInfoRow("Download size", formatDownloadSize(update.totalBytes))
                }
                if (update.inProgress || update.installing || update.downloaded || update.downloadedBytes > 0L) {
                    Spacer(Modifier.height(10.dp))
                    DownloadProgressBar(
                        downloadedBytes = update.downloadedBytes,
                        totalBytes = update.totalBytes,
                        waiting = update.inProgress,
                        color = Accent
                    )
                }
                update.headline?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = if (update.failed) Danger else Color.White, fontWeight = FontWeight.SemiBold)
                }
                update.detail?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onInstall,
                    enabled = !update.inProgress && !update.installing,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
                ) {
                    Text(
                        when {
                            update.inProgress -> "Downloading…"
                            update.installing -> "Waiting for Android…"
                            update.downloaded -> "Install update"
                            update.failed -> "Try again"
                            else -> "Download and install"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Android will ask you to confirm replacing this launcher. Your games, add-ons, and one-day access stay on this device.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun UnifiedInstallerDialog(
    busy: Boolean,
    failed: Boolean,
    accent: Color,
    onDismiss: () -> Unit,
    maxWidth: androidx.compose.ui.unit.Dp = 600.dp,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
                .padding(horizontal = 16.dp, vertical = 22.dp),
            shape = RoundedCornerShape(34.dp),
            // The shared transfer windows contain deliberately soft accent gradients. Give them
            // an opaque foundation so text from the Library/details page cannot bleed through.
            color = SurfaceDark,
            border = BorderStroke(1.dp, (if (failed) Danger else accent).copy(alpha = 0.42f))
        ) {
            content()
        }
    }
}

@Composable
private fun InstallerDiagnosticsPanel(
    expanded: Boolean,
    onToggle: () -> Unit,
    summary: String,
    report: String,
    clipboardLabel: String,
    accent: Color = Accent
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "DIAGNOSTICS",
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(summary, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (expanded) "Hide" else "Inspect",
                color = AccentBlue,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            Text(
                report,
                modifier = Modifier.padding(15.dp),
                color = Color(0xFFC9D4E2),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, report))
                    android.widget.Toast.makeText(context, "Diagnostics copied", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 15.dp, end = 15.dp, bottom = 15.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
            ) {
                Text("Copy diagnostics")
            }
        }
    }
}

private fun commonInstallerDiagnostics(): String = buildString {
    appendLine("Launcher: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("Mode: ${if (BuildConfig.IS_ROOT_MODE) "Root" else "Non-root"}")
    appendLine("Channel: ${BuildConfig.SECURITY_BUILD_CHANNEL}${if (BuildConfig.DEBUG) " (debuggable)" else ""}")
    append("Android API: ${Build.VERSION.SDK_INT}")
}

@Composable
private fun LauncherUpdateDialog(
    update: LauncherUpdateUiState,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit
) {
    val busy = update.inProgress || update.installing || update.cancelling
    var diagnosticsExpanded by rememberSaveable(update.build) { mutableStateOf(false) }
    val report = remember(update) {
        buildString {
            appendLine("Jester Mods launcher update diagnostics")
            appendLine("Stage: ${launcherUpdateStageLabel(update)}")
            appendLine("Target: ${update.version ?: "Unknown"} (${update.build})")
            appendLine("Downloaded: ${update.downloaded}")
            appendLine("Progress: ${update.downloadedBytes.coerceAtLeast(0L)} / ${update.totalBytes.coerceAtLeast(0L)} bytes")
            update.stageProgress?.let { appendLine("Stage progress: ${(it.coerceIn(0f, 1f) * 100).toInt()}%") }
            update.headline?.let { appendLine("Status: $it") }
            update.detail?.let { appendLine("Detail: $it") }
            append(commonInstallerDiagnostics())
            if (update.diagnostics.isNotEmpty()) {
                appendLine()
                appendLine("Event trail:")
                update.diagnostics.forEachIndexed { index, entry -> appendLine("${index + 1}. $entry") }
            }
        }.trim()
    }
    UnifiedInstallerDialog(
        busy = busy,
        failed = update.failed,
        accent = Accent,
        onDismiss = onDismiss,
        maxWidth = 560.dp
    ) {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 720.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A2929), SurfaceRaised, Color(0xFF10151C))
                        )
                    ),
                contentPadding = PaddingValues(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SecureTransferEmblem(
                                stage = update.stage,
                                downloadedBytes = update.downloadedBytes,
                                totalBytes = update.totalBytes,
                                stageProgress = update.stageProgress,
                                contentDescription = "Launcher update",
                                accent = Accent
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "A NEW ERA AWAITS",
                                color = Accent,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Launcher update",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Version ${update.version ?: "new"}  ·  Build ${update.build}",
                                color = AccentBlue,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            "Later",
                            color = if (busy) Muted.copy(alpha = 0.4f) else Muted,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !busy, onClick = onDismiss)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
                item {
                    Text(
                        "The next chapter of Jester Mods is ready. Update securely without leaving the launcher.",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    SecureTransferJourney(
                        stage = update.stage,
                        labels = listOf("Ready", "Download", "Verify", "Install"),
                        accent = Accent
                    )
                }
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.045f))
                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            "WHAT'S NEW",
                            color = PrivateGold,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        val entries = update.changelog.ifEmpty {
                            listOf(
                                LauncherChangelogEntry(
                                    build = update.build,
                                    version = update.version ?: "New version",
                                    notes = update.notes.orEmpty(),
                                    publishedAtEpochSeconds = 0L
                                )
                            )
                        }
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) {
                                Spacer(Modifier.height(12.dp))
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                                Spacer(Modifier.height(12.dp))
                            }
                            Text(entry.version, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                            ChangelogRundown(
                                entry.notes,
                                fallback = "Includes launcher improvements and reliability fixes."
                            )
                        }
                    }
                }
                if (update.totalBytes > 0L) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Accent.copy(alpha = 0.07f))
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Verified package", color = Muted, style = MaterialTheme.typography.bodySmall)
                            Text(formatDownloadSize(update.totalBytes), color = Accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (update.stage in setOf(
                        SecureTransferStage.PREPARING,
                        SecureTransferStage.DOWNLOADING,
                        SecureTransferStage.VERIFYING,
                        SecureTransferStage.WAITING_FOR_ANDROID
                    )) {
                    item {
                        SecureTransferProgressPanel(
                            stage = update.stage,
                            downloadedBytes = update.downloadedBytes,
                            totalBytes = update.totalBytes,
                            stageProgress = update.stageProgress,
                            accent = Accent,
                            subject = "launcher update",
                            finalAction = "Android installer"
                        )
                    }
                }
                if (update.headline != null || update.detail != null) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background((if (update.failed) Danger else AccentBlue).copy(alpha = 0.08f))
                                .padding(14.dp)
                        ) {
                            update.headline?.let {
                                Text(it, color = if (update.failed) Danger else Color.White, fontWeight = FontWeight.SemiBold)
                            }
                            update.detail?.let {
                                Spacer(Modifier.height(3.dp))
                                Text(it, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    InstallerDiagnosticsPanel(
                        expanded = diagnosticsExpanded,
                        onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                        summary = "Live launcher update report",
                        report = report,
                        clipboardLabel = "Jester Mods launcher update diagnostics"
                    )
                }
                item {
                    if (busy) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { diagnosticsExpanded = !diagnosticsExpanded },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Hairline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                            ) {
                                Text("Diagnostics")
                            }
                            OutlinedButton(
                                onClick = onCancel,
                                enabled = !update.cancelling,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Danger.copy(alpha = 0.55f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
                            ) {
                                Text(if (update.cancelling) "Stopping…" else "Cancel")
                            }
                        }
                    } else Button(
                        onClick = onInstall,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(vertical = 15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
                    ) {
                        Text(
                            when {
                                update.inProgress -> "Downloading…"
                                update.installing -> "Waiting for Android…"
                                update.downloaded -> "Install update"
                                update.failed || update.cancelled -> "Try again"
                                else -> "Begin the update"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "Signed and verified by Jester Mods. Your library, add-ons, and access remain on this device.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
    }
}

private fun launcherUpdateStageLabel(update: LauncherUpdateUiState): String = when {
    update.cancelled -> "Update paused"
    update.failed -> "Update failed"
    update.stage == SecureTransferStage.PREPARING -> "Preparing secure update"
    update.stage == SecureTransferStage.DOWNLOADING -> "Downloading update"
    update.stage == SecureTransferStage.VERIFYING -> "Verifying package"
    update.stage == SecureTransferStage.WAITING_FOR_ANDROID -> "Waiting for Android installer"
    update.downloaded -> "Verified package ready"
    else -> "Update available"
}

@Composable
private fun ModuleTransferDialog(
    state: ModuleUpdateUiState,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val busy = state.inProgress || state.cancelling
    val checkingForUpdate = state.intent == ModuleTransferIntent.UPDATE_CHECK
    var diagnosticsExpanded by rememberSaveable(state.packageName) { mutableStateOf(false) }
    val report = remember(state) {
        buildString {
            appendLine(
                if (checkingForUpdate) "Jester Mods add-on update check diagnostics"
                else "Jester Mods add-on transfer diagnostics"
            )
            appendLine("Stage: ${secureTransferStageLabel(state.stage)}")
            if (state.title.isNotBlank()) appendLine("Add-on: ${state.title}")
            if (state.packageName.isNotBlank()) appendLine("Package: ${state.packageName}")
            if (state.targetVersion.isNotBlank()) appendLine("Target version: ${state.targetVersion}")
            appendLine("Action: ${state.actionLabel}")
            appendLine("Progress: ${state.downloadedBytes.coerceAtLeast(0L)} / ${state.totalBytes.coerceAtLeast(0L)} bytes")
            state.stageProgress?.let { appendLine("Stage progress: ${(it.coerceIn(0f, 1f) * 100).toInt()}%") }
            state.headline?.let { appendLine("Status: $it") }
            state.detail?.let { appendLine("Detail: $it") }
            append(commonInstallerDiagnostics())
            if (state.diagnostics.isNotEmpty()) {
                appendLine()
                appendLine("Event trail:")
                state.diagnostics.forEachIndexed { index, entry -> appendLine("${index + 1}. $entry") }
            }
        }.trim()
    }
    UnifiedInstallerDialog(
        busy = busy,
        failed = state.failed,
        accent = AccentBlue,
        onDismiss = onDismiss,
        maxWidth = 580.dp
    ) {
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 760.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF172633), SurfaceRaised, Color(0xFF0C1118))
                    )
                ),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SecureTransferEmblem(
                            stage = state.stage,
                            downloadedBytes = state.downloadedBytes,
                            totalBytes = state.totalBytes,
                            stageProgress = state.stageProgress,
                            contentDescription = state.actionLabel,
                            accent = AccentBlue
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "SECURE ${state.actionLabel.uppercase()}",
                            color = AccentBlue,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.title.ifBlank { "Jester Mods add-on" },
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.targetVersion.isNotBlank()) {
                            Text(
                                "Version ${state.targetVersion}",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (!busy) {
                        Text(
                            if (state.completed) "Done" else "Close",
                            color = Muted,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }
            item {
                SecureTransferJourney(
                    stage = state.stage,
                    labels = if (checkingForUpdate) {
                        listOf("Connect", "Compare", "Review", "Ready")
                    } else {
                        listOf("Prepare", "Download", "Verify", "Activate")
                    },
                    accent = AccentBlue
                )
            }
            if (state.stage in setOf(
                    SecureTransferStage.PREPARING,
                    SecureTransferStage.DOWNLOADING,
                    SecureTransferStage.VERIFYING,
                    SecureTransferStage.ACTIVATING
                )) {
                item {
                    SecureTransferProgressPanel(
                        stage = state.stage,
                        downloadedBytes = state.downloadedBytes,
                        totalBytes = state.totalBytes,
                        stageProgress = state.stageProgress,
                        accent = AccentBlue,
                        subject = if (checkingForUpdate) "signed release catalog" else "add-on package",
                        finalAction = if (checkingForUpdate) "update review" else "safe activation",
                        verificationDetail = if (checkingForUpdate) {
                            "Validating the release identity, version, and changelog"
                        } else {
                            "Checking package hash, identity, and signature"
                        }
                    )
                }
            }
            item {
                val statusColor = when {
                    state.failed -> Danger
                    state.cancelled -> PrivateGold
                    state.completed -> Accent
                    else -> AccentBlue
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusColor.copy(alpha = 0.09f))
                        .border(1.dp, statusColor.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        state.headline ?: secureTransferStageLabel(state.stage),
                        color = if (state.failed) Danger else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    state.detail?.let {
                        Spacer(Modifier.height(5.dp))
                        Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (state.changelog.isNotEmpty()) {
                item { ModuleUpdateChangelog(state.changelog) }
            }
            item {
                InstallerDiagnosticsPanel(
                    expanded = diagnosticsExpanded,
                    onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                    summary = if (checkingForUpdate) {
                        "Live add-on update check report"
                    } else {
                        "Live add-on transfer report"
                    },
                    report = report,
                    clipboardLabel = if (checkingForUpdate) {
                        "Jester Mods add-on update check diagnostics"
                    } else {
                        "Jester Mods add-on transfer diagnostics"
                    },
                    accent = AccentBlue
                )
            }
            item {
                when {
                    busy -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { diagnosticsExpanded = !diagnosticsExpanded },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Hairline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                        ) { Text("Diagnostics") }
                        OutlinedButton(
                            onClick = onCancel,
                            enabled = !state.cancelling && state.stage != SecureTransferStage.ACTIVATING,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Danger.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
                        ) {
                            Text(
                                when {
                                    state.cancelling -> "Stopping…"
                                    state.stage == SecureTransferStage.ACTIVATING -> "Finishing…"
                                    else -> "Cancel"
                                }
                            )
                        }
                    }
                    state.completed -> Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
                    ) { Text("Done", fontWeight = FontWeight.Bold) }
                    else -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Hairline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)
                        ) { Text("Close") }
                        Button(
                            onClick = onRetry,
                            enabled = canRetry,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Ink)
                        ) {
                            Text(
                                moduleTransferPrimaryActionLabel(state),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameInstallDialog(
    state: GameInstallUiState,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val busy = state.inProgress || state.installing || state.cancelling
    var diagnosticsExpanded by rememberSaveable(state.packageName) { mutableStateOf(false) }
    val report = remember(state) {
        buildString {
            appendLine("Jester Mods game companion installer diagnostics")
            appendLine("Stage: ${gameInstallStageLabel(state.stage)}")
            if (state.title.isNotBlank()) appendLine("Game: ${state.title}")
            if (state.packageName.isNotBlank()) appendLine("Package: ${state.packageName}")
            if (state.targetVersion.isNotBlank()) appendLine("Target version: ${state.targetVersion}")
            if (state.packageFormat.isNotBlank()) appendLine("Artifact: ${state.packageFormat}")
            appendLine("Progress: ${state.downloadedBytes.coerceAtLeast(0L)} / ${state.totalBytes.coerceAtLeast(0L)} bytes")
            state.stageProgress?.let { appendLine("Stage progress: ${(it.coerceIn(0f, 1f) * 100).toInt()}%") }
            appendLine(commonInstallerDiagnostics())
            if (state.diagnostics.isNotEmpty()) {
                appendLine()
                appendLine("Event trail:")
                state.diagnostics.forEachIndexed { index, entry ->
                    appendLine("${index + 1}. $entry")
                }
            }
        }.trim()
    }

    UnifiedInstallerDialog(
        busy = busy,
        failed = state.failed,
        accent = AccentBlue,
        onDismiss = onDismiss
    ) {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 760.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF172633), SurfaceRaised, Color(0xFF0C1118))
                        )
                    ),
                contentPadding = PaddingValues(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GameInstallEmblem(state)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "SECURE GAME COMPANION",
                                color = AccentBlue,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.title.ifBlank { "Game installer" },
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.targetVersion.isNotBlank()) {
                                Text(
                                    "Version ${state.targetVersion}  ·  ${state.packageFormat}",
                                    color = Muted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                item { GameInstallJourney(state) }

                if (state.stage in setOf(
                        GameInstallStage.DOWNLOADING,
                        GameInstallStage.VERIFYING,
                        GameInstallStage.PREPARING_INSTALLER,
                        GameInstallStage.WAITING_FOR_ANDROID
                    )
                ) {
                    item {
                        GameInstallProgressPanel(state)
                    }
                }

                item {
                    val statusColor = when {
                        state.failed -> Danger
                        state.cancelled -> PrivateGold
                        state.completed -> Accent
                        else -> AccentBlue
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusColor.copy(alpha = 0.09f))
                            .border(1.dp, statusColor.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            state.headline ?: gameInstallStageLabel(state.stage),
                            color = if (state.failed) Danger else Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        state.detail?.let { detail ->
                            Spacer(Modifier.height(5.dp))
                            Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.stage == GameInstallStage.WAITING_FOR_ANDROID) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Jester Mods will verify the installed build when you return.",
                                color = Accent.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                item {
                    InstallerDiagnosticsPanel(
                        expanded = diagnosticsExpanded,
                        onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                        summary = "${state.diagnostics.size} installer events captured",
                        report = report,
                        clipboardLabel = "Jester Mods companion installer diagnostics"
                    )
                }

                item {
                    when {
                        busy -> Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { diagnosticsExpanded = !diagnosticsExpanded },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Hairline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                            ) {
                                Text("Diagnostics")
                            }
                            OutlinedButton(
                                onClick = onCancel,
                                enabled = !state.cancelling,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Danger.copy(alpha = 0.55f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
                            ) {
                                Text(if (state.cancelling) "Stopping…" else "Cancel")
                            }
                        }
                        state.completed -> Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                        else -> Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Hairline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)
                            ) {
                                Text("Close")
                            }
                            Button(
                                onClick = onRetry,
                                enabled = canRetry,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Ink)
                            ) {
                                Text("Try again", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun PackageSetupDialog(
    state: PackageSetupUiState,
    canRetry: Boolean,
    alternativeMethod: NonRootMethod?,
    onTryAlternative: (NonRootMethod) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val busy = state.inProgress || state.cancelling
    val identityShell = state.kind == PackageReplacementKind.IDENTITY_SHELL
    val accent = if (identityShell) PrivateViolet else AccentBlue
    val packageLabel = if (identityShell) "exact-package shell" else "direct patch"
    val canCancel = busy && state.stage !in setOf(
        SecureTransferStage.ACTIVATING,
        SecureTransferStage.WAITING_FOR_ANDROID
    )
    var diagnosticsExpanded by rememberSaveable(state.packageName) { mutableStateOf(false) }
    val report = remember(state) {
        buildString {
            appendLine("Jester Mods $packageLabel setup diagnostics")
            appendLine("Stage: ${secureTransferStageLabel(state.stage)}")
            if (state.title.isNotBlank()) appendLine("Game: ${state.title}")
            if (state.packageName.isNotBlank()) appendLine("Package: ${state.packageName}")
            appendLine("Method: ${if (identityShell) "Exact-package shell" else "Direct patch"}")
            state.stageProgress?.let {
                appendLine("Stage progress: ${(it.coerceIn(0f, 1f) * 100).toInt()}%")
            }
            state.headline?.let { appendLine("Status: $it") }
            state.detail?.let { appendLine("Detail: $it") }
            append(commonInstallerDiagnostics())
            if (state.diagnostics.isNotEmpty()) {
                appendLine()
                appendLine("Event trail:")
                state.diagnostics.forEachIndexed { index, entry ->
                    appendLine("${index + 1}. $entry")
                }
            }
        }.trim()
    }

    UnifiedInstallerDialog(
        busy = busy,
        failed = state.failed,
        accent = accent,
        onDismiss = onDismiss,
        maxWidth = 580.dp
    ) {
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 760.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.16f),
                            SurfaceRaised,
                            Color(0xFF0C1118)
                        )
                    )
                ),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SecureTransferEmblem(
                        stage = state.stage,
                        downloadedBytes = 0L,
                        totalBytes = 0L,
                        stageProgress = state.stageProgress,
                        contentDescription = "$packageLabel setup",
                        accent = accent
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (identityShell) "EXACT-PACKAGE SHELL" else "VERIFIED DIRECT PATCH",
                            color = accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.title.ifBlank { "Game setup" },
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (identityShell) {
                                "Preserving the original game inside its branded shell"
                            } else {
                                "Preparing a locally verified patched installation"
                            },
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                SecureTransferJourney(
                    stage = state.stage,
                    labels = listOf("Prepare", "Build", "Verify", "Install"),
                    accent = accent
                )
            }

            if (state.stage in setOf(
                    SecureTransferStage.PREPARING,
                    SecureTransferStage.VERIFYING,
                    SecureTransferStage.ACTIVATING,
                    SecureTransferStage.WAITING_FOR_ANDROID
                )) {
                item {
                    SecureTransferProgressPanel(
                        stage = state.stage,
                        downloadedBytes = 0L,
                        totalBytes = 0L,
                        stageProgress = state.stageProgress,
                        accent = accent,
                        subject = packageLabel,
                        finalAction = "Android installer"
                    )
                }
            }

            item {
                val statusColor = when {
                    state.failed -> Danger
                    state.cancelled -> PrivateGold
                    state.completed -> Accent
                    else -> accent
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusColor.copy(alpha = 0.09f))
                        .border(1.dp, statusColor.copy(alpha = 0.24f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        state.headline ?: secureTransferStageLabel(state.stage),
                        color = if (state.failed) Danger else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    state.detail?.let { detail ->
                        Spacer(Modifier.height(5.dp))
                        Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.stage == SecureTransferStage.WAITING_FOR_ANDROID) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Android owns the current confirmation. Jester Mods will verify the result when you return.",
                            color = Accent.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                InstallerDiagnosticsPanel(
                    expanded = diagnosticsExpanded,
                    onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                    summary = "${state.diagnostics.size} setup events captured",
                    report = report,
                    clipboardLabel = "Jester Mods $packageLabel setup diagnostics",
                    accent = accent
                )
            }

            item {
                when {
                    busy -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { diagnosticsExpanded = !diagnosticsExpanded },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Hairline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                        ) { Text("Diagnostics") }
                        OutlinedButton(
                            onClick = onCancel,
                            enabled = canCancel && !state.cancelling,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Danger.copy(alpha = 0.55f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
                        ) {
                            Text(
                                when {
                                    state.cancelling -> "Stopping..."
                                    !canCancel -> "Finishing..."
                                    else -> "Cancel"
                                }
                            )
                        }
                    }
                    state.completed -> Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
                    ) { Text("Done", fontWeight = FontWeight.Bold) }
                    else -> Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Hairline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)
                            ) { Text("Close") }
                            Button(
                                onClick = onRetry,
                                enabled = canRetry,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Ink)
                            ) { Text("Try again", fontWeight = FontWeight.Bold) }
                        }
                        if (alternativeMethod != null && (state.failed || state.cancelled)) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { onTryAlternative(alternativeMethod) },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, Accent.copy(alpha = 0.58f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                            ) {
                                Text(
                                    "Use ${alternativeMethod.displayName} instead",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameInstallEmblem(state: GameInstallUiState) {
    val progress = if (state.totalBytes > 0L) {
        (state.downloadedBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val label = when {
        state.completed -> "OK"
        state.failed -> "!"
        state.cancelled -> "X"
        state.stage == GameInstallStage.DOWNLOADING -> "${(progress * 100).toInt()}%"
        state.stage == GameInstallStage.VERIFYING ||
            state.stage == GameInstallStage.PREPARING_INSTALLER ->
            "${((state.stageProgress ?: 0f).coerceIn(0f, 1f) * 100).toInt()}%"
        state.stage == GameInstallStage.WAITING_FOR_ANDROID -> ""
        else -> "APK"
    }
    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(AccentBlue.copy(alpha = 0.34f), Accent.copy(alpha = 0.12f), SurfaceDark)
                )
            )
            .border(1.dp, AccentBlue.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (state.stage == GameInstallStage.WAITING_FOR_ANDROID) {
            InstallerOrbitAnimation()
        } else {
            Text(
                label,
                color = when {
                    state.failed -> Danger
                    state.completed -> Accent
                    else -> Color.White
                },
                style = if (label.length > 2) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun GameInstallProgressPanel(state: GameInstallUiState) {
    val phaseProgress by animateFloatAsState(
        targetValue = (state.stageProgress ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 320),
        label = "installer-phase-progress"
    )
    val accent = when (state.stage) {
        GameInstallStage.VERIFYING -> Accent
        GameInstallStage.PREPARING_INSTALLER -> PrivateViolet
        GameInstallStage.WAITING_FOR_ANDROID -> AccentBlue
        else -> AccentBlue
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.12f), Color.White.copy(alpha = 0.035f))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (state.stage) {
                        GameInstallStage.DOWNLOADING -> "DOWNLOADING"
                        GameInstallStage.VERIFYING -> "VERIFYING"
                        GameInstallStage.PREPARING_INSTALLER -> "PREPARING"
                        GameInstallStage.WAITING_FOR_ANDROID -> "INSTALLING"
                        else -> "INSTALLER"
                    },
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    when (state.stage) {
                        GameInstallStage.DOWNLOADING -> "Receiving the secure package"
                        GameInstallStage.VERIFYING -> "Proving integrity and identity"
                        GameInstallStage.PREPARING_INSTALLER -> "Building Android's install request"
                        GameInstallStage.WAITING_FOR_ANDROID -> "Android installer is active"
                        else -> gameInstallStageLabel(state.stage)
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                when (state.stage) {
                    GameInstallStage.DOWNLOADING -> if (state.totalBytes > 0L) {
                        formatDownloadSize(state.totalBytes)
                    } else "SECURE"
                    GameInstallStage.VERIFYING,
                    GameInstallStage.PREPARING_INSTALLER -> "${(phaseProgress * 100).toInt()}%"
                    GameInstallStage.WAITING_FOR_ANDROID -> "ACTIVE"
                    else -> ""
                },
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(14.dp))
        when (state.stage) {
            GameInstallStage.DOWNLOADING -> DownloadProgressBar(
                downloadedBytes = state.downloadedBytes,
                totalBytes = state.totalBytes,
                waiting = !state.cancelling,
                color = accent
            )
            GameInstallStage.VERIFYING,
            GameInstallStage.PREPARING_INSTALLER -> InstallerDeterminateProgress(
                progress = phaseProgress,
                color = accent,
                label = if (state.stage == GameInstallStage.VERIFYING) {
                    when {
                        phaseProgress < 0.8f -> "Reading and hashing package"
                        phaseProgress < 0.96f -> "Checking app identity and signature"
                        else -> "Finalizing verification"
                    }
                } else {
                    when {
                        phaseProgress < 0.52f -> "Rechecking trusted package"
                        phaseProgress < 0.74f -> "Validating installer contents"
                        phaseProgress < 0.98f -> "Writing secure install session"
                        else -> "Opening Android installer"
                    }
                }
            )
            GameInstallStage.WAITING_FOR_ANDROID -> InstallerIndeterminateProgress(accent)
            else -> Unit
        }
    }
}

@Composable
private fun InstallerDeterminateProgress(
    progress: Float,
    color: Color,
    label: String
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape)
            .background(Hairline)
            .semantics { contentDescription = "$label ${(progress * 100).toInt()} percent complete" }
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(7.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.72f), color)))
        )
    }
    Spacer(Modifier.height(9.dp))
    Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun InstallerIndeterminateProgress(
    color: Color,
    message: String = "Complete Android's secure prompt. Jester Mods will verify the result when you return.",
    contentDescription: String = "Android installation in progress"
) {
    LinearProgressIndicator(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape)
            .semantics { this.contentDescription = contentDescription },
        color = color,
        trackColor = Hairline
    )
    Spacer(Modifier.height(9.dp))
    Text(
        message,
        color = Muted,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun InstallerOrbitAnimation() {
    val transition = rememberInfiniteTransition(label = "installer-orbit")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "installer-orbit-rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.58f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720),
            repeatMode = RepeatMode.Reverse
        ),
        label = "installer-orbit-pulse"
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(42.dp).rotate(rotation)) {
            val stroke = 3.dp.toPx()
            drawArc(
                color = AccentBlue,
                startAngle = 8f,
                sweepAngle = 218f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Accent.copy(alpha = 0.82f),
                startAngle = 246f,
                sweepAngle = 68f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Box(
            Modifier
                .size(11.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(Accent)
        )
    }
}

@Composable
private fun GameInstallJourney(state: GameInstallUiState) {
    val activeStep = when (state.stage) {
        GameInstallStage.DOWNLOADING -> 0
        GameInstallStage.VERIFYING -> 1
        GameInstallStage.PREPARING_INSTALLER -> 2
        GameInstallStage.WAITING_FOR_ANDROID -> 3
        GameInstallStage.COMPLETED -> 4
        GameInstallStage.FAILED, GameInstallStage.CANCELLED -> if (state.downloaded) 2 else 0
        GameInstallStage.IDLE -> 0
    }
    val labels = listOf("Download", "Verify", "Prepare", "Install")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val reached = index <= activeStep
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(if (index == activeStep && activeStep < 4) 13.dp else 10.dp)
                        .clip(CircleShape)
                        .background(if (reached) AccentBlue else Hairline)
                        .border(
                            1.dp,
                            if (index == activeStep && activeStep < 4) Accent else Color.Transparent,
                            CircleShape
                        )
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    label,
                    color = if (reached) Color.White else Muted.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == activeStep) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

private fun gameInstallStageLabel(stage: GameInstallStage): String = when (stage) {
    GameInstallStage.IDLE -> "Ready"
    GameInstallStage.DOWNLOADING -> "Downloading package"
    GameInstallStage.VERIFYING -> "Verifying package"
    GameInstallStage.PREPARING_INSTALLER -> "Preparing installer"
    GameInstallStage.WAITING_FOR_ANDROID -> "Waiting for Android"
    GameInstallStage.COMPLETED -> "Installation complete"
    GameInstallStage.FAILED -> "Installation failed"
    GameInstallStage.CANCELLED -> "Installation cancelled"
}

@Composable
private fun InstalledModuleUpdatesDialog(
    screenCache: LauncherScreenCache,
    updates: List<LibraryGame>,
    state: InstalledModuleUpdatesUiState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onReview: (LibraryGame) -> Unit,
    onUpdate: (LibraryGame) -> Unit,
    onUpdateAll: () -> Unit
) {
    val count = updates.size
    val installedCount = updates.count {
        state.itemStates[it.packageName]?.status == InstalledModuleUpdateItemStatus.INSTALLED
    }
    val failedCount = updates.count {
        state.itemStates[it.packageName]?.status == InstalledModuleUpdateItemStatus.FAILED
    }
    val remainingCount = count - installedCount
    val activeUpdate = updates.firstOrNull { game ->
        state.itemStates[game.packageName]?.status in setOf(
            InstalledModuleUpdateItemStatus.QUEUED,
            InstalledModuleUpdateItemStatus.DOWNLOADING
        )
    }
    val activeItemState = activeUpdate?.let { state.itemStates[it.packageName] }
    val aggregateStage = when {
        count > 0 && installedCount == count -> SecureTransferStage.COMPLETED
        state.cancelled -> SecureTransferStage.CANCELLED
        state.inProgress && activeItemState != null -> activeItemState.stage
        failedCount > 0 -> SecureTransferStage.FAILED
        else -> SecureTransferStage.READY
    }
    var diagnosticsExpanded by rememberSaveable(updates.joinToString { it.packageName }) {
        mutableStateOf(false)
    }
    val report = remember(updates, state) {
        buildString {
            appendLine("Jester Mods add-on update diagnostics")
            appendLine("Stage: ${installedModuleUpdateStageLabel(state, installedCount, count)}")
            appendLine("Updates: $count")
            appendLine("Installed: $installedCount")
            appendLine("Failed: $failedCount")
            appendLine("Remaining: $remainingCount")
            updates.forEachIndexed { index, game ->
                val item = state.itemStates[game.packageName]
                    ?: InstalledModuleUpdateItemUiState()
                appendLine()
                appendLine("${index + 1}. ${game.title}")
                appendLine("Package: ${game.packageName}")
                appendLine("Build: ${game.installedBuild} -> ${game.listing?.catalog?.build ?: "Unknown"}")
                appendLine("Status: ${item.status.name.lowercase().replaceFirstChar(Char::uppercase)}")
                appendLine("Stage: ${secureTransferStageLabel(item.stage)}")
                if (item.totalBytes > 0L || item.downloadedBytes > 0L) {
                    appendLine("Progress: ${item.downloadedBytes.coerceAtLeast(0L)} / ${item.totalBytes.coerceAtLeast(0L)} bytes")
                }
                item.detail?.let { appendLine("Detail: $it") }
                if (item.diagnostics.isNotEmpty()) {
                    appendLine("Events:")
                    item.diagnostics.forEach { appendLine("- $it") }
                }
            }
            appendLine()
            append(commonInstallerDiagnostics())
        }.trim()
    }
    UnifiedInstallerDialog(
        busy = state.inProgress || state.cancelling,
        failed = failedCount > 0,
        accent = AccentBlue,
        onDismiss = onDismiss,
        maxWidth = 580.dp
    ) {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 720.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF17252A), SurfaceRaised, Color(0xFF10141C))
                        )
                    ),
                contentPadding = PaddingValues(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SecureTransferEmblem(
                                stage = aggregateStage,
                                downloadedBytes = activeItemState?.downloadedBytes ?: 0L,
                                totalBytes = activeItemState?.totalBytes ?: 0L,
                                stageProgress = activeItemState?.stageProgress,
                                contentDescription = "Installed add-on updates",
                                accent = AccentBlue
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "LIBRARY UPDATES",
                                color = Accent,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when {
                                    state.inProgress && state.updatingAll -> "Updating your library"
                                    state.inProgress -> "Installing add-on update"
                                    installedCount == count -> "Your add-ons are current"
                                    failedCount > 0 -> "Some updates need attention"
                                    count == 1 -> "An add-on is ready"
                                    else -> "$count add-ons are ready"
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                when {
                                    state.inProgress -> "${installedCount.coerceAtMost(count)} of $count complete"
                                    installedCount > 0 -> "$installedCount installed  ·  $remainingCount remaining"
                                    count == 1 -> "1 verified release"
                                    else -> "$count verified releases"
                                },
                                color = AccentBlue,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            when {
                                state.inProgress -> "Updating"
                                installedCount > 0 || failedCount > 0 -> "Done"
                                else -> "Later"
                            },
                            color = if (state.inProgress) Muted.copy(alpha = 0.55f) else Muted,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !state.inProgress, onClick = onDismiss)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
                item {
                    Text(
                        when {
                            state.inProgress -> "Keep Jester Mods open. Signed packages are downloaded, verified, and installed one at a time."
                            installedCount == count -> "Everything finished successfully. You can close this window and return to your library."
                            failedCount > 0 -> "Successful updates are already installed. Retry only the add-ons that need attention, or finish the rest together."
                            count == 1 -> "Install the verified release here now, open its details first, or choose Later and update whenever you are ready."
                            else -> "Update everything safely in sequence, install releases one by one, or choose Later and decide from each add-on's details."
                        },
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    SecureTransferJourney(
                        stage = aggregateStage,
                        labels = listOf("Queue", "Download", "Verify", "Activate"),
                        accent = AccentBlue
                    )
                }
                if (state.inProgress && activeItemState != null) {
                    item {
                        SecureTransferProgressPanel(
                            stage = aggregateStage,
                            downloadedBytes = activeItemState.downloadedBytes,
                            totalBytes = activeItemState.totalBytes,
                            stageProgress = activeItemState.stageProgress,
                            accent = AccentBlue,
                            subject = activeUpdate?.title?.let { "$it package" } ?: "add-on package",
                            finalAction = "safe activation"
                        )
                    }
                }
                item {
                    Button(
                        onClick = onUpdateAll,
                        enabled = !state.inProgress && remainingCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Ink,
                            disabledContainerColor = Accent.copy(alpha = 0.18f),
                            disabledContentColor = Color.White.copy(alpha = 0.55f)
                        )
                    ) {
                        Text(
                            when {
                                state.inProgress && state.updatingAll -> "Updating all · ${installedCount.coerceAtMost(count)}/$count"
                                state.inProgress -> "Another update is in progress"
                                remainingCount == 0 -> "All updates installed"
                                failedCount > 0 -> "Retry & update remaining ($remainingCount)"
                                count == 1 -> "Update now"
                                else -> "Update all $remainingCount add-ons"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (state.inProgress) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { diagnosticsExpanded = !diagnosticsExpanded },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Hairline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                            ) { Text("Diagnostics") }
                            OutlinedButton(
                                onClick = onCancel,
                                enabled = !state.cancelling,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Danger.copy(alpha = 0.55f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
                            ) { Text(if (state.cancelling) "Stopping…" else "Cancel") }
                        }
                    }
                }
                items(updates, key = LibraryGame::packageName) { game ->
                    InstalledModuleUpdateCard(
                        screenCache = screenCache,
                        game = game,
                        itemState = state.itemStates[game.packageName]
                            ?: InstalledModuleUpdateItemUiState(),
                        anotherUpdateInProgress = state.inProgress,
                        onReview = { onReview(game) },
                        onUpdate = { onUpdate(game) }
                    )
                }
                item {
                    InstallerDiagnosticsPanel(
                        expanded = diagnosticsExpanded,
                        onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                        summary = "$count add-on update ${if (count == 1) "record" else "records"}",
                        report = report,
                        clipboardLabel = "Jester Mods add-on update diagnostics",
                        accent = AccentBlue
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Accent.copy(alpha = 0.07f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (state.inProgress) {
                                "The window stays open until the active update finishes, so progress and any retry guidance remain visible."
                            } else {
                                "Details still includes Check for updates and Download, so choosing Later never takes away your options."
                            },
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
    }
}

private fun installedModuleUpdateStageLabel(
    state: InstalledModuleUpdatesUiState,
    installedCount: Int,
    count: Int
): String = when {
    state.inProgress && state.updatingAll -> "Updating all add-ons"
    state.inProgress -> "Installing add-on update"
    count > 0 && installedCount == count -> "All updates installed"
    state.itemStates.values.any { it.status == InstalledModuleUpdateItemStatus.FAILED } -> "Update needs attention"
    else -> "Updates available"
}

@Composable
private fun InstalledModuleUpdateCard(
    screenCache: LauncherScreenCache,
    game: LibraryGame,
    itemState: InstalledModuleUpdateItemUiState,
    anotherUpdateInProgress: Boolean,
    onReview: () -> Unit,
    onUpdate: () -> Unit
) {
    val listing = game.listing ?: return
    val bitmap = rememberLibraryBitmap(screenCache, game, 112)
    val downloadSize = listing.game?.abi?.let(listing.catalog.downloadSizeByAbi::get)
        ?: listing.catalog.downloadSizeByAbi.values.distinct().singleOrNull()
        ?: 0L
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = game.title,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text(game.title.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    game.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Installed build ${game.installedBuild}  ·  Build ${listing.catalog.build}",
                    color = AccentBlue,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    buildString {
                        append("Add-on ${listing.catalog.version}")
                        if (downloadSize > 0L) append("  ·  ${formatDownloadSize(downloadSize)}")
                    },
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (itemState.status in setOf(
                InstalledModuleUpdateItemStatus.QUEUED,
                InstalledModuleUpdateItemStatus.DOWNLOADING
            )) {
            SecureTransferProgressPanel(
                stage = itemState.stage,
                downloadedBytes = itemState.downloadedBytes,
                totalBytes = itemState.totalBytes,
                stageProgress = itemState.stageProgress,
                accent = Accent,
                subject = "add-on package",
                finalAction = "safe activation"
            )
            Spacer(Modifier.height(10.dp))
        }
        itemState.detail?.let { detail ->
            Text(
                detail,
                color = when (itemState.status) {
                    InstalledModuleUpdateItemStatus.INSTALLED -> Accent
                    InstalledModuleUpdateItemStatus.FAILED -> Danger
                    else -> Muted
                },
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onUpdate,
                enabled = !anotherUpdateInProgress &&
                    itemState.status != InstalledModuleUpdateItemStatus.INSTALLED,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Ink,
                    disabledContainerColor = when (itemState.status) {
                        InstalledModuleUpdateItemStatus.INSTALLED -> Accent.copy(alpha = 0.16f)
                        else -> Hairline
                    },
                    disabledContentColor = when (itemState.status) {
                        InstalledModuleUpdateItemStatus.INSTALLED -> Accent
                        else -> Muted
                    }
                )
            ) {
                Text(
                    when (itemState.status) {
                        InstalledModuleUpdateItemStatus.AVAILABLE -> "Update"
                        InstalledModuleUpdateItemStatus.QUEUED -> "Queued"
                        InstalledModuleUpdateItemStatus.DOWNLOADING -> "Updating"
                        InstalledModuleUpdateItemStatus.INSTALLED -> "Installed"
                        InstalledModuleUpdateItemStatus.FAILED -> "Retry"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = onReview,
                enabled = !anotherUpdateInProgress,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.42f))
            ) {
                Text("Details", color = if (anotherUpdateInProgress) Muted else AccentBlue, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SecureTransferEmblem(
    stage: SecureTransferStage,
    downloadedBytes: Long,
    totalBytes: Long,
    stageProgress: Float?,
    contentDescription: String,
    accent: Color
) {
    if (stage == SecureTransferStage.READY) {
        UpdateEmblem(contentDescription)
        return
    }
    val byteProgress = if (totalBytes > 0L) {
        (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val label = when (stage) {
        SecureTransferStage.COMPLETED -> "OK"
        SecureTransferStage.FAILED -> "!"
        SecureTransferStage.CANCELLED -> "X"
        SecureTransferStage.DOWNLOADING -> "${(byteProgress * 100).toInt()}%"
        SecureTransferStage.PREPARING,
        SecureTransferStage.VERIFYING -> stageProgress?.takeIf { it > 0.01f }
            ?.let { "${(it.coerceIn(0f, 1f) * 100).toInt()}%" }
            ?: "…"
        SecureTransferStage.ACTIVATING,
        SecureTransferStage.WAITING_FOR_ANDROID -> ""
        SecureTransferStage.READY -> ""
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.34f), Accent.copy(alpha = 0.1f), SurfaceDark)
                )
            )
            .border(1.dp, accent.copy(alpha = 0.55f), CircleShape)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        if (stage == SecureTransferStage.ACTIVATING || stage == SecureTransferStage.WAITING_FOR_ANDROID) {
            InstallerOrbitAnimation()
        } else {
            Text(
                label,
                color = when (stage) {
                    SecureTransferStage.FAILED -> Danger
                    SecureTransferStage.COMPLETED -> Accent
                    SecureTransferStage.CANCELLED -> PrivateGold
                    else -> Color.White
                },
                style = if (label.length > 2) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun SecureTransferJourney(
    stage: SecureTransferStage,
    labels: List<String>,
    accent: Color
) {
    require(labels.size == 4)
    val activeStep = when (stage) {
        SecureTransferStage.READY,
        SecureTransferStage.PREPARING,
        SecureTransferStage.CANCELLED,
        SecureTransferStage.FAILED -> 0
        SecureTransferStage.DOWNLOADING -> 1
        SecureTransferStage.VERIFYING -> 2
        SecureTransferStage.ACTIVATING,
        SecureTransferStage.WAITING_FOR_ANDROID,
        SecureTransferStage.COMPLETED -> 3
    }
    val complete = stage == SecureTransferStage.COMPLETED
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        labels.forEachIndexed { index, label ->
            val reached = index <= activeStep
            if (index > 0) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(top = 11.dp)
                        .height(2.dp)
                        .background(if (reached) accent.copy(alpha = 0.75f) else Hairline)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                complete || index < activeStep -> accent
                                index == activeStep -> accent.copy(alpha = 0.22f)
                                else -> Hairline
                            }
                        )
                        .border(
                            1.dp,
                            if (reached) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (complete || index < activeStep) "✓" else (index + 1).toString(),
                        color = if (complete || index < activeStep) Ink else if (reached) Color.White else Muted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    label,
                    color = if (reached) Color.White else Muted.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == activeStep) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SecureTransferProgressPanel(
    stage: SecureTransferStage,
    downloadedBytes: Long,
    totalBytes: Long,
    stageProgress: Float?,
    accent: Color,
    subject: String,
    finalAction: String,
    verificationDetail: String = "Checking package hash, identity, and signature"
) {
    val reportedProgress = stageProgress?.takeIf { it > 0.01f }
    val targetProgress = reportedProgress ?: when (stage) {
        SecureTransferStage.PREPARING -> 0.72f
        SecureTransferStage.VERIFYING -> 0.9f
        else -> 0f
    }
    val phaseProgress by animateFloatAsState(
        targetValue = targetProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (reportedProgress == null) 1_400 else 320),
        label = "secure-transfer-progress"
    )
    val stageAccent = when (stage) {
        SecureTransferStage.VERIFYING -> Accent
        SecureTransferStage.ACTIVATING -> PrivateViolet
        else -> accent
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(stageAccent.copy(alpha = 0.12f), Color.White.copy(alpha = 0.035f))
                )
            )
            .border(1.dp, stageAccent.copy(alpha = 0.24f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text(
            when (stage) {
                SecureTransferStage.PREPARING -> "PREPARING"
                SecureTransferStage.DOWNLOADING -> "DOWNLOADING"
                SecureTransferStage.VERIFYING -> "VERIFYING"
                SecureTransferStage.ACTIVATING -> "ACTIVATING"
                SecureTransferStage.WAITING_FOR_ANDROID -> "INSTALLING"
                else -> "SECURE TRANSFER"
            },
            color = stageAccent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            when (stage) {
                SecureTransferStage.PREPARING -> "Authorizing and preparing $subject"
                SecureTransferStage.DOWNLOADING -> "Receiving the signed $subject"
                SecureTransferStage.VERIFYING -> "Verifying $subject"
                SecureTransferStage.ACTIVATING -> "Completing $finalAction"
                SecureTransferStage.WAITING_FOR_ANDROID -> "$finalAction is active"
                else -> secureTransferStageLabel(stage)
            },
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(14.dp))
        when (stage) {
            SecureTransferStage.DOWNLOADING -> DownloadProgressBar(
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                waiting = true,
                color = stageAccent
            )
            SecureTransferStage.PREPARING,
            SecureTransferStage.VERIFYING -> InstallerDeterminateProgress(
                progress = phaseProgress,
                color = stageAccent,
                label = if (stage == SecureTransferStage.PREPARING) {
                    "Checking access and signed release details"
                } else {
                    verificationDetail
                }
            )
            SecureTransferStage.ACTIVATING,
            SecureTransferStage.WAITING_FOR_ANDROID -> InstallerIndeterminateProgress(
                color = stageAccent,
                message = if (stage == SecureTransferStage.WAITING_FOR_ANDROID) {
                    "Complete Android's secure prompt. Jester Mods will verify the result when you return."
                } else {
                    "The verified files are being activated atomically. Keep Jester Mods open."
                },
                contentDescription = if (stage == SecureTransferStage.WAITING_FOR_ANDROID) {
                    "Android installation in progress"
                } else {
                    "Verified add-on activation in progress"
                }
            )
            else -> Unit
        }
    }
}

private fun secureTransferStageLabel(stage: SecureTransferStage): String = when (stage) {
    SecureTransferStage.READY -> "Ready"
    SecureTransferStage.PREPARING -> "Preparing secure transfer"
    SecureTransferStage.DOWNLOADING -> "Downloading package"
    SecureTransferStage.VERIFYING -> "Verifying package"
    SecureTransferStage.ACTIVATING -> "Activating package"
    SecureTransferStage.WAITING_FOR_ANDROID -> "Waiting for Android"
    SecureTransferStage.COMPLETED -> "Complete"
    SecureTransferStage.FAILED -> "Failed"
    SecureTransferStage.CANCELLED -> "Paused"
}

@Composable
private fun UpdateEmblem(contentDescription: String = "Launcher update") {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Accent.copy(alpha = 0.28f), AccentBlue.copy(alpha = 0.08f))
                )
            )
            .border(1.dp, Accent.copy(alpha = 0.42f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(28.dp).semantics { this.contentDescription = contentDescription }) {
            val centerX = size.width / 2f
            drawLine(
                color = Accent,
                start = androidx.compose.ui.geometry.Offset(centerX, size.height * 0.18f),
                end = androidx.compose.ui.geometry.Offset(centerX, size.height * 0.68f),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Accent,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.48f),
                end = androidx.compose.ui.geometry.Offset(centerX, size.height * 0.70f),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Accent,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.48f),
                end = androidx.compose.ui.geometry.Offset(centerX, size.height * 0.70f),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = AccentBlue,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.23f, size.height * 0.84f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.77f, size.height * 0.84f),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ModuleBrowserScreen(
    screenCache: LauncherScreenCache,
    listings: List<ModuleListing>,
    query: String,
    filter: BrowseFilter,
    category: String?,
    sort: BrowseSort,
    visibleCount: Int,
    refreshing: Boolean,
    onQueryChange: (String) -> Unit,
    onFilterChange: (BrowseFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onSortChange: (BrowseSort) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenDownload: (ModuleListing) -> Unit
) {
    val catalogRevision = remember(listings) { browseCatalogRevision(listings) }
    val browseKey = remember(catalogRevision, query, filter, category, sort) {
        BrowseCacheKey(
            catalogRevision = catalogRevision,
            query = query.trim().lowercase(Locale.ROOT),
            filter = filter,
            category = category?.lowercase(Locale.ROOT),
            sort = sort
        )
    }
    val result = remember(browseKey) {
        screenCache.browseResults.getOrPut(browseKey) {
            browseCatalog(listings, query, filter, category, sort)
        }
    }
    val visibleListings = result.items.take(visibleCount)
    val defaultSections = query.isBlank() && filter == BrowseFilter.ALL && category == null
    val recommended = if (defaultSections) visibleListings.filter(ModuleListing::isRecommendedForDevice) else emptyList()
    val remaining = if (defaultSections) visibleListings.filterNot(ModuleListing::isRecommendedForDevice) else visibleListings
    val recommendedCount = if (defaultSections) result.items.count(ModuleListing::isRecommendedForDevice) else 0
    val remainingCount = if (defaultSections) result.items.size - recommendedCount else result.items.size
    val hasMore = visibleListings.size < result.items.size
    val loadMoreKey = "$BROWSE_LOAD_MORE_KEY_PREFIX${visibleListings.size}"
    val listState = rememberLazyListState()
    var sortMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(result.categories, category) {
        if (category != null && result.categories.none { it.equals(category, ignoreCase = true) }) {
            onCategoryChange(null)
        }
    }

    LaunchedEffect(browseKey) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(listState, browseKey, hasMore, loadMoreKey) {
        if (!hasMore) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.any { item -> item.key == loadMoreKey }
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    RefreshableScreen(
        refreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Text(
                "‹  Library",
                color = Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onBack)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text("Browse add-ons", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Choose an add-on for a supported game, then add it to your Jester Mods library.", color = Muted)
        }
        if (listings.isNotEmpty()) {
            item(key = "catalog-search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search add-ons or packages", color = Muted) },
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            Text(
                                "Clear",
                                color = Accent,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onQueryChange("") }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Accent,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Hairline,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    )
                )
            }
            item(key = "catalog-filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BrowseFilter.entries, key = BrowseFilter::name) { choice ->
                        CatalogFilterChip(
                            label = choice.label,
                            selected = choice == filter,
                            onClick = { onFilterChange(choice) }
                        )
                    }
                }
            }
            if (result.categories.size > 1) {
                item(key = "catalog-categories") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            CatalogFilterChip(
                                label = "All categories",
                                selected = category == null,
                                onClick = { onCategoryChange(null) }
                            )
                        }
                        items(result.categories, key = { it }) { choice ->
                            CatalogFilterChip(
                                label = choice,
                                selected = choice.equals(category, ignoreCase = true),
                                onClick = { onCategoryChange(choice) }
                            )
                        }
                    }
                }
            }
            item(key = "catalog-summary") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${result.items.size} ${if (result.items.size == 1) "add-on" else "add-ons"}",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box {
                        OutlinedButton(
                            onClick = { sortMenuOpen = true },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                        ) {
                            Text("Sort: ${sort.label}", style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                            modifier = Modifier.background(SurfaceRaised)
                        ) {
                            BrowseSort.entries.forEach { choice ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            choice.label,
                                            color = if (choice == sort) Accent else Color.White
                                        )
                                    },
                                    onClick = {
                                        sortMenuOpen = false
                                        onSortChange(choice)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (listings.isEmpty()) {
            item { Text("The add-on catalog is unavailable. Check your connection and try again.", color = Muted, modifier = Modifier.padding(top = 30.dp)) }
        } else if (listings.none(ModuleListing::isVisibleInBrowse)) {
            item {
                Text(
                    "All available add-ons are already installed. You can find them in your library.",
                    color = Muted,
                    modifier = Modifier.padding(top = 30.dp)
                )
            }
        } else if (result.items.isEmpty()) {
            item(key = "catalog-no-results") {
                Column(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No add-ons match these filters", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text("Try another search, category, or filter.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {
                        onQueryChange("")
                        onFilterChange(BrowseFilter.ALL)
                        onCategoryChange(null)
                    }) {
                        Text("Clear filters", color = Accent)
                    }
                }
            }
        } else {
            if (recommended.isNotEmpty()) {
                item(key = "recommended-heading") {
                    CatalogSectionHeader("Recommended for your device", recommendedCount)
                }
                items(recommended, key = { "recommended:${it.catalog.slug}" }) { listing ->
                    ModuleListingCard(
                        screenCache = screenCache,
                        listing = listing,
                        modifier = Modifier.animateItem(),
                        onInstall = { onOpenDownload(listing) }
                    )
                }
            }
            if (remaining.isNotEmpty()) {
                item(key = "remaining-heading") {
                    CatalogSectionHeader(if (defaultSections) "All add-ons" else "Results", remainingCount)
                }
                items(remaining, key = { "remaining:${it.catalog.slug}" }) { listing ->
                    ModuleListingCard(
                        screenCache = screenCache,
                        listing = listing,
                        modifier = Modifier.animateItem(),
                        onInstall = { onOpenDownload(listing) }
                    )
                }
            }
            if (hasMore) {
                item(key = loadMoreKey) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = Accent,
                        trackColor = Hairline
                    )
                    Spacer(Modifier.height(5.dp))
                    Text("Loading more add-ons…", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CatalogFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = SurfaceDark,
            labelColor = Muted,
            selectedContainerColor = Accent.copy(alpha = 0.18f),
            selectedLabelColor = Accent
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Hairline,
            selectedBorderColor = Accent.copy(alpha = 0.6f)
        )
    )
}

@Composable
private fun CatalogSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title.uppercase(), color = Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(count.toString(), color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ModuleDownloadScreen(
    screenCache: LauncherScreenCache,
    listing: ModuleListing,
    update: ModuleUpdateUiState,
    gameInstall: GameInstallUiState,
    refreshing: Boolean,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onAcquireGame: () -> Unit,
    onOpenGameStore: () -> Unit,
    onRefresh: () -> Unit,
    onDone: () -> Unit
) {
    val game = listing.game
    val context = LocalContext.current.applicationContext
    var featuresExpanded by rememberSaveable(listing.catalog.slug) { mutableStateOf(false) }
    val featureKey = remember(listing.catalog.slug, listing.catalog.build, listing.catalog.features?.path) {
        listOf(
            listing.catalog.slug,
            listing.catalog.build.toString(),
            listing.catalog.features?.path.orEmpty()
        ).joinToString(":")
    }
    val featureEntry = screenCache.featureDetails[featureKey] ?: FeatureDetailsCacheEntry()
    LaunchedEffect(featuresExpanded, featureEntry.retryNonce, featureKey) {
        val latestEntry = screenCache.featureDetails[featureKey] ?: FeatureDetailsCacheEntry()
        if (!featuresExpanded || listing.catalog.features == null || latestEntry.groups != null || latestEntry.loading) {
            return@LaunchedEffect
        }
        screenCache.featureDetails[featureKey] = latestEntry.copy(loading = true, error = null)
        runCatching {
            withContext(Dispatchers.IO) { ModuleFeaturesClient(context).load(listing.catalog) }
        }.onSuccess {
            screenCache.featureDetails[featureKey] = FeatureDetailsCacheEntry(groups = it)
        }.onFailure {
            screenCache.featureDetails[featureKey] = latestEntry.copy(
                loading = false,
                error = "Feature details are unavailable. Check your connection and try again."
            )
        }
    }
    val bitmap = rememberListingBitmap(screenCache, listing, 160)
    val action = when (listing.status) {
        ModuleInstallStatus.UPDATE_AVAILABLE -> "Update"
        ModuleInstallStatus.BROKEN_INSTALL -> "Repair"
        else -> "Add"
    }
    val needsGame = listing.status == ModuleInstallStatus.GAME_NOT_INSTALLED ||
        listing.status == ModuleInstallStatus.UNSUPPORTED_VERSION
    val directSource = listing.catalog.installSource as? GameInstallSource.DirectDownload
    val moduleDownloadSize = moduleDownloadSizeLabel(listing.catalog, game?.abi)
    val playStoreStatus = listing.playStoreVersionStatus
    val playStoreSupported = listing.playStoreVersionSupported
    val playStoreUpdateInProgress = listing.playStoreUpdateInProgress
    val playStoreOutdatedWarning = listing.playStoreOutdatedWarning
    val totalDownloadSize = if (needsGame) directSource?.let { source ->
        val addOnSize = if (game != null) {
            listing.catalog.downloadSizeByAbi[game.abi]
        } else {
            listing.catalog.downloadSizeByAbi.values.distinct().singleOrNull()
        }
        addOnSize?.let { source.size + it }
    } else null
    val directUpdateIsNewer = game == null || directSource == null || directSource.versionCode > game.versionCode
    val busy = update.inProgress || update.cancelling || gameInstall.inProgress ||
        gameInstall.installing || gameInstall.cancelling
    val companionWindowActive = directSource != null && gameInstall.visible &&
        (gameInstall.packageName.isBlank() || gameInstall.packageName == listing.catalog.config.packageName)

    RefreshableScreen(
        refreshing = refreshing,
        onRefresh = onRefresh,
        enabled = !busy,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            Text(
                "‹  Browse add-ons",
                color = if (busy) Muted else Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(
                    enabled = !busy,
                    onClick = onBack
                ).padding(vertical = 8.dp, horizontal = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text("Add-on details", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("See what is included and required before adding this add-on to your library.", color = Muted)
        }

        if (listing.privateAccessProtected) {
            item {
                PrivateModuleAccessTimer(listing.privateAccessExpiresAtEpochSeconds)
            }
        } else if (listing.limitedAccess) {
            item {
                LimitedModuleAccessNotice()
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceDark).padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (bitmap != null) {
                        Image(bitmap, listing.catalog.config.title, Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)))
                    } else {
                        Box(
                            Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceRaised),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(listing.catalog.config.title.take(1).uppercase(), color = Accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(listing.catalog.config.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (game == null) "Original game not installed" else "Original game installed",
                            color = if (game == null) Muted else Accent,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                InformationGroupLabel("GAME COMPATIBILITY")
                Spacer(Modifier.height(8.dp))
                game?.let {
                    DownloadInfoRow("Installed version", it.versionName)
                    DownloadInfoRow("Architecture", architectureLabel(it.abi))
                }
                DownloadInfoRow(
                    "Compatible versions",
                    listing.catalog.config.supportedVersions.sorted().joinToString(", ")
                )
                if (game == null) {
                    DownloadInfoRow(
                        "Architecture",
                        architectureSummary(listing.catalog.config.supportedAbis)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                Spacer(Modifier.height(12.dp))
                val methodPresentation = launcherMethodPresentation(
                    listing.catalog.config.effectiveNonRootMethod,
                    BuildConfig.IS_ROOT_MODE
                )
                InformationGroupLabel(methodPresentation.setupLabel)
                Spacer(Modifier.height(8.dp))
                DownloadInfoRow(
                    if (listing.catalog.config.offersNonRootMethodChoice && !BuildConfig.IS_ROOT_MODE) {
                        "Recommended method"
                    } else {
                        methodPresentation.fieldLabel
                    },
                    methodPresentation.method.displayName
                )
                if (listing.catalog.config.offersNonRootMethodChoice && !BuildConfig.IS_ROOT_MODE) {
                    DownloadInfoRow(
                        "Available methods",
                        listing.catalog.config.nonRootMethods.joinToString(" + ") { it.displayName }
                    )
                }
                DownloadInfoRow(
                    "Google Play",
                    playStoreStatus?.let { status ->
                        buildString {
                            append(playStoreReleaseLabel(status))
                            if (playStoreUpdateInProgress) append(" · add-on update in progress")
                            else if (playStoreOutdatedWarning) append(" · add-on update needed")
                            if (status.stale) append(" · last saved check")
                        }
                    } ?: "Check temporarily unavailable"
                )
                LauncherMethodNotice(methodPresentation)
                if (playStoreStatus != null && playStoreSupported == false) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (playStoreUpdateInProgress) {
                            if (playStoreStatus?.latestVersion != null) {
                                "Google Play has a newer game version. This add-on is marked as being updated for it."
                            } else {
                                "Google Play has a newer release. This add-on is marked as being updated for it."
                            }
                        } else {
                            if (playStoreStatus?.latestVersion != null) {
                                "Google Play has a newer game version than this add-on supports. " +
                                    "Wait for an add-on update before using the newest game version."
                            } else {
                                "Google Play has published a newer release since this add-on was verified. " +
                                    "Wait for an add-on update before updating the game."
                            }
                        },
                        color = if (playStoreUpdateInProgress) AccentBlue else Danger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                Spacer(Modifier.height(12.dp))
                InformationGroupLabel("ADD-ON PACKAGE")
                Spacer(Modifier.height(8.dp))
                DownloadInfoRow("Add-on version", listing.catalog.version)
                DownloadInfoRow("Add-on size", moduleDownloadSize ?: "Size unavailable")
                if (needsGame) directSource?.let {
                    DownloadInfoRow("Original game download", formatDownloadSize(it.size))
                }
                totalDownloadSize?.let { DownloadInfoRow("Total download", formatDownloadSize(it)) }
                DownloadInfoRow("Package", listing.catalog.config.packageName)
                listing.catalog.features?.let { features ->
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                    Spacer(Modifier.height(12.dp))
                    ModuleFeaturesSection(
                        featureCount = features.count,
                        groups = featureEntry.groups,
                        expanded = featuresExpanded,
                        loading = featureEntry.loading,
                        error = featureEntry.error,
                        onToggle = { featuresExpanded = !featuresExpanded },
                        onRetry = {
                            screenCache.featureDetails[featureKey] = featureEntry.copy(
                                groups = null,
                                loading = false,
                                error = null,
                                retryNonce = featureEntry.retryNonce + 1
                            )
                        }
                    )
                }
            }
        }

        if (needsGame) item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceRaised).padding(18.dp)
            ) {
                Text("GET THE GAME", color = AccentBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                when {
                    companionWindowActive -> {
                        Text(
                            when {
                                gameInstall.completed -> "Game companion installed"
                                gameInstall.failed -> "Game companion needs attention"
                                gameInstall.cancelled -> "Game companion install paused"
                                else -> "Secure installer window active"
                            },
                            color = when {
                                gameInstall.failed -> Danger
                                gameInstall.completed -> Accent
                                else -> Color.White
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (update.inProgress) {
                                "The add-on update window has priority. Companion progress will return when that update finishes."
                            } else {
                                "Progress, cancellation, recovery, and diagnostics are available in the companion installer window."
                            },
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    gameInstall.headline != null -> {
                        Text(gameInstall.headline, color = Color.White, fontWeight = FontWeight.SemiBold)
                        gameInstall.detail?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                    }
                    else -> when (val source = listing.catalog.installSource) {
                        is GameInstallSource.PlayStore -> {
                            Text(
                                when {
                                    playStoreStatus == null -> "Google Play listing not detected"
                                    game == null -> "Available from Google Play"
                                    else -> "Update from Google Play"
                                },
                                color = if (playStoreStatus == null) Muted else Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (playStoreStatus == null) {
                                    "The launcher couldn't confirm this package on Google Play, so the store action is hidden."
                                } else {
                                    "Google Play handles the original game installation and updates."
                                },
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        is GameInstallSource.DirectDownload -> {
                            Text(if (game == null) "Download original game" else "Compatible game update", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Version ${source.version}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            if (!directUpdateIsNewer) {
                                Spacer(Modifier.height(6.dp))
                                Text("The available download is not newer than your installed game.", color = Danger, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (listing.configuredGameSourceAvailable) {
                    Button(
                        onClick = onAcquireGame,
                        enabled = !busy && !gameInstall.completed && directUpdateIsNewer,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Ink)
                    ) {
                        Text(
                            when {
                                companionWindowActive && (gameInstall.inProgress || gameInstall.installing) -> "Installer window active"
                                companionWindowActive && gameInstall.failed -> "Use installer window"
                                listing.catalog.installSource is GameInstallSource.PlayStore && game == null -> "Get from Google Play"
                                listing.catalog.installSource is GameInstallSource.PlayStore -> "Update in Google Play"
                                game == null -> "Download game"
                                else -> "Download game update"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (directSource != null && listing.playStoreListingDetected) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenGameStore,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                    ) {
                        Text("Open Google Play instead", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (!needsGame) item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceRaised).padding(18.dp)
            ) {
                Text("ADD ADD-ON", color = AccentBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                when {
                    listing.status == ModuleInstallStatus.UNSUPPORTED_DEVICE -> {
                        Text("This device isn't supported", color = Danger, fontWeight = FontWeight.SemiBold)
                        Text(
                            "This add-on requires ${architectureSummary(listing.catalog.config.supportedAbis)}, " +
                                "but this Android system cannot run it.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    listing.status == ModuleInstallStatus.UNSUPPORTED_ABI -> {
                        Text("This device version isn't supported", color = Danger, fontWeight = FontWeight.SemiBold)
                        Text("The installed game architecture does not match the available Jester Mods add-on.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    update.inProgress -> {
                        Text(update.headline ?: "Downloading add-on", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        DownloadProgressBar(
                            downloadedBytes = update.downloadedBytes,
                            totalBytes = update.totalBytes,
                            waiting = true,
                            color = AccentBlue
                        )
                    }
                    update.completed -> {
                        Text(update.headline ?: "Download complete", color = Color.White, fontWeight = FontWeight.SemiBold)
                        update.detail?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                    }
                    update.failed -> {
                        Text(update.headline ?: "Couldn't download add-on", color = Danger, fontWeight = FontWeight.SemiBold)
                        update.detail?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                    }
                    else -> {
                        Text("Ready to $action", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Jester Mods will download the add-on for this installed game.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (update.changelog.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    ModuleUpdateChangelog(update.changelog)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = if (update.completed) onDone else onInstall,
                    enabled = !busy && listing.status != ModuleInstallStatus.UNSUPPORTED_DEVICE &&
                        listing.status != ModuleInstallStatus.UNSUPPORTED_ABI,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Ink)
                ) {
                    Text(
                        when {
                            update.completed -> "Back to library"
                            update.failed -> "Try again"
                            listing.status == ModuleInstallStatus.UPDATE_AVAILABLE -> "Update add-on"
                            listing.status == ModuleInstallStatus.BROKEN_INSTALL -> "Repair add-on"
                            else -> "Add add-on"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ModuleFeaturesSection(
    featureCount: Int,
    groups: List<ModuleFeatureGroup>?,
    expanded: Boolean,
    loading: Boolean,
    error: String?,
    onToggle: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("WHAT'S INCLUDED", color = Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "$featureCount menu ${if (featureCount == 1) "feature" else "features"}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                if (expanded) "Hide" else "Show",
                color = Accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        if (expanded) {
            when {
                loading -> {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Accent, trackColor = Hairline)
                    Spacer(Modifier.height(8.dp))
                    Text("Loading feature details…", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                error != null -> {
                    Spacer(Modifier.height(14.dp))
                    Text(error, color = Danger, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetry, border = BorderStroke(1.dp, Hairline)) {
                        Text("Try again", color = Accent)
                    }
                }
                groups != null -> groups.forEachIndexed { groupIndex, group ->
                    Spacer(Modifier.height(if (groupIndex == 0) 14.dp else 16.dp))
                    Text(group.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    group.features.forEach { feature ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("•", color = Accent, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(9.dp))
                            Text(feature, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(14.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DownloadProgressBar(
    downloadedBytes: Long,
    totalBytes: Long,
    waiting: Boolean,
    color: Color
) {
    val transferred = downloadedBytes.coerceAtLeast(0L)
    val total = totalBytes.coerceAtLeast(0L)
    val waitingForData = waiting && transferred == 0L

    if (waitingForData || total == 0L) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .semantics { contentDescription = "Preparing download" },
            color = color,
            trackColor = Hairline
        )
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (transferred == 0L) "Preparing download…" else "Downloading…",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
            when {
                transferred > 0L -> Text(formatDownloadSize(transferred), color = color, style = MaterialTheme.typography.bodySmall)
                total > 0L -> Text(formatDownloadSize(total), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    val progress = (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Hairline)
            .semantics { contentDescription = "Download ${(progress * 100).toInt()} percent complete" }
    ) {
        Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(CircleShape).background(color))
    }
    Spacer(Modifier.height(9.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            "${formatDownloadSize(transferred)} of ${formatDownloadSize(total)}",
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "${(progress * 100).toInt()}%",
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDownloadSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun moduleDownloadSizeLabel(module: CatalogModule, abi: String?): String? {
    abi?.let { module.downloadSizeByAbi[it] }?.let { return formatDownloadSize(it) }
    val sizes = module.downloadSizeByAbi.values.distinct().sorted()
    return when (sizes.size) {
        0 -> null
        1 -> formatDownloadSize(sizes.single())
        else -> "${formatDownloadSize(sizes.first())}–${formatDownloadSize(sizes.last())}"
    }
}

@Composable
private fun PrivateModuleAccessTimer(
    expiresAtEpochSeconds: Long?,
    compact: Boolean = false
) {
    val expiresAtMillis = remember(expiresAtEpochSeconds) { expiresAtEpochSeconds?.times(1_000L) }
    val now by produceState(initialValue = System.currentTimeMillis(), expiresAtMillis) {
        val expiry = expiresAtMillis ?: return@produceState
        while (true) {
            value = System.currentTimeMillis()
            if (value >= expiry) break
            delay(minOf(1_000L, maxOf(250L, expiry - value)))
        }
    }
    val remainingText = expiresAtMillis?.let { formatRemainingAccessPrimary(it - now) }
        ?: "Private add-on"
    val expiryText = remember(expiresAtMillis, compact) {
        expiresAtMillis?.let { expiry ->
            if (compact) {
                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(expiry))
            } else {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(expiry))
            }
        }
    }
    val shape = RoundedCornerShape(if (compact) 16.dp else 26.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF2A2118),
                        Color(0xFF201D2E),
                        Color(0xFF171E29)
                    )
                )
            )
            .border(BorderStroke(1.dp, PrivateGold.copy(alpha = 0.38f)), shape)
            .semantics {
                contentDescription = if (expiryText != null) {
                    "Private access $remainingText, available until $expiryText"
                } else {
                    "Private add-on, approval is verified before use"
                }
            }
            .padding(
                horizontal = if (compact) 13.dp else 20.dp,
                vertical = if (compact) 11.dp else 18.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PRIVATE ACCESS",
                color = PrivateGold,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (expiryText != null) "VERIFIED" else "PROTECTED",
                color = PrivateViolet,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(if (compact) 5.dp else 10.dp))
        Text(
            remainingText,
            color = Color.White,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        if (!compact) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (expiryText != null) {
                    "Private add-on access is active for this device."
                } else {
                    "Access is verified before this add-on can be downloaded or launched."
                },
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(PrivateGold.copy(alpha = 0.16f)))
            Spacer(Modifier.height(11.dp))
        } else {
            Spacer(Modifier.height(2.dp))
        }
        if (compact) {
            Text(
                if (expiryText != null) "GRANTED UNTIL" else "ACCESS",
                color = Muted,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(1.dp))
            Text(
                expiryText ?: "Approval required",
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (expiryText != null) "AVAILABLE UNTIL" else "ACCESS",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    expiryText ?: "Approval required",
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LimitedModuleAccessNotice(compact: Boolean = false) {
    val shape = RoundedCornerShape(if (compact) 16.dp else 26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF2A2115),
                        Color(0xFF1D2631),
                        Color(0xFF171E29)
                    )
                )
            )
            .border(BorderStroke(1.dp, LimitedAmber.copy(alpha = 0.42f)), shape)
            .semantics {
                contentDescription = "Limited access public add-on. In-game eligibility requirements may apply."
            }
            .padding(
                horizontal = if (compact) 13.dp else 20.dp,
                vertical = if (compact) 11.dp else 18.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LIMITED ACCESS",
                color = LimitedAmber,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "PUBLIC",
                color = LimitedSky,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(if (compact) 5.dp else 10.dp))
        Text(
            if (compact) "In-game requirements may apply" else "Public to browse and install",
            color = Color.White,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
        Text(
            if (compact) {
                "Access to some or all features may depend on the game."
            } else {
                "This add-on is available to everyone, but access to some or all features may depend on requirements inside the game. " +
                    "Requirements vary; check the guidance shown inside the game."
            },
            color = Muted,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
        )
    }
}

private fun playStoreReleaseLabel(status: PlayStoreVersionStatus): String =
    status.latestVersion?.let { "v$it" } ?: when (status.updateAvailable) {
        true -> "New release detected"
        false -> "Release checked"
        null -> "Release checked · version not published"
    }

private fun playStoreReleaseReference(status: PlayStoreVersionStatus?): String =
    status?.latestVersion?.let { "Google Play v$it" } ?: "a new Google Play release"

@Composable
private fun ModuleListingCard(
    screenCache: LauncherScreenCache,
    listing: ModuleListing,
    modifier: Modifier = Modifier,
    onInstall: () -> Unit
) {
    val game = listing.game
    val bitmap = rememberListingBitmap(screenCache, listing, 128)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "browse-card-scale"
    )
    val cardColor by animateColorAsState(
        targetValue = if (game != null && game.moduleSupported) Color(0xFF153129) else SurfaceDark,
        animationSpec = tween(durationMillis = 220),
        label = "browse-card-color"
    )
    val statusText = when {
        listing.playStoreUpdateInProgress ->
            "Add-on update in progress for ${playStoreReleaseReference(listing.playStoreVersionStatus)}"
        listing.playStoreOutdatedWarning ->
            "Add-on update needed for ${playStoreReleaseReference(listing.playStoreVersionStatus)}"
        game == null -> "Install the original game to continue"
        !game.versionSupported -> "A compatible game version is needed"
        !game.abiSupported -> "This installed version isn't supported"
        else -> "Ready to add to your library"
    }
    val statusColor = when {
        listing.playStoreUpdateInProgress -> AccentBlue
        listing.playStoreOutdatedWarning -> Danger
        game != null && game.moduleSupported -> Accent
        else -> Muted
    }
    val actionLabel = when {
        game == null -> "Get game"
        !game.versionSupported -> "Get compatible game"
        !game.abiSupported -> "View details"
        else -> "Add to launcher"
    }
    Column(
        modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clip(RoundedCornerShape(22.dp))
            .background(cardColor)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onInstall
            )
            .animateContentSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (bitmap != null) Image(bitmap, listing.catalog.config.title, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
            else Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceRaised), contentAlignment = Alignment.Center) {
                Text(listing.catalog.config.title.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listing.catalog.config.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(statusText, color = statusColor, style = MaterialTheme.typography.bodySmall)
                Text("Add-on ${listing.catalog.version}", color = Muted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(7.dp))
                LauncherMethodBadge(
                    launcherMethodPresentation(
                        listing.catalog.config.effectiveNonRootMethod,
                        BuildConfig.IS_ROOT_MODE
                    )
                )
            }
        }
        if (listing.privateAccessProtected) {
            Spacer(Modifier.height(13.dp))
            PrivateModuleAccessTimer(
                expiresAtEpochSeconds = listing.privateAccessExpiresAtEpochSeconds,
                compact = true
            )
        } else if (listing.limitedAccess) {
            Spacer(Modifier.height(13.dp))
            LimitedModuleAccessNotice(compact = true)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onInstall,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)
        ) {
            Text(actionLabel, fontWeight = FontWeight.Bold)
        }
    }
}

private const val BROWSE_PAGE_SIZE = 20

@Composable
private fun rememberListingBitmap(
    screenCache: LauncherScreenCache,
    listing: ModuleListing,
    localSize: Int
): ImageBitmap? {
    val localIcon = listing.game?.icon
    if (localIcon != null) {
        val localKey = remember(localIcon, localSize) {
            "local:${listing.catalog.config.packageName}:$localSize:${System.identityHashCode(localIcon)}"
        }
        return rememberCachedBitmap(screenCache, localKey, ICON_BITMAP_DEFER_MS) {
            withContext(Dispatchers.Default) {
                runCatching {
                    val drawable = localIcon.constantState?.newDrawable()?.mutate() ?: localIcon
                    drawable.toBitmap(localSize, localSize).asImageBitmap()
                }.getOrNull()
            }
        }
    }

    val context = LocalContext.current.applicationContext
    val icon = listing.catalog.icon
    val remoteKey = remember(icon?.sha256, listing.catalog.build) {
        icon?.let { "remote:${listing.catalog.slug}:${listing.catalog.build}:${it.sha256}" }
    }
    return rememberCachedBitmap(screenCache, remoteKey) {
        withContext(Dispatchers.IO) {
            runCatching { CatalogIconClient(context).load(listing.catalog)?.asImageBitmap() }
                .getOrNull()
        }
    }
}

@Composable
private fun rememberLibraryBitmap(
    screenCache: LauncherScreenCache,
    entry: LibraryGame,
    localSize: Int
): ImageBitmap? {
    val localIcon = entry.game?.icon
    if (localIcon != null) {
        val localKey = remember(localIcon, localSize) {
            "local:${entry.packageName}:$localSize:${System.identityHashCode(localIcon)}"
        }
        return rememberCachedBitmap(screenCache, localKey, ICON_BITMAP_DEFER_MS) {
            withContext(Dispatchers.Default) {
                runCatching {
                    val drawable = localIcon.constantState?.newDrawable()?.mutate() ?: localIcon
                    drawable.toBitmap(localSize, localSize).asImageBitmap()
                }.getOrNull()
            }
        }
    }

    val context = LocalContext.current.applicationContext
    val catalog = entry.listing?.catalog
    val icon = catalog?.icon
    val remoteKey = remember(icon?.sha256, catalog?.build) {
        if (catalog == null || icon == null) null else "remote:${catalog.slug}:${catalog.build}:${icon.sha256}"
    }
    return rememberCachedBitmap(screenCache, remoteKey) {
        if (catalog == null) {
            null
        } else withContext(Dispatchers.IO) {
            runCatching { CatalogIconClient(context).load(catalog)?.asImageBitmap() }.getOrNull()
        }
    }
}

@Composable
private fun rememberCachedBitmap(
    screenCache: LauncherScreenCache,
    cacheKey: String?,
    deferMillis: Long = 0L,
    loader: suspend () -> ImageBitmap?
): ImageBitmap? {
    if (cacheKey == null) return null
    screenCache.iconBitmaps[cacheKey]?.let { return it.bitmap }
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = cacheKey
    ) {
        screenCache.iconBitmaps[cacheKey]?.let {
            value = it.bitmap
            return@produceState
        }
        if (deferMillis > 0L) delay(deferMillis)
        val loaded = loader()
        screenCache.iconBitmaps[cacheKey] = BitmapCacheEntry(loaded)
        value = loaded
    }
    return bitmap
}

private fun libraryStatusLabel(entry: LibraryGame): String {
    if (entry.status != LibraryGameStatus.RUNNING) {
        when (entry.launchAction) {
            LibraryLaunchAction.PATCH_AND_INSTALL -> return "Patched install required"
            LibraryLaunchAction.UPDATE_PATCHED_INSTALL -> return "Patched game update required"
            LibraryLaunchAction.RESTORE_OFFICIAL_FOR_SHELL -> return "Official game restore required"
            LibraryLaunchAction.SHELL_AND_INSTALL -> return "Exact-package shell required"
            LibraryLaunchAction.PLAY -> Unit
        }
    }
    if (entry.localTest) {
        return entry.game?.let {
            "Local test · v${it.versionName} · ${architectureLabel(it.abi)}"
        } ?: "Local test"
    }
    if (entry.status != LibraryGameStatus.RUNNING && entry.playStoreUpdateInProgress) {
        return "Add-on update in progress · ${playStoreReleaseReference(entry.playStoreVersionStatus)}"
    }
    if (entry.status != LibraryGameStatus.RUNNING && entry.playStoreOutdatedWarning) {
        return "Add-on outdated · ${playStoreReleaseReference(entry.playStoreVersionStatus)}"
    }
    return when (entry.status) {
        LibraryGameStatus.RUNNING -> "Running · tap to resume"
        LibraryGameStatus.READY -> entry.game?.let {
            "Ready · v${it.versionName} · ${architectureLabel(it.abi)}"
        } ?: "Ready"
        LibraryGameStatus.UPDATE_AVAILABLE -> "Update available"
        LibraryGameStatus.REPAIR_NEEDED -> "Repair needed"
        LibraryGameStatus.GAME_REQUIRED -> "Original game required"
        LibraryGameStatus.UNSUPPORTED_VERSION -> "Unsupported game version"
        LibraryGameStatus.UNSUPPORTED_ABI -> "Unsupported game architecture"
    }
}

@Composable
private fun LocalTestBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .semantics { contentDescription = "Local test add-on" }
            .clip(RoundedCornerShape(999.dp))
            .background(AccentBlue.copy(alpha = 0.18f))
            .border(BorderStroke(1.dp, AccentBlue.copy(alpha = 0.55f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "TEST",
            color = AccentBlue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LauncherMethodBadge(
    presentation: LauncherMethodPresentation,
    modifier: Modifier = Modifier
) {
    val color = if (presentation.method == NonRootMethod.INJECTION) Accent else AccentBlue
    Box(
        modifier = modifier
            .semantics { contentDescription = presentation.badgeDescription }
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.48f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            presentation.badgeLabel,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun LauncherMethodNotice(presentation: LauncherMethodPresentation) {
    val color = if (presentation.method == NonRootMethod.INJECTION) Accent else AccentBlue
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.09f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.25f)), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            presentation.explanationTitle,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            presentation.explanation,
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun InformationGroupLabel(label: String) {
    Text(
        label,
        color = AccentBlue,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableScreen(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val pullState = rememberPullToRefreshState()
    Box(
        modifier = modifier.pullToRefresh(
            isRefreshing = refreshing,
            state = pullState,
            enabled = enabled && !refreshing,
            onRefresh = onRefresh
        )
    ) {
        content(this)
        PullToRefreshDefaults.Indicator(
            state = pullState,
            isRefreshing = refreshing,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = SurfaceRaised,
            color = Accent
        )
    }
}

@Composable
private fun LibraryScreen(
    screenCache: LauncherScreenCache,
    games: List<LibraryGame>,
    loading: Boolean,
    refreshing: Boolean,
    query: String,
    managing: Boolean,
    onQueryChange: (String) -> Unit,
    onManagingChange: (Boolean) -> Unit,
    onOpenGame: (LibraryGame) -> Unit,
    onRemoveMultipleFromLibrary: (List<LibraryGame>) -> Unit,
    onRefresh: () -> Unit,
    onBrowse: () -> Unit
) {
    var selectedPackages by remember { mutableStateOf(emptySet<String>()) }
    var confirmSelectedRemoval by remember { mutableStateOf(false) }
    val filteredGames = remember(games, query) {
        val needle = query.trim().lowercase(Locale.US)
        if (needle.isEmpty()) {
            games
        } else {
            games.filter { game ->
                game.title.lowercase(Locale.US).contains(needle) ||
                    game.packageName.lowercase(Locale.US).contains(needle) ||
                    launcherMethodPresentation(
                        game.module.effectiveNonRootMethod,
                        BuildConfig.IS_ROOT_MODE
                    ).method.displayName.lowercase(Locale.US).contains(needle) ||
                    libraryStatusLabel(game).lowercase(Locale.US).contains(needle)
            }
        }
    }
    val selectedGames = remember(games, selectedPackages) {
        games.filter { it.packageName in selectedPackages }
    }
    val filteredPackageNames = remember(filteredGames) { filteredGames.mapTo(linkedSetOf()) { it.packageName } }
    val allFilteredSelected = filteredPackageNames.isNotEmpty() && filteredPackageNames.all(selectedPackages::contains)
    LaunchedEffect(managing, games) {
        selectedPackages = if (managing) {
            selectedPackages.intersect(games.mapTo(hashSetOf()) { it.packageName })
        } else {
            emptySet()
        }
        if (games.isEmpty() && managing) onManagingChange(false)
    }
    val showInitialLoading = rememberMinimumVisibleState(
        requestedVisible = loading && games.isEmpty(),
        minVisibleMillis = LOADING_STATE_MIN_VISIBLE_MS
    )
    RefreshableScreen(
        refreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Text(
                "Jester Mods",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Your games, ready when you are",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))

            if (showInitialLoading) {
                LoadingLibrary()
            } else if (games.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    item { EmptyLibrary() }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "YOUR ADD-ONS",
                            color = Accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${games.size} installed",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (managing) selectedPackages = emptySet()
                            onManagingChange(!managing)
                        },
                        border = BorderStroke(1.dp, if (managing) AccentBlue else Hairline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (managing) AccentBlue else Accent
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(if (managing) "Done" else "Manage", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Find add-ons") },
                    placeholder = { Text("Game name or package") },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            Text(
                                "Clear",
                                color = Accent,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onQueryChange("") }
                                    .padding(8.dp)
                            )
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Hairline,
                        focusedLabelColor = Accent,
                        unfocusedLabelColor = Muted,
                        focusedPlaceholderColor = Muted,
                        unfocusedPlaceholderColor = Muted,
                        cursorColor = Accent
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                if (managing) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(SurfaceRaised.copy(alpha = 0.82f))
                            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(18.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (selectedGames.isEmpty()) "Select add-ons" else "${selectedGames.size} selected",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    if (allFilteredSelected) "Unselect results" else "Select results",
                                    color = Accent,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = filteredPackageNames.isNotEmpty()) {
                                            selectedPackages = toggleVisibleLibrarySelection(
                                                selectedPackages,
                                                filteredPackageNames
                                            )
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Search to narrow the list, then select one or many add-ons. Games and save data stay installed.",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (selectedGames.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { confirmSelectedRemoval = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Ink),
                                contentPadding = PaddingValues(vertical = 11.dp)
                            ) {
                                Text(
                                    "Remove selected (${selectedGames.size})",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (filteredGames.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("No add-ons found", color = Color.White, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                Text("Try another game name or package.", color = Muted)
                            }
                        }
                    }
                    items(filteredGames, key = { it.packageName }) { game ->
                        CompactGameCard(
                            screenCache = screenCache,
                            game = game,
                            managing = managing,
                            selected = game.packageName in selectedPackages,
                            onOpenGame = onOpenGame,
                            onToggleSelection = {
                                selectedPackages = toggleLibrarySelection(
                                    selectedPackages,
                                    game.packageName
                                )
                            }
                        )
                    }
                }
            }
        }

        if (!managing) {
            SupportedGamesFab(
                onBrowse = onBrowse,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
            )
        }
    }

    if (confirmSelectedRemoval && selectedGames.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmSelectedRemoval = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = SurfaceRaised,
            titleContentColor = Color.White,
            textContentColor = Muted,
            title = {
                Text(
                    if (selectedGames.size == 1) "Remove ${selectedGames.first().title}?"
                    else "Remove ${selectedGames.size} add-ons?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val removesManagedCopies = !BuildConfig.IS_ROOT_MODE && selectedGames.any {
                    it.module.effectiveNonRootMethod == NonRootMethod.INJECTION
                }
                val keepsPatchedInstalls = !BuildConfig.IS_ROOT_MODE && selectedGames.any {
                    it.module.effectiveNonRootMethod == NonRootMethod.DIRECT_PATCH
                }
                val removesIdentityShells = !BuildConfig.IS_ROOT_MODE && selectedGames.any {
                    it.module.effectiveNonRootMethod == NonRootMethod.IDENTITY_SHELL
                }
                Text(
                    when {
                        removesIdentityShells && removesManagedCopies && keepsPatchedInstalls ->
                            "This removes the selected add-ons and asks Android to uninstall any exact-package shells. Managed BlackBox copies and shell data are erased. Directly patched games and original Play installations stay on this device."
                        removesIdentityShells && removesManagedCopies ->
                            "This removes the selected add-ons and asks Android to uninstall any exact-package shells. Managed BlackBox copies, shell identities, settings, and save data are erased. Original Play installations are not uninstalled."
                        removesIdentityShells && keepsPatchedInstalls ->
                            "This removes the selected add-ons and asks Android to uninstall any exact-package shells, including their local data. Directly patched games and original Play installations stay on this device."
                        removesIdentityShells ->
                            "This removes the selected add-ons and asks Android to uninstall any installed exact-package shells, including their local data. It does not automatically restore the original Play-signed games."
                        removesManagedCopies && keepsPatchedInstalls ->
                            "This removes the selected add-ons. Managed BlackBox game copies are uninstalled with their sandbox identity, settings, and save data. Directly patched Android games and original installations stay on this device."
                        removesManagedCopies ->
                            "This removes the selected add-ons and their managed BlackBox game copies, including sandbox identities, settings, and save data. Original Android games and their data will not be changed."
                        else ->
                            "This removes the selected launcher add-on support and any retryable patch files they own. Installed Android games and save data stay on this device."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val removal = selectedGames
                        confirmSelectedRemoval = false
                        selectedPackages = emptySet()
                        onRemoveMultipleFromLibrary(removal)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Ink)
                ) { Text(if (selectedGames.size == 1) "Remove add-on" else "Remove add-ons") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmSelectedRemoval = false },
                    border = BorderStroke(1.dp, Hairline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LoadingLibrary() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Loading your library", color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Checking your installed add-ons and supported games…",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.68f),
            color = Accent,
            trackColor = Hairline
        )
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No games ready yet", color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Browse available add-ons and add them to supported games. Library entries stay here even if the original game later needs to be reinstalled.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Pull down to refresh your library and the add-on catalog.",
            color = Accent,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SupportedGamesFab(
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var redirecting by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            redirecting -> 0.84f
            pressed -> 0.92f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "supported-games-fab-scale"
    )
    Box(
        modifier = modifier
            .size(58.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (redirecting) AccentBlue else Accent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !redirecting,
                onClickLabel = "Browse add-ons",
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    redirecting = true
                    scope.launch {
                        delay(170)
                        redirecting = false
                        delay(90)
                        onBrowse()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "+",
            color = Ink,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CompactGameCard(
    screenCache: LauncherScreenCache,
    game: LibraryGame,
    managing: Boolean,
    selected: Boolean,
    onOpenGame: (LibraryGame) -> Unit,
    onToggleSelection: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "game-card-scale"
    )
    val bitmap = rememberLibraryBitmap(screenCache, game, 128)
    val haptic = LocalHapticFeedback.current
    val addOnOutdated = game.playStoreOutdatedWarning
    val addOnUpdating = game.playStoreUpdateInProgress

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) AccentBlue.copy(alpha = 0.12f) else SurfaceDark.copy(alpha = 0.94f))
            .border(
                BorderStroke(1.dp, if (selected) AccentBlue.copy(alpha = 0.7f) else Color.Transparent),
                RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    if (managing) onToggleSelection() else onOpenGame(game)
                }
            )
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = game.title,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceRaised),
                    contentAlignment = Alignment.Center
                ) {
                    Text(game.title.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    game.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    libraryStatusLabel(game),
                    color = when {
                        addOnOutdated -> Danger
                        addOnUpdating -> AccentBlue
                        game.launchAction != LibraryLaunchAction.PLAY -> AccentBlue
                        game.status == LibraryGameStatus.RUNNING || game.status == LibraryGameStatus.READY -> Muted
                        game.status == LibraryGameStatus.UPDATE_AVAILABLE -> Accent
                        else -> Danger
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                LauncherMethodBadge(
                    launcherMethodPresentation(game.module.effectiveNonRootMethod, BuildConfig.IS_ROOT_MODE)
                )
            }
            Spacer(Modifier.width(10.dp))
            if (managing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (selected) AccentBlue else SurfaceRaised)
                        .border(
                            BorderStroke(1.dp, if (selected) AccentBlue else Muted.copy(alpha = 0.55f)),
                            CircleShape
                        )
                        .clickable(onClick = onToggleSelection)
                        .semantics {
                            contentDescription = if (selected) {
                                "Unselect ${game.title}"
                            } else {
                                "Select ${game.title}"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Text("✓", color = Ink, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "›",
                    color = Accent,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
        if (game.privateAccessProtected) {
            Spacer(Modifier.height(12.dp))
            PrivateModuleAccessTimer(
                expiresAtEpochSeconds = game.privateAccessExpiresAtEpochSeconds,
                compact = true
            )
        } else if (game.limitedAccess) {
            Spacer(Modifier.height(12.dp))
            LimitedModuleAccessNotice(compact = true)
        }
    }
}

@Composable
private fun ModuleScreen(
    screenCache: LauncherScreenCache,
    game: LibraryGame,
    update: ModuleUpdateUiState,
    gameDataReset: GameDataResetUiState,
    launch: LaunchUiState,
    refreshing: Boolean,
    onBack: () -> Unit,
    onUpdate: () -> Unit,
    onVerify: (String) -> Unit,
    onLaunch: () -> Unit,
    onSelectNonRootMethod: (NonRootMethod) -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onClearGameData: () -> Unit,
    onRefresh: () -> Unit,
    onResolve: (() -> Unit)?
) {
    val installedGame = game.game
    val bitmap = rememberLibraryBitmap(screenCache, game, 192)
    val haptic = LocalHapticFeedback.current
    var confirmRemoval by remember(game.packageName) { mutableStateOf(false) }
    var confirmDataReset by remember(game.packageName) { mutableStateOf(false) }

    RefreshableScreen(
        refreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        item {
            Text(
                "‹  Library",
                color = Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = game.title,
                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(20.dp))
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            game.title,
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (game.localTest) {
                            Spacer(Modifier.width(10.dp))
                            LocalTestBadge()
                        }
                    }
                    Text(
                        installedGame?.let { "Version ${it.versionName}" } ?: "Original game not installed",
                        color = if (installedGame == null) Danger else Muted
                    )
                    installedGame?.let {
                        Text("Architecture ${architectureLabel(it.abi)}", color = Muted)
                    }
                }
            }
        }
        if (game.privateAccessProtected) {
            item {
                PrivateModuleAccessTimer(game.privateAccessExpiresAtEpochSeconds)
            }
        } else if (game.limitedAccess) {
            item {
                LimitedModuleAccessNotice()
            }
        }
        item {
            ModuleCompatibilityCard(
                game = game,
                selectionEnabled = !update.inProgress && !launch.inProgress,
                onSelectNonRootMethod = onSelectNonRootMethod
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDark)
                    .padding(18.dp)
            ) {
                Text("UPDATES", color = Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Keep this add-on ready for the game.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Check for the latest version before you play.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (update.headline != null || update.inProgress) {
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                    Spacer(Modifier.height(16.dp))
                    if (update.inProgress) {
                        DownloadProgressBar(
                            downloadedBytes = update.downloadedBytes,
                            totalBytes = update.totalBytes,
                            waiting = true,
                            color = Accent
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                            update.headline?.let {
                                Text(
                                    it,
                                    color = if (update.failed) Danger else Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            update.detail?.let {
                                Spacer(Modifier.height(3.dp))
                                Text(it, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (update.totalBytes > 0L) {
                    Spacer(Modifier.height(12.dp))
                    DownloadInfoRow("Download size", formatDownloadSize(update.totalBytes))
                }
                if (update.changelog.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    ModuleUpdateChangelog(update.changelog)
                }
                Spacer(Modifier.height(18.dp))
                update.verificationUrl?.let { verificationUrl ->
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onVerify(verificationUrl)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text("Continue", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        if (installedGame == null) onResolve?.invoke() else onUpdate()
                    },
                    enabled = !update.inProgress && (installedGame != null || onResolve != null),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text(
                        when {
                            installedGame == null -> "Install original game"
                            game.status == LibraryGameStatus.REPAIR_NEEDED -> "Repair add-on"
                            game.status == LibraryGameStatus.UPDATE_AVAILABLE -> "Download update"
                            update.updateAvailable -> "Download update"
                            update.failed -> "Try again"
                            update.completed -> "Check again"
                            else -> "Check for updates"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceRaised.copy(alpha = 0.82f))
                    .padding(18.dp)
            ) {
                Text(
                    when (game.launchAction) {
                        LibraryLaunchAction.PLAY -> "PLAY"
                        LibraryLaunchAction.RESTORE_OFFICIAL_FOR_SHELL -> "SAFE MIGRATION"
                        LibraryLaunchAction.SHELL_AND_INSTALL -> "SHELL & INSTALL"
                        else -> "PATCH & INSTALL"
                    },
                    color = AccentBlue,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        installedGame == null ->
                            "Install the original game to make this Library entry ready to play."
                        !installedGame.versionSupported && !installedGame.abiSupported ->
                            "This game version and architecture are not supported yet."
                        !installedGame.versionSupported ->
                            "This game version is not supported yet."
                        !installedGame.abiSupported ->
                            "This game architecture is not supported by this add-on."
                        game.status == LibraryGameStatus.REPAIR_NEEDED ->
                            "Repair the add-on before opening this game."
                        game.launchAction == LibraryLaunchAction.PATCH_AND_INSTALL ->
                            "Create and install the patched game first. Android will ask before removing the current Play-signed installation."
                        game.launchAction == LibraryLaunchAction.UPDATE_PATCHED_INSTALL ->
                            "The embedded add-on changed. Update the patched game in place before playing."
                        game.launchAction == LibraryLaunchAction.RESTORE_OFFICIAL_FOR_SHELL ->
                            "A previous patched installation was detected. Restore the official game before creating its exact-package shell."
                        game.launchAction == LibraryLaunchAction.SHELL_AND_INSTALL ->
                            "Preserve the untouched game and install its game-branded exact-package shell."
                        else ->
                            "Jester Mods will open the game with your features ready."
                    },
                    color = if (game.status in setOf(
                            LibraryGameStatus.RUNNING,
                            LibraryGameStatus.READY,
                            LibraryGameStatus.UPDATE_AVAILABLE
                        )) Muted else Danger,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (launch.headline != null || launch.inProgress) {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                    Spacer(Modifier.height(14.dp))
                    if (launch.inProgress) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = AccentBlue,
                            trackColor = Hairline
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                        launch.headline?.let {
                            Text(
                                it,
                                color = if (launch.failed) Danger else Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        launch.detail?.let {
                            Spacer(Modifier.height(3.dp))
                            Text(it, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (game.status in setOf(
                                LibraryGameStatus.RUNNING,
                                LibraryGameStatus.READY,
                                LibraryGameStatus.UPDATE_AVAILABLE
                            )) onLaunch() else onResolve?.invoke()
                    },
                    enabled = !update.inProgress && !launch.inProgress && (
                        (installedGame?.moduleSupported == true && game.installedComplete) || onResolve != null
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Ink)
                ) {
                    Text(
                        libraryPrimaryActionLabel(game.status, game.launchAction, launch),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { confirmRemoval = true },
                enabled = !update.inProgress && !launch.inProgress && !gameDataReset.inProgress,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text("Remove from Library", color = Danger, fontWeight = FontWeight.SemiBold)
            }
        }
        val canClearGameData = if (BuildConfig.IS_ROOT_MODE) {
            game.game != null
        } else {
            game.module.effectiveNonRootMethod == NonRootMethod.INJECTION
        }
        if (canClearGameData) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { confirmDataReset = true },
                        enabled = !update.inProgress && !launch.inProgress && !gameDataReset.inProgress,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        border = BorderStroke(1.dp, Hairline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                    ) {
                        Text(
                            if (gameDataReset.inProgress) "Clearing data..." else "Clear data",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (gameDataReset.headline != null) {
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceRaised.copy(alpha = 0.72f))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .animateContentSize()
                        ) {
                            Text(
                                gameDataReset.headline,
                                color = if (gameDataReset.failed) Danger else Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            gameDataReset.detail?.let { detail ->
                                Spacer(Modifier.height(3.dp))
                                Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = SurfaceRaised,
            titleContentColor = Color.White,
            textContentColor = Muted,
            title = { Text("Remove ${game.title}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    when (launcherMethodPresentation(
                        game.module.effectiveNonRootMethod,
                        BuildConfig.IS_ROOT_MODE
                    ).method) {
                        NonRootMethod.DIRECT_PATCH ->
                            "This removes the add-on and its retryable patch files from the launcher. The currently installed patched game and its data stay on Android; it is not uninstalled or restored to the Google Play version."
                        NonRootMethod.IDENTITY_SHELL ->
                            "If the exact-package compatibility shell is installed, Android will ask you to uninstall it, including its local app data. Jester Mods removes the add-on and preserved shell files only after that succeeds. The original Play-signed game is not restored automatically."
                        NonRootMethod.INJECTION -> if (!BuildConfig.IS_ROOT_MODE) {
                            "This removes the add-on and the managed game copy from BlackBox, including its sandbox identity, settings, and save data. The original Android game and its data will not be changed."
                        } else {
                            "This removes only its add-on from the Library. The original Android game and its data will not be changed."
                        }
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemoval = false
                        onRemoveFromLibrary()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Ink)
                ) { Text("Remove") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmRemoval = false },
                    border = BorderStroke(1.dp, Hairline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                ) { Text("Cancel") }
            }
        )
    }

    if (confirmDataReset) {
        AlertDialog(
            onDismissRequest = { confirmDataReset = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = SurfaceRaised,
            titleContentColor = Color.White,
            textContentColor = Muted,
            title = { Text("Clear ${game.title} data?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (BuildConfig.IS_ROOT_MODE) {
                        "This permanently clears the installed game's local identity, sign-in state, settings, saves, cache, downloads, and OBB data. The installed game and its add-on stay in your Library. The next Play starts with fresh game data."
                    } else {
                        "This fully removes the managed BlackBox installation, including its virtual identity, sign-in state, settings, saves, cache, downloads, and OBB data. The add-on stays in your Library and the original Android game is untouched. The next Play starts with a fresh managed installation."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDataReset = false
                        onClearGameData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Ink)
                ) { Text("Clear data") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { confirmDataReset = false },
                    border = BorderStroke(1.dp, Hairline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModuleCompatibilityCard(
    game: LibraryGame,
    selectionEnabled: Boolean,
    onSelectNonRootMethod: (NonRootMethod) -> Unit
) {
    val installedGame = game.game
    val methodPresentation = launcherMethodPresentation(
        game.module.effectiveNonRootMethod,
        BuildConfig.IS_ROOT_MODE
    )
    val ready = installedGame?.moduleSupported == true && game.installedComplete &&
        game.launchAction == LibraryLaunchAction.PLAY
    val playStoreUpdateInProgress = game.playStoreUpdateInProgress
    val playStoreOutdatedWarning = game.playStoreOutdatedWarning
    val attention = !ready || playStoreOutdatedWarning
    val statusColor = when {
        game.launchAction != LibraryLaunchAction.PLAY -> AccentBlue
        playStoreUpdateInProgress -> AccentBlue
        attention -> Danger
        else -> Accent
    }
    val headline = when {
        installedGame == null -> "Original game needed"
        !installedGame.versionSupported && !installedGame.abiSupported -> "Version and architecture not supported"
        !installedGame.versionSupported -> "Game version not supported"
        !installedGame.abiSupported -> "Architecture not supported"
        !game.installedComplete -> "Add-on needs repair"
        game.launchAction == LibraryLaunchAction.PATCH_AND_INSTALL -> "Patched install required"
        game.launchAction == LibraryLaunchAction.UPDATE_PATCHED_INSTALL -> "Patched game update required"
        game.launchAction == LibraryLaunchAction.RESTORE_OFFICIAL_FOR_SHELL -> "Official game restore required"
        game.launchAction == LibraryLaunchAction.SHELL_AND_INSTALL -> "Exact-package shell required"
        playStoreUpdateInProgress -> "Add-on update in progress"
        playStoreOutdatedWarning -> "Add-on update advised"
        game.running -> "Running and ready"
        else -> "Ready to play"
    }
    val detail = when {
        installedGame == null ->
            "Install the original game first. The launcher will re-check compatibility automatically."
        !installedGame.versionSupported ->
            "Keep the game on a supported version before opening it from the Library."
        !installedGame.abiSupported ->
            "This add-on only supports the listed device architecture."
        !game.installedComplete ->
            "Repair the add-on package so the runtime menu can load cleanly."
        game.launchAction == LibraryLaunchAction.PATCH_AND_INSTALL ->
            "The add-on is downloaded. Build and install the patched game before the Play button becomes available."
        game.launchAction == LibraryLaunchAction.UPDATE_PATCHED_INSTALL ->
            "Install the refreshed patch in place so the game contains this add-on version."
        game.launchAction == LibraryLaunchAction.RESTORE_OFFICIAL_FOR_SHELL ->
            "Jester Mods detected its previous patch and will keep it out of the new shell. Restore the official Google Play game first."
        game.launchAction == LibraryLaunchAction.SHELL_AND_INSTALL ->
            "Jester Mods will preserve the untouched game package and create a shell with its exact name and icon."
        playStoreUpdateInProgress ->
            "The maintainer is updating this add-on for the newer game version shown by Google Play."
        playStoreOutdatedWarning ->
            "Google Play is showing a newer game version than this add-on currently supports."
        else ->
            "Requirements are satisfied. The in-game menu will show a compact runtime status only."
    }
    val supportedVersions = game.module.supportedVersions.sorted().joinToString(", ")
        .ifBlank { "Declared by add-on" }
    val supportedArchitectures = architectureSummary(game.module.supportedAbis)
        .ifBlank { "Declared by add-on" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceRaised.copy(alpha = 0.88f))
            .border(BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Text("COMPATIBILITY", color = Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(10.dp))
            Text(
                headline,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Spacer(Modifier.height(12.dp))
        DownloadInfoRow(
            "Installed game",
            installedGame?.let { "v${it.versionName} · ${architectureLabel(it.abi)}" } ?: "Not installed"
        )
        DownloadInfoRow("Supported versions", supportedVersions)
        DownloadInfoRow("Supported architecture", supportedArchitectures)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Spacer(Modifier.height(10.dp))
        InformationGroupLabel(methodPresentation.setupLabel)
        Spacer(Modifier.height(6.dp))
        DownloadInfoRow(
            if (game.module.offersNonRootMethodChoice && !BuildConfig.IS_ROOT_MODE) {
                "Selected method"
            } else {
                methodPresentation.fieldLabel
            },
            methodPresentation.method.displayName
        )
        if (game.listing?.catalog?.installSource is GameInstallSource.PlayStore) {
            DownloadInfoRow(
                "Google Play",
                game.playStoreVersionStatus?.let {
                    buildString {
                        append(playStoreReleaseLabel(it))
                        if (playStoreUpdateInProgress) append(" · add-on update in progress")
                        else if (playStoreOutdatedWarning) append(" · update add-on first")
                        if (it.stale) append(" · saved check")
                    }
                } ?: "Check temporarily unavailable"
            )
        }
        if (game.module.offersNonRootMethodChoice && !BuildConfig.IS_ROOT_MODE) {
            Spacer(Modifier.height(10.dp))
            CompatibilityMethodSelector(
                methods = game.module.nonRootMethods,
                selected = game.module.effectiveNonRootMethod,
                recommended = game.module.nonRootMethod,
                installed = game.installedNonRootMethod,
                enabled = selectionEnabled && game.installedNonRootMethod == null,
                onSelect = onSelectNonRootMethod
            )
        } else {
            LauncherMethodNotice(methodPresentation)
        }
    }
}

@Composable
private fun CompatibilityMethodSelector(
    methods: List<NonRootMethod>,
    selected: NonRootMethod,
    recommended: NonRootMethod,
    installed: NonRootMethod?,
    enabled: Boolean,
    onSelect: (NonRootMethod) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AccentBlue.copy(alpha = 0.08f))
            .border(
                BorderStroke(1.dp, AccentBlue.copy(alpha = 0.24f)),
                RoundedCornerShape(20.dp)
            )
            .padding(14.dp)
    ) {
        Text(
            "DEVICE SETUP",
            color = AccentBlue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Choose what works on this device",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Device security and game kernels behave differently. Start with the recommended route, " +
                "then use the alternative if setup is blocked or the game closes during startup.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        methods.forEachIndexed { index, method ->
            val chosen = method == selected
            val color = if (chosen) Accent else AccentBlue
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (chosen) color.copy(alpha = 0.12f) else SurfaceDark.copy(alpha = 0.68f))
                    .border(
                        BorderStroke(1.dp, if (chosen) color.copy(alpha = 0.62f) else Hairline),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(enabled = enabled && !chosen) { onSelect(method) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            method.displayName,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when (method) {
                                NonRootMethod.IDENTITY_SHELL ->
                                    "Keeps the untouched game as a private payload and runs it through its exact package identity."
                                NonRootMethod.DIRECT_PATCH ->
                                    "Builds a signed replacement with the add-on embedded for devices that reject the shell route."
                                NonRootMethod.INJECTION ->
                                    "Runs the original game inside the managed non-root environment."
                            },
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            installed == method -> "ACTIVE"
                            chosen && method == recommended -> "RECOMMENDED"
                            chosen -> "SELECTED"
                            method == recommended -> "RECOMMENDED"
                            else -> "ALTERNATIVE"
                        },
                        color = color,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (index != methods.lastIndex) Spacer(Modifier.height(9.dp))
        }
        if (installed != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "The installed ${installed.displayName.lowercase()} is locked in for safety. " +
                    "Restore the original game before choosing another route.",
                color = Accent,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ModuleUpdateChangelog(entries: List<ModuleChangelogEntry>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(SurfaceRaised.copy(alpha = 0.78f)).padding(14.dp)
    ) {
        Text("CHANGES SINCE YOUR VERSION", color = AccentBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                Spacer(Modifier.height(11.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                Spacer(Modifier.height(11.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }
            Text("${entry.version} · Build ${entry.build}", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(entry.updateType.replaceFirstChar { it.uppercase() }, color = Accent, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            ChangelogRundown(
                entry.notes,
                fallback = "Add-on maintenance and compatibility improvements."
            )
        }
    }
}
