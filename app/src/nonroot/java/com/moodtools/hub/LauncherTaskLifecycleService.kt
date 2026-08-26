package com.moodtools.hub

import android.app.ActivityManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

/** Receives the launcher-task removal even while a BlackBox guest is in another task. */
class LauncherTaskLifecycleService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        val removedRoot = rootIntent?.component
        val launcherRemoved = isLauncherTaskComponent(
            packageName,
            removedRoot?.packageName,
            removedRoot?.className
        ) || (removedRoot == null && !launcherTaskIsPresent())
        if (!launcherRemoved) {
            Log.i(
                TAG,
                "BlackBox guest task removed; keeping the launcher and other guest state alive"
            )
            super.onTaskRemoved(rootIntent)
            return
        }
        Log.i(TAG, "Launcher task removed; stopping BlackBox guests")
        NonRootBlackBoxRuntime.shutdown(applicationContext)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun launcherTaskIsPresent(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return activityManager.appTasks.orEmpty().any { task ->
            val info = runCatching { task.taskInfo }.getOrNull() ?: return@any false
            listOfNotNull(info.baseIntent?.component, info.baseActivity, info.topActivity)
                .any(::isLauncherComponent)
        }
    }

    private fun isLauncherComponent(component: ComponentName): Boolean =
        isLauncherTaskComponent(packageName, component.packageName, component.className)

    companion object {
        private const val TAG = "NonRootBlackBox"

        fun ensureRunning(context: Context) {
            context.applicationContext.startService(
                Intent(context.applicationContext, LauncherTaskLifecycleService::class.java)
            )
        }
    }
}

internal fun isLauncherTaskComponent(
    hostPackage: String,
    componentPackage: String?,
    componentClass: String?
): Boolean = componentPackage == hostPackage &&
    (componentClass == "$hostPackage.LauncherActivity" || componentClass == ".LauncherActivity")
