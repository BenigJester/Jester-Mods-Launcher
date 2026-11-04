package com.moodtools.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.moodtools.hub.modules.InstalledGame
import com.moodtools.hub.modules.ModuleIntegrityVerifier
import com.moodtools.hub.modules.ModuleRepository
import com.moodtools.hub.modules.PluginLoader
import java.io.File
import java.security.MessageDigest

/** Persistent, backed-up managed bundle replacement. No native library mount. */
internal object SuperSusBundleLauncher {
    fun launch(
        context: Context,
        game: InstalledGame,
        onProgress: ((String, String) -> Unit)?
    ): Boolean {
        checkVersion(context)
        check(game.packageName == SuperSusBundleContract.PACKAGE && game.abi == "arm64-v8a")
        if (!Settings.canDrawOverlays(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            error("Allow display over other apps, then press Play again.")
        }
        val intent = context.packageManager.getLaunchIntentForPackage(game.packageName)
            ?: error("Super Sus launch activity is unavailable")
        val repository = ModuleRepository(context)
        val module = repository.loadModules().single { it.packageName == game.packageName }
        val directory = repository.directoryFor(module)
        ModuleIntegrityVerifier().verify(directory, module, game.abi)
        val loader = PluginLoader(context)
        check(loader.embeddedLibrarySha256(module) == SuperSusBundleContract.PATCHED) {
            "The installed Super Sus module has an unsupported managed bundle"
        }
        // Legacy extractor names are retained at the module ABI boundary only.
        // This output is a UnityFS bundle, never a native library.
        loader.loadNativeForExtraction(module, File(directory, module.nativeFile))
        val staged = File(context.codeCacheDir, "supersus-bundle/roles.bundle.ab")
        check(staged.parentFile?.isDirectory == true || staged.parentFile?.mkdirs() == true)
        check(!staged.exists() || staged.delete()) { "Could not clear the cached Super Sus bundle" }
        check(loader.extractEmbeddedLibrary(module, staged) &&
            staged.length() == SuperSusBundleContract.PAYLOAD_BYTES &&
            sha256(staged) == SuperSusBundleContract.PATCHED) { "Super Sus bundle extraction failed verification" }
        val userId = android.os.Process.myUid() / 100000
        ExternalControllerService.stop(context)
        onProgress?.invoke("Preparing Super Sus", "Verifying resource build 387/415 and backing up its original bundle.")
        try {
            val result = RootShell.run(SuperSusBundleContract.installCommand(userId, staged.absolutePath))
            val installed = result.success && result.output.contains("bundle-verified:" + SuperSusBundleContract.PATCHED)
            check(installed) { "Super Sus bundle install failed: " + result.output.takeLast(400) }
            RootShell.run("pm grant " + quote(context.packageName) + " android.permission.POST_NOTIFICATIONS")
            RootLaunchDiagnostics.begin(context, game, SuperSusBundleContract.PATCHED)
            ExternalControllerService.start(context, game.packageName)
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            onProgress?.invoke("Opening game",
                "Verified managed bundle installed. Known-role colors are still marked for testing.")
            return true
        } catch (failure: Exception) {
            ExternalControllerService.stop(context)
            // Atomic preparation leaves the original intact on pre-install errors.
            // Also attempt guarded recovery if final verification failed after rename.
            val rollback = RootShell.run(SuperSusBundleContract.restoreCommand(userId))
            if (!rollback.success) failure.addSuppressed(
                IllegalStateException("Bundle rollback requires review: " + rollback.output.takeLast(300)))
            throw failure
        }
    }

    fun restore(context: Context) {
        checkVersion(context)
        ExternalControllerService.stop(context)
        val result = RootShell.run(SuperSusBundleContract.restoreCommand(android.os.Process.myUid() / 100000))
        check(result.success && (result.output.contains("bundle-verified:" + SuperSusBundleContract.ORIGINAL) ||
            result.output.contains("bundle-original:" + SuperSusBundleContract.ORIGINAL))) {
            "Super Sus bundle restore failed: " + result.output.takeLast(400)
        }
    }

    @Suppress("DEPRECATION")
    private fun checkVersion(context: Context) {
        val info = context.packageManager.getPackageInfo(SuperSusBundleContract.PACKAGE, 0)
        check(info.versionName == SuperSusBundleContract.VERSION &&
            info.versionCode.toLong() == SuperSusBundleContract.VERSION_CODE) {
            "This Super Sus APK build is not supported by the managed bundle profile"
        }
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
