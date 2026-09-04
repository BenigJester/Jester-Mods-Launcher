package com.moodtools.hub

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import com.moodtools.hub.modules.InstalledGame
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.BEnvironment

/** Owns BlackBox directly inside the single non-root launcher package. */
object NonRootBlackBoxRuntime {
    private const val TAG = "NonRootBlackBox"
    private const val DEFAULT_USER_ID = 0
    private const val SERVICE_READY_TIMEOUT_MS = 12_000L
    private const val SESSION_PREFERENCES = "nonroot_blackbox_session"
    private const val ACTIVE_PACKAGE_KEY = "active_package"
    private const val KINGDOM_ADVENTURERS_PACKAGE = "net.kairosoft.android.kingdom_en"
    private val packagePattern = Regex("^[A-Za-z0-9_.]+$")
    private val launchedPackages = ConcurrentHashMap.newKeySet<String>()
    private val googleCompatibilityStack = listOf(
        "com.google.android.gsf",
        "com.google.android.gms",
        "com.android.vending"
    )

    @Volatile
    var lastFailureDetail: String? = null
        private set

    fun isRunning(packageName: String): Boolean {
        if (!packagePattern.matches(packageName)) return false
        return runCatching {
            BlackBoxCore.get().ensureBlackProcessInitialized()
            BlackBoxCore.isRunningApplication(packageName, DEFAULT_USER_ID)
        }.getOrDefault(false)
    }

    /** Uninstalls one managed guest, including its BlackBox identity and per-game data. */
    fun removePackage(context: Context, packageName: String) {
        require(packagePattern.matches(packageName)) { "Invalid game package name" }
        val appContext = context.applicationContext
        val core = BlackBoxCore.get()
        core.ensureBlackProcessReady(SERVICE_READY_TIMEOUT_MS)

        val wasActive = activePackage(appContext) == packageName
        val wasRunning = BlackBoxCore.isRunningApplication(packageName, DEFAULT_USER_ID)
        runCatching { core.stopPackage(packageName, DEFAULT_USER_ID) }
            .onFailure { error ->
                Log.w(TAG, "Could not stop virtual package $packageName before removal", error)
            }

        if (core.isInstalled(packageName, DEFAULT_USER_ID)) {
            core.uninstallPackageAsUser(packageName, DEFAULT_USER_ID)
        }
        check(!core.isInstalled(packageName, DEFAULT_USER_ID)) {
            "BlackBox could not remove $packageName"
        }
        removeRemainingPackageStorage(packageName, DEFAULT_USER_ID)

        launchedPackages.remove(packageName)
        if (wasActive) clearActivePackage(appContext)
        if (wasActive || wasRunning) finishVirtualGuestTasks(appContext)
        Log.i(TAG, "Removed virtual package identity and data for $packageName")
    }

    private fun removeRemainingPackageStorage(packageName: String, userId: Int) {
        val targets = listOf(
            BEnvironment.getDataDir(packageName, userId),
            BEnvironment.getDeDataDir(packageName, userId),
            BEnvironment.getExternalDataDir(packageName, userId),
            BEnvironment.getExternalObbDir(packageName, userId),
            BEnvironment.getAppDir(packageName)
        )
        targets.forEach { target ->
            if (target.exists()) {
                check(target.deleteRecursively() && !target.exists()) {
                    "Could not delete BlackBox storage for $packageName"
                }
            }
        }
    }

    /** Stops every virtual guest and all auxiliary BlackBox processes owned by this launcher. */
    fun shutdown(context: Context) {
        val appContext = context.applicationContext
        val core = BlackBoxCore.get()
        val rememberedPackage = activePackage(appContext)
        val packagesToStop = launchedPackages.toMutableSet().apply {
            rememberedPackage?.let(::add)
            addAll(runningModulePackages(appContext))
        }
        packagesToStop.forEach { packageName ->
            runCatching { core.stopPackage(packageName, DEFAULT_USER_ID) }
                .onFailure { error ->
                    Log.w(TAG, "Could not stop virtual package $packageName", error)
                }
        }
        launchedPackages.clear()
        clearActivePackage(appContext)
        finishVirtualGuestTasks(appContext)

        val currentPid = Process.myPid()
        val currentUid = Process.myUid()
        val hostPrefix = appContext.packageName + ":"
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activityManager?.runningAppProcesses.orEmpty()
            .asSequence()
            .filter { it.uid == currentUid && it.pid != currentPid && it.processName.startsWith(hostPrefix) }
            .forEach { process ->
                Log.i(TAG, "Stopping BlackBox process ${process.processName} pid=${process.pid}")
                Process.killProcess(process.pid)
            }
    }

    fun launch(context: Context, game: InstalledGame): Boolean {
        lastFailureDetail = null
        return runCatching {
            require(packagePattern.matches(game.packageName)) { "Invalid game package name" }
            val core = BlackBoxCore.get()
            core.ensureBlackProcessReady(SERVICE_READY_TIMEOUT_MS)
            val userId = DEFAULT_USER_ID

            prepareGuestSwitch(context, core, game.packageName, userId)
            val retryInternalStorage = needsInternalStorageRetry(game.packageName, userId)
            if (BlackBoxCore.isRunningApplication(game.packageName, userId) && !retryInternalStorage) {
                Log.i(TAG, "Resuming existing virtual session for ${game.packageName} user=$userId")
                check(core.launchApk(game.packageName, userId)) {
                    "BlackBox could not resume ${game.packageName} on user $userId"
                }
                launchedPackages += game.packageName
                rememberActivePackage(context, game.packageName)
                return@runCatching true
            }

            runCatching { core.stopPackage(game.packageName, userId) }
            clearModulePayload(game.packageName, userId)
            ensureGoogleCompatibilityStack(context, core, userId)

            val hostInfo = context.packageManager.getApplicationInfo(game.packageName, 0)
            Log.i(
                TAG,
                "Host clone ${game.packageName}: user=$userId base=${hostInfo.sourceDir} " +
                    "splits=${hostInfo.splitSourceDirs?.size ?: 0}"
            )
            val install = core.installPackageAsUser(game.packageName, userId)
            if (!install.success) {
                error(
                    "Game install failed for ${game.packageName}: " +
                        (install.msg ?: "BlackBox installation failed")
                )
            }

            clearModulePayload(game.packageName, userId)
            stageModulePayload(context, game, userId)
            retryInternalStorageIfNeeded(game.packageName, userId)
            logGoogleCompatibilityState(core, userId)

            check(core.launchApk(game.packageName, userId)) {
                "BlackBox launchApk returned false for ${game.packageName} on user $userId"
            }
            launchedPackages += game.packageName
            rememberActivePackage(context, game.packageName)
            true
        }.onFailure { error ->
            lastFailureDetail = error.message ?: error.javaClass.simpleName
            Log.e(TAG, "Virtual launch failed for ${game.packageName}: $lastFailureDetail", error)
            runCatching { clearModulePayload(game.packageName, DEFAULT_USER_ID) }
        }.getOrDefault(false)
    }

    /**
     * BlackBox proxy activities all belong to the launcher package. When Android recreates the
     * launcher process, an old guest task can outlive the in-memory launch set and be selected for
     * the next proxy launch. Retire every conflicting guest before resolving the requested game.
     */
    private fun prepareGuestSwitch(
        context: Context,
        core: BlackBoxCore,
        targetPackage: String,
        userId: Int
    ) {
        val appContext = context.applicationContext
        val rememberedPackage = activePackage(appContext)
        val candidates = launchedPackages.toMutableSet().apply {
            rememberedPackage?.let(::add)
            addAll(runningModulePackages(appContext))
        }
        val conflictingPackages = candidates.filterTo(linkedSetOf()) { packageName ->
            packageName != targetPackage && packagePattern.matches(packageName) &&
                (packageName == rememberedPackage || BlackBoxCore.isRunningApplication(packageName, userId))
        }
        val targetRunning = BlackBoxCore.isRunningApplication(targetPackage, userId)

        conflictingPackages.forEach { packageName ->
            Log.i(TAG, "Stopping previous virtual session $packageName before opening $targetPackage")
            runCatching { core.stopPackage(packageName, userId) }
                .onFailure { error -> Log.w(TAG, "Could not stop previous guest $packageName", error) }
            launchedPackages.remove(packageName)
        }

        // A non-running target needs a fresh proxy task. This also removes an orphaned game1 task
        // when the launcher process was reclaimed before it could persist any in-memory state.
        if (conflictingPackages.isNotEmpty() || !targetRunning) {
            finishVirtualGuestTasks(appContext)
        }
        if (rememberedPackage != null && rememberedPackage != targetPackage) {
            clearActivePackage(appContext)
        }
    }

    private fun runningModulePackages(context: Context): Set<String> {
        val moduleRoot = File(context.filesDir, "menus")
        return moduleRoot.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .map(File::getName)
            .filter(packagePattern::matches)
            .filter { packageName ->
                runCatching { BlackBoxCore.isRunningApplication(packageName, DEFAULT_USER_ID) }
                    .getOrDefault(false)
            }
            .toSet()
    }

    private fun finishVirtualGuestTasks(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        activityManager.appTasks.orEmpty().forEach { task ->
            val taskInfo = runCatching { task.taskInfo }.getOrNull() ?: return@forEach
            val components = listOfNotNull(
                taskInfo.baseIntent?.component,
                taskInfo.baseActivity,
                taskInfo.topActivity
            )
            if (components.none { component ->
                    isVirtualGuestTaskComponent(
                        context.packageName,
                        component.packageName,
                        component.className
                    )
                }) {
                return@forEach
            }
            runCatching { task.finishAndRemoveTask() }
                .onSuccess { Log.i(TAG, "Removed stale BlackBox guest task ${taskInfo.taskId}") }
                .onFailure { error -> Log.w(TAG, "Could not remove BlackBox guest task", error) }
        }
    }

    private fun activePackage(context: Context): String? =
        context.getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
            .getString(ACTIVE_PACKAGE_KEY, null)
            ?.takeIf(packagePattern::matches)

    private fun rememberActivePackage(context: Context, packageName: String) {
        check(packagePattern.matches(packageName))
        context.applicationContext
            .getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(ACTIVE_PACKAGE_KEY, packageName)
            .commit()
    }

    private fun clearActivePackage(context: Context) {
        context.applicationContext
            .getSharedPreferences(SESSION_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(ACTIVE_PACKAGE_KEY)
            .commit()
    }

    private fun ensureGoogleCompatibilityStack(context: Context, core: BlackBoxCore, userId: Int) {
        for (packageName in googleCompatibilityStack) {
            val hostInstalled = try {
                context.packageManager.getApplicationInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            if (!hostInstalled) {
                Log.i(TAG, "Google compatibility package not installed on host: $packageName")
                continue
            }

            val alreadyInstalled = core.isInstalled(packageName, userId)
            val result = core.installPackageAsUser(packageName, userId)
            if (result.success) {
                Log.i(
                    TAG,
                    (if (alreadyInstalled) "Refreshed " else "Cloned ") +
                        "$packageName into BlackBox user $userId"
                )
            } else {
                Log.w(
                    TAG,
                    "Could not ${if (alreadyInstalled) "refresh" else "clone"} $packageName " +
                        "into BlackBox user $userId: ${result.msg ?: "unknown install error"}"
                )
            }
        }
    }

    private fun logGoogleCompatibilityState(core: BlackBoxCore, userId: Int) {
        val states = googleCompatibilityStack.joinToString { packageName ->
            "$packageName=${core.isInstalled(packageName, userId)}"
        }
        Log.i(TAG, "Google compatibility stack user=$userId: $states")
    }

    private fun clearModulePayload(packageName: String, userId: Int) {
        val targetDirectory = BEnvironment.getDataFilesDir(packageName, userId)
        listOf("libmenu_native.so", "classes.dex", "config.json").forEach { name ->
            val payload = File(targetDirectory, name)
            if (payload.exists() && !payload.delete()) {
                Log.w(TAG, "Could not delete stale BlackBox module file: ${payload.absolutePath}")
            }
            File(targetDirectory, "$name.incoming").delete()
        }
    }

    private fun needsInternalStorageRetry(packageName: String, userId: Int): Boolean {
        if (packageName != KINGDOM_ADVENTURERS_PACKAGE) return false
        val preferences = kingdomPlayerPreferences(packageName, userId)
        return preferences.isFile && rewriteKairoStoragePreference(preferences.readText()) != null
    }

    private fun retryInternalStorageIfNeeded(packageName: String, userId: Int) {
        if (packageName != KINGDOM_ADVENTURERS_PACKAGE) return
        val preferences = kingdomPlayerPreferences(packageName, userId)
        if (!preferences.isFile) return
        val rewritten = rewriteKairoStoragePreference(preferences.readText()) ?: return

        val atomicFile = AtomicFile(preferences)
        val output = atomicFile.startWrite()
        try {
            output.write(rewritten.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
            Log.i(TAG, "Reset BlackBox storage fallback for $packageName")
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun kingdomPlayerPreferences(packageName: String, userId: Int): File = File(
        BEnvironment.getDataDir(packageName, userId),
        "shared_prefs/$packageName.v2.playerprefs.xml"
    )

    private fun stageModulePayload(context: Context, game: InstalledGame, userId: Int) {
        val moduleDirectory = File(context.filesDir, "menus/${game.packageName}")
        val nativeSource = File(moduleDirectory, game.module.nativeFile)
        val dexSource = File(moduleDirectory, game.module.dexFile)
        val configSource = File(moduleDirectory, "config.json")
        require(nativeSource.isFile) { "Module native library is missing" }
        require(dexSource.isFile) { "Module DEX is missing" }
        require(configSource.isFile) { "Module config is missing" }

        val configuredPackage = org.json.JSONObject(configSource.readText())
            .optString("package_name", "")
            .trim()
        require(configuredPackage == game.packageName) {
            "Module config targets $configuredPackage instead of ${game.packageName}"
        }

        val targetDirectory = BEnvironment.getDataFilesDir(game.packageName, userId)
        check(targetDirectory.mkdirs() || targetDirectory.isDirectory) {
            "Could not create BlackBox module directory"
        }
        copyAtomically(nativeSource, File(targetDirectory, "libmenu_native.so"))
        copyAtomically(dexSource, File(targetDirectory, "classes.dex"), readOnly = true)
        copyAtomically(configSource, File(targetDirectory, "config.json"))
    }

    private fun copyAtomically(source: File, target: File, readOnly: Boolean = false) {
        val incoming = File(target.parentFile, "${target.name}.incoming")
        incoming.delete()
        source.copyTo(incoming, overwrite = true)
        check(incoming.length() > 0L) { "${target.name} is empty" }
        if (readOnly) {
            check(incoming.setReadOnly()) { "Could not protect ${target.name}" }
        }
        if (target.exists()) check(target.delete()) { "Could not replace ${target.name}" }
        check(incoming.renameTo(target)) { "Could not finalize ${target.name}" }
    }
}

internal fun isVirtualGuestTaskComponent(
    hostPackage: String,
    componentPackage: String?,
    componentClass: String?
): Boolean = componentPackage == hostPackage && componentClass != null &&
    (componentClass.startsWith("top.niunaijun.blackbox.proxy.") ||
        componentClass == "top.niunaijun.blackbox.app.LauncherActivity")

private val kairoExternalStoragePreference = Regex(
    """(<int\s+name=["']_storage_path["']\s+value=["'])0(["']\s*/>)"""
)

internal fun rewriteKairoStoragePreference(xml: String): String? {
    val rewritten = kairoExternalStoragePreference.replaceFirst(xml, "$1" + "1" + "$2")
    return rewritten.takeUnless { it == xml }
}
