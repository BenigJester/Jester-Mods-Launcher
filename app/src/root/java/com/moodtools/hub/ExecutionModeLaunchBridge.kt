package com.moodtools.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.moodtools.hub.modules.InstalledGame
import com.moodtools.hub.modules.LibraryLaunchAction
import com.moodtools.hub.modules.LibraryGame
import com.moodtools.hub.modules.ModuleIntegrityVerifier
import com.moodtools.hub.modules.ModuleRepository
import com.moodtools.hub.modules.PluginLoader
import com.moodtools.hub.modules.RootMethod
import com.moodtools.hub.modules.architectureLabel
import com.moodtools.hub.modules.architectureSummary
import com.moodtools.hub.security.RuntimeSecurityGuard
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/** Launches through the module's declared root method: external control or legacy injection. */
object ExecutionModeLaunchBridge {
    private const val ROOT_RUNTIME_DIRECTORY = "/data/local/tmp/moodtools-standalone-root"
    private const val EXTERNAL_CONTROLLER_PACKAGE = "com.dts.freefireth"
    private const val ORIGINAL_IL2CPP_SHA256 = "bf70daa86a1c0224b6e0191918a7912829615858f31eb2c4fd644d020effbdff"
    private const val IL2CPP_ROLLBACK = "/data/local/tmp/libil2cpp.before-jester-controller.so"
    private val packagePattern = Regex("^[A-Za-z0-9_.]+$")

    fun prepare(context: Context): String? = null

    fun onLauncherTaskRemoved(context: Context) = Unit

    fun removeLibraryGameData(context: Context, game: LibraryGame) = Unit

    fun isInstalledIdentityShell(context: Context, game: LibraryGame): Boolean = false

    fun installedNonRootMethod(context: Context, packageName: String): com.moodtools.hub.modules.NonRootMethod? = null

    fun requiresOfficialRestoreBeforeIdentityShell(context: Context, game: InstalledGame): Boolean = false

    fun discardPreparedIdentityShell(context: Context, packageName: String) = Unit

    fun clearLibraryGameData(context: Context, game: LibraryGame) {
        val packageName = game.packageName
        require(packagePattern.matches(packageName)) { "Invalid game package name" }
        check(game.game != null) { "The installed game could not be found" }

        val userResult = RootShell.run("am get-current-user")
        check(userResult.success) { "Could not determine the current Android user" }
        val userId = userResult.output
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.matches(Regex("^[0-9]+$")) }
            ?.toIntOrNull()
            ?: error("Could not determine the current Android user")

        val installed = RootShell.run("pm path --user $userId ${quote(packageName)}")
        check(installed.success && installed.output.lineSequence().any { it.trim().startsWith("package:") }) {
            "The installed game could not be found"
        }

        val cleared = RootShell.run("pm clear --user $userId ${quote(packageName)}")
        check(cleared.success && cleared.output.lineSequence().any { it.trim() == "Success" }) {
            "Android could not clear the installed game's data"
        }

        val externalTargets = listOf(
            "/storage/emulated/$userId/Android/data/$packageName",
            "/storage/emulated/$userId/Android/obb/$packageName",
            "/sdcard/Android/data/$packageName",
            "/sdcard/Android/obb/$packageName"
        )
        val externalCleanup = RootShell.run(
            "rm -rf -- ${externalTargets.joinToString(" ") { quote(it) }}"
        )
        check(externalCleanup.success) { "Could not remove the game's remaining external data" }
    }

    fun requiresPackageReplacement(context: Context, game: InstalledGame): Boolean = false

    fun libraryLaunchAction(context: Context, game: InstalledGame): LibraryLaunchAction =
        LibraryLaunchAction.PLAY

    fun preparePackageReplacement(
        context: Context,
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)? = null
    ): PackageReplacementRequest = error("Package replacement is unavailable in Root mode")

    fun installPackageReplacement(context: Context, request: PackageReplacementRequest) {
        error("Package replacement is unavailable in Root mode")
    }

    fun isPackageReplacementInstalled(
        context: Context,
        request: PackageReplacementRequest
    ): Boolean = false

    fun launchPackageReplacement(context: Context, request: PackageReplacementRequest): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(request.packageName) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun isGameRunning(context: Context, packageName: String): Boolean {
        if (!packagePattern.matches(packageName)) return false
        return RootShell.run("pidof ${quote(packageName)}").success
    }

    fun launch(
        context: Context,
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)? = null
    ): Boolean {
        fun report(headline: String, detail: String) {
            onProgress?.invoke(headline, detail)
        }

        report("Checking your setup", "Making sure Jester Mods can open the game.")
        val runtimeSecurity = RuntimeSecurityGuard.inspect(context)
        if (!runtimeSecurity.allowed) {
            return fail(
                context,
                "This launcher installation failed its security check. Install an official build and try again.",
                "Runtime security check failed: ${runtimeSecurity.blockingSignals.joinToString()}",
                onProgress
            )
        }
        if (!packagePattern.matches(game.packageName)) {
            return fail(context, "This game can't be opened right now.", "Invalid module package name", onProgress)
        }
        if (!game.versionSupported) {
            return fail(context, "This game version or build isn't supported yet.",
                "Installed ${game.versionName} (build ${game.versionCode}); supported versions ${game.module.supportedVersions.joinToString()}, builds ${game.module.supportedVersionCodes.joinToString().ifBlank { "not declared" }}", onProgress)
        }
        if (!game.abiSupported) {
            return fail(context, "This game architecture isn't supported by this add-on.",
                "Installed ${architectureLabel(game.abi)}; supported ${architectureSummary(game.module.supportedAbis)}", onProgress)
        }
        val rootCheck = RootShell.run("id")
        if (!rootCheck.success || !rootCheck.output.contains("uid=0")) {
            return fail(context, "Root access is required to use Root mode.", "Root access was not granted to Jester Mods", onProgress)
        }
        if (game.module.rootMethod == RootMethod.EXTERNAL_CONTROLLER) {
            if (game.packageName != EXTERNAL_CONTROLLER_PACKAGE) {
                return fail(context, "This external controller is not supported by this launcher build.",
                    "Unexpected external controller package ${game.packageName}", onProgress)
            }
            return launchExternalController(context, game, onProgress)
        }
        val kernelMachine = RootShell.run("uname -m")
        val injectorAsset = kernelMachine
            .takeIf { it.success }
            ?.output
            ?.let(::rootInjectorAssetForKernel)
        if (injectorAsset == null) {
            return fail(
                context,
                "This device isn't supported by Root mode.",
                "Unsupported Android kernel architecture: ${kernelMachine.output.trim().ifEmpty { "unknown" }}",
                onProgress
            )
        }

        runCatching {
            context.packageManager.getApplicationInfo(game.packageName, 0)
        }.getOrElse {
            return fail(context, "Jester Mods couldn't find the installed game.", "The installed game could not be inspected", onProgress)
        }

        report("Preparing your game", "Getting your saved features ready.")
        val moduleDirectory = File(context.filesDir, "menus/${game.packageName}")
        report("Verifying add-on", "Checking the signed add-on before launch.")
        runCatching {
            ModuleIntegrityVerifier().verify(moduleDirectory, game.module, game.abi)
        }.getOrElse { error ->
            return fail(
                context,
                "The add-on failed its security check. Repair or update it, then try again.",
                "Module integrity verification failed: ${error.message}",
                onProgress
            )
        }
        val dex = File(moduleDirectory, game.module.dexFile)
        val nativePayload = File(moduleDirectory, game.module.nativeFile)
        if (!dex.isFile || dex.length() <= 0L) {
            return fail(context, "Game files aren't ready. Check for updates and try again.", "Module DEX is missing", onProgress)
        }
        if (!nativePayload.isFile || nativePayload.length() <= 0L) {
            return fail(context, "Game files aren't ready. Check for updates and try again.", "Module native payload is missing", onProgress)
        }

        report("Almost ready", "Preparing Jester Mods for the game.")
        val injector = runCatching { extractInjector(context, injectorAsset) }.getOrElse {
            return fail(context, "Jester Mods couldn't prepare the game. Please try again.", "Root injector extraction failed: ${it.message}", onProgress)
        }
        val bootstrap = runCatching { extractRootBootstrap(context) }.getOrElse {
            return fail(context, "Jester Mods couldn't prepare the game. Please try again.", "Root bootstrap extraction failed: ${it.message}", onProgress)
        }

        val remoteInjector = "$ROOT_RUNTIME_DIRECTORY/moodtools-injector"
        val remoteBootstrap = "$ROOT_RUNTIME_DIRECTORY/libmoodtools_root_runtime.so"
        val remoteDex = "$ROOT_RUNTIME_DIRECTORY/classes.dex"
        val remoteNative = "$ROOT_RUNTIME_DIRECTORY/libmenu_native.so"
        val stageCommands = listOf(
            "am force-stop ${quote(game.packageName)}",
            "mkdir -p ${quote(ROOT_RUNTIME_DIRECTORY)}",
            "cp -f ${quote(injector.absolutePath)} ${quote(remoteInjector)}",
            "cp -f ${quote(bootstrap.absolutePath)} ${quote(remoteBootstrap)}",
            "cp -f ${quote(dex.absolutePath)} ${quote(remoteDex)}",
            "cp -f ${quote(nativePayload.absolutePath)} ${quote(remoteNative)}",
            "chown 0:0 ${quote(remoteInjector)} ${quote(remoteBootstrap)} ${quote(remoteDex)} ${quote(remoteNative)}",
            "chmod 0755 ${quote(remoteInjector)}",
            "chmod 0644 ${quote(remoteBootstrap)} ${quote(remoteDex)} ${quote(remoteNative)}",
            "chcon u:object_r:shell_data_file:s0 ${quote(remoteInjector)} ${quote(remoteBootstrap)} " +
                "${quote(remoteDex)} ${quote(remoteNative)} 2>/dev/null || true"
        )
        report("Almost ready", "Finishing setup before the game opens.")
        val stage = RootShell.run(stageCommands.joinToString(" && "))
        if (!stage.success) {
            return fail(context, "Jester Mods couldn't prepare the game. Please try again.", "Root payload staging failed: ${stage.output.takeLast(240)}", onProgress)
        }

        // The injector opens the module files as root, transfers them into
        // target-owned memfds, and passes only those descriptors to the game.
        // No payload is installed in /data/adb or copied into another app's data.
        report("Opening game", "Starting ${game.module.title} now.")
        val injection = RootShell.run(
            "${quote(remoteInjector)} " +
                "--package ${quote(game.packageName)} " +
                "--libs ${quote(remoteBootstrap)} " +
                "--payload-dex ${quote(remoteDex)} " +
                "--payload-native ${quote(remoteNative)} " +
                "--launch-language ${selectedMenuLanguage(context)} " +
                "--launch --bp-ld --memfd --timeout 8000"
        )
        if (!injection.success) {
            return fail(context, "The game couldn't be opened. Please try again.", "Root launch failed: ${injection.output.takeLast(300)}", onProgress)
        }

        report("Opening game", "${game.module.title} is starting now.")
        return true
    }

    private fun launchExternalController(
        context: Context,
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)?
    ): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return fail(context, "Allow display over other apps, then press Play again.",
                "External controller overlay permission is missing", onProgress)
        }

        val repository = ModuleRepository(context)
        val module = repository.loadModules().singleOrNull { it.packageName == game.packageName }
            ?: return fail(context, "The Free Fire controller is not installed.",
                "External controller module is missing", onProgress)
        val moduleDirectory = repository.directoryFor(module)
        runCatching { ModuleIntegrityVerifier().verify(moduleDirectory, module, game.abi) }
            .getOrElse { error ->
                return fail(context, "The add-on failed its security check. Repair or update it, then try again.",
                    "External controller verification failed: ${error.message}", onProgress)
            }

        val controllerLoader = PluginLoader(context)
        val expectedPatchedHash = runCatching { controllerLoader.embeddedLibrarySha256(module) }
            .getOrElse { error -> return fail(context,
                "The embedded Free Fire patch failed verification.",
                "Embedded hash declaration failed: ${error.message}", onProgress) }

        onProgress?.invoke("Preparing Free Fire", "Checking the installed game library.")
        val baseApk = RootShell.run("pm path ${quote(game.packageName)}")
            .takeIf { it.success }
            ?.output
            ?.lineSequence()
            ?.map(String::trim)
            ?.firstOrNull { it.startsWith("package:") && it.endsWith("/base.apk") }
            ?.removePrefix("package:")
            ?: return fail(context, "Free Fire's installed files could not be resolved.",
                "Base APK path is unavailable", onProgress)
        val appDirectory = baseApk.substringBeforeLast('/')
        val liveLibrary = "$appDirectory/lib/arm64/libil2cpp.so"
        val liveHash = RootShell.run("sha256sum ${quote(liveLibrary)}")
            .takeIf { it.success }
            ?.output
            ?.trim()
            ?.substringBefore(' ')
            ?.lowercase()
            ?: return fail(context, "Free Fire's game library could not be verified.",
                "Installed libil2cpp hash is unavailable", onProgress)

        if (liveHash != expectedPatchedHash) {
            if (liveHash != ORIGINAL_IL2CPP_SHA256) {
                val rollbackHash = RootShell.run("sha256sum ${quote(IL2CPP_ROLLBACK)}")
                    .takeIf { it.success }?.output?.trim()?.substringBefore(' ')?.lowercase()
                if (rollbackHash != ORIGINAL_IL2CPP_SHA256) {
                    return fail(context, "Free Fire's game library is an unknown build. Restore or update the game first.",
                        "Refusing to replace unexpected libil2cpp hash $liveHash", onProgress)
                }
            }
            val nativePayload = File(moduleDirectory, module.nativeFile)
            val staged = File(context.codeCacheDir, "freefire-controller/libil2cpp.so").apply {
                parentFile?.mkdirs()
                delete()
            }
            val extraction = runCatching {
                controllerLoader.loadNative(module, nativePayload) &&
                    controllerLoader.extractEmbeddedLibrary(module, staged)
            }
            val extractedHash = staged.takeIf(File::isFile)?.let(::sha256)
            if (extraction.getOrDefault(false) != true || extractedHash != expectedPatchedHash) {
                staged.delete()
                return fail(context, "The embedded Free Fire patch failed verification.",
                    extraction.exceptionOrNull()?.let { "Embedded extraction failed: ${it.message}" }
                        ?: "Extracted libil2cpp hash mismatch: ${extractedHash ?: "missing"}", onProgress)
            }

            onProgress?.invoke("Preparing Free Fire", "Installing the verified game library.")
            val temporary = "$appDirectory/lib/arm64/libil2cpp.so.jester-next"
            val installCommands = mutableListOf(
                "am force-stop ${quote(game.packageName)}",
            )
            if (liveHash == ORIGINAL_IL2CPP_SHA256) {
                installCommands += "cp -p ${quote(liveLibrary)} ${quote(IL2CPP_ROLLBACK)}"
            }
            installCommands += listOf(
                "cp -f ${quote(staged.absolutePath)} ${quote(temporary)}",
                "chown 1000:1000 ${quote(temporary)}",
                "chmod 0755 ${quote(temporary)}",
                "restorecon ${quote(temporary)}",
                "mv -f ${quote(temporary)} ${quote(liveLibrary)}",
                "restorecon ${quote(liveLibrary)}"
            )
            val installed = RootShell.run(installCommands.joinToString(" && "))
            staged.delete()
            if (!installed.success) {
                return fail(context, "The patched game library could not be installed.",
                    "External controller install failed: ${installed.output.takeLast(300)}", onProgress)
            }
            val installedHash = RootShell.run("sha256sum ${quote(liveLibrary)}")
                .output.trim().substringBefore(' ').lowercase()
            if (installedHash != expectedPatchedHash) {
                return fail(context, "The installed game library failed verification.",
                    "Installed libil2cpp hash mismatch: $installedHash", onProgress)
            }
        }

        ExternalControllerService.start(context, game.packageName)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(game.packageName)
            ?: return fail(context, "Free Fire could not be opened.", "Launch intent is missing", onProgress)
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        onProgress?.invoke("Opening game", "Free Fire is starting without runtime injection.")
        return true
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractInjector(context: Context, assetPath: String): File {
        val directory = File(context.codeCacheDir, "root-launcher").apply { mkdirs() }
        val output = File(directory, "moodtools-injector")
        val temporary = File(directory, "moodtools-injector.tmp")
        context.assets.open(assetPath).use { input ->
            temporary.outputStream().use { destination -> input.copyTo(destination) }
        }
        check(temporary.length() > 0L) { "Root injector asset is empty" }
        if (output.exists()) check(output.delete()) { "could not replace cached injector" }
        check(temporary.renameTo(output)) { "could not finalize cached injector" }
        output.setReadable(true, true)
        output.setExecutable(true, true)
        return output
    }

    private fun extractRootBootstrap(context: Context): File {
        val extracted = File(context.applicationInfo.nativeLibraryDir, "libmoodtools_root_runtime.so")
        if (extracted.isFile && extracted.length() > 0L) return extracted

        val directory = File(context.codeCacheDir, "root-launcher").apply { mkdirs() }
        val output = File(directory, "libmoodtools_root_runtime.so")
        val temporary = File(directory, "libmoodtools_root_runtime.so.tmp")
        ZipFile(context.applicationInfo.sourceDir).use { apk ->
            val entry = apk.getEntry("lib/arm64-v8a/libmoodtools_root_runtime.so")
                ?: error("ARM64 root bootstrap is absent from the APK")
            apk.getInputStream(entry).use { input ->
                temporary.outputStream().use { destination -> input.copyTo(destination) }
            }
        }
        check(temporary.length() > 0L) { "ARM64 root bootstrap is empty" }
        if (output.exists()) check(output.delete()) { "could not replace cached root bootstrap" }
        check(temporary.renameTo(output)) { "could not finalize cached root bootstrap" }
        output.setReadable(true, true)
        return output
    }

    private fun fail(
        context: Context,
        userMessage: String,
        diagnostic: String,
        onProgress: ((headline: String, detail: String) -> Unit)?
    ): Boolean {
        android.util.Log.e("JesterMoodsLaunch", diagnostic)
        onProgress?.invoke("Launch failed", userMessage)
        showToast(context, userMessage)
        return false
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"
}

internal fun rootInjectorAssetForKernel(kernelMachine: String): String? =
    when (kernelMachine.trim().lowercase()) {
        "aarch64", "arm64" -> "root-runtime/arm64-v8a/moodtools-injector"
        "x86_64", "amd64" -> "root-runtime/x86_64/moodtools-injector"
        else -> null
    }

object ExecutionModeStartupGate {
    fun check(): StartupGateResult {
        val result = RootShell.run("id")
        return if (result.success && result.output.contains("uid=0")) {
            StartupGateResult(allowed = true)
        } else {
            StartupGateResult(
                allowed = false,
                message = "Root permission was not granted. Jester Mods Root cannot safely continue without it, so the launcher will close."
            )
        }
    }
}

private object RootShell {
    data class Result(val success: Boolean, val output: String)

    fun run(command: String): Result {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(60, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return@runCatching Result(false, "Root command timed out")
            }
            Result(process.exitValue() == 0, process.inputStream.bufferedReader().use { it.readText() })
        }.getOrElse { Result(false, it.message ?: it.javaClass.simpleName) }
    }
}
