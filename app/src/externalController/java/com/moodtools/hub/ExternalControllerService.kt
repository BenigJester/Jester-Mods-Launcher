package com.moodtools.hub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.moodtools.hub.modules.ModuleConfig
import com.moodtools.hub.modules.ModuleIntegrityVerifier
import com.moodtools.hub.modules.ModuleRepository
import com.moodtools.hub.modules.PluginContext
import com.moodtools.hub.modules.PluginLoader
import java.io.File

/** Hosts a module menu in Jester's process; no controller code enters the game process. */
class ExternalControllerService : Service() {
    private val pluginLoader by lazy { PluginLoader(this) }
    private var activePackage: String? = null
    private var activeModule: ModuleConfig? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            "External controller",
            NotificationManager.IMPORTANCE_LOW
        ))
        val launcher = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Jester Mods controller active")
            .setContentText("Keeping the external game menu available")
            .setContentIntent(launcher)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE)
        if (packageName.isNullOrBlank()) return START_REDELIVER_INTENT
        val featureStatePath = intent.getStringExtra(EXTRA_FEATURE_STATE_PATH)
        val directPatch = intent.getBooleanExtra(EXTRA_DIRECT_PATCH, false)
        if (packageName == activePackage) {
            if (directPatch) {
                runCatching {
                    val module = requireNotNull(activeModule)
                    val directory = ModuleRepository(this).directoryFor(module)
                    pluginLoader.loadNativeDirectPatchExternal(
                        module,
                        File(directory, module.nativeFile)
                    )
                }.onFailure { error -> Log.e(TAG, "Direct-patch state resend failed", error) }
            }
            return START_REDELIVER_INTENT
        }
        runCatching {
            val repository = ModuleRepository(this)
            val module = repository.loadModules().single { it.packageName == packageName }
            val moduleDirectory = repository.directoryFor(module)
            val abi = module.supportedAbis.first()
            ModuleIntegrityVerifier().verify(moduleDirectory, module, abi)
            val nativePayload = File(moduleDirectory, module.nativeFile)
            if (directPatch) {
                pluginLoader.loadNativeDirectPatchExternal(module, nativePayload)
            } else if (featureStatePath.isNullOrBlank()) {
                pluginLoader.loadNative(module, nativePayload)
            } else {
                pluginLoader.loadNativeBlackBox(module, nativePayload, File(featureStatePath))
            }
            val plugin = pluginLoader.load(module) ?: error("Module menu entry point is missing")
            plugin.onLaunch(PluginContext(this, packageName, moduleDirectory.absolutePath,
                "external_controller"))
            activePackage = packageName
            activeModule = module
        }.onFailure { error ->
            Log.e(TAG, "External controller failed", error)
            stopSelf(startId)
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        activeModule?.let { runCatching { pluginLoader.stopExternal(it) } }
        activeModule = null
        activePackage = null
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "JesterExternalControl"
        private const val CHANNEL_ID = "external_controller"
        private const val NOTIFICATION_ID = 4127
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_FEATURE_STATE_PATH = "feature_state_path"
        private const val EXTRA_DIRECT_PATCH = "direct_patch"

        fun start(context: android.content.Context, packageName: String) {
            context.startForegroundService(Intent(context, ExternalControllerService::class.java)
                .putExtra(EXTRA_PACKAGE, packageName))
        }

        fun startBlackBox(
            context: android.content.Context,
            packageName: String,
            featureStatePath: File
        ) {
            context.startForegroundService(Intent(context, ExternalControllerService::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_FEATURE_STATE_PATH, featureStatePath.absolutePath))
        }

        fun startDirectPatch(context: android.content.Context, packageName: String) {
            context.startForegroundService(Intent(context, ExternalControllerService::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_DIRECT_PATCH, true))
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, ExternalControllerService::class.java))
        }
    }
}
