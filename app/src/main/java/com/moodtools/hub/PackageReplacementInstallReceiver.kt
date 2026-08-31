package com.moodtools.hub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow

data class PackageReplacementRequest(
    val packageName: String,
    val title: String,
    val versionCode: Long,
    val apks: List<File>,
    val requiresUninstall: Boolean
)

data class PackageReplacementInstallResult(
    val packageName: String,
    val successful: Boolean,
    val message: String?
)

object PackageReplacementInstallEvents {
    val results = MutableSharedFlow<PackageReplacementInstallResult>(extraBufferCapacity = 1)
}

class PackageReplacementInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (packageName.isBlank()) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirmation == null) {
                emitFailure(packageName, "Android did not provide an installation screen")
                return
            }
            runCatching {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmation)
            }.onFailure { error ->
                emitFailure(
                    packageName,
                    error.message?.take(240)?.takeIf { it.isNotBlank() }
                        ?: "Android could not open its installation screen"
                )
            }
            return
        }
        PackageReplacementInstallEvents.results.tryEmit(
            PackageReplacementInstallResult(
                packageName = packageName,
                successful = status == PackageInstaller.STATUS_SUCCESS,
                message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.take(240)
            )
        )
    }

    private fun emitFailure(packageName: String, message: String) {
        PackageReplacementInstallEvents.results.tryEmit(
            PackageReplacementInstallResult(
                packageName = packageName,
                successful = false,
                message = message
            )
        )
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.moodtools.hub.PACKAGE_REPLACEMENT_INSTALL_RESULT"
        const val EXTRA_PACKAGE = "replacement_package"
    }
}
