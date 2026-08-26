package com.moodtools.hub.networking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow

data class GameInstallResult(
    val packageName: String,
    val versionCode: Long,
    val successful: Boolean,
    val message: String?
)

object GameInstallEvents {
    val results = MutableSharedFlow<GameInstallResult>(extraBufferCapacity = 1)
}

class GameInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val versionCode = intent.getLongExtra(EXTRA_VERSION_CODE, 0L)
        if (packageName.isBlank() || versionCode <= 0L) return

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            return
        }

        val successful = status == PackageInstaller.STATUS_SUCCESS
        if (successful) GameInstallClient(context).clearDownload(packageName, versionCode)
        GameInstallEvents.results.tryEmit(
            GameInstallResult(
                packageName = packageName,
                versionCode = versionCode,
                successful = successful,
                message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.take(240)
            )
        )
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.moodtools.hub.GAME_INSTALL_RESULT"
        const val EXTRA_PACKAGE = "game_package"
        const val EXTRA_VERSION_CODE = "game_version_code"
    }
}
