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
import com.moodtools.hub.modules.GameInstallUiState
import com.moodtools.hub.modules.InstalledGame
import com.moodtools.hub.modules.LibraryGame
import com.moodtools.hub.modules.LibraryGameStatus
import com.moodtools.hub.modules.LibraryLaunchAction
import com.moodtools.hub.modules.LaunchUiState
import com.moodtools.hub.modules.LauncherUpdateUiState
import com.moodtools.hub.modules.ModuleRepository
import com.moodtools.hub.modules.ModuleListing
import com.moodtools.hub.modules.ModuleUpdateUiState
import com.moodtools.hub.modules.PlayStoreVersionStatus
import com.moodtools.hub.modules.DirectPatchPromptUiState
import com.moodtools.hub.modules.EmbeddedPrivateModuleInstaller
import com.moodtools.hub.modules.architectureLabel
import com.moodtools.hub.modules.sortLibraryGames
import com.moodtools.hub.modules.mergeCatalogAndLocalModuleConfigs
import com.moodtools.hub.networking.LauncherAccessManager
import com.moodtools.hub.networking.GameInstallClient
import com.moodtools.hub.networking.GameInstallEvents
import com.moodtools.hub.networking.GameInstallResult
import com.moodtools.hub.networking.LauncherRelease
import com.moodtools.hub.networking.LauncherChangelogEntry
import com.moodtools.hub.networking.LauncherUpdateClient
import com.moodtools.hub.networking.ModuleChangelogClient
import com.moodtools.hub.networking.ModuleCatalogClient
import com.moodtools.hub.networking.PlayStoreVersionClient
import com.moodtools.hub.networking.ReleaseVerificationRequired
import com.moodtools.hub.networking.SmartStorageManager
import com.moodtools.hub.networking.UpdateClient
import com.moodtools.hub.networking.UpdateRequest
import com.moodtools.hub.security.RuntimeSecurityGuard
import java.io.File
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Calendar
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
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

class LauncherActivity : ComponentActivity() {
    private val viewModel by viewModels<LauncherViewModel>()
    private var waitingForLauncherUnlock = false
    private var waitingForInstallerPermission = false
    private var waitingForGameInstallerPermission = false
    private var pendingGameInstall: ModuleListing? = null
    private var waitingForPackageReplacementPermission = false
    private var pendingPackageReplacement: PackageReplacementRequest? = null
    private var packageReplacementPhase = PackageReplacementPhase.IDLE
    private var packageReplacementReconciliation: Job? = null
    private var packageReplacementLeftLauncher = false
    private val packageUninstaller = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val request = pendingPackageReplacement ?: return@registerForActivityResult
        reconcilePackageUninstall(request, result.resultCode)
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
            debugLauncherUpdateTest = BuildConfig.DEBUG && intent?.getBooleanExtra(DEBUG_LAUNCHER_UPDATE_TEST_EXTRA, false) == true
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
                    gameInstallState = viewModel.gameInstallState,
                    launcherUpdateState = viewModel.launcherUpdateState,
                    changelogState = viewModel.changelogState,
                    accountIdentityState = viewModel.accountIdentityState,
                    launchState = viewModel.launchState,
                    directPatchPromptState = viewModel.directPatchPromptState,
                    onOpenGame = viewModel::openLibraryGame,
                    onBack = viewModel::closeGame,
                    onUpdate = viewModel::updateLibraryGame,
                    onVerify = ::openTrustedWebPage,
                    onLaunch = ::launchLibraryGame,
                    onConfirmDirectPatch = ::confirmPackageReplacementPrompt,
                    onDismissDirectPatch = ::cancelPackageReplacement,
                    onRemoveFromLibrary = viewModel::removeFromLibrary,
                    onRemoveMultipleFromLibrary = viewModel::removeMultipleFromLibrary,
                    onRefreshCatalog = viewModel::refreshCatalogAndLibrary,
                    onBrowse = viewModel::openModuleBrowser,
                    onCloseBrowser = viewModel::closeModuleBrowser,
                    onOpenDownload = viewModel::openModuleDownload,
                    onCloseDownload = viewModel::closeModuleDownload,
                    onInstall = viewModel::installModule,
                    onAcquireGame = ::acquireGame,
                    onOpenGameStore = ::openGameStore,
                    onOpenLauncherUpdate = viewModel::openLauncherUpdate,
                    onCloseLauncherUpdate = viewModel::closeLauncherUpdate,
                    onInstallLauncherUpdate = ::downloadOrInstallLauncherUpdate,
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
                    finishPackageReplacementSuccessfully()
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
        viewModel.onHostResumed(refreshGames = pendingPackageReplacement == null)
        if (waitingForLauncherUnlock) {
            waitingForLauncherUnlock = false
            viewModel.onUnlockBrowserReturnedWithoutCallback()
        }
        if (waitingForInstallerPermission) {
            waitingForInstallerPermission = false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
                viewModel.downloadAndInstallLauncherUpdate()
            } else {
                viewModel.onLauncherInstallPermissionDenied()
            }
        }
        if (waitingForGameInstallerPermission) {
            waitingForGameInstallerPermission = false
            val listing = pendingGameInstall
            pendingGameInstall = null
            if (listing != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    packageManager.canRequestPackageInstalls())) {
                viewModel.downloadAndInstallGame(listing)
            } else if (listing != null) {
                viewModel.onGameInstallPermissionDenied()
            }
        }
        if (waitingForPackageReplacementPermission) {
            waitingForPackageReplacementPermission = false
            val request = pendingPackageReplacement
            if (request != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    packageManager.canRequestPackageInstalls())) {
                showPackageReplacementWarning(request)
            } else if (request != null) {
                finishPackageReplacementWithFailure(
                    "Allow Jester Mods to install unknown apps before patching ${request.title}."
                )
            }
        }
        schedulePackageReplacementReconciliation()
    }

    override fun onPause() {
        if (packageReplacementPhase == PackageReplacementPhase.COMMITTING_INSTALL ||
            packageReplacementPhase == PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT) {
            packageReplacementLeftLauncher = true
        }
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
        if (pendingPackageReplacement != null) return
        packageReplacementPhase = PackageReplacementPhase.PREPARING
        val updatingPatchedInstall = entry.launchAction == LibraryLaunchAction.UPDATE_PATCHED_INSTALL
        viewModel.onPackageReplacementProgress(
            "Preparing ${game.module.title}",
            if (updatingPatchedInstall) {
                "Creating an in-place patch update that keeps the installed game's local data."
            } else {
                "Creating the patched non-root package before Android removes anything."
            }
        )
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                ExecutionModeLaunchBridge.preparePackageReplacement(
                    applicationContext,
                    game,
                    viewModel::onPackageReplacementProgress
                )
            }.onSuccess { request ->
                withContext(Dispatchers.Main) {
                    pendingPackageReplacement = request
                    packageReplacementPhase = PackageReplacementPhase.IDLE
                    requestPackageReplacementPermissionOrConfirm(request)
                }
            }.onFailure { error ->
                android.util.Log.e("JesterMoodsDirectPatch", "Could not prepare direct package patch", error)
                packageReplacementPhase = PackageReplacementPhase.IDLE
                viewModel.onPackageReplacementFailed(
                    error.message?.take(220)?.takeIf { it.isNotBlank() }
                        ?: "The ${game.module.title} patch could not be prepared.",
                    game.module.title
                )
            }
        }
    }

    private fun requestPackageReplacementPermissionOrConfirm(request: PackageReplacementRequest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()) {
            waitingForPackageReplacementPermission = true
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                )
            }.onFailure {
                waitingForPackageReplacementPermission = false
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
        viewModel.showDirectPatchPrompt(request.title, request.requiresUninstall)
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
            Intent.ACTION_UNINSTALL_PACKAGE,
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
            "Installing patched ${request.title}",
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
                            "Confirm the patched game installation. The launcher will detect when it finishes."
                        )
                        schedulePackageReplacementReconciliation()
                    }
                }
            }.onFailure { error ->
                android.util.Log.e("JesterMoodsDirectPatch", "Could not start patched install", error)
                withContext(Dispatchers.Main) {
                    if (pendingPackageReplacement === request) {
                        finishPackageReplacementWithFailure(
                            error.message?.take(220)?.takeIf { it.isNotBlank() }
                                ?: "Android could not start the patched ${request.title} installation."
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

    private fun schedulePackageReplacementReconciliation() {
        val request = pendingPackageReplacement ?: return
        if (packageReplacementPhase != PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT) return
        packageReplacementReconciliation?.cancel()
        packageReplacementReconciliation = lifecycleScope.launch {
            delay(
                if (packageReplacementLeftLauncher) {
                    PACKAGE_REPLACEMENT_RETURN_RECONCILE_DELAY_MS
                } else {
                    PACKAGE_REPLACEMENT_INSTALLER_OPEN_TIMEOUT_MS
                }
            )
            if (pendingPackageReplacement !== request ||
                packageReplacementPhase != PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            val deadline = SystemClock.elapsedRealtime() + PACKAGE_REPLACEMENT_INSTALL_RECONCILE_TIMEOUT_MS
            while (pendingPackageReplacement === request &&
                packageReplacementPhase == PackageReplacementPhase.WAITING_FOR_INSTALL_RESULT &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
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
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                finishPackageReplacementWithFailure(
                    "The Android installation was cancelled or could not be confirmed. The prepared patch is still available."
                )
            }
        }
    }

    private fun finishPackageReplacementSuccessfully() {
        val request = pendingPackageReplacement ?: return
        packageReplacementReconciliation?.cancel()
        packageReplacementReconciliation = null
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
        pendingPackageReplacement = null
        packageReplacementPhase = PackageReplacementPhase.IDLE
        packageReplacementLeftLauncher = false
        viewModel.hideDirectPatchPrompt()
        viewModel.onPackageReplacementFailed(detail, title)
    }

    private fun cancelPackageReplacement() {
        val title = pendingPackageReplacement?.title ?: "Game"
        packageReplacementReconciliation?.cancel()
        packageReplacementReconciliation = null
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
            waitingForInstallerPermission = true
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                )
            }.onFailure {
                waitingForInstallerPermission = false
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
                    waitingForGameInstallerPermission = true
                    pendingGameInstall = listing
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                    )
                } else {
                    viewModel.downloadAndInstallGame(listing)
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

    private val _gameInstallState = MutableStateFlow(GameInstallUiState())
    val gameInstallState: StateFlow<GameInstallUiState> = _gameInstallState

    private val _launcherUpdateState = MutableStateFlow(LauncherUpdateUiState())
    val launcherUpdateState: StateFlow<LauncherUpdateUiState> = _launcherUpdateState

    private val _changelogState = MutableStateFlow(ChangelogUiState())
    val changelogState: StateFlow<ChangelogUiState> = _changelogState
    private val _accountIdentityState = MutableStateFlow(AccountIdentityUiState())
    val accountIdentityState: StateFlow<AccountIdentityUiState> = _accountIdentityState

    private val _launchState = MutableStateFlow(LaunchUiState())
    val launchState: StateFlow<LaunchUiState> = _launchState

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
    private val catalogClient = ModuleCatalogClient(application)
    private val accessManager = LauncherAccessManager(application)
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
    private var debugAccessBypassActive = false
    private var currentLauncherRelease: LauncherRelease? = null
    @Volatile
    private var launcherInstallerOpened = false
    @Volatile
    private var launcherInstallerRecovery: Job? = null

    init {
        viewModelScope.launch {
            GameInstallEvents.results.collect(::onGameInstallResult)
        }
    }

    fun start(
        initialLink: Uri?,
        debugAccessBypass: Boolean = false,
        debugLauncherUpdateTest: Boolean = false
    ) {
        if (started) return
        started = true
        launcherUpdateTestChannel = debugLauncherUpdateTest
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
        val installedPrivateModules = repository.embeddedPrivateModules()
        val configuredScope = BuildConfig.PRIVATE_MODULE_SCOPE.takeIf {
            BuildConfig.PRIVATE_MODULE_ENABLED
        }
        val approvalByScope = (installedPrivateModules.values + listOfNotNull(configuredScope))
            .distinct()
            .associateWith { scope ->
                if (bypassPrivateApproval && BuildConfig.DEBUG) return@associateWith true
                runCatching {
                    accessManager.currentPrivateLease(scope)
                }.onFailure { error ->
                    android.util.Log.w(
                        "JesterMoodsPrivateAccess",
                        "Private module approval could not be refreshed; the module will stay hidden.",
                        error
                    )
                }.getOrNull() != null
            }

        installedPrivateModules.forEach { (packageName, scope) ->
            if (approvalByScope[scope] != true) {
                runCatching { repository.removeFromLibrary(packageName) }
                    .onFailure { error ->
                        android.util.Log.e(
                            "JesterMoodsPrivateModule",
                            "A private module could not be hidden after approval ended.",
                            error
                        )
                    }
            }
        }
        if (configuredScope != null && approvalByScope[configuredScope] == true) {
            runCatching { embeddedPrivateModuleInstaller.installIfConfigured() }
                .onFailure { error ->
                    android.util.Log.e(
                        "JesterMoodsPrivateModule",
                        "Embedded module installation failed; normal launcher startup will continue.",
                        error
                    )
                }
        }
        markLauncherReady(launcherExpiresAt)
        refreshGames(refreshCatalog = true, forceGameScan = true)
        checkLauncherUpdate()
        if (!isLocalTestStageLink(initialLink)) initialLink?.let(::handleDeepLink)
    }

    private fun markLauncherReady(expiresAt: Long) {
        _startupState.value = LauncherStartupState.Ready(expiresAt)
    }

    fun openLauncherUpdate() {
        if (_launcherUpdateState.value.available) {
            _changelogState.value = _changelogState.value.copy(open = false)
            _launcherUpdateState.value = _launcherUpdateState.value.copy(screenOpen = true)
        }
    }

    fun openChangelog() {
        _changelogState.value = _changelogState.value.copy(open = true)
        refreshChangelog()
    }

    fun closeChangelog() {
        _changelogState.value = _changelogState.value.copy(
            open = false,
            selectedModuleHistory = null,
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
        _changelogState.value = _changelogState.value.copy(
            selectedModuleHistory = null,
            moduleHistoryLoadingPackage = packageName,
            moduleHistoryError = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { moduleChangelogClient.load(listing.catalog) }
                .onSuccess { history ->
                    if (_changelogState.value.moduleHistoryLoadingPackage == packageName) {
                        _changelogState.value = _changelogState.value.copy(
                            selectedModuleHistory = history,
                            moduleHistoryLoadingPackage = null,
                            moduleHistoryError = null
                        )
                    }
                }
                .onFailure {
                    if (_changelogState.value.moduleHistoryLoadingPackage == packageName) {
                        _changelogState.value = _changelogState.value.copy(
                            moduleHistoryLoadingPackage = null,
                            moduleHistoryError = "This add-on's verified history is unavailable. Try again when you're online."
                        )
                    }
                }
        }
    }

    fun closeModuleChangelog() {
        _changelogState.value = _changelogState.value.copy(
            selectedModuleHistory = null,
            moduleHistoryLoadingPackage = null,
            moduleHistoryError = null
        )
    }

    fun refreshChangelog() {
        if (_changelogState.value.loading) return
        val cachedLauncher = launcherUpdateClient.loadCachedChangelog().orEmpty()
        _changelogState.value = _changelogState.value.copy(
            loading = true,
            launcherEntries = cachedLauncher.ifEmpty { _changelogState.value.launcherEntries },
            error = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            refreshGames(refreshCatalog = true, forceGameScan = true)
            val launcherResult = runCatching { launcherUpdateClient.refreshChangelog() }
            val launcherEntries = launcherResult.getOrElse {
                launcherUpdateClient.loadCachedChangelog().orEmpty()
            }
            // Keep the global feed cheap even with thousands of modules. Full signed history is
            // loaded only when a user opens an actual update offer for that module.
            val moduleHistories = _availableModules.value.map { listing ->
                moduleChangelogClient.summary(listing.catalog)
            }.sortedWith(
                compareBy<com.moodtools.hub.networking.ModuleChangelog, String>(String.CASE_INSENSITIVE_ORDER) {
                    it.title
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
            )
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

    fun closeLauncherUpdate() {
        val current = _launcherUpdateState.value
        if (!current.inProgress && !current.installing) {
            _launcherUpdateState.value = current.copy(screenOpen = false)
        }
    }

    fun downloadAndInstallLauncherUpdate() {
        val current = _launcherUpdateState.value
        if (!current.available || current.inProgress || current.installing) return
        val release = currentLauncherRelease?.takeIf { it.build == current.build } ?: return
        launcherInstallerRecovery?.cancel()
        launcherInstallerRecovery = null
        launcherInstallerOpened = false
        _launcherUpdateState.value = current.copy(
            inProgress = !current.downloaded,
            installing = false,
            failed = false,
            headline = if (current.downloaded) "Opening Android installer" else "Downloading launcher update",
            detail = if (current.downloaded) "Confirm the update when Android asks." else "Keep Jester Mods open while the update downloads."
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (!launcherUpdateClient.isDownloaded(release)) {
                    launcherUpdateClient.download(release) { downloaded, total ->
                        _launcherUpdateState.value = _launcherUpdateState.value.copy(
                            inProgress = true,
                            headline = "Downloading launcher update",
                            detail = "The installer will open automatically when the download is verified.",
                            downloadedBytes = downloaded,
                            totalBytes = total
                        )
                    }
                }
                _launcherUpdateState.value = _launcherUpdateState.value.copy(
                    inProgress = false,
                    installing = true,
                    downloaded = true,
                    failed = false,
                    headline = "Opening Android installer",
                    detail = "Android is preparing its confirmation screen."
                )
                launcherUpdateClient.install(release)
                launcherInstallerOpened = true
                launcherInstallerRecovery = viewModelScope.launch {
                    delay(LAUNCHER_INSTALLER_RECOVERY_DELAY_MS)
                    val waiting = _launcherUpdateState.value
                    if (launcherInstallerOpened && waiting.installing && waiting.build == release.build) {
                        launcherInstallerOpened = false
                        launcherInstallerRecovery = null
                        _launcherUpdateState.value = waiting.copy(
                            inProgress = false,
                            installing = false,
                            downloaded = true,
                            failed = false,
                            headline = "Update ready to install",
                            detail = "Android is taking longer than expected. Tap Install update to open the confirmation again."
                        )
                    }
                }
            }.onFailure { error ->
                launcherInstallerRecovery?.cancel()
                launcherInstallerRecovery = null
                launcherInstallerOpened = false
                android.util.Log.e("JesterMoodsLauncherUpdate", "Launcher update failed", error)
                val state = _launcherUpdateState.value
                val downloaded = launcherUpdateClient.isDownloaded(release)
                _launcherUpdateState.value = state.copy(
                    inProgress = false,
                    installing = false,
                    downloaded = downloaded,
                    failed = true,
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

    fun onLauncherInstallPermissionDenied() {
        val current = _launcherUpdateState.value
        if (!current.available) return
        _launcherUpdateState.value = current.copy(
            inProgress = false,
            installing = false,
            failed = true,
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
        val current = _launcherUpdateState.value
        if (!current.available) return
        val installed = runCatching { launcherUpdateClient.installedBuild() }.getOrDefault(0L)
        if (installed >= current.build) {
            launcherInstallerRecovery?.cancel()
            launcherInstallerRecovery = null
            launcherInstallerOpened = false
            launcherUpdateClient.markInstallSucceeded(installed)
            currentLauncherRelease = null
            _launcherUpdateState.value = LauncherUpdateUiState()
            return
        }
        if (current.installing && launcherInstallerOpened) {
            launcherInstallerRecovery?.cancel()
            launcherInstallerRecovery = null
            launcherInstallerOpened = false
            _launcherUpdateState.value = current.copy(
                inProgress = false,
                installing = false,
                downloaded = true,
                failed = false,
                headline = "Update ready to install",
                detail = "Android closed without installing the update. Tap Install update to open it again."
            )
            return
        }
    }

    private fun checkLauncherUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            val releaseResult = runCatching { launcherUpdateClient.refresh(launcherUpdateTestChannel) }
            val release = releaseResult.getOrElse { error ->
                android.util.Log.e("JesterMoodsLauncherUpdate", "Launcher update check failed", error)
                launcherUpdateClient.loadCached(launcherUpdateTestChannel)
            } ?: return@launch
            val history = if (launcherUpdateTestChannel) {
                emptyList()
            } else {
                runCatching { launcherUpdateClient.refreshChangelog() }
                    .getOrElse { launcherUpdateClient.loadCachedChangelog().orEmpty() }
            }
            publishLauncherUpdate(release, history)
        }
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
        _launcherUpdateState.value = LauncherUpdateUiState(
            available = true,
            build = release.build,
            version = release.version,
            notes = release.notes,
            changelog = newEntries,
            downloaded = downloaded,
            headline = if (downloaded) "Update ready to install" else "A launcher update is available",
            detail = if (downloaded) "The verified download is saved on this device." else "Download it here without leaving Jester Mods.",
            downloadedBytes = if (downloaded) release.size else 0L,
            totalBytes = release.size
        )
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
        _gameInstallState.value = GameInstallUiState(
            headline = "Installation permission is needed",
            detail = "Allow Jester Mods to install apps, then tap Download game again.",
            failed = true
        )
    }

    fun downloadAndInstallGame(listing: ModuleListing) {
        val source = listing.catalog.installSource as? GameInstallSource.DirectDownload ?: return
        if (_gameInstallState.value.inProgress || _gameInstallState.value.installing) return
        if (listing.game != null && source.versionCode <= listing.game.versionCode) {
            _gameInstallState.value = GameInstallUiState(
                headline = "No newer compatible game download",
                detail = "The available file cannot replace your installed game version.",
                failed = true
            )
            return
        }
        val alreadyDownloaded = gameInstallClient.isDownloaded(listing.catalog)
        _gameInstallState.value = GameInstallUiState(
            inProgress = !alreadyDownloaded,
            downloaded = alreadyDownloaded,
            headline = if (alreadyDownloaded) "Opening Android installer" else "Downloading original game",
            detail = if (alreadyDownloaded) {
                "Confirm the installation when Android asks."
            } else {
                "Keep Jester Mods open while the verified game downloads."
            },
            downloadedBytes = if (alreadyDownloaded) source.size else 0L,
            totalBytes = source.size
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (!alreadyDownloaded) {
                    gameInstallClient.download(listing.catalog) { downloaded, total ->
                        _gameInstallState.value = _gameInstallState.value.copy(
                            inProgress = true,
                            headline = "Downloading original game",
                            detail = "The Android installer will open after the file is verified.",
                            downloadedBytes = downloaded,
                            totalBytes = total
                        )
                    }
                }
                _gameInstallState.value = _gameInstallState.value.copy(
                    inProgress = false,
                    installing = true,
                    downloaded = true,
                    failed = false,
                    headline = "Ready to install",
                    detail = "Confirm the original game installation in the Android prompt."
                )
                gameInstallClient.install(listing.catalog)
            }.onFailure { error ->
                android.util.Log.e("JesterMoodsGameInstall", "Game download or install failed", error)
                val downloaded = gameInstallClient.isDownloaded(listing.catalog)
                val progress = _gameInstallState.value
                _gameInstallState.value = GameInstallUiState(
                    downloaded = downloaded,
                    failed = true,
                    headline = if (downloaded) "Couldn't open the installer" else "Couldn't download the game",
                    detail = if (downloaded) {
                        "Allow Jester Mods to install apps in Android settings, then try again."
                    } else {
                        "Check your connection and available storage, then try again."
                    },
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = source.size
                )
            }
        }
    }

    private fun onGameInstallResult(result: GameInstallResult) {
        viewModelScope.launch(Dispatchers.IO) {
            if (result.successful) {
                refreshGames(forceGameScan = true)
                _gameInstallState.value = GameInstallUiState(
                    completed = true,
                    headline = "Original game installed",
                    detail = "You can now add it to your Jester Mods library."
                )
            } else {
                _gameInstallState.value = GameInstallUiState(
                    downloaded = true,
                    failed = true,
                    headline = "The game wasn't installed",
                    detail = if (result.message.orEmpty().contains("cancel", ignoreCase = true)) {
                        "Installation was cancelled. The verified download is still saved."
                    } else {
                        "Try the installation again. The verified download is still saved."
                    }
                )
            }
        }
    }

    fun openLibraryGame(game: LibraryGame) {
        _selectedLibraryGame.value = game
        _selectedGame.value = game.game
        _updateState.value = ModuleUpdateUiState()
        _launchState.value = LaunchUiState()
    }

    fun closeGame() {
        _selectedLibraryGame.value = null
        _selectedGame.value = null
        _updateState.value = ModuleUpdateUiState()
        _launchState.value = LaunchUiState()
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

    fun onPackageReplacementProgress(headline: String, detail: String) {
        _launchState.value = LaunchUiState(
            inProgress = true,
            headline = headline,
            detail = detail
        )
    }

    fun showDirectPatchPrompt(title: String, replacesOriginal: Boolean) {
        _directPatchPromptState.value = DirectPatchPromptUiState(
            visible = true,
            title = title,
            replacesOriginal = replacesOriginal
        )
    }

    fun hideDirectPatchPrompt() {
        _directPatchPromptState.value = DirectPatchPromptUiState()
    }

    fun onPackageReplacementFailed(detail: String, title: String) {
        _launchState.value = LaunchUiState(
            headline = "$title patch failed",
            detail = detail,
            failed = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            refreshGames(forceGameScan = true)
        }
    }

    fun onPackageReplacementCancelled(title: String) {
        _launchState.value = LaunchUiState(
            headline = "$title wasn't changed",
            detail = "The prepared patch remains available if you try again."
        )
    }

    fun onPackageReplacementInstalled(request: PackageReplacementRequest) {
        _launchState.value = LaunchUiState(
            headline = "${request.title} patch installed",
            detail = "The patched game is ready. Open it from your Library when you want to play.",
            completed = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.onDirectPatchInstallSucceeded(request.packageName)
            refreshGames(forceGameScan = true)
        }
    }

    fun removeFromLibrary(entry: LibraryGame) {
        removeLibraryEntries(listOf(entry))
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
                runCatching { repository.removeFromLibrary(entry.packageName) }.onSuccess {
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
                    completed = true
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
        _updateState.value = ModuleUpdateUiState(
            inProgress = true,
            headline = "Preparing update",
            detail = "Checking the download for ${game.module.title}.",
            changelog = offeredChangelog,
            totalBytes = offeredState.totalBytes.takeIf { it > 0L }
                ?: moduleDownloadSize(game.packageName, game.abi)
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
                    progressDetail = "Downloading the latest add-on for ${game.module.title}."
                )
            }.onSuccess { result ->
                refreshGames()
                val progress = _updateState.value
                _updateState.value = ModuleUpdateUiState(
                    headline = "Update installed",
                    detail = buildString {
                        append(result.version?.let { "Version $it" } ?: "The latest version")
                        append(" is ready to use.")
                    },
                    completed = true,
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = progress.totalBytes,
                    changelog = offeredChangelog
                )
            }.onFailure(::showUpdateFailure)
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
        _updateState.value = ModuleUpdateUiState(
            inProgress = true,
            headline = "Preparing download",
            detail = "Checking the files for ${game.module.title}.",
            changelog = offeredChangelog,
            totalBytes = offeredState.totalBytes.takeIf { it > 0L } ?: moduleDownloadSize(listing)
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
                    gameHint = game
                )
            }.onSuccess { result ->
                refreshGames()
                val progress = _updateState.value
                _updateState.value = ModuleUpdateUiState(
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
                    changelog = offeredChangelog
                )
            }.onFailure { error ->
                showModuleDownloadFailure(error, action)
            }
        }
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
            _updateState.value = ModuleUpdateUiState(
                inProgress = true,
                headline = "Finishing update",
                detail = "Getting everything ready.",
                totalBytes = moduleDownloadSize(packageName, matchingGame?.abi)
            )
            runCatching { applyUpdate(request) }
                .onSuccess { result ->
                    clearReleaseGate()
                    val progress = _updateState.value
                    refreshGames()
                    _selectedGame.value = _games.value.firstOrNull { it.packageName == packageName }
                    _selectedLibraryGame.value = _libraryGames.value.firstOrNull {
                        it.packageName == packageName
                    }
                    _updateState.value = ModuleUpdateUiState(
                        headline = "Update complete",
                        detail = buildString {
                            append(result.version?.let { "Version $it" } ?: "The latest version")
                            append(" is ready to play.")
                        },
                        completed = true,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes
                    )
                }
                .onFailure(::showUpdateFailure)
        }
    }

    private fun applyUpdate(
        request: UpdateRequest,
        gameHint: InstalledGame? = null,
        progressHeadline: String = "Downloading add-on",
        progressDetail: String? = null
    ): com.moodtools.hub.networking.UpdateResult {
        require(privateScopeForPackage(request.packageName) == null) {
            "The embedded private add-on can only be updated by installing a new signed build that contains it."
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
        val authorization = accessManager.authorizeModule(request.packageName, abi)
        return UpdateClient(moduleDirectory).applyStandalone(
            request.packageName,
            abi,
            authorization
        ) { downloaded, total ->
            val current = _updateState.value
            _updateState.value = current.copy(
                inProgress = true,
                headline = progressHeadline,
                detail = progressDetail ?: "Getting ${game.module.title} ready for Jester Mods.",
                downloadedBytes = downloaded,
                totalBytes = total
            )
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
        val result = runCatching {
            accessManager.currentPrivateLease(scope)
        }
        val privateLease = result.getOrNull()
        if (privateLease != null) return true

        if (result.isFailure) {
            android.util.Log.e(
                "JesterMoodsPrivateAccess",
                "Private approval check failed before a protected action",
                result.exceptionOrNull()
            )
        }
        runCatching { repository.removeFromLibrary(packageName) }
            .onFailure { error ->
                android.util.Log.e(
                    "JesterMoodsPrivateModule",
                    "The unapproved private module could not be hidden.",
                    error
                )
            }
        scannedConfigs = null
        if (_selectedLibraryGame.value?.packageName == packageName) {
            _selectedLibraryGame.value = null
            _selectedGame.value = null
        }
        refreshGames(forceGameScan = true)
        val detail = if (result.isFailure) {
            "Jester Mods could not verify this private add-on approval. Connect and try again."
        } else {
            "This device is not approved for this private add-on. Send the support code to the owner."
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
        if (BuildConfig.PRIVATE_MODULE_ENABLED &&
            packageName == BuildConfig.PRIVATE_MODULE_PACKAGE) {
            BuildConfig.PRIVATE_MODULE_SCOPE
        } else {
            repository.embeddedPrivateScope(packageName)
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
        if (error is NewSessionAccessBoundaryException) return
        android.util.Log.e("JesterMoodsUpdate", "Update failed", error)
        val progress = _updateState.value
        _updateState.value = ModuleUpdateUiState(
            headline = "Couldn't finish the update",
            detail = "Please try again. If it keeps happening, restart Jester Mods.",
            failed = true,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes
        )
    }

    private fun showModuleDownloadFailure(error: Throwable, action: String) {
        if (error is NewSessionAccessBoundaryException) return
        android.util.Log.e("JesterMoodsDownload", "Game support $action failed", error)
        val progress = _updateState.value
        _updateState.value = ModuleUpdateUiState(
            headline = when (action) {
                "update" -> "Couldn't update add-on"
                "repair" -> "Couldn't repair add-on"
                else -> "Couldn't download add-on"
            },
            detail = "Check your connection and available storage, then try again.",
            failed = true,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes
        )
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
            if (playStorePreferences.getLong(playStoreAttemptDayKey(packageName), Long.MIN_VALUE) == today) {
                if (cached != null) statuses[packageName] = cached
                return@forEach
            }
            editor.putLong(playStoreAttemptDayKey(packageName), today)
            changed = true
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
                val status = PlayStoreVersionStatus(
                    latestVersion = fresh.version,
                    checkedAtEpochSeconds = fresh.checkedAtEpochSeconds,
                    checkedDay = today,
                    stale = false
                )
                statuses[packageName] = status
                editor.putString(playStoreVersionKey(packageName), fresh.version)
                editor.putLong(playStoreCheckedAtKey(packageName), fresh.checkedAtEpochSeconds)
                editor.putLong(playStoreCheckedDayKey(packageName), today)
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
            ?: return null
        val checkedAt = playStorePreferences.getLong(playStoreCheckedAtKey(packageName), 0L)
        val checkedDay = playStorePreferences.getLong(playStoreCheckedDayKey(packageName), Long.MIN_VALUE)
        if (checkedAt <= 0L || checkedDay == Long.MIN_VALUE) return null
        return PlayStoreVersionStatus(
            latestVersion = version,
            checkedAtEpochSeconds = checkedAt,
            checkedDay = checkedDay,
            stale = checkedDay != today
        )
    }

    private fun currentLocalDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / (24L * 60L * 60L * 1000L)
    }

    private fun playStoreAttemptDayKey(packageName: String): String = PLAY_STORE_ATTEMPT_DAY_PREFIX + packageName
    private fun playStoreCheckedDayKey(packageName: String): String = PLAY_STORE_DAY_PREFIX + packageName
    private fun playStoreVersionKey(packageName: String): String = PLAY_STORE_VERSION_PREFIX + packageName
    private fun playStoreCheckedAtKey(packageName: String): String = PLAY_STORE_CHECKED_AT_PREFIX + packageName

    private fun publishGames(catalog: List<com.moodtools.hub.modules.CatalogModule>, forceGameScan: Boolean) {
        val application = getApplication<android.app.Application>()
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
                    } ?: LibraryLaunchAction.PLAY
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
            val playStoreStatuses = playStoreStatusesFor(catalog)
            val detected = scanGames(scanConfigs, forceGameScan)
            val detectedByPackage = detected.associateBy { it.packageName }
            val listings = catalog.map { item ->
                ModuleListing(
                    catalog = item,
                    game = detectedByPackage[item.config.packageName],
                    installedBuild = repository.installedBuild(item.config.packageName),
                    installedComplete = repository.isInstalled(item.config.packageName),
                    deviceArchitectureSupported = DeviceArchitectureGuard.supports(item.config.supportedAbis),
                    playStoreVersionStatus = playStoreStatuses[item.config.packageName]
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
                        } ?: LibraryLaunchAction.PLAY
                    )
                }
            _libraryGames.value = sortLibraryGames(catalogLibraryGames + localOnlyLibraryGames)
        }
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
        private const val PLAY_STORE_ATTEMPT_DAY_PREFIX = "attempt_day_"
        private const val PLAY_STORE_DAY_PREFIX = "checked_day_"
        private const val PLAY_STORE_VERSION_PREFIX = "latest_version_"
        private const val PLAY_STORE_CHECKED_AT_PREFIX = "checked_at_"
        private const val RELEASE_GATE_TTL_MS = 20L * 60L * 1000L
        private const val MINIMUM_ACCESS_CHECK_GATE_MS = 2_000L
        private const val LAUNCHER_INSTALLER_RECOVERY_DELAY_MS = 20_000L
        private const val DEBUG_ACCESS_DURATION_SECONDS = 24L * 60L * 60L
        private const val MAX_RELEASE_GRANT_CHARS = 4096
        private const val LOCAL_TEST_STAGE_DIR = "jester-local-modules"
        private const val LOCAL_TEST_TOKEN_FILE = "stage-token.txt"
        private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_.]{3,200}")
        private val LOCAL_TEST_TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{32,128}")
        private val NONCE_PATTERN = Regex("[A-Za-z0-9_-]{43}")
        private val GRANT_PATTERN = Regex("[A-Za-z0-9_.-]+")
    }
}
