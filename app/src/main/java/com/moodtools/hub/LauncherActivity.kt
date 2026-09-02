package com.moodtools.hub

import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.os.PersistableBundle
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.moodtools.hub.discovery.GameScanner
import com.moodtools.hub.modules.GameHubScreen
import com.moodtools.hub.modules.AccountIdentityUiState
import com.moodtools.hub.modules.ChangelogUiState
import com.moodtools.hub.modules.DeviceArchitectureGuard
import com.moodtools.hub.modules.GameInstallSource
import com.moodtools.hub.modules.GameInstallStage
import com.moodtools.hub.modules.GameInstallUiState
import com.moodtools.hub.modules.GameDataResetUiState
import com.moodtools.hub.modules.InstalledGame
import com.moodtools.hub.modules.LibraryGame
import com.moodtools.hub.modules.LibraryGameStatus
import com.moodtools.hub.modules.LibraryLaunchAction
import com.moodtools.hub.modules.LaunchUiState
import com.moodtools.hub.modules.PackageSetupUiState
import com.moodtools.hub.modules.LauncherUpdateUiState
import com.moodtools.hub.modules.InstalledModuleUpdatesUiState
import com.moodtools.hub.modules.InstalledModuleUpdateItemStatus
import com.moodtools.hub.modules.InstalledModuleUpdateItemUiState
import com.moodtools.hub.modules.ModuleRepository
import com.moodtools.hub.modules.ModuleListing
import com.moodtools.hub.modules.ModuleUpdateUiState
import com.moodtools.hub.modules.NonRootMethod
import com.moodtools.hub.modules.PlayStoreVersionStatus
import com.moodtools.hub.modules.SecureTransferStage
import com.moodtools.hub.modules.installedModuleUpdates
import com.moodtools.hub.modules.DirectPatchPromptUiState
import com.moodtools.hub.modules.EmbeddedPrivateModuleInstaller
import com.moodtools.hub.modules.architectureLabel
import com.moodtools.hub.modules.sortLibraryGames
import com.moodtools.hub.modules.mergeCatalogAndLocalModuleConfigs
import com.moodtools.hub.networking.LauncherAccessManager
import com.moodtools.hub.networking.LauncherPrivateAccessResult
import com.moodtools.hub.networking.GameInstallClient
import com.moodtools.hub.networking.GameInstallCancelledException
import com.moodtools.hub.networking.GameInstallEvents
import com.moodtools.hub.networking.GameInstallResult
import com.moodtools.hub.networking.LauncherRelease
import com.moodtools.hub.networking.LauncherChangelogEntry
import com.moodtools.hub.networking.LauncherUpdateClient
import com.moodtools.hub.networking.ModuleChangelogClient
import com.moodtools.hub.networking.ModuleCatalogClient
import com.moodtools.hub.networking.ModuleDownloadAuthorizationExpired
import com.moodtools.hub.networking.LauncherServiceException
import com.moodtools.hub.networking.PlayStoreVersionClient
import com.moodtools.hub.networking.ReleaseVerificationRequired
import com.moodtools.hub.networking.SmartStorageManager
import com.moodtools.hub.networking.StandaloneUpdateStage
import com.moodtools.hub.networking.UpdateClient
import com.moodtools.hub.networking.UpdateRequest
import com.moodtools.hub.security.RuntimeSecurityGuard
import java.io.File
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.Calendar
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect

private enum class ProtectedActionBoundary(val description: String) {
    GAME_LAUNCH("start another game"),
    MODULE_DOWNLOAD("download an add-on")
}

private class NewSessionAccessBoundaryException : IllegalStateException(
    "Active launcher access is required"
)

private enum class PackageReplacementPhase {
    IDLE,
    PREPARING,
    WAITING_FOR_UNINSTALL,
    COMMITTING_INSTALL,
    WAITING_FOR_INSTALL_RESULT
}

private class PendingGameInstaller(
    val packageName: String,
    val versionCode: Long,
    @Volatile var sessionId: Int? = null
)

private class GameInstallCancellation {
    @Volatile var cancelled: Boolean = false
}

private class SecureTransferCancellation {
    @Volatile var cancelled: Boolean = false
}

internal fun shouldUninstallIdentityShellBeforeRemoving(
    isRootMode: Boolean,
    method: NonRootMethod,
    installedIdentityShell: Boolean
): Boolean = !isRootMode && method == NonRootMethod.IDENTITY_SHELL && installedIdentityShell

internal fun newestPlayStoreStatus(
    cached: PlayStoreVersionStatus?,
    received: PlayStoreVersionStatus
): PlayStoreVersionStatus = when {
    cached == null -> received
    received.checkedAtEpochSeconds > cached.checkedAtEpochSeconds -> received
    received.checkedAtEpochSeconds < cached.checkedAtEpochSeconds -> cached
    (received.listingUpdatedAtEpochSeconds ?: 0L) >
        (cached.listingUpdatedAtEpochSeconds ?: 0L) -> received
    received.latestVersion != null && cached.latestVersion == null -> received
    !received.stale && cached.stale -> received
    received.updateAvailable != cached.updateAvailable -> received
    else -> cached
}

private enum class InstallerPermissionTarget {
    LAUNCHER_UPDATE,
    ORIGINAL_GAME,
    PACKAGE_REPLACEMENT
}

class LauncherActivity : ComponentActivity() {
    private val viewModel by viewModels<LauncherViewModel>()
    private var waitingForLauncherUnlock = false
    private var pendingInstallerPermissionTarget: InstallerPermissionTarget? = null
    private var pendingGameInstall: ModuleListing? = null
    private var pendingPackageReplacement: PackageReplacementRequest? = null
    private var packageReplacementPhase = PackageReplacementPhase.IDLE
    private var packageReplacementPreparation: Job? = null
    private var packageReplacementReconciliation: Job? = null
    private var packageReplacementLeftLauncher = false
    private var pendingLegacyPatchMigration: LibraryGame? = null
    private var legacyPatchMigrationReconciliation: Job? = null
    private val identityShellRemovalQueue = ArrayDeque<LibraryGame>()
    private var pendingIdentityShellRemoval: LibraryGame? = null
    private var identityShellRemovalReconciliation: Job? = null
    private val packageUninstaller = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val request = pendingPackageReplacement ?: return@registerForActivityResult
        reconcilePackageUninstall(request, result.resultCode)
    }
    private val legacyPatchUninstaller = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val entry = pendingLegacyPatchMigration ?: return@registerForActivityResult
        reconcileLegacyPatchUninstall(entry, result.resultCode)
    }
    private val identityShellUninstaller = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val entry = pendingIdentityShellRemoval ?: return@registerForActivityResult
        reconcileIdentityShellRemoval(entry, result.resultCode)
    }
    private val unknownSourcesSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        reconcileInstallerPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        viewModel.start(
            initialLink = intent?.data,
            debugAccessBypass = BuildConfig.DEBUG && intent?.getBooleanExtra(DEBUG_ACCESS_BYPASS_EXTRA, false) == true,
            debugLauncherUpdateTest = BuildConfig.DEBUG && intent?.getBooleanExtra(DEBUG_LAUNCHER_UPDATE_TEST_EXTRA, false) == true,
            debugModuleUpdatesTest = BuildConfig.DEBUG && intent?.getBooleanExtra(DEBUG_MODULE_UPDATES_TEST_EXTRA, false) == true
        )
        setContent {
            val startup by viewModel.startupState.collectAsStateWithLifecycle()
            val entered by viewModel.launcherEntered.collectAsStateWithLifecycle()
            if (shouldEnterLauncherLibrary(startup, entered)) {
                GameHubScreen(
                    state = viewModel.libraryGames,
                    libraryLoading = viewModel.libraryLoading,
                    catalogRefreshing = viewModel.catalogRefreshing,
                    availableModules = viewModel.availableModules,
                    browserOpen = viewModel.moduleBrowserOpen,
                    downloadListing = viewModel.downloadListing,
                    selectedGame = viewModel.selectedLibraryGame,
                    updateState = viewModel.updateState,
                    gameDataResetState = viewModel.gameDataResetState,
                    gameInstallState = viewModel.gameInstallState,
                    launcherUpdateState = viewModel.launcherUpdateState,
                    installedModuleUpdatesState = viewModel.installedModuleUpdatesState,
                    changelogState = viewModel.changelogState,
                    accountIdentityState = viewModel.accountIdentityState,
                    launchState = viewModel.launchState,
                    packageSetupState = viewModel.packageSetupState,
                    directPatchPromptState = viewModel.directPatchPromptState,
                    onOpenGame = viewModel::openLibraryGame,
                    onBack = viewModel::closeGame,
                    onUpdate = viewModel::updateLibraryGame,
                    onVerify = ::openTrustedWebPage,
                    onLaunch = ::launchLibraryGame,
                    onConfirmDirectPatch = ::confirmPackageSetupPrompt,
                    onDismissDirectPatch = ::dismissPackageSetupPrompt,
                    onCancelPackageSetup = ::cancelActivePackageSetup,
                    onDismissPackageSetup = viewModel::dismissPackageSetup,
                    onRetryPackageSetup = ::retryPackageSetup,
                    onRemoveFromLibrary = ::requestRemoveFromLibrary,
                    onClearGameData = viewModel::clearGameData,
                    onRemoveMultipleFromLibrary = ::requestRemoveMultipleFromLibrary,
                    onRefreshCatalog = viewModel::refreshCatalogAndLibrary,
                    onBrowse = viewModel::openModuleBrowser,
                    onCloseBrowser = viewModel::closeModuleBrowser,
                    onOpenDownload = viewModel::openModuleDownload,
                    onCloseDownload = viewModel::closeModuleDownload,
                    onInstall = viewModel::installModule,
                    onAcquireGame = ::acquireGame,
                    onCancelGameInstall = viewModel::cancelGameInstall,
                    onDismissGameInstall = viewModel::dismissGameInstall,
                    onOpenGameStore = ::openGameStore,
                    onOpenLauncherUpdate = viewModel::openLauncherUpdate,
                    onCloseLauncherUpdate = viewModel::closeLauncherUpdate,
                    onInstallLauncherUpdate = ::downloadOrInstallLauncherUpdate,
                    onCancelLauncherUpdate = viewModel::cancelLauncherUpdate,
                    onCancelModuleTransfer = viewModel::cancelModuleTransfer,
                    onDismissModuleTransfer = viewModel::dismissModuleTransfer,
                    onDismissInstalledModuleUpdates = viewModel::dismissInstalledModuleUpdates,
                    onCancelInstalledModuleUpdates = viewModel::cancelInstalledModuleUpdates,
                    onReviewInstalledModuleUpdate = viewModel::reviewInstalledModuleUpdate,
                    onUpdateInstalledModule = viewModel::updateInstalledModuleFromPrompt,
                    onUpdateAllInstalledModules = viewModel::updateAllInstalledModules,
                    onOpenChangelog = viewModel::openChangelog,
                    onCloseChangelog = viewModel::closeChangelog,
                    onOpenAccountIdentity = viewModel::openAccountIdentity,
                    onCloseAccountIdentity = viewModel::closeAccountIdentity,
                    onRetryChangelog = viewModel::refreshChangelog,
                    onOpenModuleChangelog = viewModel::openModuleChangelog,
                    onCloseModuleChangelog = viewModel::closeModuleChangelog
                )
            } else {
                if (startup is LauncherStartupState.RootDenied) {
                    LaunchedEffect(startup) {
                        delay(ROOT_DENIED_EXIT_DELAY_MS)
                        finishAffinity()
                    }
                }
                LauncherGateScreen(
                    state = startup,
                    onUnlock = ::beginLauncherUnlock,
                    onRetry = viewModel::retryAccessRecovery,
                    onEnter = viewModel::enterLauncher,
                    onCopySupportCode = ::copySupportCode,
                    onExit = { finishAffinity() }
                )
            }
        }
        lifecycleScope.launch {
            PackageReplacementInstallEvents.results.collect { result ->
                if (result.packageName != pendingPackageReplacement?.packageName) return@collect
                if (result.successful) {
                    // PackageInstaller can report success just before PackageManager exposes the
                    // replacement application's metadata. Keep the setup pending until the exact
                    // shell/patch identity is observable, otherwise the Library can immediately
                    // rescan the stale original package and leave its action on "Create & install".
                    schedulePackageReplacementReconciliation(installerReportedSuccess = true)
                } else {
                    finishPackageReplacementWithFailure(
                        result.message?.takeIf { it.isNotBlank() }
                            ?: "Android did not install the patched ${pendingPackageReplacement?.title ?: "game"} package."
                    )
                }
            }
        }
    }

    private fun copySupportCode() {
        runCatching {
            val code = viewModel.supportCode()
            val clip = ClipData.newPlainText("Jester Mods support code", code)
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(clip)
            Toast.makeText(this, "Support code copied", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Support code is unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data?.let(viewModel::isLauncherUnlockLink) == true) {
            waitingForLauncherUnlock = false
        }
        viewModel.handleDeepLink(intent.data)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onHostResumed(
            refreshGames = pendingPackageReplacement == null && pendingIdentityShellRemoval == null &&
                pendingLegacyPatchMigration == null
        )
        if (waitingForLauncherUnlock) {
            waitingForLauncherUnlock = false
            viewModel.onUnlockBrowserReturnedWithoutCallback()
        }
        schedulePackageReplacementReconciliation()
    }

    override fun onPause() {
        if (packageReplacementPhase == PackageReplacementPhase.COMMITTING_INSTALL ||
            packageReplacementPhase == PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT) {
            packageReplacementLeftLauncher = true
        }
        viewModel.onHostPaused()
        super.onPause()
    }

    override fun onDestroy() {
        val launcherTaskRemoved = isFinishing && !isChangingConfigurations
        if (launcherTaskRemoved) {
            ExecutionModeLaunchBridge.onLauncherTaskRemoved(applicationContext)
        }
        super.onDestroy()
    }

    private fun launchLibraryGame(entry: LibraryGame) {
        val game = entry.game ?: return
        if (!ExecutionModeLaunchBridge.requiresPackageReplacement(this, game)) {
            viewModel.launchLibraryGame(entry)
            return
        }
        if (pendingPackageReplacement != null || pendingLegacyPatchMigration != null) return
        if (ExecutionModeLaunchBridge.requiresOfficialRestoreBeforeIdentityShell(this, game)) {
            viewModel.authorizePackageReplacement(game) {
                if (pendingPackageReplacement == null && pendingLegacyPatchMigration == null) {
                    pendingLegacyPatchMigration = entry
                    viewModel.showLegacyPatchMigration(entry.title)
                }
            }
            return
        }
        viewModel.authorizePackageReplacement(game) {
            preparePackageReplacement(entry, game)
        }
    }

    private fun requestRemoveFromLibrary(entry: LibraryGame) {
        requestRemoveLibraryEntries(listOf(entry))
    }

    private fun requestRemoveMultipleFromLibrary(entries: List<LibraryGame>) {
        requestRemoveLibraryEntries(entries)
    }

    private fun requestRemoveLibraryEntries(entries: List<LibraryGame>) {
        val targets = entries.distinctBy(LibraryGame::packageName)
        if (targets.isEmpty()) return
        if (pendingIdentityShellRemoval != null || identityShellRemovalQueue.isNotEmpty()) {
            Toast.makeText(this, "Finish the current shell removal first", Toast.LENGTH_SHORT).show()
            return
        }

        val removeDirectly = mutableListOf<LibraryGame>()
        targets.forEach { entry ->
            val installedIdentityShell = ExecutionModeLaunchBridge.isInstalledIdentityShell(
                applicationContext,
                entry
            )
            if (shouldUninstallIdentityShellBeforeRemoving(
                    BuildConfig.IS_ROOT_MODE,
                    entry.module.nonRootMethod,
                    installedIdentityShell
                )) {
                identityShellRemovalQueue.addLast(entry)
            } else {
                removeDirectly += entry
            }
        }
        if (removeDirectly.isNotEmpty()) {
            viewModel.removeMultipleFromLibrary(removeDirectly)
        }
        launchNextIdentityShellRemoval()
    }

    private fun launchNextIdentityShellRemoval() {
        if (pendingIdentityShellRemoval != null) return
        val entry = identityShellRemovalQueue.pollFirst() ?: return
        pendingIdentityShellRemoval = entry
        val uninstall = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${entry.packageName}")
        ).putExtra(Intent.EXTRA_RETURN_RESULT, true)
        runCatching { identityShellUninstaller.launch(uninstall) }
            .onFailure {
                finishIdentityShellRemoval(
                    entry,
                    removed = false,
                    message = "Android could not open the ${entry.title} shell uninstall screen."
                )
            }
    }

    private fun reconcileIdentityShellRemoval(entry: LibraryGame, resultCode: Int) {
        if (pendingIdentityShellRemoval !== entry) return
        if (!ExecutionModeLaunchBridge.isInstalledIdentityShell(applicationContext, entry)) {
            finishIdentityShellRemoval(entry, removed = true)
            return
        }
        if (resultCode != RESULT_OK) {
            finishIdentityShellRemoval(
                entry,
                removed = false,
                message = "${entry.title} shell was not uninstalled, so it remains in your Library."
            )
            return
        }

        identityShellRemovalReconciliation?.cancel()
        identityShellRemovalReconciliation = lifecycleScope.launch {
            val deadline = SystemClock.elapsedRealtime() + PACKAGE_UNINSTALL_RECONCILE_TIMEOUT_MS
            while (pendingIdentityShellRemoval === entry) {
                val stillInstalled = withContext(Dispatchers.IO) {
                    ExecutionModeLaunchBridge.isInstalledIdentityShell(applicationContext, entry)
                }
                if (!stillInstalled) {
                    identityShellRemovalReconciliation = null
                    finishIdentityShellRemoval(entry, removed = true)
                    return@launch
                }
                if (SystemClock.elapsedRealtime() >= deadline) break
                delay(PACKAGE_REPLACEMENT_POLL_INTERVAL_MS)
            }
            if (pendingIdentityShellRemoval === entry) {
                identityShellRemovalReconciliation = null
                finishIdentityShellRemoval(
                    entry,
                    removed = false,
                    message = "Android confirmed the uninstall, but the ${entry.title} shell still appears installed."
                )
            }
        }
    }

    private fun finishIdentityShellRemoval(
        entry: LibraryGame,
        removed: Boolean,
        message: String? = null
    ) {
        if (pendingIdentityShellRemoval !== entry) return
        identityShellRemovalReconciliation = null
        pendingIdentityShellRemoval = null
        if (removed) {
            viewModel.removeFromLibrary(entry)
        } else if (!message.isNullOrBlank()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        launchNextIdentityShellRemoval()
    }

    private fun confirmPackageSetupPrompt() {
        if (pendingLegacyPatchMigration != null) {
            beginLegacyPatchRestore()
        } else {
            confirmPackageReplacementPrompt()
        }
    }

    private fun dismissPackageSetupPrompt() {
        if (pendingLegacyPatchMigration != null) {
            val title = pendingLegacyPatchMigration?.title ?: "Game"
            pendingLegacyPatchMigration = null
            legacyPatchMigrationReconciliation?.cancel()
            legacyPatchMigrationReconciliation = null
            viewModel.hideDirectPatchPrompt()
            viewModel.onLegacyPatchMigrationCancelled(title)
            viewModel.dismissPackageSetup()
        } else {
            cancelPackageReplacement()
            viewModel.dismissPackageSetup()
        }
    }

    private fun cancelActivePackageSetup() {
        val preparing = packageReplacementPreparation
        if (preparing?.isActive == true && pendingPackageReplacement == null) {
            val title = viewModel.packageSetupState.value.title.ifBlank { "Game" }
            preparing.cancel()
            packageReplacementPreparation = null
            packageReplacementPhase = PackageReplacementPhase.IDLE
            viewModel.hideDirectPatchPrompt()
            viewModel.onPackageReplacementCancelled(title)
            return
        }
        if (pendingPackageReplacement != null) {
            cancelPackageReplacement()
        }
    }

    private fun retryPackageSetup() {
        if (packageReplacementPreparation?.isActive == true || pendingPackageReplacement != null ||
            pendingLegacyPatchMigration != null) return
        val entry = viewModel.selectedLibraryGame.value ?: return
        viewModel.dismissPackageSetup()
        launchLibraryGame(entry)
    }

    private fun beginLegacyPatchRestore() {
        val entry = pendingLegacyPatchMigration ?: return
        viewModel.hideDirectPatchPrompt()
        viewModel.onLegacyPatchMigrationStarted(entry.title)
        val uninstall = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${entry.packageName}")
        ).putExtra(Intent.EXTRA_RETURN_RESULT, true)
        runCatching { legacyPatchUninstaller.launch(uninstall) }
            .onFailure {
                finishLegacyPatchRestore(
                    entry,
                    removed = false,
                    message = "Android could not open the ${entry.title} uninstall screen."
                )
            }
    }

    private fun reconcileLegacyPatchUninstall(entry: LibraryGame, resultCode: Int) {
        if (pendingLegacyPatchMigration !== entry) return
        if (!isPackageInstalled(entry.packageName)) {
            finishLegacyPatchRestore(entry, removed = true)
            return
        }
        if (resultCode != RESULT_OK) {
            finishLegacyPatchRestore(
                entry,
                removed = false,
                message = "The previous ${entry.title} patch was not removed. Nothing was changed."
            )
            return
        }

        legacyPatchMigrationReconciliation?.cancel()
        viewModel.onLegacyPatchMigrationReconciling(entry.title)
        legacyPatchMigrationReconciliation = lifecycleScope.launch {
            val deadline = SystemClock.elapsedRealtime() + PACKAGE_UNINSTALL_RECONCILE_TIMEOUT_MS
            while (pendingLegacyPatchMigration === entry) {
                val stillInstalled = withContext(Dispatchers.IO) {
                    isPackageInstalled(entry.packageName)
                }
                if (!stillInstalled) {
                    legacyPatchMigrationReconciliation = null
                    finishLegacyPatchRestore(entry, removed = true)
                    return@launch
                }
                if (SystemClock.elapsedRealtime() >= deadline) break
                delay(PACKAGE_REPLACEMENT_POLL_INTERVAL_MS)
            }
            if (pendingLegacyPatchMigration === entry) {
                legacyPatchMigrationReconciliation = null
                finishLegacyPatchRestore(
                    entry,
                    removed = false,
                    message = "Android confirmed the uninstall, but the previous patch still appears installed."
                )
            }
        }
    }

    private fun finishLegacyPatchRestore(
        entry: LibraryGame,
        removed: Boolean,
        message: String? = null
    ) {
        if (pendingLegacyPatchMigration !== entry) return
        legacyPatchMigrationReconciliation?.cancel()
        legacyPatchMigrationReconciliation = null
        pendingLegacyPatchMigration = null
        if (!removed) {
            viewModel.onLegacyPatchMigrationFailed(
                entry.title,
                message ?: "The previous patch could not be removed."
            )
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                ExecutionModeLaunchBridge.discardPreparedIdentityShell(
                    applicationContext,
                    entry.packageName
                )
            }.onFailure { error ->
                android.util.Log.w(
                    "JesterMoodsMigration",
                    "Could not clear stale ${entry.packageName} shell material; preparation will replace it.",
                    error
                )
            }
            withContext(Dispatchers.Main) {
                viewModel.onLegacyPatchRemoved(entry)
            }
        }
    }

    private fun preparePackageReplacement(entry: LibraryGame, game: InstalledGame) {
        if (pendingPackageReplacement != null) return
        packageReplacementPhase = PackageReplacementPhase.PREPARING
        val updatingPatchedInstall = entry.launchAction == LibraryLaunchAction.UPDATE_PATCHED_INSTALL
        viewModel.onPackageReplacementProgress(
            "Preparing ${game.module.title}",
            if (game.module.nonRootMethod == com.moodtools.hub.modules.NonRootMethod.IDENTITY_SHELL) {
                "Preserving the untouched game and creating its exact-package compatibility shell."
            } else if (updatingPatchedInstall) {
                "Creating an in-place patch update that keeps the installed game's local data."
            } else {
                "Creating the patched non-root package before Android removes anything."
            }
        )
        packageReplacementPreparation?.cancel()
        packageReplacementPreparation = lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                ExecutionModeLaunchBridge.preparePackageReplacement(
                    applicationContext,
                    game,
                    viewModel::onPackageReplacementProgress
                )
            }.onSuccess { request ->
                packageReplacementPreparation = null
                withContext(Dispatchers.Main) {
                    pendingPackageReplacement = request
                    packageReplacementPhase = PackageReplacementPhase.IDLE
                    requestPackageReplacementPermissionOrConfirm(request)
                }
            }.onFailure { error ->
                packageReplacementPreparation = null
                if (error is CancellationException) return@onFailure
                android.util.Log.e("JesterMoodsPackageSetup", "Could not prepare replacement package", error)
                packageReplacementPhase = PackageReplacementPhase.IDLE
                viewModel.onPackageReplacementFailed(
                    error.message?.take(220)?.takeIf { it.isNotBlank() }
                                ?: "The ${game.module.title} package could not be prepared.",
                    game.module.title
                )
            }
        }
    }

    private fun requestPackageReplacementPermissionOrConfirm(request: PackageReplacementRequest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()) {
            viewModel.onPackageReplacementProgress(
                "Waiting for Android",
                "Allow Jester Mods to install unknown apps, then return to review the ${request.title} setup."
            )
            openInstallerPermissionSettings(InstallerPermissionTarget.PACKAGE_REPLACEMENT) {
                finishPackageReplacementWithFailure(
                    "Android could not open the unknown-app installation setting."
                )
            }
            return
        }
        showPackageReplacementWarning(request)
    }

    private fun showPackageReplacementWarning(request: PackageReplacementRequest) {
        if (isFinishing || isDestroyed || pendingPackageReplacement !== request) return
        viewModel.showDirectPatchPrompt(request.title, request.requiresUninstall, request.kind)
    }

    private fun confirmPackageReplacementPrompt() {
        val request = pendingPackageReplacement ?: return
        viewModel.hideDirectPatchPrompt()
        beginPackageReplacement(request)
    }

    private fun beginPackageReplacement(request: PackageReplacementRequest) {
        if (pendingPackageReplacement !== request) return
        viewModel.onPackageReplacementProgress(
            "Waiting for Android",
            if (request.requiresUninstall) {
                "Confirm the ${request.title} uninstall. Installation follows automatically."
            } else {
                "Confirm the patched ${request.title} update."
            }
        )
        if (!request.requiresUninstall) {
            installPackageReplacement(request)
            return
        }
        if (!isPackageInstalled(request.packageName)) {
            installPackageReplacement(request)
            return
        }
        val uninstall = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${request.packageName}")
        ).putExtra(Intent.EXTRA_RETURN_RESULT, true)
        packageReplacementPhase = PackageReplacementPhase.WAITING_FOR_UNINSTALL
        runCatching { packageUninstaller.launch(uninstall) }
            .onFailure {
                finishPackageReplacementWithFailure("Android could not open the ${request.title} uninstall screen.")
            }
    }

    private fun installPackageReplacement(request: PackageReplacementRequest) {
        if (pendingPackageReplacement !== request ||
            packageReplacementPhase == PackageReplacementPhase.COMMITTING_INSTALL ||
            packageReplacementPhase == PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT) return
        packageReplacementPhase = PackageReplacementPhase.COMMITTING_INSTALL
        packageReplacementLeftLauncher = false
        viewModel.onPackageReplacementProgress(
            if (request.kind == PackageReplacementKind.IDENTITY_SHELL) {
                "Installing ${request.title} shell"
            } else {
                "Installing patched ${request.title}"
            },
            "Preparing Android's installation screen. You can keep using the launcher while it opens."
        )
        // APK verification and copying a split set into Package Installer can take seconds.
        // Never perform that disk/crypto work on the main thread after the uninstall result.
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                ExecutionModeLaunchBridge.installPackageReplacement(applicationContext, request)
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    if (pendingPackageReplacement === request) {
                        packageReplacementPhase = PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT
                        viewModel.onPackageReplacementProgress(
                            "Waiting for Android",
                            if (request.kind == PackageReplacementKind.IDENTITY_SHELL) {
                                "Confirm the game-branded shell installation. The launcher will detect when it finishes."
                            } else {
                                "Confirm the patched game installation. The launcher will detect when it finishes."
                            }
                        )
                        schedulePackageReplacementReconciliation()
                    }
                }
            }.onFailure { error ->
                android.util.Log.e("JesterMoodsPackageSetup", "Could not start package install", error)
                withContext(Dispatchers.Main) {
                    if (pendingPackageReplacement === request) {
                        finishPackageReplacementWithFailure(
                            error.message?.take(220)?.takeIf { it.isNotBlank() }
                                ?: "Android could not start the ${request.title} installation."
                        )
                    }
                }
            }
        }
    }

    private fun reconcilePackageUninstall(request: PackageReplacementRequest, resultCode: Int) {
        if (pendingPackageReplacement !== request ||
            packageReplacementPhase != PackageReplacementPhase.WAITING_FOR_UNINSTALL) return
        if (!isPackageInstalled(request.packageName)) {
            installPackageReplacement(request)
            return
        }
        if (resultCode != RESULT_OK) {
            finishPackageReplacementWithFailure("${request.title} was not uninstalled. Nothing was changed.")
            return
        }

        packageReplacementReconciliation?.cancel()
        viewModel.onPackageReplacementProgress(
            "Finishing ${request.title} uninstall",
            "Android confirmed the uninstall. Waiting for its package records to finish updating."
        )
        packageReplacementReconciliation = lifecycleScope.launch {
            val deadline = SystemClock.elapsedRealtime() + PACKAGE_UNINSTALL_RECONCILE_TIMEOUT_MS
            while (pendingPackageReplacement === request &&
                packageReplacementPhase == PackageReplacementPhase.WAITING_FOR_UNINSTALL) {
                val stillInstalled = withContext(Dispatchers.IO) {
                    isPackageInstalled(request.packageName)
                }
                if (!stillInstalled) {
                    packageReplacementReconciliation = null
                    installPackageReplacement(request)
                    return@launch
                }
                if (SystemClock.elapsedRealtime() >= deadline) break
                delay(PACKAGE_REPLACEMENT_POLL_INTERVAL_MS)
            }
            if (pendingPackageReplacement === request &&
                packageReplacementPhase == PackageReplacementPhase.WAITING_FOR_UNINSTALL) {
                finishPackageReplacementWithFailure(
                    "Android confirmed the uninstall, but the game still appears installed. Nothing else was changed."
                )
            }
        }
    }

    private fun schedulePackageReplacementReconciliation(installerReportedSuccess: Boolean = false) {
        val request = pendingPackageReplacement ?: return
        if (packageReplacementPhase != PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT) return
        packageReplacementReconciliation?.cancel()
        packageReplacementReconciliation = lifecycleScope.launch {
            delay(
                if (installerReportedSuccess) {
                    0L
                } else if (packageReplacementLeftLauncher) {
                    PACKAGE_REPLACEMENT_RETURN_RECONCILE_DELAY_MS
                } else {
                    PACKAGE_REPLACEMENT_INSTALLER_OPEN_TIMEOUT_MS
                }
            )
            if (pendingPackageReplacement !== request ||
                packageReplacementPhase != PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT ||
                (!installerReportedSuccess &&
                    !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) return@launch
            val deadline = SystemClock.elapsedRealtime() + PACKAGE_REPLACEMENT_INSTALL_RECONCILE_TIMEOUT_MS
            while (pendingPackageReplacement === request &&
                packageReplacementPhase == PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT &&
                (installerReportedSuccess ||
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
                val installed = withContext(Dispatchers.IO) {
                    ExecutionModeLaunchBridge.isPackageReplacementInstalled(applicationContext, request)
                }
                if (installed) {
                    finishPackageReplacementSuccessfully()
                    return@launch
                }
                if (SystemClock.elapsedRealtime() >= deadline) break
                delay(PACKAGE_REPLACEMENT_POLL_INTERVAL_MS)
            }
            if (pendingPackageReplacement === request &&
                packageReplacementPhase == PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT &&
                (installerReportedSuccess ||
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))) {
                finishPackageReplacementWithFailure(
                    if (installerReportedSuccess) {
                        "Android installed the package, but its verified package identity was not ready in time. Return to the Library and refresh before retrying."
                    } else {
                        "The Android installation was cancelled or could not be confirmed. The prepared package is still available."
                    }
                )
            }
        }
    }

    private fun finishPackageReplacementSuccessfully() {
        val request = pendingPackageReplacement ?: return
        packageReplacementReconciliation?.cancel()
        packageReplacementReconciliation = null
        packageReplacementPreparation = null
        pendingPackageReplacement = null
        packageReplacementPhase = PackageReplacementPhase.IDLE
        packageReplacementLeftLauncher = false
        viewModel.hideDirectPatchPrompt()
        viewModel.onPackageReplacementInstalled(request)
    }

    private fun finishPackageReplacementWithFailure(detail: String) {
        val title = pendingPackageReplacement?.title ?: "Game"
        packageReplacementReconciliation?.cancel()
        packageReplacementReconciliation = null
        packageReplacementPreparation?.cancel()
        packageReplacementPreparation = null
        pendingPackageReplacement = null
        packageReplacementPhase = PackageReplacementPhase.IDLE
        packageReplacementLeftLauncher = false
        viewModel.hideDirectPatchPrompt()
        viewModel.onPackageReplacementFailed(detail, title)
    }

    private fun cancelPackageReplacement() {
        val title = pendingPackageReplacement?.title
            ?: viewModel.packageSetupState.value.title.ifBlank { "Game" }
        packageReplacementReconciliation?.cancel()
        packageReplacementReconciliation = null
        packageReplacementPreparation?.cancel()
        packageReplacementPreparation = null
        pendingPackageReplacement = null
        packageReplacementPhase = PackageReplacementPhase.IDLE
        packageReplacementLeftLauncher = false
        viewModel.hideDirectPatchPrompt()
        viewModel.onPackageReplacementCancelled(title)
    }

    private fun isPackageInstalled(targetPackage: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(targetPackage, 0)
    }.isSuccess

    private fun downloadOrInstallLauncherUpdate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()) {
            openInstallerPermissionSettings(InstallerPermissionTarget.LAUNCHER_UPDATE) {
                viewModel.onLauncherInstallPermissionDenied()
            }
            return
        }
        viewModel.downloadAndInstallLauncherUpdate()
    }

    private fun acquireGame(listing: ModuleListing) {
        when (val source = listing.catalog.installSource) {
            is GameInstallSource.PlayStore -> openGameStore(listing)
            is GameInstallSource.DirectDownload -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !packageManager.canRequestPackageInstalls()) {
                    pendingGameInstall = listing
                    openInstallerPermissionSettings(InstallerPermissionTarget.ORIGINAL_GAME) {
                        pendingGameInstall = null
                        viewModel.onGameInstallPermissionDenied()
                    }
                } else {
                    viewModel.downloadAndInstallGame(listing)
                }
            }
        }
    }

    private fun openInstallerPermissionSettings(
        target: InstallerPermissionTarget,
        onLaunchFailure: () -> Unit
    ) {
        if (pendingInstallerPermissionTarget != null) return
        pendingInstallerPermissionTarget = target
        runCatching {
            unknownSourcesSettings.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
        }.onFailure {
            pendingInstallerPermissionTarget = null
            onLaunchFailure()
        }
    }

    private fun reconcileInstallerPermission() {
        val target = pendingInstallerPermissionTarget ?: return
        pendingInstallerPermissionTarget = null
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            packageManager.canRequestPackageInstalls()
        when (target) {
            InstallerPermissionTarget.LAUNCHER_UPDATE -> {
                if (granted) viewModel.downloadAndInstallLauncherUpdate()
                else viewModel.onLauncherInstallPermissionDenied()
            }
            InstallerPermissionTarget.ORIGINAL_GAME -> {
                val listing = pendingGameInstall
                pendingGameInstall = null
                if (listing != null && granted) viewModel.downloadAndInstallGame(listing)
                else if (listing != null) viewModel.onGameInstallPermissionDenied()
            }
            InstallerPermissionTarget.PACKAGE_REPLACEMENT -> {
                val request = pendingPackageReplacement
                if (request != null && granted) {
                    showPackageReplacementWarning(request)
                } else if (request != null) {
                    finishPackageReplacementWithFailure(
                        "Allow Jester Mods to install unknown apps before patching ${request.title}."
                    )
                }
            }
        }
    }

    private fun openGameStore(listing: ModuleListing) {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${listing.catalog.config.packageName}")
        ).addCategory(Intent.CATEGORY_BROWSABLE)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(listing.catalog.playStoreUrl))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val target = if (market.resolveActivity(packageManager) != null) market else web
        if (target.resolveActivity(packageManager) != null) {
            viewModel.onGameStoreOpened()
            startActivity(target)
        } else {
            viewModel.onGameStoreUnavailable()
        }
    }

    private fun openTrustedWebPage(address: String) {
        val uri = Uri.parse(address)
        require(uri.scheme == "https" && uri.host.equals("jester.moodtools.workers.dev", ignoreCase = true)) {
            "Refusing to open an untrusted web address"
        }
        val browser = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)

        // The launcher also owns the release callback deep link. Prefer the user's
        // normal web browser for the verification page so the HTTPS route cannot
        // accidentally loop straight back into Jester Mods. Some Android builds return
        // the system resolver (package "android") from resolveActivity; only pin the
        // request when that package is also a genuine generic HTTPS handler.
        val genericWeb = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val browserPackages = packageManager
            .queryIntentActivities(genericWeb, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .filterNot { it == packageName }
            .distinct()
        val defaultPackage = packageManager
            .resolveActivity(genericWeb, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
        val browserPackage = defaultPackage?.takeIf(browserPackages::contains)
        if (browserPackage != null) {
            startActivity(browser.setPackage(browserPackage))
            return
        }

        // Package visibility and heavily customized OEM resolvers can still hide every
        // browser candidate. Let Android choose, while explicitly excluding this activity.
        val chooser = Intent.createChooser(browser, "Open in browser").putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(this, LauncherActivity::class.java))
        )
        startActivity(chooser)
    }

    private fun beginLauncherUnlock() {
        runCatching {
            val address = viewModel.beginLauncherUnlock()
            waitingForLauncherUnlock = true
            openTrustedWebPage(address)
        }
            .onFailure {
                android.util.Log.e("JesterMoodsAccess", "Could not open the unlock web route", it)
                waitingForLauncherUnlock = false
                viewModel.onUnlockBrowserLaunchFailed()
            }
    }

    companion object {
        private const val ROOT_DENIED_EXIT_DELAY_MS = 6_000L
        private const val PACKAGE_REPLACEMENT_RETURN_RECONCILE_DELAY_MS = 1_200L
        private const val PACKAGE_REPLACEMENT_INSTALLER_OPEN_TIMEOUT_MS = 12_000L
        private const val PACKAGE_REPLACEMENT_POLL_INTERVAL_MS = 400L
        private const val PACKAGE_UNINSTALL_RECONCILE_TIMEOUT_MS = 6_000L
        private const val PACKAGE_REPLACEMENT_INSTALL_RECONCILE_TIMEOUT_MS = 12_000L
        private const val DEBUG_ACCESS_BYPASS_EXTRA = "moodtools.test_bypass_unlock"
        private const val DEBUG_LAUNCHER_UPDATE_TEST_EXTRA = "moodtools.test_launcher_update"
        private const val DEBUG_MODULE_UPDATES_TEST_EXTRA = "moodtools.test_module_updates"
    }

}

class LauncherViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val _startupState = MutableStateFlow<LauncherStartupState>(LauncherStartupState.CheckingRoot)
    val startupState: StateFlow<LauncherStartupState> = _startupState

    private val _launcherEntered = MutableStateFlow(false)
    val launcherEntered: StateFlow<Boolean> = _launcherEntered

    private val _games = MutableStateFlow<List<InstalledGame>>(emptyList())
    val games: StateFlow<List<InstalledGame>> = _games

    private val _libraryGames = MutableStateFlow<List<LibraryGame>>(emptyList())
    val libraryGames: StateFlow<List<LibraryGame>> = _libraryGames

    private val _libraryLoading = MutableStateFlow(true)
    val libraryLoading: StateFlow<Boolean> = _libraryLoading

    private val _catalogRefreshing = MutableStateFlow(false)
    val catalogRefreshing: StateFlow<Boolean> = _catalogRefreshing

    private val _selectedGame = MutableStateFlow<InstalledGame?>(null)
    val selectedGame: StateFlow<InstalledGame?> = _selectedGame

    private val _selectedLibraryGame = MutableStateFlow<LibraryGame?>(null)
    val selectedLibraryGame: StateFlow<LibraryGame?> = _selectedLibraryGame

    private val _updateState = MutableStateFlow(ModuleUpdateUiState())
    val updateState: StateFlow<ModuleUpdateUiState> = _updateState

    private val _gameDataResetState = MutableStateFlow(GameDataResetUiState())
    val gameDataResetState: StateFlow<GameDataResetUiState> = _gameDataResetState

    private val _gameInstallState = MutableStateFlow(GameInstallUiState())
    val gameInstallState: StateFlow<GameInstallUiState> = _gameInstallState

    private val _launcherUpdateState = MutableStateFlow(LauncherUpdateUiState())
    val launcherUpdateState: StateFlow<LauncherUpdateUiState> = _launcherUpdateState

    private val _installedModuleUpdatesState = MutableStateFlow(InstalledModuleUpdatesUiState())
    val installedModuleUpdatesState: StateFlow<InstalledModuleUpdatesUiState> = _installedModuleUpdatesState

    private val _changelogState = MutableStateFlow(ChangelogUiState())
    val changelogState: StateFlow<ChangelogUiState> = _changelogState
    private val _accountIdentityState = MutableStateFlow(AccountIdentityUiState())
    val accountIdentityState: StateFlow<AccountIdentityUiState> = _accountIdentityState

    private val _launchState = MutableStateFlow(LaunchUiState())
    val launchState: StateFlow<LaunchUiState> = _launchState

    private val _packageSetupState = MutableStateFlow(PackageSetupUiState())
    val packageSetupState: StateFlow<PackageSetupUiState> = _packageSetupState

    private val _directPatchPromptState = MutableStateFlow(DirectPatchPromptUiState())
    val directPatchPromptState: StateFlow<DirectPatchPromptUiState> = _directPatchPromptState

    private val _availableModules = MutableStateFlow<List<ModuleListing>>(emptyList())
    val availableModules: StateFlow<List<ModuleListing>> = _availableModules

    private val _moduleBrowserOpen = MutableStateFlow(false)
    val moduleBrowserOpen: StateFlow<Boolean> = _moduleBrowserOpen

    private val _downloadListing = MutableStateFlow<ModuleListing?>(null)
    val downloadListing: StateFlow<ModuleListing?> = _downloadListing

    private val repository = ModuleRepository(application)
    private val scanner = GameScanner(application)
    private val accessManager = LauncherAccessManager(application)
    private val catalogClient = ModuleCatalogClient(application, accessManager)
    private val launcherUpdateClient = LauncherUpdateClient(application)
    private val moduleChangelogClient = ModuleChangelogClient(application)
    private val gameInstallClient = GameInstallClient(application)
    private val playStoreVersionClient = PlayStoreVersionClient()
    private val storageManager = SmartStorageManager(application.filesDir, application.cacheDir)
    private val embeddedPrivateModuleInstaller = EmbeddedPrivateModuleInstaller(application)
    private val gatePreferences = application.getSharedPreferences(GATE_PREFERENCES, android.content.Context.MODE_PRIVATE)
    private val libraryPreferences = application.getSharedPreferences(LIBRARY_PREFERENCES, android.content.Context.MODE_PRIVATE)
    private val playStorePreferences = application.getSharedPreferences(PLAY_STORE_VERSION_PREFERENCES, android.content.Context.MODE_PRIVATE)

    private var started = false
    private var scannedConfigs: List<com.moodtools.hub.modules.ModuleConfig>? = null
    private var detectedGamesCache: List<InstalledGame> = emptyList()
    private var launcherUpdateTestChannel = false
    @Volatile
    private var dismissedLauncherUpdateBuild: Long? = null
    private var moduleUpdatesTestPreviewActive = false
    private var debugAccessBypassActive = false
    private var currentLauncherRelease: LauncherRelease? = null
    @Volatile
    private var launcherInstallerOpened = false
    @Volatile
    private var launcherInstallerRecovery: Job? = null
    @Volatile
    private var pendingGameInstaller: PendingGameInstaller? = null
    @Volatile
    private var gameInstallerLeftLauncher = false
    @Volatile
    private var gameInstallerRecovery: Job? = null
    @Volatile
    private var gameInstallCancellation: GameInstallCancellation? = null
    @Volatile
    private var launcherUpdateCancellation: SecureTransferCancellation? = null
    @Volatile
    private var moduleTransferCancellation: SecureTransferCancellation? = null
    @Volatile
    private var installedModuleUpdateCancellation: SecureTransferCancellation? = null
    private var privateAccessExpiryByScope: Map<String, Long> = emptyMap()
    @Volatile
    private var acknowledgedInstalledModuleUpdates: Set<String> = emptySet()
    private var installedModuleUpdateJob: Job? = null

    init {
        viewModelScope.launch {
            GameInstallEvents.results.collect(::onGameInstallResult)
        }
    }

    fun start(
        initialLink: Uri?,
        debugAccessBypass: Boolean = false,
        debugLauncherUpdateTest: Boolean = false,
        debugModuleUpdatesTest: Boolean = false
    ) {
        if (started) return
        started = true
        launcherUpdateTestChannel = debugLauncherUpdateTest
        moduleUpdatesTestPreviewActive = debugModuleUpdatesTest
        debugAccessBypassActive = debugAccessBypass
        _launcherEntered.value = false
        viewModelScope.launch(Dispatchers.IO) {
            val security = RuntimeSecurityGuard.inspect(getApplication())
            if (!security.allowed) {
                _startupState.value = LauncherStartupState.SecurityBlocked(
                    "This launcher installation could not be trusted. Install an official, correctly signed build and try again."
                )
                return@launch
            }
            runCatching { cleanDownloadStorage() }
                .onSuccess { result ->
                    if (result.deletedFiles > 0 || result.recoveredFiles > 0) {
                        android.util.Log.i(
                            "JesterMoodsStorage",
                            "Cleaned ${result.deletedFiles} files (${result.reclaimedBytes} bytes); " +
                                "recovered ${result.recoveredFiles} interrupted add-on files."
                        )
                    }
                }
                .onFailure { error ->
                    android.util.Log.w("JesterMoodsStorage", "Storage maintenance will retry next launch.", error)
                }
            val root = ExecutionModeStartupGate.check()
            if (!root.allowed) {
                _startupState.value = LauncherStartupState.RootDenied(
                    root.message ?: "Root access is required to continue."
                )
                return@launch
            }
            runCatching { ExecutionModeLaunchBridge.prepare(getApplication()) }
                .onSuccess { detail ->
                    if (detail != null) android.util.Log.w("JesterMoodsMigration", detail)
                }
                .onFailure { error ->
                    android.util.Log.w(
                        "JesterMoodsMigration",
                        "The legacy launcher data handoff will be retried.",
                        error
                    )
                }
            if (isLocalTestStageLink(initialLink)) {
                runCatching { importLocalTestModules(initialLink!!) }
                    .onFailure { error ->
                        android.util.Log.e("JesterMoodsLocalTest", "Local staged module import failed", error)
                    }
            }
            if (debugAccessBypass) {
                completeAuthorizedStartup(
                    launcherExpiresAt = System.currentTimeMillis() / 1_000L + DEBUG_ACCESS_DURATION_SECONDS,
                    initialLink = initialLink,
                    bypassPrivateApproval = true
                )
                return@launch
            }
            _startupState.value = LauncherStartupState.CheckingAccess
            if (isLauncherUnlock(initialLink)) {
                redeemLauncherAccess(initialLink!!)
                return@launch
            }
            checkLauncherAccess(initialLink)
        }
    }

    private fun cleanDownloadStorage(): com.moodtools.hub.networking.StorageCleanupResult {
        return storageManager.cleanStartup(launcherUpdateClient.installedBuild()) { packageName ->
            runCatching {
                val info = getApplication<android.app.Application>().packageManager
                    .getPackageInfo(packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
            }.getOrNull()
        }
    }

    fun retryAccessRecovery() {
        if (_startupState.value !is LauncherStartupState.ConnectionRequired) return
        viewModelScope.launch(Dispatchers.IO) { checkLauncherAccess() }
    }

    private suspend fun checkLauncherAccess(initialLink: Uri? = null) {
        _startupState.value = LauncherStartupState.CheckingAccess
        val gateStartedAt = SystemClock.elapsedRealtime()
        val result = runCatching { accessManager.currentLease() }
        awaitMinimumAccessCheckGate(gateStartedAt)
        result
            .onSuccess { lease ->
                if (lease == null) {
                    // The server was reached and confirmed there is no recoverable access.
                    _startupState.value = LauncherStartupState.Locked()
                } else {
                    completeAuthorizedStartup(lease.expiresAt, initialLink)
                }
            }
            .onFailure { error ->
                android.util.Log.e("JesterMoodsAccess", "Digital key recovery check failed", error)
                _startupState.value = LauncherStartupState.ConnectionRequired(
                    "Jester Mods needs internet once after its app data is cleared or it is reinstalled. " +
                        "Connect, then tap Try again. Active access on this device will be restored automatically."
                )
            }
    }

    private suspend fun awaitMinimumAccessCheckGate(startedAt: Long) {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = MINIMUM_ACCESS_CHECK_GATE_MS - elapsed
        if (remaining > 0L) delay(remaining)
    }

    fun beginLauncherUnlock(): String {
        val address = accessManager.createUnlockUrl()
        // Replace the locked frame before Android animates to the browser. When the verified
        // callback returns, this neutral progress state remains visible until redemption finishes.
        _startupState.value = LauncherStartupState.CheckingAccess
        return address
    }

    fun onUnlockBrowserReturnedWithoutCallback() {
        if (_startupState.value is LauncherStartupState.CheckingAccess) {
            _startupState.value = LauncherStartupState.Locked()
        }
    }

    fun onUnlockBrowserLaunchFailed() {
        if (_startupState.value is LauncherStartupState.CheckingAccess) {
            _startupState.value = LauncherStartupState.Locked(
                "Android could not open the Linkvertise route in a web browser. " +
                    "Install or enable a browser, then try again."
            )
        }
    }

    fun isLauncherUnlockLink(uri: Uri?): Boolean = isLauncherUnlock(uri)

    fun enterLauncher() {
        if (_startupState.value is LauncherStartupState.Ready) _launcherEntered.value = true
    }

    fun supportCode(): String = accessManager.accountIdentity().grantPassIdentity

    private suspend fun completeAuthorizedStartup(
        launcherExpiresAt: Long,
        initialLink: Uri? = null,
        bypassPrivateApproval: Boolean = false
    ) {
        val installedPrivateModules = repository.privateModules()
        val configuredScope = BuildConfig.PRIVATE_MODULE_SCOPE.takeIf {
            BuildConfig.PRIVATE_MODULE_ENABLED
        }
        val cachedCatalog = catalogClient.loadCached().orEmpty()
        val knownPrivateScopes = (
            installedPrivateModules.values +
                listOfNotNull(configuredScope) +
                cachedCatalog.mapNotNull { it.privateScope }
            )
            .distinct()
        val cachedApprovalByScope = knownPrivateScopes.associateWith { scope ->
            if (bypassPrivateApproval && BuildConfig.DEBUG) return@associateWith null
            accessManager.cachedPrivateAccess(scope)
        }
        privateAccessExpiryByScope = cachedApprovalByScope.mapNotNull { (scope, result) ->
            (result as? LauncherPrivateAccessResult.Approved)?.lease?.grantExpiresAt?.let { scope to it }
        }.toMap()

        val configuredApproved = configuredScope != null &&
            (bypassPrivateApproval && BuildConfig.DEBUG ||
                cachedApprovalByScope[configuredScope] is LauncherPrivateAccessResult.Approved)
        if (configuredApproved) {
            runCatching { embeddedPrivateModuleInstaller.installIfConfigured() }
                .onFailure { error ->
                    android.util.Log.e(
                        "JesterMoodsPrivateModule",
                        "Embedded module installation failed; normal launcher startup will continue.",
                        error
                    )
                }
        }
        // Hydrate every launcher screen from verified disk/local state before exposing the
        // Library. Network-backed catalog, Play Store, and changelog checks continue below.
        // This keeps an existing library visible immediately on every process start.
        hydrateCachedGames(cachedCatalog)
        primeChangelogFromCache()
        markLauncherReady(launcherExpiresAt)
        if (BuildConfig.DEBUG && moduleUpdatesTestPreviewActive) {
            _launcherEntered.value = true
        }
        // Cached signed approvals own the first frame. Refresh revocation and extended expiry
        // information only after the launcher is visible, without temporarily presenting a
        // cached private add-on as public while the network request is in flight.
        val refreshedApprovalByScope = knownPrivateScopes.associateWith { scope ->
            if (bypassPrivateApproval && BuildConfig.DEBUG) return@associateWith null
            accessManager.checkPrivateAccess(scope).also { result ->
                if (result is LauncherPrivateAccessResult.Unavailable) {
                    android.util.Log.w(
                        "JesterMoodsPrivateAccess",
                        "Private module approval could not be refreshed; retaining cached support.",
                        result.error
                    )
                }
            }
        }
        val refreshedExpiries = privateAccessExpiryByScope.toMutableMap()
        refreshedApprovalByScope.forEach { (scope, result) ->
            when (result) {
                is LauncherPrivateAccessResult.Approved -> {
                    refreshedExpiries[scope] = result.lease.grantExpiresAt
                }
                LauncherPrivateAccessResult.Denied -> refreshedExpiries.remove(scope)
                is LauncherPrivateAccessResult.Unavailable -> Unit
                null -> Unit
            }
        }
        privateAccessExpiryByScope = refreshedExpiries
        val configuredApprovedAfterRefresh = configuredScope != null &&
            (bypassPrivateApproval && BuildConfig.DEBUG ||
                refreshedApprovalByScope[configuredScope] is LauncherPrivateAccessResult.Approved)
        if (!configuredApproved && configuredApprovedAfterRefresh) {
            runCatching { embeddedPrivateModuleInstaller.installIfConfigured() }
                .onFailure { error ->
                    android.util.Log.e(
                        "JesterMoodsPrivateModule",
                        "Embedded module installation failed; normal launcher startup will continue.",
                        error
                    )
                }
        }
        // Publish the signed launcher-release cache concurrently before waiting on catalog
        // refresh. Installed add-on offers were already published by hydrateCachedGames().
        checkLauncherUpdate()
        refreshGames(refreshCatalog = true, forceGameScan = true)
        if (!isLocalTestStageLink(initialLink)) initialLink?.let(::handleDeepLink)
    }

    private fun markLauncherReady(expiresAt: Long) {
        _startupState.value = LauncherStartupState.Ready(expiresAt)
    }

    fun openLauncherUpdate() {
        if (_launcherUpdateState.value.available) {
            _changelogState.value = _changelogState.value.copy(open = false)
            _launcherUpdateState.update { current ->
                if (current.available) current.copy(screenOpen = true) else current
            }
        }
    }

    fun openChangelog() {
        primeChangelogFromCache()
        _changelogState.value = _changelogState.value.copy(open = true)
        refreshChangelog()
    }

    fun closeChangelog() {
        _changelogState.value = _changelogState.value.copy(
            open = false,
            selectedModuleHistory = null,
            selectedModulePackage = null,
            moduleHistoryLoadingPackage = null,
            moduleHistoryError = null
        )
    }

    fun openAccountIdentity() {
        _changelogState.value = _changelogState.value.copy(open = false)
        _accountIdentityState.value = runCatching {
            val identity = accessManager.accountIdentity()
            AccountIdentityUiState(
                open = true,
                grantPassIdentity = identity.grantPassIdentity,
                deviceId = identity.deviceId,
                recoveryId = identity.recoveryId,
                installationId = identity.installationId,
                proofKeyId = identity.proofKeyId,
                flavor = identity.flavor,
                accessVersion = identity.accessVersion
            )
        }.getOrElse { error ->
            AccountIdentityUiState(
                open = true,
                error = error.message ?: "Account identity is unavailable on this device."
            )
        }
    }

    fun closeAccountIdentity() {
        _accountIdentityState.value = _accountIdentityState.value.copy(open = false)
    }

    fun openModuleChangelog(packageName: String) {
        val listing = _availableModules.value.firstOrNull {
            it.catalog.config.packageName == packageName
        } ?: return
        val cachedHistory = moduleChangelogClient.loadCached(listing.catalog)
        _changelogState.value = _changelogState.value.copy(
            selectedModuleHistory = cachedHistory,
            selectedModulePackage = packageName,
            moduleHistoryLoadingPackage = packageName,
            moduleHistoryError = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { moduleChangelogClient.refresh(listing.catalog) }
                .onSuccess { history ->
                    if (_changelogState.value.moduleHistoryLoadingPackage == packageName) {
                        _changelogState.value = _changelogState.value.copy(
                            selectedModuleHistory = history,
                            selectedModulePackage = packageName,
                            moduleHistoryLoadingPackage = null,
                            moduleHistoryError = null
                        )
                    }
                }
                .onFailure {
                    if (_changelogState.value.moduleHistoryLoadingPackage == packageName) {
                        _changelogState.value = _changelogState.value.copy(
                            moduleHistoryLoadingPackage = null,
                            moduleHistoryError = if (cachedHistory == null) {
                                "This add-on's verified history is unavailable. Try again when you're online."
                            } else {
                                "Showing saved history. Try again when you're online to check for updates."
                            }
                        )
                    }
                }
        }
    }

    fun closeModuleChangelog() {
        _changelogState.value = _changelogState.value.copy(
            selectedModuleHistory = null,
            selectedModulePackage = null,
            moduleHistoryLoadingPackage = null,
            moduleHistoryError = null
        )
    }

    fun refreshChangelog() {
        if (_changelogState.value.loading) return
        primeChangelogFromCache()
        _changelogState.value = _changelogState.value.copy(
            loading = true,
            error = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            refreshGames(refreshCatalog = true)
            val launcherResult = runCatching { launcherUpdateClient.refreshChangelog() }
            val launcherEntries = launcherResult.getOrElse {
                launcherUpdateClient.loadCachedChangelog().orEmpty()
            }
            // Keep the global feed cheap even with thousands of modules. Full signed history is
            // loaded only when a user opens an actual update offer for that module.
            val moduleHistories = moduleChangelogSummaries()
            _changelogState.value = _changelogState.value.copy(
                loading = false,
                launcherEntries = launcherEntries,
                moduleHistories = moduleHistories,
                error = if (launcherEntries.isEmpty() && launcherResult.isFailure) {
                    "Launcher release history is unavailable. Add-on changes shown below are still verified from the catalog."
                } else null
            )
        }
    }

    /** Publishes verified launcher and catalog summaries without doing network work. */
    private fun primeChangelogFromCache() {
        val current = _changelogState.value
        val cachedLauncher = launcherUpdateClient.loadCachedChangelog().orEmpty()
        val cachedModules = moduleChangelogSummaries()
        _changelogState.value = current.copy(
            launcherEntries = cachedLauncher.ifEmpty { current.launcherEntries },
            moduleHistories = cachedModules.ifEmpty { current.moduleHistories }
        )
    }

    private fun moduleChangelogSummaries() = _availableModules.value.map { listing ->
        moduleChangelogClient.summary(listing.catalog)
    }.sortedWith(
        compareBy<com.moodtools.hub.networking.ModuleChangelog, String>(String.CASE_INSENSITIVE_ORDER) {
            it.title
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
    )

    fun closeLauncherUpdate() {
        _launcherUpdateState.update { current ->
            if (!current.inProgress && !current.installing && !current.cancelling) {
                if (current.available && current.build > 0L) {
                    dismissedLauncherUpdateBuild = current.build
                }
                current.copy(screenOpen = false)
            } else {
                current
            }
        }
    }

    fun dismissInstalledModuleUpdates() {
        val current = _installedModuleUpdatesState.value
        if (current.inProgress) return
        val visibleUpdates = current.packageNames.mapNotNull { packageName ->
            (_libraryGames.value + current.previewUpdates).firstOrNull { it.packageName == packageName }
                ?.installedModuleUpdateKey()
        }
        acknowledgedInstalledModuleUpdates = acknowledgedInstalledModuleUpdates + visibleUpdates
        _installedModuleUpdatesState.value = InstalledModuleUpdatesUiState()
    }

    fun reviewInstalledModuleUpdate(game: LibraryGame) {
        if (_installedModuleUpdatesState.value.inProgress) return
        dismissInstalledModuleUpdates()
        val current = _libraryGames.value.firstOrNull { it.packageName == game.packageName } ?: return
        openLibraryGame(current)
        updateLibraryGame(current)
    }

    fun updateInstalledModuleFromPrompt(game: LibraryGame) {
        runInstalledModuleUpdateQueue(listOf(game), updatingAll = false)
    }

    fun updateAllInstalledModules() {
        val state = _installedModuleUpdatesState.value
        val visibleUpdates = state.packageNames.mapNotNull { packageName ->
            (_libraryGames.value + state.previewUpdates).firstOrNull { it.packageName == packageName }
        }
        val pendingUpdates = visibleUpdates.filter { game ->
            state.itemStates[game.packageName]?.status != InstalledModuleUpdateItemStatus.INSTALLED
        }
        runInstalledModuleUpdateQueue(pendingUpdates, updatingAll = true)
    }

    private fun runInstalledModuleUpdateQueue(
        requestedUpdates: List<LibraryGame>,
        updatingAll: Boolean
    ) {
        if (installedModuleUpdateJob?.isActive == true || _installedModuleUpdatesState.value.inProgress) return
        val updates = requestedUpdates.distinctBy(LibraryGame::packageName)
        if (updates.isEmpty()) return
        val cancellation = SecureTransferCancellation()
        installedModuleUpdateCancellation = cancellation
        val current = _installedModuleUpdatesState.value
        val snapshots = (current.previewUpdates + updates).distinctBy(LibraryGame::packageName)
        val queuedStates = current.itemStates.toMutableMap().apply {
            updates.forEach { game ->
                this[game.packageName] = InstalledModuleUpdateItemUiState(
                    status = InstalledModuleUpdateItemStatus.QUEUED,
                    stage = SecureTransferStage.PREPARING,
                    stageProgress = null,
                    detail = if (updatingAll) "Waiting its turn" else "Preparing verified update",
                    totalBytes = game.listing?.let(::moduleDownloadSize) ?: 0L,
                    diagnostics = listOf("Added to the verified update queue")
                )
            }
        }
        _installedModuleUpdatesState.value = current.copy(
            open = true,
            packageNames = (current.packageNames + updates.map(LibraryGame::packageName)).distinct(),
            previewUpdates = snapshots,
            inProgress = true,
            cancelling = false,
            cancelled = false,
            updatingAll = updatingAll,
            itemStates = queuedStates
        )
        installedModuleUpdateJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var accessInterrupted = false
                updates.forEach { snapshot ->
                    if (accessInterrupted || cancellation.cancelled) return@forEach
                    val game = _libraryGames.value.firstOrNull { it.packageName == snapshot.packageName }
                        ?: snapshot
                    val listing = game.listing
                    val installedGame = listing?.game ?: game.game
                    if (listing == null || installedGame == null) {
                        updateInstalledModuleItem(game.packageName) {
                            InstalledModuleUpdateItemUiState(
                                status = InstalledModuleUpdateItemStatus.FAILED,
                                stage = SecureTransferStage.FAILED,
                                detail = "Open details after reinstalling the original game, then try again."
                            )
                        }
                        return@forEach
                    }
                    updateInstalledModuleItem(game.packageName) { previous ->
                        previous.copy(
                            status = InstalledModuleUpdateItemStatus.DOWNLOADING,
                            stage = SecureTransferStage.PREPARING,
                            stageProgress = null,
                            detail = "Preparing signed download",
                            downloadedBytes = 0L,
                            totalBytes = moduleDownloadSize(listing)
                        )
                    }
                    runCatching {
                        applyUpdate(
                            request = UpdateRequest(
                                packageName = game.packageName,
                                grant = null,
                                nonce = null,
                                buildHint = null
                            ),
                            gameHint = installedGame,
                            onProgress = { downloaded, total ->
                                updateInstalledModuleItem(game.packageName) { previous ->
                                    previous.copy(
                                        status = InstalledModuleUpdateItemStatus.DOWNLOADING,
                                        stage = if (downloaded >= total && total > 0L) {
                                            SecureTransferStage.VERIFYING
                                        } else {
                                            SecureTransferStage.DOWNLOADING
                                        },
                                        stageProgress = if (downloaded >= total && total > 0L) 0f else null,
                                        detail = if (downloaded >= total && total > 0L) {
                                            "Verifying signed package"
                                        } else {
                                            "Downloading secure package"
                                        },
                                        downloadedBytes = downloaded,
                                        totalBytes = total
                                    )
                                }
                            },
                            onStage = { stage ->
                                updateInstalledModuleItem(game.packageName) { previous ->
                                    previous.copy(
                                        status = InstalledModuleUpdateItemStatus.DOWNLOADING,
                                        stage = stage,
                                        stageProgress = when (stage) {
                                            SecureTransferStage.PREPARING,
                                            SecureTransferStage.VERIFYING -> null
                                            else -> null
                                        },
                                        detail = when (stage) {
                                            SecureTransferStage.PREPARING -> "Authorizing signed release"
                                            SecureTransferStage.DOWNLOADING -> "Downloading secure package"
                                            SecureTransferStage.VERIFYING -> "Verifying integrity and identity"
                                            SecureTransferStage.ACTIVATING -> "Activating add-on safely"
                                            else -> previous.detail
                                        }
                                    )
                                }
                            },
                            onDiagnostic = { message ->
                                updateInstalledModuleItem(game.packageName) { previous ->
                                    previous.copy(
                                        diagnostics = appendDiagnostic(previous.diagnostics, message)
                                    )
                                }
                            },
                            isCancelled = { cancellation.cancelled }
                        )
                    }.onSuccess { result ->
                        updateInstalledModuleItem(game.packageName) { previous ->
                            previous.copy(
                                status = InstalledModuleUpdateItemStatus.INSTALLED,
                                stage = SecureTransferStage.COMPLETED,
                                stageProgress = 1f,
                                detail = result.version?.let { "Version $it is ready" } ?: "Latest release is ready",
                                downloadedBytes = previous.totalBytes.takeIf { it > 0L } ?: previous.downloadedBytes
                            )
                        }
                    }.onFailure { error ->
                        if (cancellation.cancelled) {
                            updateInstalledModuleItem(game.packageName) { previous ->
                                previous.copy(
                                    status = InstalledModuleUpdateItemStatus.AVAILABLE,
                                    stage = SecureTransferStage.CANCELLED,
                                    stageProgress = null,
                                    detail = "Paused safely; tap Update whenever you're ready"
                                )
                            }
                            return@onFailure
                        }
                        accessInterrupted = error is NewSessionAccessBoundaryException
                        if (!accessInterrupted) {
                            android.util.Log.e(
                                "JesterMoodsUpdate",
                                "Update-center install failed for ${game.packageName}",
                                error
                            )
                        }
                        updateInstalledModuleItem(game.packageName) { previous ->
                            previous.copy(
                                status = InstalledModuleUpdateItemStatus.FAILED,
                                stage = SecureTransferStage.FAILED,
                                stageProgress = null,
                                detail = if (accessInterrupted) {
                                    "Unlock launcher access, then retry this update."
                                } else {
                                    moduleDownloadFailureDetail(error)
                                }
                            )
                        }
                    }
                }
                if (accessInterrupted || cancellation.cancelled) {
                    _installedModuleUpdatesState.update { state ->
                        state.copy(
                            itemStates = state.itemStates.mapValues { (_, item) ->
                                if (item.status == InstalledModuleUpdateItemStatus.QUEUED) {
                                    item.copy(
                                        status = InstalledModuleUpdateItemStatus.AVAILABLE,
                                        stage = if (cancellation.cancelled) {
                                            SecureTransferStage.CANCELLED
                                        } else {
                                            SecureTransferStage.READY
                                        },
                                        detail = if (cancellation.cancelled) {
                                            "Paused before this download began"
                                        } else {
                                            "Ready after launcher access is restored"
                                        }
                                    )
                                } else item
                            }
                        )
                    }
                }
                refreshGames()
            } finally {
                _installedModuleUpdatesState.update { state ->
                    state.copy(
                        inProgress = false,
                        cancelling = false,
                        cancelled = cancellation.cancelled,
                        updatingAll = false
                    )
                }
                installedModuleUpdateJob = null
                installedModuleUpdateCancellation = null
            }
        }
    }

    fun cancelInstalledModuleUpdates() {
        val current = _installedModuleUpdatesState.value
        if (!current.inProgress && !current.cancelling) {
            dismissInstalledModuleUpdates()
            return
        }
        val cancellation = installedModuleUpdateCancellation ?: return
        cancellation.cancelled = true
        _installedModuleUpdatesState.value = current.copy(
            cancelling = true,
            cancelled = false
        )
    }

    private fun updateInstalledModuleItem(
        packageName: String,
        transform: (InstalledModuleUpdateItemUiState) -> InstalledModuleUpdateItemUiState
    ) {
        _installedModuleUpdatesState.update { state ->
            val currentItem = state.itemStates[packageName] ?: InstalledModuleUpdateItemUiState()
            val updatedItem = transform(currentItem)
            state.copy(
                itemStates = state.itemStates + (packageName to updatedItem)
            )
        }
    }

    fun downloadAndInstallLauncherUpdate() {
        val current = _launcherUpdateState.value
        if (!current.available || current.inProgress || current.installing || current.cancelling) return
        val release = currentLauncherRelease?.takeIf { it.build == current.build } ?: return
        val cancellation = SecureTransferCancellation()
        launcherUpdateCancellation = cancellation
        launcherInstallerRecovery?.cancel()
        launcherInstallerRecovery = null
        launcherInstallerOpened = false
        _launcherUpdateState.value = current.copy(
            inProgress = !current.downloaded,
            installing = false,
            cancelling = false,
            cancelled = false,
            stage = if (current.downloaded) SecureTransferStage.WAITING_FOR_ANDROID else SecureTransferStage.PREPARING,
            stageProgress = null,
            failed = false,
            headline = if (current.downloaded) "Opening Android installer" else "Downloading launcher update",
            detail = if (current.downloaded) "Confirm the update when Android asks." else "Checking for a reusable verified package first.",
            diagnostics = appendDiagnostic(current.diagnostics, "Launcher update started")
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val alreadyDownloaded = launcherUpdateClient.isDownloaded(
                    release = release,
                    onVerificationProgress = { progress ->
                        if (launcherUpdateCancellation === cancellation && !cancellation.cancelled) {
                            _launcherUpdateState.update { state ->
                                state.copy(
                                    stage = SecureTransferStage.VERIFYING,
                                    stageProgress = progress,
                                    headline = "Checking saved launcher update",
                                    detail = "Verifying package integrity, identity, and signing certificate."
                                )
                            }
                        }
                    },
                    isCancelled = { cancellation.cancelled }
                )
                if (!alreadyDownloaded) {
                    _launcherUpdateState.update { state ->
                        state.copy(
                            stage = SecureTransferStage.DOWNLOADING,
                            stageProgress = null,
                            headline = "Downloading launcher update",
                            detail = "The package will be verified before Android can open it.",
                            diagnostics = appendDiagnostic(state.diagnostics, "Secure download started")
                        )
                    }
                    launcherUpdateClient.download(
                        release = release,
                        onProgress = { downloaded, total ->
                            if (launcherUpdateCancellation !== cancellation || cancellation.cancelled) return@download
                            val verifying = downloaded >= total && total > 0L
                            _launcherUpdateState.value = _launcherUpdateState.value.copy(
                                inProgress = true,
                                stage = if (verifying) SecureTransferStage.VERIFYING else SecureTransferStage.DOWNLOADING,
                                stageProgress = if (verifying) 0f else null,
                                headline = if (verifying) "Verifying launcher update" else "Downloading launcher update",
                                detail = if (verifying) {
                                    "Checking integrity, app identity, and signing certificate."
                                } else {
                                    "The verified installer will open automatically."
                                },
                                downloadedBytes = downloaded,
                                totalBytes = total
                            )
                        },
                        onVerificationProgress = { progress ->
                            if (launcherUpdateCancellation === cancellation && !cancellation.cancelled) {
                                _launcherUpdateState.update { state ->
                                    state.copy(
                                        stage = SecureTransferStage.VERIFYING,
                                        stageProgress = progress,
                                        headline = "Verifying launcher update",
                                        detail = "Checking integrity, app identity, and signing certificate."
                                    )
                                }
                            }
                        },
                        onDiagnostic = { message ->
                            if (launcherUpdateCancellation === cancellation && !cancellation.cancelled) {
                                _launcherUpdateState.update { state ->
                                    state.copy(diagnostics = appendDiagnostic(state.diagnostics, message))
                                }
                            }
                        },
                        isCancelled = { cancellation.cancelled }
                    )
                }
                if (cancellation.cancelled) error("Launcher update cancelled")
                _launcherUpdateState.value = _launcherUpdateState.value.copy(
                    inProgress = false,
                    installing = true,
                    stage = SecureTransferStage.WAITING_FOR_ANDROID,
                    stageProgress = null,
                    downloaded = true,
                    failed = false,
                    headline = "Opening Android installer",
                    detail = "Android is preparing its confirmation screen.",
                    diagnostics = appendDiagnostic(
                        _launcherUpdateState.value.diagnostics,
                        "Verified launcher package handed to Android"
                    )
                )
                launcherUpdateClient.install(release, isCancelled = { cancellation.cancelled })
                if (cancellation.cancelled || launcherUpdateCancellation !== cancellation) {
                    error("Launcher update cancelled")
                }
                launcherInstallerOpened = true
                launcherInstallerRecovery = viewModelScope.launch {
                    delay(LAUNCHER_INSTALLER_RECOVERY_DELAY_MS)
                    val waiting = _launcherUpdateState.value
                    if (launcherInstallerOpened && waiting.installing && waiting.build == release.build) {
                        launcherInstallerOpened = false
                        launcherInstallerRecovery = null
                        launcherUpdateCancellation = null
                        _launcherUpdateState.value = waiting.copy(
                            inProgress = false,
                            installing = false,
                            downloaded = true,
                            stage = SecureTransferStage.READY,
                            failed = false,
                            headline = "Update ready to install",
                            detail = "Android is taking longer than expected. Tap Install update to open the confirmation again."
                        )
                    }
                }
            }.onFailure { error ->
                if (launcherUpdateCancellation !== cancellation) return@onFailure
                launcherInstallerRecovery?.cancel()
                launcherInstallerRecovery = null
                launcherInstallerOpened = false
                launcherUpdateCancellation = null
                if (cancellation.cancelled) {
                    val state = _launcherUpdateState.value
                    _launcherUpdateState.value = state.copy(
                        inProgress = false,
                        installing = false,
                        cancelling = false,
                        cancelled = true,
                        failed = false,
                        stage = SecureTransferStage.CANCELLED,
                        stageProgress = null,
                        headline = "Launcher update paused",
                        detail = "The partial package was kept safely, so a future retry can resume faster.",
                        diagnostics = appendDiagnostic(state.diagnostics, "Cancelled by the user")
                    )
                    return@onFailure
                }
                android.util.Log.e("JesterMoodsLauncherUpdate", "Launcher update failed", error)
                val state = _launcherUpdateState.value
                val downloaded = launcherUpdateClient.isDownloaded(release)
                _launcherUpdateState.value = state.copy(
                    inProgress = false,
                    installing = false,
                    downloaded = downloaded,
                    failed = true,
                    stage = SecureTransferStage.FAILED,
                    stageProgress = null,
                    headline = if (downloaded) "Couldn't open the installer" else "Couldn't download the update",
                    detail = if (downloaded) {
                        "Android couldn't open its app installer. The verified update is still saved, so you can restart the device and try again."
                    } else {
                        "Check your connection and available storage, then try again."
                    }
                )
            }
        }
    }

    fun cancelLauncherUpdate() {
        val current = _launcherUpdateState.value
        if (!current.inProgress && !current.installing && !current.cancelling) {
            closeLauncherUpdate()
            return
        }
        val cancellation = launcherUpdateCancellation ?: return
        cancellation.cancelled = true
        if (current.installing) {
            launcherInstallerRecovery?.cancel()
            launcherInstallerRecovery = null
            launcherInstallerOpened = false
            launcherUpdateCancellation = null
            _launcherUpdateState.value = current.copy(
                inProgress = false,
                installing = false,
                cancelling = false,
                cancelled = true,
                failed = false,
                stage = SecureTransferStage.CANCELLED,
                headline = "Stopped waiting for Android",
                detail = "If Android's installer is still visible, close it there. The verified update remains saved.",
                diagnostics = appendDiagnostic(current.diagnostics, "Stopped waiting for Android installer")
            )
        } else {
            _launcherUpdateState.value = current.copy(
                cancelling = true,
                headline = "Stopping safely",
                detail = "Keeping the resumable download intact for a future retry.",
                diagnostics = appendDiagnostic(current.diagnostics, "Cancellation requested by the user")
            )
        }
    }

    fun onLauncherInstallPermissionDenied() {
        val current = _launcherUpdateState.value
        if (!current.available) return
        _launcherUpdateState.value = current.copy(
            inProgress = false,
            installing = false,
            failed = true,
            stage = SecureTransferStage.FAILED,
            headline = "Installation permission is needed",
            detail = "Allow Jester Mods to install app updates, then tap Install update again."
        )
    }

    fun onHostResumed(refreshGames: Boolean = true) {
        if (refreshGames && started && _startupState.value is LauncherStartupState.Ready) {
            viewModelScope.launch(Dispatchers.IO) {
                refreshGames(refreshCatalog = true, forceGameScan = true)
            }
        }
        reconcileReturnedGameInstaller()
        val current = _launcherUpdateState.value
        if (!current.available) return
        val installed = runCatching { launcherUpdateClient.installedBuild() }.getOrDefault(0L)
        if (installed >= current.build) {
            launcherInstallerRecovery?.cancel()
            launcherInstallerRecovery = null
            launcherInstallerOpened = false
            launcherUpdateCancellation = null
            launcherUpdateClient.markInstallSucceeded(installed)
            currentLauncherRelease = null
            _launcherUpdateState.value = LauncherUpdateUiState()
            return
        }
        if (current.installing && launcherInstallerOpened) {
            launcherInstallerRecovery?.cancel()
            launcherInstallerRecovery = null
            launcherInstallerOpened = false
            launcherUpdateCancellation = null
            _launcherUpdateState.value = current.copy(
                inProgress = false,
                installing = false,
                downloaded = true,
                failed = false,
                stage = SecureTransferStage.READY,
                headline = "Update ready to install",
                detail = "Android closed without installing the update. Tap Install update to open it again."
            )
            return
        }
    }

    fun onHostPaused() {
        if (pendingGameInstaller != null && _gameInstallState.value.installing) {
            gameInstallerLeftLauncher = true
        }
    }

    private fun checkLauncherUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            if (launcherUpdateTestChannel && BuildConfig.DEBUG) {
                // The CMD helper uses this deterministic, package-safe design preview. Do not
                // depend on a published test APK (whose release application ID intentionally
                // differs from the side-by-side debug launcher).
                publishLauncherUpdatePreview()
                return@launch
            }
            val cachedRelease = launcherUpdateClient.loadCached(launcherUpdateTestChannel)
            if (cachedRelease != null) {
                publishLauncherUpdate(
                    release = cachedRelease,
                    history = if (launcherUpdateTestChannel) {
                        emptyList()
                    } else {
                        launcherUpdateClient.loadCachedChangelog().orEmpty()
                    }
                )
            }

            val release = runCatching { launcherUpdateClient.refresh(launcherUpdateTestChannel) }
                .onFailure { error ->
                    android.util.Log.e("JesterMoodsLauncherUpdate", "Launcher update check failed", error)
                }
                .getOrNull()
            release ?: return@launch
            val history = if (launcherUpdateTestChannel) {
                emptyList()
            } else {
                runCatching { launcherUpdateClient.refreshChangelog() }
                    .getOrElse { launcherUpdateClient.loadCachedChangelog().orEmpty() }
            }
            publishLauncherUpdate(release, history)
        }
    }

    private fun publishLauncherUpdatePreview() {
        val installedBuild = launcherUpdateClient.installedBuild()
        _launcherUpdateState.value = LauncherUpdateUiState(
            available = true,
            screenOpen = true,
            build = installedBuild + 1L,
            version = "Preview",
            notes = "A polished update experience, ready for the next signed launcher release.",
            changelog = listOf(
                LauncherChangelogEntry(
                    build = installedBuild + 1L,
                    version = "Preview",
                    notes = "Preview of the launcher update window. No package will be downloaded.",
                    publishedAtEpochSeconds = System.currentTimeMillis() / 1_000L
                )
            ),
            headline = "A launcher update is available",
            detail = "Design preview triggered by the debug launcher.",
            totalBytes = 24L * 1024L * 1024L
        )
    }

    private fun publishLauncherUpdate(
        release: LauncherRelease,
        history: List<LauncherChangelogEntry> = launcherUpdateClient.loadCachedChangelog().orEmpty()
    ) {
        val installedBuild = launcherUpdateClient.installedBuild()
        if (installedBuild >= release.build) {
            launcherUpdateClient.markInstallSucceeded(installedBuild)
            currentLauncherRelease = null
            _launcherUpdateState.value = LauncherUpdateUiState()
            return
        }
        currentLauncherRelease = release
        val downloaded = launcherUpdateClient.isDownloaded(release)
        val newEntries = history.filter { it.build in (installedBuild + 1)..release.build }
            .ifEmpty {
                listOf(
                    LauncherChangelogEntry(
                        build = release.build,
                        version = release.version,
                        notes = release.notes.orEmpty(),
                        publishedAtEpochSeconds = System.currentTimeMillis() / 1_000L
                    )
                )
            }
        val readyHeadline = if (downloaded) {
            "Update ready to install"
        } else {
            "A launcher update is available"
        }
        val readyDetail = if (downloaded) {
            "The verified download is saved on this device."
        } else {
            "Download it here without leaving Jester Mods."
        }
        _launcherUpdateState.update { current ->
            val sameRelease = current.available && current.build == release.build
            val screenOpen = LauncherUpdatePromptPolicy.shouldOpen(
                releaseBuild = release.build,
                dismissedBuild = dismissedLauncherUpdateBuild,
                currentBuild = current.build,
                currentlyOpen = current.screenOpen
            )
            if (sameRelease) {
                current.copy(
                    available = true,
                    screenOpen = screenOpen,
                    version = release.version,
                    notes = release.notes,
                    changelog = newEntries,
                    downloaded = current.downloaded || downloaded,
                    headline = if (current.inProgress || current.installing || current.failed || current.cancelled) {
                        current.headline
                    } else {
                        readyHeadline
                    },
                    detail = if (current.inProgress || current.installing || current.failed || current.cancelled) {
                        current.detail
                    } else {
                        readyDetail
                    },
                    downloadedBytes = maxOf(
                        current.downloadedBytes,
                        if (downloaded) release.size else 0L
                    ),
                    totalBytes = release.size
                )
            } else {
                LauncherUpdateUiState(
                    available = true,
                    screenOpen = screenOpen,
                    build = release.build,
                    version = release.version,
                    notes = release.notes,
                    changelog = newEntries,
                    downloaded = downloaded,
                    headline = readyHeadline,
                    detail = readyDetail,
                    downloadedBytes = if (downloaded) release.size else 0L,
                    totalBytes = release.size
                )
            }
        }
    }

    fun openModuleBrowser() {
        _selectedGame.value = null
        _downloadListing.value = null
        _updateState.value = ModuleUpdateUiState()
        _gameInstallState.value = GameInstallUiState()
        _moduleBrowserOpen.value = true
        refreshCatalogAndLibrary()
    }

    fun refreshCatalogAndLibrary() {
        if (_catalogRefreshing.value) return
        _catalogRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                refreshGames(refreshCatalog = true, forceGameScan = true)
            } finally {
                _catalogRefreshing.value = false
            }
        }
    }

    fun closeModuleBrowser() {
        if (_updateState.value.inProgress || _gameInstallState.value.inProgress ||
            _gameInstallState.value.installing) return
        _moduleBrowserOpen.value = false
        _downloadListing.value = null
        _updateState.value = ModuleUpdateUiState()
        _gameInstallState.value = GameInstallUiState()
    }

    fun openModuleDownload(listing: ModuleListing) {
        if (_updateState.value.inProgress || _gameInstallState.value.inProgress ||
            _gameInstallState.value.installing) return
        _downloadListing.value = listing
        _updateState.value = ModuleUpdateUiState(
            totalBytes = moduleDownloadSize(listing)
        )
        _gameInstallState.value = GameInstallUiState()
    }

    fun closeModuleDownload() {
        if (_updateState.value.inProgress || _gameInstallState.value.inProgress ||
            _gameInstallState.value.installing) return
        _downloadListing.value = null
        _updateState.value = ModuleUpdateUiState()
        _gameInstallState.value = GameInstallUiState()
    }

    fun onGameStoreOpened() {
        _gameInstallState.value = GameInstallUiState(
            headline = "Continue in Google Play",
            detail = "Install or update the original game there, then return to Jester Mods."
        )
    }

    fun onGameStoreUnavailable() {
        _gameInstallState.value = GameInstallUiState(
            headline = "Couldn't open Google Play",
            detail = "Open Google Play on this device and search for the game.",
            failed = true
        )
    }

    fun onGameInstallPermissionDenied() {
        val listing = _downloadListing.value
        val source = listing?.catalog?.installSource as? GameInstallSource.DirectDownload
        _gameInstallState.value = GameInstallUiState(
            visible = true,
            stage = GameInstallStage.FAILED,
            title = listing?.catalog?.config?.title.orEmpty(),
            packageName = listing?.catalog?.config?.packageName.orEmpty(),
            targetVersion = source?.version.orEmpty(),
            packageFormat = source?.format?.name.orEmpty(),
            headline = "Installation permission is needed",
            detail = "Allow Jester Mods to install apps, then tap Download game again.",
            failed = true,
            diagnostics = listOf("Android denied the unknown-app installation permission")
        )
    }

    fun downloadAndInstallGame(listing: ModuleListing) {
        val source = listing.catalog.installSource as? GameInstallSource.DirectDownload ?: return
        if (_gameInstallState.value.inProgress || _gameInstallState.value.installing ||
            _gameInstallState.value.cancelling) return
        if (listing.game != null && source.versionCode <= listing.game.versionCode) {
            _gameInstallState.value = GameInstallUiState(
                visible = true,
                stage = GameInstallStage.FAILED,
                title = listing.catalog.config.title,
                packageName = listing.catalog.config.packageName,
                targetVersion = source.version,
                packageFormat = source.format.name,
                headline = "No newer compatible game download",
                detail = "The available file cannot replace your installed game version.",
                failed = true,
                diagnostics = listOf(
                    "Installed build: ${listing.game.versionCode}",
                    "Available build: ${source.versionCode}"
                )
            )
            return
        }
        val cancellation = GameInstallCancellation()
        gameInstallCancellation = cancellation
        _gameInstallState.value = GameInstallUiState(
            visible = true,
            inProgress = true,
            stage = GameInstallStage.VERIFYING,
            title = listing.catalog.config.title,
            packageName = listing.catalog.config.packageName,
            targetVersion = source.version,
            packageFormat = source.format.name,
            headline = "Checking saved package",
            detail = "Looking for a verified download that can be reused before starting the transfer.",
            totalBytes = source.size,
            stageProgress = 0f,
            diagnostics = listOf(
                "Target: ${listing.catalog.config.packageName}",
                "Version: ${source.version} (build ${source.versionCode})",
                "Package: ${source.format.name} · ${source.size} bytes",
                "Inspecting local download cache"
            )
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val alreadyDownloaded = gameInstallClient.isDownloaded(
                    game = listing.catalog,
                    onVerificationProgress = { progress ->
                        updateGameInstallStageProgress(
                            cancellation = cancellation,
                            stage = GameInstallStage.VERIFYING,
                            progress = progress
                        )
                    },
                    isCancelled = { cancellation.cancelled }
                )
                if (cancellation.cancelled) throw GameInstallCancelledException()
                if (alreadyDownloaded) {
                    _gameInstallState.value = _gameInstallState.value.copy(
                        downloaded = true,
                        downloadedBytes = source.size,
                        stageProgress = 1f,
                        stage = GameInstallStage.VERIFYING,
                        headline = "Verified download found",
                        detail = "Reusing the trusted package already saved on this device.",
                        diagnostics = appendDiagnostic(
                            _gameInstallState.value.diagnostics,
                            "Using the previously verified download"
                        )
                    )
                } else {
                    _gameInstallState.value = _gameInstallState.value.copy(
                        stage = GameInstallStage.DOWNLOADING,
                        stageProgress = null,
                        headline = "Downloading original game",
                        detail = "Keep Jester Mods open while the verified game downloads.",
                        diagnostics = appendDiagnostic(
                            _gameInstallState.value.diagnostics,
                            "Secure download started"
                        )
                    )
                }
                if (!alreadyDownloaded) {
                    gameInstallClient.download(
                        game = listing.catalog,
                        onProgress = { downloaded, total ->
                            if (gameInstallCancellation === cancellation && !cancellation.cancelled) {
                                val verifying = downloaded >= total && total > 0L
                                _gameInstallState.value = _gameInstallState.value.copy(
                                    inProgress = true,
                                    stage = if (verifying) GameInstallStage.VERIFYING else GameInstallStage.DOWNLOADING,
                                    headline = if (verifying) "Verifying original game" else "Downloading original game",
                                    detail = if (verifying) {
                                        "Checking the signed package before anything is handed to Android."
                                    } else {
                                        "The Android installer will open after the file is verified."
                                    },
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    stageProgress = if (verifying) 0f else null
                                )
                            }
                        },
                        onVerificationProgress = { progress ->
                            updateGameInstallStageProgress(
                                cancellation = cancellation,
                                stage = GameInstallStage.VERIFYING,
                                progress = progress
                            )
                        },
                        onDiagnostic = { message -> appendGameInstallDiagnostic(cancellation, message) },
                        isCancelled = { cancellation.cancelled }
                    )
                }
                if (cancellation.cancelled) throw GameInstallCancelledException()
                _gameInstallState.value = _gameInstallState.value.copy(
                    inProgress = false,
                    installing = true,
                    stage = GameInstallStage.PREPARING_INSTALLER,
                    downloaded = true,
                    failed = false,
                    headline = "Preparing Android installer",
                    detail = "Building a trusted installation request for Android.",
                    stageProgress = 0f,
                    diagnostics = appendDiagnostic(
                        _gameInstallState.value.diagnostics,
                        "Package verification completed successfully"
                    )
                )
                val pending = PendingGameInstaller(
                    packageName = listing.catalog.config.packageName,
                    versionCode = source.versionCode
                )
                gameInstallerRecovery?.cancel()
                gameInstallerRecovery = null
                pendingGameInstaller = pending
                gameInstallerLeftLauncher = false
                pending.sessionId = gameInstallClient.install(
                    game = listing.catalog,
                    onPreparationProgress = { progress ->
                        updateGameInstallStageProgress(
                            cancellation = cancellation,
                            stage = GameInstallStage.PREPARING_INSTALLER,
                            progress = progress
                        )
                    },
                    isCancelled = { cancellation.cancelled }
                )
                if (pendingGameInstaller === pending && gameInstallCancellation === cancellation) {
                    _gameInstallState.value = _gameInstallState.value.copy(
                        stage = GameInstallStage.WAITING_FOR_ANDROID,
                        stageProgress = null,
                        headline = "Your move",
                        detail = "Confirm the original game installation in Android's secure prompt.",
                        diagnostics = appendDiagnostic(
                            _gameInstallState.value.diagnostics,
                            pending.sessionId?.let { "Android installer session $it opened" }
                                ?: "Android package confirmation opened"
                        )
                    )
                    scheduleGameInstallerRecovery(pending)
                }
            }.onFailure { error ->
                if (gameInstallCancellation !== cancellation) return@onFailure
                clearGameInstallerWait()
                gameInstallCancellation = null
                if (cancellation.cancelled || error is GameInstallCancelledException) {
                    val progress = _gameInstallState.value
                    _gameInstallState.value = progress.copy(
                        visible = true,
                        inProgress = false,
                        installing = false,
                        cancelling = false,
                        cancelled = true,
                        failed = false,
                        stage = GameInstallStage.CANCELLED,
                        stageProgress = null,
                        headline = "Installation cancelled",
                        detail = "Your partial download was kept safely, so a future retry can resume faster.",
                        diagnostics = appendDiagnostic(progress.diagnostics, "Cancelled by the user")
                    )
                    return@onFailure
                }
                android.util.Log.e("JesterMoodsGameInstall", "Game download or install failed", error)
                val downloaded = runCatching { gameInstallClient.isDownloaded(listing.catalog) }
                    .getOrDefault(false)
                val progress = _gameInstallState.value
                _gameInstallState.value = progress.copy(
                    visible = true,
                    inProgress = false,
                    installing = false,
                    cancelling = false,
                    downloaded = downloaded,
                    failed = true,
                    cancelled = false,
                    stage = GameInstallStage.FAILED,
                    stageProgress = null,
                    headline = if (downloaded) "Couldn't open the installer" else "Couldn't download the game",
                    detail = if (downloaded) {
                        "Allow Jester Mods to install apps in Android settings, then try again."
                    } else {
                        "Check your connection and available storage, then try again."
                    },
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = source.size,
                    diagnostics = appendDiagnostic(
                        progress.diagnostics,
                        "${error.javaClass.simpleName}: ${error.message?.take(240) ?: "No detail provided"}"
                    )
                )
            }
        }
    }

    fun cancelGameInstall() {
        val current = _gameInstallState.value
        if (!current.inProgress && !current.installing && !current.cancelling) {
            dismissGameInstall()
            return
        }
        val cancellation = gameInstallCancellation ?: return
        cancellation.cancelled = true
        pendingGameInstaller?.sessionId?.let(gameInstallClient::cancelInstall)
        if (current.stage == GameInstallStage.WAITING_FOR_ANDROID) {
            clearGameInstallerWait()
            gameInstallCancellation = null
            _gameInstallState.value = current.copy(
                inProgress = false,
                installing = false,
                cancelling = false,
                cancelled = true,
                failed = false,
                stage = GameInstallStage.CANCELLED,
                stageProgress = null,
                headline = "Stopped waiting for Android",
                detail = "If Android's confirmation is still visible, close it there. The verified download remains saved.",
                diagnostics = appendDiagnostic(current.diagnostics, "Installer wait cancelled by the user")
            )
        } else {
            _gameInstallState.value = current.copy(
                cancelling = true,
                headline = "Stopping safely",
                detail = "Finishing the current file operation without damaging the resumable download.",
                diagnostics = appendDiagnostic(current.diagnostics, "Cancellation requested by the user")
            )
        }
    }

    fun dismissGameInstall() {
        val current = _gameInstallState.value
        if (current.inProgress || current.installing || current.cancelling) return
        _gameInstallState.value = GameInstallUiState()
    }

    private fun appendGameInstallDiagnostic(
        cancellation: GameInstallCancellation,
        message: String
    ) {
        if (gameInstallCancellation !== cancellation || cancellation.cancelled) return
        val current = _gameInstallState.value
        _gameInstallState.value = current.copy(
            diagnostics = appendDiagnostic(current.diagnostics, message)
        )
    }

    private fun updateGameInstallStageProgress(
        cancellation: GameInstallCancellation,
        stage: GameInstallStage,
        progress: Float
    ) {
        if (gameInstallCancellation !== cancellation || cancellation.cancelled) return
        val current = _gameInstallState.value
        if (current.stage != stage) return
        _gameInstallState.value = current.copy(stageProgress = progress.coerceIn(0f, 1f))
    }

    private fun appendDiagnostic(current: List<String>, message: String): List<String> {
        val clean = message.trim().replace(Regex("\\s+"), " ").take(320)
        if (clean.isBlank() || current.lastOrNull() == clean) return current
        return (current + clean).takeLast(MAX_GAME_INSTALL_DIAGNOSTICS)
    }

    private fun onGameInstallResult(result: GameInstallResult) {
        val pending = pendingGameInstaller ?: return
        if (result.packageName != pending.packageName || result.versionCode != pending.versionCode) return
        if (result.sessionId >= 0 && pending.sessionId != null && result.sessionId != pending.sessionId) return
        viewModelScope.launch(Dispatchers.IO) {
            if (result.successful) {
                finishGameInstallSuccessfully(pending)
            } else {
                if (!clearGameInstallerWait(pending)) return@launch
                gameInstallCancellation = null
                val message = result.message.orEmpty()
                val current = _gameInstallState.value
                _gameInstallState.value = current.copy(
                    visible = true,
                    inProgress = false,
                    installing = false,
                    downloaded = true,
                    failed = true,
                    stage = GameInstallStage.FAILED,
                    headline = "The game wasn't installed",
                    detail = when {
                        message.contains("cancel", ignoreCase = true) ->
                            "Installation was cancelled. The verified download is still saved."
                        message.contains("screen", ignoreCase = true) ||
                            message.contains("open", ignoreCase = true) ->
                            "Android couldn't open its installer. The verified download is still saved; tap Try again."
                        else -> "Try the installation again. The verified download is still saved."
                    },
                    diagnostics = appendDiagnostic(
                        current.diagnostics,
                        "Android result: ${message.take(240).ifBlank { "Installation failed without a status message" }}"
                    )
                )
            }
        }
    }

    private fun scheduleGameInstallerRecovery(pending: PendingGameInstaller) {
        gameInstallerRecovery?.cancel()
        gameInstallerRecovery = viewModelScope.launch(Dispatchers.IO) {
            delay(GAME_INSTALLER_RECOVERY_DELAY_MS)
            gameInstallerRecovery = null
            reconcileGameInstaller(pending, returnedFromInstaller = false)
        }
    }

    private fun reconcileReturnedGameInstaller() {
        val pending = pendingGameInstaller ?: return
        if (!gameInstallerLeftLauncher || !_gameInstallState.value.installing) return
        gameInstallerLeftLauncher = false
        gameInstallerRecovery?.cancel()
        gameInstallerRecovery = viewModelScope.launch(Dispatchers.IO) {
            val deadline = SystemClock.elapsedRealtime() + GAME_INSTALLER_RETURN_RECONCILE_WINDOW_MS
            while (pendingGameInstaller === pending && _gameInstallState.value.installing) {
                val installed = gameInstallClient.isInstalledAtLeast(
                    pending.packageName,
                    pending.versionCode
                )
                when (GameInstallerReconciliationPolicy.decide(
                    installed = installed,
                    launcherLeft = gameInstallerLeftLauncher,
                    deadlineReached = SystemClock.elapsedRealtime() >= deadline
                )) {
                    GameInstallerReconciliationDecision.SUCCEEDED -> {
                        gameInstallerRecovery = null
                        finishGameInstallSuccessfully(pending)
                        return@launch
                    }
                    GameInstallerReconciliationDecision.KEEP_WAITING ->
                        delay(GAME_INSTALLER_RECONCILE_POLL_MS)
                    GameInstallerReconciliationDecision.FAILED -> {
                        gameInstallerRecovery = null
                        finishGameInstallerReconciliationFailure(pending, returnedFromInstaller = true)
                        return@launch
                    }
                }
            }
        }
    }

    private suspend fun reconcileGameInstaller(
        pending: PendingGameInstaller,
        returnedFromInstaller: Boolean
    ) {
        if (pendingGameInstaller !== pending || !_gameInstallState.value.installing) return
        when (GameInstallerReconciliationPolicy.decide(
            installed = gameInstallClient.isInstalledAtLeast(pending.packageName, pending.versionCode),
            launcherLeft = gameInstallerLeftLauncher,
            deadlineReached = true
        )) {
            GameInstallerReconciliationDecision.SUCCEEDED -> finishGameInstallSuccessfully(pending)
            GameInstallerReconciliationDecision.KEEP_WAITING -> Unit
            GameInstallerReconciliationDecision.FAILED ->
                finishGameInstallerReconciliationFailure(pending, returnedFromInstaller)
        }
    }

    private fun finishGameInstallerReconciliationFailure(
        pending: PendingGameInstaller,
        returnedFromInstaller: Boolean
    ) {
        if (!clearGameInstallerWait(pending)) return
        gameInstallCancellation = null
        val current = _gameInstallState.value
        _gameInstallState.value = current.copy(
            visible = true,
            inProgress = false,
            installing = false,
            downloaded = true,
            failed = true,
            stage = GameInstallStage.FAILED,
            stageProgress = null,
            headline = "Android installer closed",
            detail = if (returnedFromInstaller) {
                "The installation wasn't completed. The verified download is still saved; tap Try again."
            } else {
                "Android didn't open its installer in time. The verified download is still saved; tap Try again."
            },
            diagnostics = appendDiagnostic(
                current.diagnostics,
                if (returnedFromInstaller) {
                    "Android returned without installing the target build"
                } else {
                    "Android installer did not report a result before the recovery timeout"
                }
            )
        )
    }

    private suspend fun finishGameInstallSuccessfully(pending: PendingGameInstaller) {
        if (!clearGameInstallerWait(pending)) return
        gameInstallCancellation = null
        gameInstallClient.clearDownload(pending.packageName, pending.versionCode)
        refreshGames(forceGameScan = true)
        val current = _gameInstallState.value
        _gameInstallState.value = current.copy(
            visible = true,
            inProgress = false,
            installing = false,
            completed = true,
            failed = false,
            stage = GameInstallStage.COMPLETED,
            stageProgress = 1f,
            headline = "Original game installed",
            detail = "You can now add it to your Jester Mods library.",
            diagnostics = appendDiagnostic(current.diagnostics, "Installed build verified on this device")
        )
    }

    private fun clearGameInstallerWait(expected: PendingGameInstaller? = null): Boolean {
        if (expected != null && pendingGameInstaller !== expected) return false
        gameInstallerRecovery?.cancel()
        gameInstallerRecovery = null
        pendingGameInstaller = null
        gameInstallerLeftLauncher = false
        return true
    }

    fun openLibraryGame(game: LibraryGame) {
        _selectedLibraryGame.value = game
        _selectedGame.value = game.game
        _updateState.value = ModuleUpdateUiState()
        _gameDataResetState.value = GameDataResetUiState()
        _launchState.value = LaunchUiState()
        if (!_packageSetupState.value.inProgress) {
            _packageSetupState.value = PackageSetupUiState()
        }
    }

    fun closeGame() {
        _selectedLibraryGame.value = null
        _selectedGame.value = null
        _updateState.value = ModuleUpdateUiState()
        _gameDataResetState.value = GameDataResetUiState()
        _launchState.value = LaunchUiState()
        if (!_packageSetupState.value.inProgress) {
            _packageSetupState.value = PackageSetupUiState()
        }
    }

    fun updateLibraryGame(entry: LibraryGame) {
        when {
            entry.status in setOf(
                LibraryGameStatus.GAME_REQUIRED,
                LibraryGameStatus.UNSUPPORTED_VERSION,
                LibraryGameStatus.UNSUPPORTED_ABI
            ) && entry.listing != null -> openModuleDownload(entry.listing)
            entry.listing != null && entry.status in setOf(
                LibraryGameStatus.UPDATE_AVAILABLE,
                LibraryGameStatus.REPAIR_NEEDED
            ) -> installModule(entry.listing)
            entry.game != null -> updateModule(entry.game)
        }
    }

    fun launchLibraryGame(entry: LibraryGame) {
        entry.game?.let(::launchGame)
    }

    fun authorizePackageReplacement(game: InstalledGame, onAuthorized: () -> Unit) {
        if (!game.moduleSupported || _launchState.value.inProgress || _updateState.value.inProgress) return
        val kind = if (game.module.nonRootMethod == NonRootMethod.IDENTITY_SHELL) {
            PackageReplacementKind.IDENTITY_SHELL
        } else {
            PackageReplacementKind.DIRECT_PATCH
        }
        _packageSetupState.value = PackageSetupUiState(
            visible = true,
            inProgress = true,
            stage = SecureTransferStage.PREPARING,
            stageProgress = 0.08f,
            title = game.module.title,
            packageName = game.packageName,
            kind = kind,
            headline = "Checking access",
            detail = if (kind == PackageReplacementKind.IDENTITY_SHELL) {
                "Verifying this add-on before preparing the exact-package shell."
            } else {
                "Verifying this add-on before preparing the patched game."
            },
            diagnostics = listOf("Started protected package setup")
        )
        _launchState.value = LaunchUiState(
            inProgress = true,
            headline = "Checking access",
            detail = if (game.module.nonRootMethod ==
                com.moodtools.hub.modules.NonRootMethod.IDENTITY_SHELL) {
                "Verifying this add-on before preparing the exact-package shell."
            } else {
                "Verifying this add-on before preparing the patched game."
            }
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (!checkAccessForNewProtectedAction(
                    ProtectedActionBoundary.GAME_LAUNCH,
                    game.packageName
                )) {
                val launchFailure = _launchState.value
                _packageSetupState.update { current ->
                    current.copy(
                        inProgress = false,
                        failed = true,
                        stage = SecureTransferStage.FAILED,
                        stageProgress = null,
                        headline = launchFailure.headline ?: "Couldn't check access",
                        detail = launchFailure.detail,
                        diagnostics = appendDiagnostic(current.diagnostics, "Access check did not complete")
                    )
                }
                return@launch
            }
            withContext(Dispatchers.Main) { onAuthorized() }
        }
    }

    fun onPackageReplacementProgress(headline: String, detail: String) {
        val stage = packageSetupStage(headline)
        _packageSetupState.update { current ->
            current.copy(
                visible = true,
                inProgress = true,
                cancelling = false,
                cancelled = false,
                completed = false,
                failed = false,
                stage = stage,
                stageProgress = packageSetupStageProgress(headline, stage),
                headline = headline,
                detail = detail,
                diagnostics = appendDiagnostic(current.diagnostics, "$headline: $detail")
            )
        }
        _launchState.value = LaunchUiState(
            inProgress = true,
            headline = headline,
            detail = detail
        )
    }

    fun showDirectPatchPrompt(
        title: String,
        replacesOriginal: Boolean,
        kind: PackageReplacementKind
    ) {
        _directPatchPromptState.value = DirectPatchPromptUiState(
            visible = true,
            title = title,
            replacesOriginal = replacesOriginal,
            kind = kind
        )
        _packageSetupState.update { current ->
            current.copy(
                visible = true,
                inProgress = false,
                stage = SecureTransferStage.VERIFYING,
                stageProgress = 1f,
                title = title,
                kind = kind,
                headline = if (kind == PackageReplacementKind.IDENTITY_SHELL) {
                    "Shell verified and ready"
                } else {
                    "Patch verified and ready"
                },
                detail = "Review the Android replacement steps before continuing.",
                diagnostics = appendDiagnostic(current.diagnostics, "Prepared package passed verification")
            )
        }
    }

    fun showLegacyPatchMigration(title: String) {
        _launchState.value = LaunchUiState()
        _packageSetupState.value = PackageSetupUiState(
            visible = true,
            title = title,
            kind = PackageReplacementKind.IDENTITY_SHELL,
            stage = SecureTransferStage.READY,
            headline = "Official game restoration required",
            detail = "The previous direct patch must be removed before the exact-package shell can be created.",
            diagnostics = listOf("Detected a previous Jester direct-patch installation")
        )
        _directPatchPromptState.value = DirectPatchPromptUiState(
            visible = true,
            title = title,
            replacesOriginal = true,
            kind = PackageReplacementKind.IDENTITY_SHELL,
            restoresOfficialGame = true
        )
    }

    fun hideDirectPatchPrompt() {
        _directPatchPromptState.value = DirectPatchPromptUiState()
    }

    fun dismissPackageSetup() {
        if (!_packageSetupState.value.inProgress) {
            _packageSetupState.value = PackageSetupUiState()
        }
    }

    private fun packageSetupStage(headline: String): SecureTransferStage {
        val normalized = headline.lowercase()
        return when {
            "waiting for android" in normalized -> SecureTransferStage.WAITING_FOR_ANDROID
            "installing" in normalized -> SecureTransferStage.ACTIVATING
            "ready" in normalized || "verifying" in normalized -> SecureTransferStage.VERIFYING
            else -> SecureTransferStage.PREPARING
        }
    }

    private fun packageSetupStageProgress(
        headline: String,
        stage: SecureTransferStage
    ): Float? {
        if (stage == SecureTransferStage.ACTIVATING ||
            stage == SecureTransferStage.WAITING_FOR_ANDROID) return null
        val normalized = headline.lowercase()
        return when {
            "ready" in normalized -> 1f
            "verifying" in normalized -> 0.9f
            "signing" in normalized -> 0.78f
            "creating" in normalized || "patching" in normalized -> 0.58f
            "staging" in normalized || "inspecting" in normalized -> 0.3f
            "finishing" in normalized -> 0.88f
            "preparing" in normalized -> 0.18f
            "checking" in normalized -> 0.08f
            else -> 0.12f
        }
    }

    fun onLegacyPatchMigrationStarted(title: String) {
        onPackageReplacementProgress(
            "Waiting for Android",
            "Confirm the $title uninstall. Its Browse add-ons requirements will open next."
        )
        _launchState.value = LaunchUiState(
            inProgress = true,
            headline = "Waiting for Android",
            detail = "Confirm the $title uninstall. Its Browse add-ons requirements will open next."
        )
    }

    fun onLegacyPatchMigrationReconciling(title: String) {
        onPackageReplacementProgress(
            "Finishing $title cleanup",
            "Android confirmed the uninstall. Checking that the previous patch is fully removed."
        )
        _launchState.value = LaunchUiState(
            inProgress = true,
            headline = "Finishing $title cleanup",
            detail = "Android confirmed the uninstall. Checking that the previous patch is fully removed."
        )
    }

    fun onLegacyPatchMigrationCancelled(title: String) {
        _packageSetupState.update { current ->
            current.copy(
                visible = true,
                inProgress = false,
                cancelled = true,
                stage = SecureTransferStage.CANCELLED,
                stageProgress = null,
                headline = "$title wasn't changed",
                detail = "The previous patch remains installed. You can restore the official game whenever you're ready.",
                diagnostics = appendDiagnostic(current.diagnostics, "Setup cancelled before Android changed the package")
            )
        }
        _launchState.value = LaunchUiState(
            headline = "$title wasn't changed",
            detail = "The previous patch remains installed. You can restore the official game whenever you're ready."
        )
    }

    fun onLegacyPatchMigrationFailed(title: String, detail: String) {
        _packageSetupState.update { current ->
            current.copy(
                visible = true,
                inProgress = false,
                failed = true,
                stage = SecureTransferStage.FAILED,
                stageProgress = null,
                headline = "$title migration needs attention",
                detail = detail,
                diagnostics = appendDiagnostic(current.diagnostics, "Migration failed: $detail")
            )
        }
        _launchState.value = LaunchUiState(
            headline = "$title migration needs attention",
            detail = detail,
            failed = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            refreshGames(forceGameScan = true)
        }
    }

    fun onLegacyPatchRemoved(entry: LibraryGame) {
        val packageName = entry.packageName
        val listing = _availableModules.value.firstOrNull {
            it.catalog.config.packageName == packageName
        } ?: entry.listing

        _selectedLibraryGame.value = null
        _selectedGame.value = null
        _moduleBrowserOpen.value = true
        _downloadListing.value = listing?.copy(game = null)
        _updateState.value = ModuleUpdateUiState(
            totalBytes = listing?.let(::moduleDownloadSize) ?: 0L
        )
        _gameInstallState.value = GameInstallUiState(
            headline = "Choose the original game source",
            detail = "The previous ${entry.title} patch was removed safely. Download its companion game when offered, or choose Google Play."
        )
        _packageSetupState.value = PackageSetupUiState()
        _launchState.value = LaunchUiState()
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.onDirectPatchMigrationSucceeded(packageName)
            refreshGames(refreshCatalog = listing == null, forceGameScan = true)
            if (listing == null && _moduleBrowserOpen.value && _downloadListing.value == null) {
                _availableModules.value.firstOrNull {
                    it.catalog.config.packageName == packageName
                }?.let { resolved ->
                    _downloadListing.value = resolved.copy(game = null)
                    _updateState.value = ModuleUpdateUiState(
                        totalBytes = moduleDownloadSize(resolved)
                    )
                }
            }
        }
    }

    fun onPackageReplacementFailed(detail: String, title: String) {
        _packageSetupState.update { current ->
            current.copy(
                visible = true,
                inProgress = false,
                failed = true,
                stage = SecureTransferStage.FAILED,
                stageProgress = null,
                title = title,
                headline = "$title setup failed",
                detail = detail,
                diagnostics = appendDiagnostic(current.diagnostics, "Setup failed: $detail")
            )
        }
        _launchState.value = LaunchUiState(
            headline = "$title setup failed",
            detail = detail,
            failed = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            refreshGames(forceGameScan = true)
        }
    }

    fun onPackageReplacementCancelled(title: String) {
        _packageSetupState.update { current ->
            current.copy(
                visible = true,
                inProgress = false,
                cancelling = false,
                cancelled = true,
                stage = SecureTransferStage.CANCELLED,
                stageProgress = null,
                title = title,
                headline = "$title setup paused",
                detail = "No Android package was changed. You can safely try again whenever you're ready.",
                diagnostics = appendDiagnostic(current.diagnostics, "Setup cancelled by the user")
            )
        }
        _launchState.value = LaunchUiState(
            headline = "$title wasn't changed",
            detail = "The prepared package remains available if you try again."
        )
    }

    fun onPackageReplacementInstalled(request: PackageReplacementRequest) {
        _packageSetupState.update { current ->
            current.copy(
                visible = true,
                inProgress = false,
                cancelling = false,
                completed = true,
                failed = false,
                cancelled = false,
                stage = SecureTransferStage.COMPLETED,
                stageProgress = 1f,
                title = request.title,
                packageName = request.packageName,
                kind = request.kind,
                headline = if (request.kind == PackageReplacementKind.IDENTITY_SHELL) {
                    "${request.title} shell installed"
                } else {
                    "${request.title} patch installed"
                },
                detail = if (request.kind == PackageReplacementKind.IDENTITY_SHELL) {
                    "The game-branded shell is ready. Open it here or directly from your home screen."
                } else {
                    "The patched game is ready. Open it from your Library when you want to play."
                },
                diagnostics = appendDiagnostic(current.diagnostics, "Installed package verified on this device")
            )
        }
        _launchState.value = LaunchUiState(
            headline = if (request.kind == PackageReplacementKind.IDENTITY_SHELL) {
                "${request.title} shell installed"
            } else {
                "${request.title} patch installed"
            },
            detail = if (request.kind == PackageReplacementKind.IDENTITY_SHELL) {
                "The game-branded shell is ready. Open it here or directly from your home screen."
            } else {
                "The patched game is ready. Open it from your Library when you want to play."
            }
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (request.kind == PackageReplacementKind.DIRECT_PATCH) {
                storageManager.onDirectPatchInstallSucceeded(request.packageName)
            }
            refreshGames(forceGameScan = true)
        }
    }

    fun removeFromLibrary(entry: LibraryGame) {
        removeLibraryEntries(listOf(entry))
    }

    fun clearGameData(entry: LibraryGame) {
        if (_gameDataResetState.value.inProgress || _launchState.value.inProgress ||
            _updateState.value.inProgress) return
        val rootMode = BuildConfig.IS_ROOT_MODE
        _gameDataResetState.value = GameDataResetUiState(
            inProgress = true,
            headline = if (rootMode) "Clearing game data" else "Clearing managed game data",
            detail = if (rootMode) {
                "Resetting the installed game to a fresh state."
            } else {
                "Removing the BlackBox installation and its virtual identity."
            }
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                ExecutionModeLaunchBridge.clearLibraryGameData(getApplication(), entry)
            }.onSuccess {
                libraryPreferences.edit().remove(lastLaunchKey(entry.packageName)).apply()
                _launchState.value = LaunchUiState()
                refreshGames(forceGameScan = true)
                _gameDataResetState.value = GameDataResetUiState(
                    completed = true,
                    headline = "Game data cleared",
                    detail = if (rootMode) {
                        "The next Play starts with fresh game data."
                    } else {
                        "The next Play will create a fresh managed installation."
                    }
                )
            }.onFailure { error ->
                android.util.Log.e(
                    "JesterMoodsLibrary",
                    "Could not clear managed data for ${entry.packageName}",
                    error
                )
                _gameDataResetState.value = GameDataResetUiState(
                    failed = true,
                    headline = "Couldn't clear game data",
                    detail = "Restart Jester Mods and try again."
                )
            }
        }
    }

    fun removeMultipleFromLibrary(entries: List<LibraryGame>) {
        removeLibraryEntries(entries)
    }

    private fun removeLibraryEntries(entries: List<LibraryGame>) {
        val targets = entries.distinctBy(LibraryGame::packageName)
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val removedPackages = linkedSetOf<String>()
            var failures = 0
            targets.forEach { entry ->
                runCatching {
                    ExecutionModeLaunchBridge.removeLibraryGameData(getApplication(), entry)
                    repository.removeFromLibrary(entry.packageName)
                }.onSuccess {
                    removedPackages += entry.packageName
                    runCatching { storageManager.onAddOnRemoved(entry.packageName) }
                        .onFailure { error ->
                            android.util.Log.e(
                                "JesterMoodsLibrary",
                                "Removed ${entry.packageName}, but could not reclaim its retry files",
                                error
                            )
                        }
                }.onFailure { error ->
                    failures += 1
                    android.util.Log.e(
                        "JesterMoodsLibrary",
                        "Could not remove ${entry.packageName}",
                        error
                    )
                }
            }
            if (removedPackages.isNotEmpty()) {
                libraryPreferences.edit().also { editor ->
                    removedPackages.forEach { editor.remove(lastLaunchKey(it)) }
                }.apply()
                if (_selectedLibraryGame.value?.packageName?.let(removedPackages::contains) == true) {
                    _selectedLibraryGame.value = null
                    _selectedGame.value = null
                }
                refreshGames(forceGameScan = true)
            }
            if (failures > 0) {
                _updateState.value = ModuleUpdateUiState(
                    headline = if (failures == 1) "Couldn't remove one add-on" else "Couldn't remove $failures add-ons",
                    detail = if (removedPackages.isEmpty()) {
                        "Restart Jester Mods and try the selection again."
                    } else {
                        "The other selected add-ons were removed. Restart Jester Mods and try the remaining selection again."
                    },
                    failed = true
                )
            }
        }
    }

    fun launchGame(game: InstalledGame) {
        if (!game.moduleSupported || _launchState.value.inProgress || _updateState.value.inProgress) return

        _launchState.value = LaunchUiState(
            inProgress = true,
            headline = "Getting things ready",
            detail = "This should only take a moment."
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (!checkAccessForNewProtectedAction(
                    ProtectedActionBoundary.GAME_LAUNCH,
                    game.packageName
                )) return@launch

            val launched = ExecutionModeLaunchBridge.launch(
                context = getApplication<android.app.Application>(),
                game = game
            ) { headline, detail ->
                val failed = headline == "Launch failed"
                _launchState.value = LaunchUiState(
                    inProgress = !failed,
                    headline = headline,
                    detail = detail,
                    failed = failed
                )
            }

            if (launched) {
                val launchedAt = System.currentTimeMillis()
                libraryPreferences.edit().putLong(lastLaunchKey(game.packageName), launchedAt).apply()
                _libraryGames.value = sortLibraryGames(_libraryGames.value.map { entry ->
                    if (entry.packageName == game.packageName) {
                        entry.copy(lastLaunchedAtEpochMillis = launchedAt, running = true)
                    } else entry
                })
                _selectedLibraryGame.value = _libraryGames.value.firstOrNull {
                    it.packageName == game.packageName
                }
                _launchState.value = LaunchUiState(
                    headline = "Opening game",
                    detail = "${game.module.title} is starting now.",
                    gameLaunched = true
                )
            } else if (!_launchState.value.failed) {
                _launchState.value = LaunchUiState(
                    headline = "Couldn't open the game",
                    detail = "Try again. If it keeps happening, restart Jester Mods.",
                    failed = true
                )
            }
        }
    }

    fun updateModule(game: InstalledGame) {
        if (_updateState.value.inProgress) return
        if (_updateState.value.updateAvailable) {
            downloadModuleUpdate(game)
            return
        }
        val expectedBytes = moduleDownloadSize(game.packageName, game.abi)
        _updateState.value = ModuleUpdateUiState(
            inProgress = true,
            headline = "Checking for updates",
            detail = "Comparing your installed version with the latest available version.",
            totalBytes = expectedBytes
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                catalogClient.refresh()
            }.onSuccess { catalog ->
                publishGames(catalog, forceGameScan = false)
                val listing = _availableModules.value.firstOrNull {
                    it.catalog.config.packageName == game.packageName
                }
                if (listing != null && listing.installedBuild < listing.catalog.build) {
                    publishModuleUpdateOffer(listing)
                } else {
                    _updateState.value = ModuleUpdateUiState(
                        headline = "You're up to date",
                        detail = listing?.catalog?.version?.let {
                            "Version $it is the latest available version."
                        } ?: "No newer version is available for this game.",
                        completed = true
                    )
                }
            }.onFailure { error ->
                android.util.Log.e("JesterMoodsUpdate", "Update check failed", error)
                _updateState.value = ModuleUpdateUiState(
                    headline = "Couldn't check for updates",
                    detail = "Check your connection, then try again.",
                    failed = true,
                    totalBytes = expectedBytes
                )
            }
        }
    }

    private fun downloadModuleUpdate(game: InstalledGame) {
        val offeredState = _updateState.value
        val offeredChangelog = offeredState.changelog
        val listing = _availableModules.value.firstOrNull {
            it.catalog.config.packageName == game.packageName
        }
        val cancellation = SecureTransferCancellation()
        moduleTransferCancellation = cancellation
        _updateState.value = ModuleUpdateUiState(
            visible = true,
            inProgress = true,
            stage = SecureTransferStage.PREPARING,
            stageProgress = null,
            title = game.module.title,
            packageName = game.packageName,
            targetVersion = listing?.catalog?.version.orEmpty(),
            actionLabel = "Add-on update",
            headline = "Preparing update",
            detail = "Checking the download for ${game.module.title}.",
            changelog = offeredChangelog,
            totalBytes = offeredState.totalBytes.takeIf { it > 0L }
                ?: moduleDownloadSize(game.packageName, game.abi),
            diagnostics = listOf("Preparing ${game.module.title} update")
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                applyUpdate(
                    request = UpdateRequest(
                        packageName = game.packageName,
                        grant = null,
                        nonce = null,
                        buildHint = null
                    ),
                    progressHeadline = "Downloading update",
                    progressDetail = "Downloading the latest add-on for ${game.module.title}.",
                    isCancelled = { cancellation.cancelled }
                )
            }.onSuccess { result ->
                if (cancellation.cancelled) {
                    showModuleTransferCancelled()
                    return@onSuccess
                }
                moduleTransferCancellation = null
                refreshGames()
                val progress = _updateState.value
                _updateState.value = ModuleUpdateUiState(
                    visible = true,
                    stage = SecureTransferStage.COMPLETED,
                    stageProgress = 1f,
                    title = game.module.title,
                    packageName = game.packageName,
                    targetVersion = result.version.orEmpty(),
                    actionLabel = "Add-on update",
                    headline = "Update installed",
                    detail = buildString {
                        append(result.version?.let { "Version $it" } ?: "The latest version")
                        append(" is ready to use.")
                    },
                    completed = true,
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = progress.totalBytes,
                    changelog = offeredChangelog,
                    diagnostics = appendDiagnostic(progress.diagnostics, "Add-on update activated successfully")
                )
            }.onFailure { error ->
                if (cancellation.cancelled) showModuleTransferCancelled() else showUpdateFailure(error)
            }
        }
    }

    fun installModule(listing: ModuleListing) {
        val game = listing.game ?: return
        if (_updateState.value.inProgress) return
        if (!DeviceArchitectureGuard.supports(listing.catalog.config.supportedAbis)) {
            _updateState.value = ModuleUpdateUiState(
                headline = "This add-on isn't supported",
                detail = "Its architecture cannot run on this device's Android system.",
                failed = true
            )
            return
        }
        val action = when (listing.status) {
            com.moodtools.hub.modules.ModuleInstallStatus.UPDATE_AVAILABLE -> "update"
            com.moodtools.hub.modules.ModuleInstallStatus.BROKEN_INSTALL -> "repair"
            else -> "download"
        }
        if (action == "update" && !_updateState.value.updateAvailable) {
            _updateState.value = ModuleUpdateUiState(
                inProgress = true,
                headline = "Loading update details",
                detail = "Verifying the changelog for ${game.module.title}.",
                totalBytes = moduleDownloadSize(listing)
            )
            viewModelScope.launch(Dispatchers.IO) { publishModuleUpdateOffer(listing) }
            return
        }
        val offeredState = _updateState.value
        val offeredChangelog = offeredState.changelog
        val cancellation = SecureTransferCancellation()
        moduleTransferCancellation = cancellation
        _updateState.value = ModuleUpdateUiState(
            visible = true,
            inProgress = true,
            stage = SecureTransferStage.PREPARING,
            stageProgress = null,
            title = game.module.title,
            packageName = game.packageName,
            targetVersion = listing.catalog.version,
            actionLabel = when (action) {
                "update" -> "Add-on update"
                "repair" -> "Add-on repair"
                else -> "Add-on download"
            },
            headline = "Preparing download",
            detail = "Checking the files for ${game.module.title}.",
            changelog = offeredChangelog,
            totalBytes = offeredState.totalBytes.takeIf { it > 0L } ?: moduleDownloadSize(listing),
            diagnostics = listOf("Preparing ${game.module.title} $action")
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                applyUpdate(
                    request = UpdateRequest(
                        packageName = game.packageName,
                        grant = null,
                        nonce = null,
                        buildHint = null
                    ),
                    gameHint = game,
                    isCancelled = { cancellation.cancelled }
                )
            }.onSuccess { result ->
                if (cancellation.cancelled) {
                    showModuleTransferCancelled()
                    return@onSuccess
                }
                moduleTransferCancellation = null
                refreshGames()
                val progress = _updateState.value
                _updateState.value = ModuleUpdateUiState(
                    visible = true,
                    stage = SecureTransferStage.COMPLETED,
                    stageProgress = 1f,
                    title = game.module.title,
                    packageName = game.packageName,
                    targetVersion = result.version.orEmpty(),
                    actionLabel = when (action) {
                        "update" -> "Add-on update"
                        "repair" -> "Add-on repair"
                        else -> "Add-on download"
                    },
                    headline = when (action) {
                        "update" -> "Add-on updated"
                        "repair" -> "Add-on repaired"
                        else -> "Download complete"
                    },
                    detail = buildString {
                        append("${game.module.title} is ready in your library")
                        result.version?.let { append(" with Jester Mods $it") }
                        append(".")
                    },
                    completed = true,
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = progress.totalBytes,
                    changelog = offeredChangelog,
                    diagnostics = appendDiagnostic(progress.diagnostics, "Verified add-on activated successfully")
                )
            }.onFailure { error ->
                if (cancellation.cancelled) showModuleTransferCancelled() else showModuleDownloadFailure(error, action)
            }
        }
    }

    fun cancelModuleTransfer() {
        val current = _updateState.value
        if (current.stage == SecureTransferStage.ACTIVATING) return
        if (!current.inProgress && !current.cancelling) {
            dismissModuleTransfer()
            return
        }
        val cancellation = moduleTransferCancellation ?: return
        cancellation.cancelled = true
        _updateState.value = current.copy(
            cancelling = true,
            headline = "Stopping safely",
            detail = "Keeping the resumable package intact for a future retry.",
            diagnostics = appendDiagnostic(current.diagnostics, "Cancellation requested by the user")
        )
    }

    fun dismissModuleTransfer() {
        val current = _updateState.value
        if (current.inProgress || current.cancelling) return
        _updateState.value = current.copy(visible = false)
    }

    private fun showModuleTransferCancelled() {
        moduleTransferCancellation = null
        val current = _updateState.value
        _updateState.value = current.copy(
            visible = true,
            inProgress = false,
            cancelling = false,
            cancelled = true,
            completed = false,
            failed = false,
            stage = SecureTransferStage.CANCELLED,
            stageProgress = null,
            headline = "Add-on download paused",
            detail = "The partial package was kept safely, so the next attempt can resume faster.",
            diagnostics = appendDiagnostic(current.diagnostics, "Cancelled by the user")
        )
    }

    private fun publishModuleUpdateOffer(listing: ModuleListing) {
        val history = moduleChangelogClient.load(listing.catalog)
        val newEntries = history.entries.filter {
            it.build > listing.installedBuild && it.build <= listing.catalog.build
        }.ifEmpty { moduleChangelogClient.summary(listing.catalog).entries }
        _updateState.value = ModuleUpdateUiState(
            headline = "Update available",
            detail = "Version ${listing.catalog.version} is ready. Review everything that changed before downloading.",
            updateAvailable = true,
            changelog = newEntries,
            totalBytes = moduleDownloadSize(listing)
        )
    }

    private fun moduleDownloadSize(listing: ModuleListing): Long {
        val abi = listing.game?.abi
        return abi?.let(listing.catalog.downloadSizeByAbi::get)
            ?: listing.catalog.downloadSizeByAbi.values.distinct().singleOrNull()
            ?: 0L
    }

    private fun moduleDownloadSize(packageName: String, abi: String?): Long {
        val listing = _availableModules.value.firstOrNull {
            it.catalog.config.packageName == packageName
        } ?: return 0L
        return abi?.let(listing.catalog.downloadSizeByAbi::get)
            ?: moduleDownloadSize(listing)
    }

    fun handleDeepLink(uri: Uri?) {
        val link = uri ?: return
        if (isLocalTestStageLink(link)) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { importLocalTestModules(link) }
                    .onSuccess { imported ->
                        refreshGames(refreshCatalog = true, forceGameScan = true)
                        val first = imported.firstOrNull()
                        if (first != null) {
                            _selectedLibraryGame.value = _libraryGames.value.firstOrNull {
                                it.packageName == first
                            }
                            _selectedGame.value = _games.value.firstOrNull {
                                it.packageName == first
                            }
                        }
                        _updateState.value = ModuleUpdateUiState(
                            headline = "Local test module staged",
                            detail = "${imported.size} module${if (imported.size == 1) "" else "s"} copied from ADB test storage.",
                            completed = true
                        )
                    }
                    .onFailure { error ->
                        android.util.Log.e("JesterMoodsLocalTest", "Local staged module import failed", error)
                        _updateState.value = ModuleUpdateUiState(
                            headline = "Couldn't stage local test module",
                            detail = error.message ?: "Check the helper output, then try again.",
                            failed = true
                        )
                    }
            }
            return
        }
        if (isLauncherUnlock(link)) {
            if (_startupState.value is LauncherStartupState.RootDenied ||
                _startupState.value is LauncherStartupState.CheckingRoot) return
            viewModelScope.launch(Dispatchers.IO) { redeemLauncherAccess(link) }
            return
        }
        if (_startupState.value !is LauncherStartupState.Ready) return
        handleUpdateDeepLink(link)
    }

    private fun isLocalTestStageLink(uri: Uri?): Boolean = uri?.scheme.equals("moodtools-local-test", true) &&
        uri?.host.equals("stage", true)

    private fun importLocalTestModules(link: Uri): List<String> {
        val token = link.getQueryParameter("token")
            ?.takeIf { it.matches(LOCAL_TEST_TOKEN_PATTERN) }
            ?: error("Local test token is missing or invalid")
        val packages = link.getQueryParameter("packages")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() && it.size <= 100 }
            ?: error("Local test package list is missing")
        require(packages.all { it.matches(PACKAGE_PATTERN) }) {
            "Local test package list contains an invalid package name"
        }

        val application = getApplication<android.app.Application>()
        val externalRoot = File(
            application.getExternalFilesDir(null) ?: error("External app test storage is unavailable"),
            LOCAL_TEST_STAGE_DIR
        ).canonicalFile
        val tokenFile = File(externalRoot, LOCAL_TEST_TOKEN_FILE).canonicalFile
        require(tokenFile.parentFile == externalRoot && tokenFile.isFile) {
            "Local test token file is missing"
        }
        require(tokenFile.readText(Charsets.UTF_8).trim() == token) {
            "Local test token did not match"
        }

        val menuRoot = File(application.filesDir, "menus").canonicalFile
        menuRoot.mkdirs()
        require(menuRoot.isDirectory) { "Launcher module storage is unavailable" }
        val imported = mutableListOf<String>()
        try {
            for (packageName in packages) {
                val source = File(externalRoot, packageName).canonicalFile
                require(source.parentFile == externalRoot && source.isDirectory) {
                    "Local test module is missing: $packageName"
                }
                val target = File(menuRoot, packageName).canonicalFile
                val next = File(menuRoot, "$packageName.local-test-next").canonicalFile
                require(target.parentFile == menuRoot && next.parentFile == menuRoot) {
                    "Local test target escaped launcher storage"
                }
                if (next.exists()) check(next.deleteRecursively()) {
                    "Could not clear previous local test staging for $packageName"
                }
                check(next.mkdirs()) { "Could not create local test staging for $packageName" }
                copyLocalTestFile(source, next, "config.json")
                copyLocalTestFile(source, next, "classes.dex")
                copyLocalTestFile(source, next, "libmenu_native.so")
                validateLocalTestConfig(packageName, File(next, "config.json"))
                File(next, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).writeText(
                    JSONObject()
                        .put("schema", 1)
                        .put("packageName", packageName)
                        .put("createdAt", System.currentTimeMillis())
                        .toString()
                )
                if (target.exists()) check(target.deleteRecursively()) {
                    "Could not replace existing module $packageName"
                }
                check(next.renameTo(target)) { "Could not commit local test module $packageName" }
                imported += packageName
            }
        } finally {
            tokenFile.delete()
        }
        return imported
    }

    private fun copyLocalTestFile(sourceDirectory: File, targetDirectory: File, name: String) {
        val source = File(sourceDirectory, name).canonicalFile
        require(source.parentFile == sourceDirectory && source.isFile && source.length() > 0L) {
            "Local test file is missing: $name"
        }
        source.copyTo(File(targetDirectory, name), overwrite = true)
    }

    private fun validateLocalTestConfig(packageName: String, configFile: File) {
        val json = JSONObject(configFile.readText(Charsets.UTF_8))
        val configuredPackage = json.optString("package_name", json.optString("target_package"))
        require(configuredPackage == packageName) {
            "Local test module config package does not match $packageName"
        }
        require(json.optString("dex_file", "classes.dex") == "classes.dex")
        require(json.optString("native_file", "libmenu_native.so") == "libmenu_native.so")
    }

    private fun handleUpdateDeepLink(link: Uri) {
        val validRoute = when (link.scheme?.lowercase()) {
            "moodtools-update" -> link.host.equals("resume", ignoreCase = true)
            "mymenu" -> link.host.equals("download", ignoreCase = true)
            else -> false
        }
        if (!validRoute) return

        val packageName = link.pathSegments.firstOrNull()
            ?.takeIf { it.matches(PACKAGE_PATTERN) }
            ?: return
        val grant = link.getQueryParameter("grant")
        val nonce = link.getQueryParameter("nonce")
        val buildHint = link.getQueryParameter("build")

        if (link.scheme.equals("moodtools-update", ignoreCase = true)
            && !validateReleaseReturn(packageName, nonce, grant, buildHint)) {
            _updateState.value = ModuleUpdateUiState(
                headline = "Verification expired",
                detail = "Please start the update again.",
                failed = true,
                totalBytes = moduleDownloadSize(packageName, null)
            )
            return
        }

        val request = UpdateRequest(
            packageName = packageName,
            grant = grant,
            nonce = nonce,
            buildHint = buildHint
        )
        viewModelScope.launch(Dispatchers.IO) {
            val matchingGame = _games.value.firstOrNull { it.packageName == packageName }
                ?: scanner.scan(repository.loadModules()).firstOrNull { it.packageName == packageName }
            if (matchingGame != null) {
                _selectedGame.value = matchingGame
                _selectedLibraryGame.value = _libraryGames.value.firstOrNull {
                    it.packageName == packageName
                }
            }
            val cancellation = SecureTransferCancellation()
            moduleTransferCancellation = cancellation
            _updateState.value = ModuleUpdateUiState(
                visible = true,
                inProgress = true,
                stage = SecureTransferStage.PREPARING,
                stageProgress = null,
                title = matchingGame?.module?.title.orEmpty(),
                packageName = packageName,
                actionLabel = "Add-on update",
                headline = "Finishing update",
                detail = "Getting everything ready.",
                totalBytes = moduleDownloadSize(packageName, matchingGame?.abi),
                diagnostics = listOf("Release verification completed in browser")
            )
            runCatching { applyUpdate(request, isCancelled = { cancellation.cancelled }) }
                .onSuccess { result ->
                    if (cancellation.cancelled) {
                        showModuleTransferCancelled()
                        return@onSuccess
                    }
                    moduleTransferCancellation = null
                    clearReleaseGate()
                    val progress = _updateState.value
                    refreshGames()
                    _selectedGame.value = _games.value.firstOrNull { it.packageName == packageName }
                    _selectedLibraryGame.value = _libraryGames.value.firstOrNull {
                        it.packageName == packageName
                    }
                    _updateState.value = ModuleUpdateUiState(
                        visible = true,
                        stage = SecureTransferStage.COMPLETED,
                        stageProgress = 1f,
                        title = matchingGame?.module?.title.orEmpty(),
                        packageName = packageName,
                        targetVersion = result.version.orEmpty(),
                        actionLabel = "Add-on update",
                        headline = "Update complete",
                        detail = buildString {
                            append(result.version?.let { "Version $it" } ?: "The latest version")
                            append(" is ready to play.")
                        },
                        completed = true,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                        diagnostics = appendDiagnostic(progress.diagnostics, "Add-on update activated successfully")
                    )
                }
                .onFailure { error ->
                    if (cancellation.cancelled) showModuleTransferCancelled() else showUpdateFailure(error)
                }
        }
    }

    private fun applyUpdate(
        request: UpdateRequest,
        gameHint: InstalledGame? = null,
        progressHeadline: String = "Downloading add-on",
        progressDetail: String? = null,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
        onStage: ((SecureTransferStage) -> Unit)? = null,
        onDiagnostic: ((String) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): com.moodtools.hub.networking.UpdateResult {
        require(repository.embeddedPrivateScope(request.packageName) == null) {
            "The embedded private add-on can only be updated by installing a new signed launcher build that contains it."
        }
        val game = gameHint?.takeIf { it.packageName == request.packageName }
            ?: _games.value.firstOrNull { it.packageName == request.packageName }
            ?: scanner.scan(repository.loadModules()).firstOrNull { it.packageName == request.packageName }
            ?: error("Installed game could not be inspected")
        check(DeviceArchitectureGuard.supports(game.module.supportedAbis)) {
            "Add-on architecture is not supported by this device"
        }
        check(game.abiSupported) {
            "Add-on architecture does not support the installed game"
        }
        if (!checkAccessForNewProtectedAction(
                ProtectedActionBoundary.MODULE_DOWNLOAD,
                request.packageName
            )) {
            throw NewSessionAccessBoundaryException()
        }
        val abi = game.abi.takeIf { it == "arm64-v8a" || it == "armeabi-v7a" }
            ?: error("Unsupported or unknown game architecture: ${architectureLabel(game.abi)}")
        val moduleDirectory = File(
            getApplication<android.app.Application>().filesDir,
            "menus/${request.packageName}"
        )
        val client = UpdateClient(moduleDirectory)
        val publishProgress: (Long, Long) -> Unit = { downloaded, total ->
            if (onProgress != null) {
                onProgress(downloaded, total)
            } else {
                val current = _updateState.value
                _updateState.value = current.copy(
                    visible = true,
                    inProgress = true,
                    stage = if (downloaded >= total && total > 0L) {
                        SecureTransferStage.VERIFYING
                    } else {
                        SecureTransferStage.DOWNLOADING
                    },
                    stageProgress = if (downloaded >= total && total > 0L) 0f else null,
                    headline = progressHeadline,
                    detail = progressDetail ?: "Getting ${game.module.title} ready for Jester Mods.",
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            }
        }
        fun downloadWithFreshAuthorization() = client.applyStandalone(
            request.packageName,
            abi,
            accessManager.authorizeModule(request.packageName, abi),
            onProgress = publishProgress,
            onStage = { clientStage ->
                val stage = when (clientStage) {
                    StandaloneUpdateStage.PREPARING -> SecureTransferStage.PREPARING
                    StandaloneUpdateStage.DOWNLOADING -> SecureTransferStage.DOWNLOADING
                    StandaloneUpdateStage.VERIFYING -> SecureTransferStage.VERIFYING
                    StandaloneUpdateStage.ACTIVATING -> SecureTransferStage.ACTIVATING
                }
                if (onStage != null) {
                    onStage(stage)
                } else {
                    _updateState.update { state ->
                        state.copy(
                            visible = true,
                            inProgress = true,
                            stage = stage,
                            stageProgress = when (stage) {
                                SecureTransferStage.PREPARING,
                                SecureTransferStage.VERIFYING -> null
                                else -> null
                            },
                            headline = when (stage) {
                                SecureTransferStage.PREPARING -> "Preparing secure download"
                                SecureTransferStage.DOWNLOADING -> progressHeadline
                                SecureTransferStage.VERIFYING -> "Verifying add-on"
                                SecureTransferStage.ACTIVATING -> "Activating add-on"
                                else -> state.headline
                            },
                            detail = when (stage) {
                                SecureTransferStage.PREPARING -> "Authorizing the signed package for this device."
                                SecureTransferStage.VERIFYING -> "Proving package integrity and signed identity."
                                SecureTransferStage.ACTIVATING -> "Switching safely to the verified add-on files."
                                else -> progressDetail ?: state.detail
                            }
                        )
                    }
                }
            },
            onDiagnostic = { message ->
                if (onDiagnostic != null) {
                    onDiagnostic(message)
                } else {
                    _updateState.update { state ->
                        state.copy(diagnostics = appendDiagnostic(state.diagnostics, message))
                    }
                }
            },
            isCancelled = isCancelled
        )
        return try {
            downloadWithFreshAuthorization()
        } catch (expired: ModuleDownloadAuthorizationExpired) {
            android.util.Log.w(
                "JesterMoodsDownload",
                "Module authorization expired during transfer; renewing it once.",
                expired
            )
            downloadWithFreshAuthorization()
        }
    }

    private fun checkAccessForNewProtectedAction(
        action: ProtectedActionBoundary,
        packageName: String
    ): Boolean {
        if (debugAccessBypassActive) {
            return true
        }
        val leaseResult = runCatching { accessManager.currentLease() }
        return when (NewSessionAccessPolicy.decide(
            lease = leaseResult.getOrNull(),
            failure = leaseResult.exceptionOrNull()
        )) {
            NewSessionAccessDecision.ALLOW ->
                checkPrivateAccessForNewProtectedAction(action, packageName)
            NewSessionAccessDecision.REQUIRE_UNLOCK -> {
                _launcherEntered.value = false
                _moduleBrowserOpen.value = false
                _downloadListing.value = null
                _launchState.value = LaunchUiState()
                _updateState.value = ModuleUpdateUiState()
                _startupState.value = LauncherStartupState.Locked(
                    "Your access has expired. Unlock again to ${action.description}. " +
                        "Any game you already have open will keep running."
                )
                false
            }
            NewSessionAccessDecision.RETRY -> {
                android.util.Log.e(
                    "JesterMoodsAccess",
                    "Digital key check failed before a new protected action",
                    leaseResult.exceptionOrNull()
                )
                val detail = "Check your connection, then try again. Any game already open will keep running."
                when (action) {
                    ProtectedActionBoundary.GAME_LAUNCH -> {
                        _launchState.value = LaunchUiState(
                            headline = "Couldn't check your access",
                            detail = detail,
                            failed = true
                        )
                    }
                    ProtectedActionBoundary.MODULE_DOWNLOAD -> {
                        val current = _updateState.value
                        _updateState.value = ModuleUpdateUiState(
                            headline = "Couldn't check your access",
                            detail = detail,
                            failed = true,
                            totalBytes = current.totalBytes
                        )
                    }
                }
                false
            }
        }
    }

    private fun checkPrivateAccessForNewProtectedAction(
        action: ProtectedActionBoundary,
        packageName: String
    ): Boolean {
        val scope = privateScopeForPackage(packageName) ?: return true
        val result = accessManager.checkPrivateAccess(scope)
        if (result is LauncherPrivateAccessResult.Approved) {
            if (privateAccessExpiryByScope[scope] != result.lease.grantExpiresAt) {
                privateAccessExpiryByScope = privateAccessExpiryByScope + (scope to result.lease.grantExpiresAt)
                refreshGames()
            }
            return true
        }

        val denied = result === LauncherPrivateAccessResult.Denied
        if (denied) {
            privateAccessExpiryByScope = privateAccessExpiryByScope - scope
            refreshGames()
        } else if (result is LauncherPrivateAccessResult.Unavailable) {
            android.util.Log.e(
                "JesterMoodsPrivateAccess",
                "Private approval check failed before a protected action; retaining local support",
                result.error
            )
        }
        val detail = if (denied) {
            "This device is not approved for this private add-on. Send the support code to the owner."
        } else {
            "Jester Mods could not verify this private add-on approval. Connect and try again."
        }
        when (action) {
            ProtectedActionBoundary.GAME_LAUNCH -> _launchState.value = LaunchUiState(
                headline = "Private add-on locked",
                detail = detail,
                failed = true
            )
            ProtectedActionBoundary.MODULE_DOWNLOAD -> _updateState.value = ModuleUpdateUiState(
                headline = "Private add-on locked",
                detail = detail,
                failed = true
            )
        }
        return false
    }

    private fun privateScopeForPackage(packageName: String): String? =
        _availableModules.value.firstOrNull { it.catalog.config.packageName == packageName }
            ?.catalog?.privateScope
            ?: if (BuildConfig.PRIVATE_MODULE_ENABLED &&
            packageName == BuildConfig.PRIVATE_MODULE_PACKAGE) {
            BuildConfig.PRIVATE_MODULE_SCOPE
        } else {
            repository.privateScope(packageName)
        }

    private fun beginReleaseVerification(packageName: String, gate: ReleaseVerificationRequired) {
        val nonceBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val nonce = Base64.encodeToString(
            nonceBytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        gatePreferences.edit()
            .putString(GATE_PACKAGE, packageName)
            .putString(GATE_NONCE, nonce)
            .putLong(GATE_BUILD, gate.updateBuild)
            .putLong(GATE_EXPIRES, System.currentTimeMillis() + RELEASE_GATE_TTL_MS)
            .apply()
        val verificationUrl = "https://jester.moodtools.workers.dev${gate.releasePath}?nonce=" +
            URLEncoder.encode(nonce, Charsets.UTF_8.name())
        _updateState.value = ModuleUpdateUiState(
            headline = "One more step",
            detail = "Complete the quick verification, then Jester Mods will finish the update.",
            verificationUrl = verificationUrl,
            totalBytes = moduleDownloadSize(packageName, null)
        )
    }

    private fun validateReleaseReturn(
        packageName: String,
        nonce: String?,
        grant: String?,
        buildHint: String?
    ): Boolean {
        val build = buildHint?.toLongOrNull() ?: return false
        val valid = packageName == gatePreferences.getString(GATE_PACKAGE, null)
            && build > 0
            && build == gatePreferences.getLong(GATE_BUILD, -1L)
            && System.currentTimeMillis() <= gatePreferences.getLong(GATE_EXPIRES, 0L)
            && nonce != null
            && nonce.matches(NONCE_PATTERN)
            && nonce == gatePreferences.getString(GATE_NONCE, null)
            && grant != null
            && grant.length <= MAX_RELEASE_GRANT_CHARS
            && grant.matches(GRANT_PATTERN)
        if (!valid && System.currentTimeMillis() > gatePreferences.getLong(GATE_EXPIRES, 0L)) {
            clearReleaseGate()
        }
        return valid
    }

    private fun clearReleaseGate() {
        gatePreferences.edit().clear().apply()
    }

    private fun showUpdateFailure(error: Throwable) {
        moduleTransferCancellation = null
        if (error is NewSessionAccessBoundaryException) return
        android.util.Log.e("JesterMoodsUpdate", "Update failed", error)
        val progress = _updateState.value
        _updateState.value = progress.copy(
            visible = true,
            inProgress = false,
            cancelling = false,
            cancelled = false,
            headline = "Couldn't finish the update",
            detail = "Please try again. If it keeps happening, restart Jester Mods.",
            failed = true,
            stage = SecureTransferStage.FAILED,
            stageProgress = null,
            diagnostics = appendDiagnostic(
                progress.diagnostics,
                "${error.javaClass.simpleName}: ${error.message?.take(240) ?: "No detail provided"}"
            )
        )
    }

    private fun showModuleDownloadFailure(error: Throwable, action: String) {
        moduleTransferCancellation = null
        if (error is NewSessionAccessBoundaryException) return
        android.util.Log.e("JesterMoodsDownload", "Game support $action failed", error)
        val progress = _updateState.value
        val detail = moduleDownloadFailureDetail(error)
        _updateState.value = progress.copy(
            visible = true,
            inProgress = false,
            cancelling = false,
            cancelled = false,
            headline = when (action) {
                "update" -> "Couldn't update add-on"
                "repair" -> "Couldn't repair add-on"
                else -> "Couldn't download add-on"
            },
            detail = detail,
            failed = true,
            updateAvailable = action == "update",
            stage = SecureTransferStage.FAILED,
            stageProgress = null,
            diagnostics = appendDiagnostic(
                progress.diagnostics,
                "${error.javaClass.simpleName}: ${error.message?.take(240) ?: "No detail provided"}"
            )
        )
    }

    private fun moduleDownloadFailureDetail(error: Throwable): String {
        val serviceError = generateSequence(error) { it.cause }
            .filterIsInstance<LauncherServiceException>()
            .firstOrNull()
        return when (serviceError?.code) {
            "ACCESS_REQUIRED", "ACCESS_EXPIRED" ->
                "Your launcher access has expired. Unlock the launcher again, then retry."
            "LAUNCHER_UPDATE_REQUIRED" ->
                "Install the latest Jester Mods Launcher update, then retry."
            "PROOF_KEY_REQUIRED", "PROOF_REJECTED" ->
                "The server could not verify this launcher session. Restart the launcher or unlock it again."
            "ATTESTATION_REQUIRED" ->
                "This device could not complete the security check required for this add-on."
            "MODULE_UNAVAILABLE" ->
                "This add-on is not available for your device architecture or launcher version."
            else -> serviceError?.message
                ?: "Check your connection and available storage, then try again."
        }
    }

    private fun refreshGames(
        refreshCatalog: Boolean = false,
        forceGameScan: Boolean = false
    ) {
        if (_libraryGames.value.isEmpty()) _libraryLoading.value = true
        val cachedCatalog = catalogClient.loadCached()
        if (cachedCatalog != null) {
            publishGames(cachedCatalog, forceGameScan)
        }

        if (refreshCatalog) {
            val refreshedCatalog = runCatching { catalogClient.refresh() }.getOrElse {
                android.util.Log.e("JesterMoodsCatalog", "Catalog refresh failed", it)
                null
            }
            if (refreshedCatalog != null && refreshedCatalog != cachedCatalog) {
                publishGames(refreshedCatalog, forceGameScan && cachedCatalog == null)
            } else if (refreshedCatalog != null && cachedCatalog == null) {
                publishGames(refreshedCatalog, forceGameScan)
            }
            if (refreshedCatalog != null || cachedCatalog != null) return
        } else if (cachedCatalog != null) {
            return
        }

        publishGames(emptyList(), forceGameScan)
    }

    /**
     * Builds the first frame only from verified files and Android package state. This method must
     * stay network-free: it runs before the launcher Library becomes visible.
     */
    private fun hydrateCachedGames(
        catalog: List<com.moodtools.hub.modules.CatalogModule> = catalogClient.loadCached().orEmpty()
    ) {
        publishGames(
            catalog = catalog,
            forceGameScan = true,
            refreshRemoteMetadata = false
        )
    }

    private fun playStoreStatusesFor(
        catalog: List<com.moodtools.hub.modules.CatalogModule>
    ): Map<String, PlayStoreVersionStatus> {
        if (catalog.isEmpty()) return emptyMap()
        val today = currentLocalDay()
        val statuses = mutableMapOf<String, PlayStoreVersionStatus>()
        val editor = playStorePreferences.edit()
        var changed = false
        catalog.forEach { module ->
            val packageName = module.config.packageName
            val cached = cachedPlayStoreStatus(packageName, today)
            val fresh = runCatching { playStoreVersionClient.load(packageName) }
                .onFailure {
                    android.util.Log.w(
                        "JesterMoodsPlayStore",
                        "Could not check Play Store version for $packageName",
                        it
                    )
                }
                .getOrNull()
            if (fresh != null) {
                val received = PlayStoreVersionStatus(
                    latestVersion = fresh.version,
                    listingUpdatedAtEpochSeconds = fresh.listingUpdatedAtEpochSeconds,
                    updateAvailable = fresh.updateAvailable,
                    checkedAtEpochSeconds = fresh.checkedAtEpochSeconds,
                    checkedDay = today,
                    stale = fresh.stale
                )
                val status = newestPlayStoreStatus(cached, received)
                statuses[packageName] = status
                if (status.latestVersion != null) editor.putString(playStoreVersionKey(packageName), status.latestVersion)
                else editor.remove(playStoreVersionKey(packageName))
                if (status.listingUpdatedAtEpochSeconds != null) {
                    editor.putLong(
                        playStoreListingUpdatedAtKey(packageName),
                        status.listingUpdatedAtEpochSeconds
                    )
                } else editor.remove(playStoreListingUpdatedAtKey(packageName))
                editor.putInt(
                    playStoreUpdateAvailableKey(packageName),
                    when (status.updateAvailable) {
                        true -> 1
                        false -> 0
                        null -> -1
                    }
                )
                editor.putLong(playStoreCheckedAtKey(packageName), status.checkedAtEpochSeconds)
                editor.putLong(playStoreCheckedDayKey(packageName), status.checkedDay)
                editor.putBoolean(playStoreStaleKey(packageName), status.stale)
                changed = true
            } else if (cached != null) {
                statuses[packageName] = cached.copy(stale = true)
            }
        }
        if (changed) editor.apply()
        return statuses
    }

    private fun cachedPlayStoreStatus(packageName: String, today: Long): PlayStoreVersionStatus? {
        val version = playStorePreferences.getString(playStoreVersionKey(packageName), null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val listingUpdatedAt = playStorePreferences
            .getLong(playStoreListingUpdatedAtKey(packageName), 0L)
            .takeIf { it > 0L }
        if (version == null && listingUpdatedAt == null) return null
        val checkedAt = playStorePreferences.getLong(playStoreCheckedAtKey(packageName), 0L)
        val checkedDay = playStorePreferences.getLong(playStoreCheckedDayKey(packageName), Long.MIN_VALUE)
        if (checkedAt <= 0L || checkedDay == Long.MIN_VALUE) return null
        val updateAvailable = when (playStorePreferences.getInt(
            playStoreUpdateAvailableKey(packageName),
            -1
        )) {
            1 -> true
            0 -> false
            else -> null
        }
        return PlayStoreVersionStatus(
            latestVersion = version,
            listingUpdatedAtEpochSeconds = listingUpdatedAt,
            updateAvailable = updateAvailable,
            checkedAtEpochSeconds = checkedAt,
            checkedDay = checkedDay,
            stale = playStorePreferences.getBoolean(playStoreStaleKey(packageName), false) ||
                checkedDay != today
        )
    }

    private fun cachedPlayStoreStatusesFor(
        catalog: List<com.moodtools.hub.modules.CatalogModule>
    ): Map<String, PlayStoreVersionStatus> {
        val today = currentLocalDay()
        return catalog.asSequence()
            .mapNotNull { module ->
                cachedPlayStoreStatus(module.config.packageName, today)?.let { status ->
                    module.config.packageName to status
                }
            }
            .toMap()
    }

    private fun currentLocalDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / (24L * 60L * 60L * 1000L)
    }

    private fun playStoreCheckedDayKey(packageName: String): String = PLAY_STORE_DAY_PREFIX + packageName
    private fun playStoreVersionKey(packageName: String): String = PLAY_STORE_VERSION_PREFIX + packageName
    private fun playStoreCheckedAtKey(packageName: String): String = PLAY_STORE_CHECKED_AT_PREFIX + packageName
    private fun playStoreListingUpdatedAtKey(packageName: String): String =
        PLAY_STORE_LISTING_UPDATED_AT_PREFIX + packageName
    private fun playStoreUpdateAvailableKey(packageName: String): String =
        PLAY_STORE_UPDATE_AVAILABLE_PREFIX + packageName
    private fun playStoreStaleKey(packageName: String): String = PLAY_STORE_STALE_PREFIX + packageName

    private fun resolvePrivateAccessExpiries(
        catalog: List<com.moodtools.hub.modules.CatalogModule>
    ) {
        val now = System.currentTimeMillis() / 1_000L
        val unresolvedScopes = catalog.mapNotNull { it.privateScope }
            .distinct()
            .filter { scope -> (privateAccessExpiryByScope[scope] ?: 0L) <= now }
        if (unresolvedScopes.isEmpty()) return

        val resolved = privateAccessExpiryByScope.toMutableMap()
        unresolvedScopes.forEach { scope ->
            when (val result = accessManager.checkPrivateAccess(scope)) {
                is LauncherPrivateAccessResult.Approved -> resolved[scope] = result.lease.grantExpiresAt
                LauncherPrivateAccessResult.Denied -> resolved.remove(scope)
                is LauncherPrivateAccessResult.Unavailable -> android.util.Log.w(
                    "JesterMoodsPrivateAccess",
                    "The private access timer for $scope could not be refreshed.",
                    result.error
                )
            }
        }
        privateAccessExpiryByScope = resolved
    }

    private fun publishGames(
        catalog: List<com.moodtools.hub.modules.CatalogModule>,
        forceGameScan: Boolean,
        refreshRemoteMetadata: Boolean = true
    ) {
        val application = getApplication<android.app.Application>()
        if (refreshRemoteMetadata) resolvePrivateAccessExpiries(catalog)
        if (catalog.isEmpty()) {
            val localConfigs = repository.loadModules().filter { repository.isInLibrary(it.packageName) }
            val local = scanGames(localConfigs, forceGameScan)
            _games.value = local
            _availableModules.value = emptyList()
            val detectedByPackage = local.associateBy { it.packageName }
            _libraryGames.value = sortLibraryGames(localConfigs.map { config ->
                val game = detectedByPackage[config.packageName]
                LibraryGame(
                    module = config,
                    game = game,
                    listing = null,
                    installedBuild = repository.installedBuild(config.packageName),
                    installedComplete = repository.isInstalled(config.packageName),
                    localTest = repository.isLocalTest(config.packageName),
                    lastLaunchedAtEpochMillis = libraryPreferences.getLong(lastLaunchKey(config.packageName), 0L),
                    running = game != null && ExecutionModeLaunchBridge.isGameRunning(application, config.packageName),
                    launchAction = game?.let {
                        ExecutionModeLaunchBridge.libraryLaunchAction(application, it)
                    } ?: LibraryLaunchAction.PLAY,
                    privateScope = repository.privateScope(config.packageName),
                    privateAccessExpiresAtEpochSeconds = repository.privateScope(config.packageName)
                        ?.let(privateAccessExpiryByScope::get)
                )
            })
        } else {
            val localConfigs = repository.loadModules().filter {
                repository.isInLibrary(it.packageName)
            }
            val scanConfigs = mergeCatalogAndLocalModuleConfigs(
                catalog.map { it.config },
                localConfigs
            )
            val playStoreStatuses = if (refreshRemoteMetadata) {
                playStoreStatusesFor(catalog)
            } else {
                cachedPlayStoreStatusesFor(catalog)
            }
            val detected = scanGames(scanConfigs, forceGameScan)
            val detectedByPackage = detected.associateBy { it.packageName }
            val listings = catalog.map { item ->
                ModuleListing(
                    catalog = item,
                    game = detectedByPackage[item.config.packageName],
                    installedBuild = repository.installedBuild(item.config.packageName),
                    installedComplete = repository.isInstalled(item.config.packageName),
                    deviceArchitectureSupported = DeviceArchitectureGuard.supports(item.config.supportedAbis),
                    playStoreVersionStatus = playStoreStatuses[item.config.packageName],
                    privateAccessExpiresAtEpochSeconds = item.privateScope
                        ?.let(privateAccessExpiryByScope::get)
                )
            }
            _availableModules.value = listings
            _games.value = detected.filter { repository.isInstalled(it.packageName) }
            val catalogPackages = catalog.mapTo(hashSetOf()) { it.config.packageName }
            val catalogLibraryGames = listings
                .filter { repository.isInLibrary(it.catalog.config.packageName) }
                .map { listing ->
                    val packageName = listing.catalog.config.packageName
                    LibraryGame(
                        module = listing.game?.module ?: listing.catalog.config,
                        game = listing.game,
                        listing = listing,
                        installedBuild = listing.installedBuild,
                        installedComplete = listing.installedComplete,
                        localTest = repository.isLocalTest(packageName),
                        lastLaunchedAtEpochMillis = libraryPreferences.getLong(lastLaunchKey(packageName), 0L),
                        running = listing.game != null &&
                            ExecutionModeLaunchBridge.isGameRunning(application, packageName),
                        launchAction = listing.game?.let {
                            ExecutionModeLaunchBridge.libraryLaunchAction(application, it)
                        } ?: LibraryLaunchAction.PLAY,
                        playStoreVersionStatus = listing.playStoreVersionStatus
                    )
                }
            val localOnlyLibraryGames = localConfigs
                .filter { it.packageName !in catalogPackages }
                .map { config ->
                    val game = detectedByPackage[config.packageName]
                    LibraryGame(
                        module = config,
                        game = game,
                        listing = null,
                        installedBuild = repository.installedBuild(config.packageName),
                        installedComplete = repository.isInstalled(config.packageName),
                        localTest = repository.isLocalTest(config.packageName),
                        lastLaunchedAtEpochMillis = libraryPreferences.getLong(
                            lastLaunchKey(config.packageName),
                            0L
                        ),
                        running = game != null && ExecutionModeLaunchBridge.isGameRunning(
                            application,
                            config.packageName
                        ),
                        launchAction = game?.let {
                            ExecutionModeLaunchBridge.libraryLaunchAction(application, it)
                        } ?: LibraryLaunchAction.PLAY,
                        privateScope = repository.privateScope(config.packageName),
                        privateAccessExpiresAtEpochSeconds = repository.privateScope(config.packageName)
                            ?.let(privateAccessExpiryByScope::get)
                    )
                }
            _libraryGames.value = sortLibraryGames(catalogLibraryGames + localOnlyLibraryGames)
        }
        syncInstalledModuleUpdatePrompt()
        _libraryLoading.value = false
        val refreshed = _games.value
        val selectedPackage = _selectedGame.value?.packageName
        if (selectedPackage != null) {
            _selectedGame.value = refreshed.firstOrNull { it.packageName == selectedPackage }
        }
        val selectedLibraryPackage = _selectedLibraryGame.value?.packageName
        if (selectedLibraryPackage != null) {
            _selectedLibraryGame.value = _libraryGames.value.firstOrNull {
                it.packageName == selectedLibraryPackage
            }
        }
        val downloadPackage = _downloadListing.value?.catalog?.config?.packageName
        if (downloadPackage != null) {
            _downloadListing.value = _availableModules.value.firstOrNull {
                it.catalog.config.packageName == downloadPackage
            }
        }
    }

    private fun syncInstalledModuleUpdatePrompt() {
        if (BuildConfig.DEBUG && moduleUpdatesTestPreviewActive) {
            val updates = installedModuleUpdatePreviewGames()
            val updateKeys = updates.mapTo(mutableSetOf()) { it.installedModuleUpdateKey() }
            val current = _installedModuleUpdatesState.value
            if (current.open && (current.inProgress || current.itemStates.isNotEmpty())) {
                _installedModuleUpdatesState.value = current.copy(
                    packageNames = (current.packageNames + updates.map(LibraryGame::packageName)).distinct(),
                    previewUpdates = (current.previewUpdates + updates).distinctBy(LibraryGame::packageName)
                )
                return
            }
            _installedModuleUpdatesState.value = InstalledModuleUpdatesUiState(
                open = InstalledModuleUpdatePromptPolicy.shouldOpen(
                    updateKeys = updateKeys,
                    dismissedKeys = acknowledgedInstalledModuleUpdates,
                    currentlyOpen = current.open
                ),
                packageNames = updates.map(LibraryGame::packageName),
                previewUpdates = updates
            )
            return
        }
        val updates = installedModuleUpdates(_libraryGames.value)
        val current = _installedModuleUpdatesState.value
        if (updates.isEmpty()) {
            if (!current.open || (!current.inProgress && current.itemStates.isEmpty())) {
                _installedModuleUpdatesState.value = InstalledModuleUpdatesUiState()
            }
            return
        }
        if (current.open && (current.inProgress || current.itemStates.isNotEmpty())) {
            _installedModuleUpdatesState.value = current.copy(
                packageNames = (current.packageNames + updates.map(LibraryGame::packageName)).distinct(),
                previewUpdates = (current.previewUpdates + updates).distinctBy(LibraryGame::packageName)
            )
            return
        }
        val updateKeys = updates.mapTo(mutableSetOf()) { it.installedModuleUpdateKey() }
        _installedModuleUpdatesState.value = InstalledModuleUpdatesUiState(
            open = InstalledModuleUpdatePromptPolicy.shouldOpen(
                updateKeys = updateKeys,
                dismissedKeys = acknowledgedInstalledModuleUpdates,
                currentlyOpen = current.open
            ),
            packageNames = updates.map(LibraryGame::packageName)
        )
    }

    private fun LibraryGame.installedModuleUpdateKey(): String =
        "$packageName:${listing?.catalog?.build ?: installedBuild}"

    private fun installedModuleUpdatePreviewGames(): List<LibraryGame> {
        val catalogPreviews = _availableModules.value
            .sortedBy { it.catalog.config.title.lowercase() }
            .take(DEBUG_MODULE_UPDATE_PREVIEW_COUNT)
            .map { listing ->
                val availableBuild = maxOf(2L, listing.catalog.build)
                val installedBuild = availableBuild - 1L
                val previewListing = listing.copy(
                    game = null,
                    installedBuild = installedBuild,
                    installedComplete = true
                )
                LibraryGame(
                    module = previewListing.catalog.config,
                    game = null,
                    listing = previewListing,
                    installedBuild = installedBuild,
                    installedComplete = true
                )
            }
        if (catalogPreviews.size == DEBUG_MODULE_UPDATE_PREVIEW_COUNT) return catalogPreviews

        val existingPackages = catalogPreviews.mapTo(mutableSetOf(), LibraryGame::packageName)
        val fallbacks = listOf(
            debugModuleUpdatePreviewGame("debug.preview.township", "Township", 37L, 38L, "38.1.0", 18L),
            debugModuleUpdatePreviewGame("debug.preview.candy", "Candy Crush Saga", 62L, 64L, "1.312.0", 24L),
            debugModuleUpdatePreviewGame("debug.preview.airforce", "Airforce 1945", 14L, 17L, "14.02", 21L)
        )
        return (catalogPreviews + fallbacks.filter { existingPackages.add(it.packageName) })
            .take(DEBUG_MODULE_UPDATE_PREVIEW_COUNT)
    }

    private fun debugModuleUpdatePreviewGame(
        packageName: String,
        title: String,
        installedBuild: Long,
        availableBuild: Long,
        version: String,
        downloadMegabytes: Long
    ): LibraryGame {
        val config = com.moodtools.hub.modules.ModuleConfig(
            packageName = packageName,
            title = title,
            supportedVersions = setOf(version),
            supportedAbis = setOf("arm64-v8a"),
            entryPoint = null,
            dexFile = "classes.dex",
            nativeFile = "libmenu_native.so",
            iconFile = null
        )
        val catalog = com.moodtools.hub.modules.CatalogModule(
            config = config,
            slug = packageName.substringAfterLast('.'),
            build = availableBuild,
            version = version,
            notes = "Debug preview release",
            icon = null,
            installSource = GameInstallSource.PlayStore(
                "https://play.google.com/store/apps/details?id=$packageName"
            ),
            downloadSizeByAbi = mapOf("arm64-v8a" to downloadMegabytes * 1024L * 1024L)
        )
        val listing = ModuleListing(
            catalog = catalog,
            game = null,
            installedBuild = installedBuild,
            installedComplete = true
        )
        return LibraryGame(
            module = config,
            game = null,
            listing = listing,
            installedBuild = installedBuild,
            installedComplete = true
        )
    }

    private fun lastLaunchKey(packageName: String): String = "last_launch_$packageName"

    private fun scanGames(
        configs: List<com.moodtools.hub.modules.ModuleConfig>,
        force: Boolean
    ): List<InstalledGame> {
        if (!force && scannedConfigs == configs) return detectedGamesCache
        return scanner.scan(configs).also { detected ->
            scannedConfigs = configs
            detectedGamesCache = detected
        }
    }

    private fun isLauncherUnlock(uri: Uri?): Boolean = uri?.scheme.equals("moodtools-launcher", true) &&
        uri?.host.equals("unlock", true)

    private suspend fun redeemLauncherAccess(uri: Uri) {
        _startupState.value = LauncherStartupState.CheckingAccess
        val gateStartedAt = SystemClock.elapsedRealtime()
        val result = runCatching { accessManager.redeem(uri) }
        awaitMinimumAccessCheckGate(gateStartedAt)
        result
            .onSuccess { lease ->
                completeAuthorizedStartup(lease.expiresAt)
            }
            .onFailure { error ->
                android.util.Log.e("JesterMoodsAccess", "Digital key redemption failed", error)
                _startupState.value = LauncherStartupState.Locked(
                    "The digital key could not be accepted. Please start a fresh unlock and try again."
                )
            }
    }

    companion object {
        private const val GATE_PREFERENCES = "jester_moods_update_gate"
        private const val LIBRARY_PREFERENCES = "jester_moods_library"
        private const val PLAY_STORE_VERSION_PREFERENCES = "jester_moods_play_store_versions"
        private const val GATE_PACKAGE = "package"
        private const val GATE_NONCE = "nonce"
        private const val GATE_BUILD = "build"
        private const val GATE_EXPIRES = "expires"
        private const val PLAY_STORE_DAY_PREFIX = "checked_day_"
        private const val PLAY_STORE_VERSION_PREFIX = "latest_version_"
        private const val PLAY_STORE_CHECKED_AT_PREFIX = "checked_at_"
        private const val PLAY_STORE_LISTING_UPDATED_AT_PREFIX = "listing_updated_at_"
        private const val PLAY_STORE_UPDATE_AVAILABLE_PREFIX = "update_available_"
        private const val PLAY_STORE_STALE_PREFIX = "stale_"
        private const val RELEASE_GATE_TTL_MS = 20L * 60L * 1000L
        private const val MINIMUM_ACCESS_CHECK_GATE_MS = 2_000L
        private const val LAUNCHER_INSTALLER_RECOVERY_DELAY_MS = 20_000L
        private const val GAME_INSTALLER_RECONCILE_POLL_MS = 250L
        private const val GAME_INSTALLER_RETURN_RECONCILE_WINDOW_MS = 10_000L
        private const val GAME_INSTALLER_RECOVERY_DELAY_MS = 20_000L
        private const val MAX_GAME_INSTALL_DIAGNOSTICS = 32
        private const val DEBUG_ACCESS_DURATION_SECONDS = 24L * 60L * 60L
        private const val DEBUG_MODULE_UPDATE_PREVIEW_COUNT = 3
        private const val MAX_RELEASE_GRANT_CHARS = 4096
        private const val LOCAL_TEST_STAGE_DIR = "jester-local-modules"
        private const val LOCAL_TEST_TOKEN_FILE = "stage-token.txt"
        private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_.]{3,200}")
        private val LOCAL_TEST_TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{32,128}")
        private val NONCE_PATTERN = Regex("[A-Za-z0-9_-]{43}")
        private val GRANT_PATTERN = Regex("[A-Za-z0-9_.-]+")
    }
}
