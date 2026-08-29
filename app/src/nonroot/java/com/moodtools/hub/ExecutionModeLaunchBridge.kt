package com.moodtools.hub

import android.content.Context
import com.moodtools.hub.modules.InstalledGame
import com.moodtools.hub.modules.LibraryLaunchAction
import com.moodtools.hub.modules.LibraryGame
import com.moodtools.hub.modules.ModuleIntegrityVerifier
import com.moodtools.hub.modules.NonRootMethod
import com.moodtools.hub.modules.architectureLabel
import com.moodtools.hub.modules.architectureSummary
import com.moodtools.hub.security.RuntimeSecurityGuard
import com.moodtools.hub.soulpatch.DirectPackagePatchManager
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
        if (game.module.nonRootMethod == NonRootMethod.INJECTION) {
            NonRootBlackBoxRuntime.removePackage(context, game.packageName)
        }
    }

    fun clearLibraryGameData(context: Context, game: LibraryGame) {
        require(game.module.nonRootMethod == NonRootMethod.INJECTION) {
            "Only managed BlackBox games have clearable sandbox data"
        }
        NonRootBlackBoxRuntime.removePackage(context, game.packageName)
    }

    fun requiresPackageReplacement(context: Context, game: InstalledGame): Boolean {
        if (game.module.nonRootMethod != NonRootMethod.DIRECT_PATCH) return false
        return directPatchManager(context, game.packageName).requiresReplacement(game)
    }

    fun libraryLaunchAction(context: Context, game: InstalledGame): LibraryLaunchAction {
        if (game.module.nonRootMethod != NonRootMethod.DIRECT_PATCH) {
            return LibraryLaunchAction.PLAY
        }
        val patcher = directPatchManager(context, game.packageName)
        return when {
            !patcher.isPatchedInstallation() -> LibraryLaunchAction.PATCH_AND_INSTALL
            patcher.requiresReplacement(game) -> LibraryLaunchAction.UPDATE_PATCHED_INSTALL
            else -> LibraryLaunchAction.PLAY
        }
    }

    fun preparePackageReplacement(
        context: Context,
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)? = null
    ): PackageReplacementRequest {
        require(game.module.nonRootMethod == NonRootMethod.DIRECT_PATCH) {
            "This add-on does not use direct package patching"
        }
        val patcher = directPatchManager(context, game.packageName)
        return patcher.prepare(game, onProgress)
    }

    fun installPackageReplacement(context: Context, request: PackageReplacementRequest) {
        val patcher = directPatchManager(context, request.packageName)
        patcher.install(request)
    }

    fun isPackageReplacementInstalled(
        context: Context,
        request: PackageReplacementRequest
    ): Boolean = directPatchManager(context, request.packageName).isRequestInstalled(request)

    fun launchPackageReplacement(context: Context, request: PackageReplacementRequest): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(request.packageName) ?: return false
        return runCatching {
            directPatchManager(context, request.packageName).authorizeLaunch(intent)
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
        if (game.module.nonRootMethod == NonRootMethod.DIRECT_PATCH) {
            val patcher = directPatchManager(context, game.packageName)
            if (!patcher.isPatchedInstallation()) {
                onProgress?.invoke(
                    "Launch failed",
                    "Prepare the patched ${game.module.title} package before opening the game."
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
                patcher.authorizeLaunch(intent)
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
}

object ExecutionModeStartupGate {
    fun check(): StartupGateResult = StartupGateResult(allowed = true)
}
