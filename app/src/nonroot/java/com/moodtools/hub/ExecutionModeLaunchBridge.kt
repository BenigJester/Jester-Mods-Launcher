package com.moodtools.hub

import android.content.Context
import com.moodtools.hub.modules.InstalledGame
import com.moodtools.hub.modules.LibraryLaunchAction
import com.moodtools.hub.modules.LibraryGame
import com.moodtools.hub.modules.ModuleIntegrityVerifier
import com.moodtools.hub.modules.NonRootMethod
import com.moodtools.hub.modules.identityShellLaunchAction
import com.moodtools.hub.modules.architectureLabel
import com.moodtools.hub.modules.architectureSummary
import com.moodtools.hub.security.RuntimeSecurityGuard
import com.moodtools.hub.soulpatch.DirectPackagePatchManager
import com.moodtools.hub.identity.IdentityShellManager
import java.io.File

/** Routes each game through the non-root method declared by its module metadata. */
object ExecutionModeLaunchBridge {
    fun prepare(context: Context): String? {
        LauncherTaskLifecycleService.ensureRunning(context)
        return null
    }

    fun onLauncherTaskRemoved(context: Context) {
        NonRootBlackBoxRuntime.shutdown(context)
    }

    fun removeLibraryGameData(context: Context, game: LibraryGame) {
        when (game.module.nonRootMethod) {
            NonRootMethod.INJECTION -> NonRootBlackBoxRuntime.removePackage(context, game.packageName)
            NonRootMethod.IDENTITY_SHELL -> {
                val manager = identityShellManager(context, game.packageName)
                check(!manager.isInstalledShell()) {
                    "The exact-package shell must be uninstalled before its add-on is removed"
                }
                manager.removePreparedArtifacts()
            }
            NonRootMethod.DIRECT_PATCH -> Unit
        }
    }

    fun isInstalledIdentityShell(context: Context, game: LibraryGame): Boolean =
        game.module.nonRootMethod == NonRootMethod.IDENTITY_SHELL &&
            identityShellManager(context, game.packageName).isInstalledShell()

    fun requiresOfficialRestoreBeforeIdentityShell(context: Context, game: InstalledGame): Boolean =
        game.module.nonRootMethod == NonRootMethod.IDENTITY_SHELL &&
            !identityShellManager(context, game.packageName).isInstalledShell() &&
            directPatchManager(context, game.packageName).isPatchedInstallation()

    fun discardPreparedIdentityShell(context: Context, packageName: String) {
        identityShellManager(context, packageName).removePreparedArtifacts()
    }

    fun clearLibraryGameData(context: Context, game: LibraryGame) {
        require(game.module.nonRootMethod == NonRootMethod.INJECTION) {
            "Only managed BlackBox games have clearable sandbox data"
        }
        NonRootBlackBoxRuntime.removePackage(context, game.packageName)
    }

    fun requiresPackageReplacement(context: Context, game: InstalledGame): Boolean {
        return when (game.module.nonRootMethod) {
            NonRootMethod.INJECTION -> false
            NonRootMethod.DIRECT_PATCH -> directPatchManager(context, game.packageName)
                .requiresReplacement(game)
            NonRootMethod.IDENTITY_SHELL -> identityShellManager(context, game.packageName)
                .requiresReplacement(game)
        }
    }

    fun libraryLaunchAction(context: Context, game: InstalledGame): LibraryLaunchAction {
        return when (game.module.nonRootMethod) {
            NonRootMethod.INJECTION -> LibraryLaunchAction.PLAY
            NonRootMethod.DIRECT_PATCH -> {
                val patcher = directPatchManager(context, game.packageName)
                when {
                    !patcher.isPatchedInstallation() -> LibraryLaunchAction.PATCH_AND_INSTALL
                    patcher.requiresReplacement(game) -> LibraryLaunchAction.UPDATE_PATCHED_INSTALL
                    else -> LibraryLaunchAction.PLAY
                }
            }
            NonRootMethod.IDENTITY_SHELL -> identityShellLaunchAction(
                installedIdentityShell = identityShellManager(context, game.packageName).isInstalledShell(),
                installedDirectPatch = directPatchManager(context, game.packageName).isPatchedInstallation()
            )
        }
    }

    fun preparePackageReplacement(
        context: Context,
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)? = null
    ): PackageReplacementRequest {
        require(game.module.nonRootMethod != NonRootMethod.INJECTION) {
            "This add-on does not replace its outer package"
        }
        return when (game.module.nonRootMethod) {
            NonRootMethod.DIRECT_PATCH -> directPatchManager(context, game.packageName)
                .prepare(game, onProgress)
            NonRootMethod.IDENTITY_SHELL -> {
                require(!directPatchManager(context, game.packageName).isPatchedInstallation()) {
                    "Restore the official game before creating its exact-package shell"
                }
                identityShellManager(context, game.packageName).prepare(game, onProgress)
            }
            NonRootMethod.INJECTION -> error("Injection does not prepare a replacement package")
        }
    }

    fun installPackageReplacement(context: Context, request: PackageReplacementRequest) {
        when (request.kind) {
            PackageReplacementKind.DIRECT_PATCH -> directPatchManager(context, request.packageName)
                .install(request)
            PackageReplacementKind.IDENTITY_SHELL -> identityShellManager(context, request.packageName)
                .install(request)
        }
    }

    fun isPackageReplacementInstalled(
        context: Context,
        request: PackageReplacementRequest
    ): Boolean = when (request.kind) {
        PackageReplacementKind.DIRECT_PATCH -> directPatchManager(context, request.packageName)
            .isRequestInstalled(request)
        PackageReplacementKind.IDENTITY_SHELL -> identityShellManager(context, request.packageName)
            .isInstalledShell()
    }

    fun launchPackageReplacement(context: Context, request: PackageReplacementRequest): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(request.packageName) ?: return false
        return runCatching {
            when (request.kind) {
                PackageReplacementKind.DIRECT_PATCH ->
                    directPatchManager(context, request.packageName).authorizeLaunch(intent)
                PackageReplacementKind.IDENTITY_SHELL ->
                    identityShellManager(context, request.packageName).authorizeLaunch(intent)
            }
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun isGameRunning(context: Context, packageName: String): Boolean =
        NonRootBlackBoxRuntime.isRunning(packageName)

    fun launch(
        context: Context,
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)? = null
    ): Boolean {
        val runtimeSecurity = RuntimeSecurityGuard.inspect(context)
        if (!runtimeSecurity.allowed) {
            android.util.Log.e(
                "JesterMoodsLaunch",
                "Runtime security check failed: ${runtimeSecurity.blockingSignals.joinToString()}"
            )
            onProgress?.invoke(
                "Launch failed",
                "This launcher installation failed its security check. Install an official build and try again."
            )
            return false
        }
        if (!game.versionSupported) {
            onProgress?.invoke("Launch failed", "This installed game version is not supported yet.")
            return false
        }
        if (!game.abiSupported) {
            onProgress?.invoke(
                "Launch failed",
                "Installed ${architectureLabel(game.abi)}; add-on supports ${architectureSummary(game.module.supportedAbis)}."
            )
            return false
        }
        onProgress?.invoke("Verifying add-on", "Checking the signed add-on before launch.")
        runCatching {
            val directory = File(context.filesDir, "menus/${game.packageName}")
            ModuleIntegrityVerifier().verify(directory, game.module, game.abi)
        }.getOrElse { error ->
            android.util.Log.e("JesterMoodsLaunch", "Module integrity verification failed", error)
            onProgress?.invoke(
                "Launch failed",
                "The add-on failed its security check. Repair or update it, then try again."
            )
            return false
        }
        if (game.module.nonRootMethod != NonRootMethod.INJECTION) {
            val patcher = directPatchManager(context, game.packageName)
            val ready = when (game.module.nonRootMethod) {
                NonRootMethod.DIRECT_PATCH -> patcher.isPatchedInstallation()
                NonRootMethod.IDENTITY_SHELL -> identityShellManager(context, game.packageName)
                    .isInstalledShell()
                NonRootMethod.INJECTION -> false
            }
            if (!ready) {
                onProgress?.invoke(
                    "Launch failed",
                    "Prepare the ${game.module.displayNameForSetup()} package before opening the game."
                )
                return false
            }
            onProgress?.invoke("Opening game", "Starting ${game.module.title} directly on Android.")
            val intent = context.packageManager.getLaunchIntentForPackage(game.packageName)
            if (intent == null) {
                onProgress?.invoke("Launch failed", "Android could not find ${game.module.title}'s launch activity.")
                return false
            }
            return runCatching {
                when (game.module.nonRootMethod) {
                    NonRootMethod.DIRECT_PATCH -> patcher.authorizeLaunch(intent)
                    NonRootMethod.IDENTITY_SHELL ->
                        identityShellManager(context, game.packageName).authorizeLaunch(intent)
                    NonRootMethod.INJECTION -> Unit
                }
                context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                onProgress?.invoke("Opening game", "${game.module.title} is starting now.")
                true
            }.getOrElse { error ->
                android.util.Log.e("JesterMoodsLaunch", "Direct package launch failed", error)
                onProgress?.invoke("Launch failed", "Android could not open the patched ${game.module.title} package.")
                false
            }
        }

        onProgress?.invoke("Preparing your game", "Getting Jester Mods ready.")
        onProgress?.invoke("Opening game", "Starting ${game.module.title} now.")
        val launched = NonRootBlackBoxRuntime.launch(context, game)
        if (launched) {
            onProgress?.invoke("Opening game", "${game.module.title} is starting now.")
        } else {
            val diagnostic = NonRootBlackBoxRuntime.lastFailureDetail
            onProgress?.invoke(
                "Launch failed",
                diagnostic?.let { "BlackBox: $it" }
                    ?: "Jester Mods could not open the game. Please try again."
            )
        }
        return launched
    }

    private fun directPatchManager(context: Context, packageName: String) =
        DirectPackagePatchManager(context, packageName)

    private fun identityShellManager(context: Context, packageName: String) =
        IdentityShellManager(context, packageName)

    private fun com.moodtools.hub.modules.ModuleConfig.displayNameForSetup(): String =
        if (nonRootMethod == NonRootMethod.IDENTITY_SHELL) "exact-package shell" else "patched game"
}

object ExecutionModeStartupGate {
    fun check(): StartupGateResult = StartupGateResult(allowed = true)
}
