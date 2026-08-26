package com.moodtools.hub.networking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow

object LauncherUpdateInstallEvents {
    val results = MutableSharedFlow<Long>(extraBufferCapacity = 1)
}

class LauncherUpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val build = intent.getLongExtra(EXTRA_BUILD, 0L)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                LauncherUpdateClient(context).markInstallSucceeded(build)
                statusPreferences(context).edit()
                    .putString(STATUS_KEY, STATUS_SUCCESS_VALUE)
                    .putLong(BUILD_KEY, build)
                    .remove(MESSAGE_KEY)
                    .apply()
                LauncherUpdateInstallEvents.results.tryEmit(build)
            }
            else -> {
                statusPreferences(context).edit()
                    .putString(STATUS_KEY, STATUS_FAILURE_VALUE)
                    .putLong(BUILD_KEY, build)
                    .putString(MESSAGE_KEY, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.take(240))
                    .apply()
                LauncherUpdateInstallEvents.results.tryEmit(build)
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.moodtools.hub.LAUNCHER_UPDATE_RESULT"
        const val EXTRA_BUILD = "launcher_update_build"
        const val STATUS_KEY = "status"
        const val BUILD_KEY = "build"
        const val MESSAGE_KEY = "message"
        const val STATUS_FAILURE_VALUE = "failure"
        const val STATUS_SUCCESS_VALUE = "success"
        private const val PREFERENCES = "launcher_update_install"

        fun statusPreferences(context: Context) =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }
}
