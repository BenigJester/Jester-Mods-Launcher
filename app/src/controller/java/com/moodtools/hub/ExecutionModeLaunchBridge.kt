package com.moodtools.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.moodtools.hub.modules.InstalledGame
import com.moodtools.hub.modules.LibraryGame
import com.moodtools.hub.modules.LibraryLaunchAction
import com.moodtools.hub.modules.ModuleIntegrityVerifier
import com.moodtools.hub.modules.ModuleRepository
import com.moodtools.hub.modules.RootMethod
import com.moodtools.hub.modules.architectureLabel
import com.moodtools.hub.modules.architectureSummary
import com.moodtools.hub.security.RuntimeSecurityGuard
import java.util.concurrent.TimeUnit

/** Root controller edition: starts the external controller and launches the game normally. */
object ExecutionModeLaunchBridge {
    private const val SUPPORTED_PACKAGE = "com.dts.freefireth"
    private val packagePattern = Regex("^[A-Za-z0-9_.]+$")

    fun prepare(context: Context): String? = null
    fun onLauncherTaskRemoved(context: Context) = Unit
    fun removeLibraryGameData(context: Context, game: LibraryGame) = Unit
    fun isInstalledIdentityShell(context: Context, game: LibraryGame): Boolean = false
    fun installedNonRootMethod(context: Context, packageName: String): com.moodtools.hub.modules.NonRootMethod? = null
    fun requiresOfficialRestoreBeforeIdentityShell(context: Context, game: InstalledGame): Boolean = false
    fun discardPreparedIdentityShell(context: Context, packageName: String) = Unit
    fun requiresPackageReplacement(context: Context, game: InstalledGame): Boolean = false

    fun libraryLaunchAction(context: Context, game: InstalledGame): LibraryLaunchAction =
        LibraryLaunchAction.PLAY

    fun preparePackageReplacement(
        context: Context,
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)? = null
    ): PackageReplacementRequest = error("Package replacement is unavailable in Controller mode")

    fun installPackageReplacement(context: Context, request: PackageReplacementRequest) {
        error("Package replacement is unavailable in Controller mode")
    }

    fun isPackageReplacementInstalled(context: Context, request: PackageReplacementRequest): Boolean = false

    fun launchPackageReplacement(context: Context, request: PackageReplacementRequest): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(request.packageName) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun clearLibraryGameData(context: Context, game: LibraryGame) {
        val packageName = game.packageName
        require(packagePattern.matches(packageName)) { "Invalid game package name" }
        check(game.game != null) { "The installed game could not be found" }
        val userId = RootShell.run("am get-current-user").takeIf { it.success }
            ?.output?.lineSequence()?.map(String::trim)
            ?.firstOrNull { it.matches(Regex("^[0-9]+$")) }
            ?: error("Could not determine the current Android user")
        check(RootShell.run("pm path --user $userId ${quote(packageName)}").success) {
            "The installed game could not be found"
        }
        check(RootShell.run("pm clear --user $userId ${quote(packageName)}").success) {
            "Android could not clear the installed game's data"
        }
        val targets = listOf(
            "/storage/emulated/$userId/Android/data/$packageName",
            "/storage/emulated/$userId/Android/obb/$packageName",
            "/sdcard/Android/data/$packageName",
            "/sdcard/Android/obb/$packageName"
        )
        check(RootShell.run("rm -rf -- ${targets.joinToString(" ") { quote(it) }}").success) {
            "Could not remove the game's remaining external data"
        }
    }

    fun isGameRunning(context: Context, packageName: String): Boolean =
        packagePattern.matches(packageName) && RootShell.run("pidof ${quote(packageName)}").success

    fun launch(
        context: Context,
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)? = null
    ): Boolean {
        fun report(headline: String, detail: String) = onProgress?.invoke(headline, detail)
        report("Checking your setup", "Making sure Jester Mods can open the game.")
        val security = RuntimeSecurityGuard.inspect(context)
        if (!security.allowed) return fail(context,
            "This launcher installation failed its security check. Install an official build and try again.",
            "Runtime security check failed: ${security.blockingSignals.joinToString()}", onProgress)
        if (!packagePattern.matches(game.packageName)) return fail(context,
            "This game can't be opened right now.", "Invalid module package name", onProgress)
        if (!game.versionSupported) return fail(context,
            "This game version or build isn't supported yet.",
            "Installed ${game.versionName} (build ${game.versionCode}); supported versions ${game.module.supportedVersions.joinToString()}, builds ${game.module.supportedVersionCodes.joinToString().ifBlank { "not declared" }}",
            onProgress)
        if (!game.abiSupported) return fail(context,
            "This game architecture isn't supported by this add-on.",
            "Installed ${architectureLabel(game.abi)}; supported ${architectureSummary(game.module.supportedAbis)}",
            onProgress)
        val root = RootShell.run("id")
        if (!root.success || !root.output.contains("uid=0")) return fail(context,
            "Root access is required to use Controller mode.", "Root access was not granted", onProgress)
        if (game.module.rootMethod != RootMethod.EXTERNAL_CONTROLLER || game.packageName != SUPPORTED_PACKAGE) {
            return fail(context, "This module is not supported by the controller edition.",
                "Unsupported module ${game.packageName}/${game.module.rootMethod.jsonValue}", onProgress)
        }
        return launchController(context, game, onProgress)
    }

    private fun launchController(
        context: Context,
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)?
    ): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return fail(context, "Allow display over other apps, then press Play again.",
                "Controller overlay permission is missing", onProgress)
        }

        val repository = ModuleRepository(context)
        val module = repository.loadModules().singleOrNull { it.packageName == game.packageName }
            ?: return fail(context, "The Free Fire controller is not installed.",
                "Controller module is missing", onProgress)
        val moduleDirectory = repository.directoryFor(module)
        runCatching { ModuleIntegrityVerifier().verify(moduleDirectory, module, game.abi) }
            .getOrElse { error -> return fail(context,
                "The add-on failed its security check. Repair or update it, then try again.",
                "Controller verification failed: ${error.message}", onProgress) }

        ExternalControllerService.start(context, game.packageName)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(game.packageName)
            ?: return fail(context, "Free Fire could not be opened.", "Launch intent is missing", onProgress)
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        onProgress?.invoke("Opening game", "Free Fire is starting normally.")
        return true
    }

    private fun fail(
        context: Context,
        userMessage: String,
        diagnostic: String,
        onProgress: ((headline: String, detail: String) -> Unit)?
    ): Boolean {
        android.util.Log.e("JesterMoodsLaunch", diagnostic)
        onProgress?.invoke("Launch failed", userMessage)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, userMessage, Toast.LENGTH_LONG).show()
        }
        return false
    }

    private fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"
}

object ExecutionModeStartupGate {
    fun check(): StartupGateResult {
        val result = RootShell.run("id")
        return if (result.success && result.output.contains("uid=0")) {
            StartupGateResult(allowed = true)
        } else {
            StartupGateResult(allowed = false,
                message = "Root permission was not granted. Jester Mods Controller cannot continue without it, so the launcher will close.")
        }
    }
}

private object RootShell {
    data class Result(val success: Boolean, val output: String)

    fun run(command: String): Result = runCatching {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val completed = process.waitFor(60, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return@runCatching Result(false, "Root command timed out")
        }
        Result(process.exitValue() == 0,
            process.inputStream.bufferedReader().use { it.readText() })
    }.getOrElse { Result(false, it.message ?: it.javaClass.simpleName) }
}
