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
    val requiresUninstall: Boolean,
    val kind: PackageReplacementKind = PackageReplacementKind.DIRECT_PATCH,
    /** Updates only supplied APKs while Android retains the installed package's existing splits. */
    val inheritExistingInstallation: Boolean = false
)

enum class PackageReplacementKind {
    DIRECT_PATCH,
    IDENTITY_SHELL
}

data class PackageReplacementInstallResult(
    val packageName: String,
    val successful: Boolean,
    val message: String?,
    val status: Int? = null,
    val sessionId: Int? = null,
    internal val observation: PackageInstallObservation = PackageInstallObservation.RESULT
)

object PackageReplacementInstallEvents {
    val results = MutableSharedFlow<PackageReplacementInstallResult>(extraBufferCapacity = 1)
}

class PackageReplacementInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (packageName.isBlank()) return
        val reportedStatus = if (intent.hasExtra(PackageInstaller.EXTRA_STATUS))
            intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) else null
        val status = reportedStatus ?: PackageInstaller.STATUS_FAILURE
        val sessionId = if (intent.hasExtra(PackageInstaller.EXTRA_SESSION_ID))
            intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1).takeIf { it >= 0 } else null
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirmation == null) {
                emitFailure(packageName, "Android did not provide an installation screen", reportedStatus, sessionId,
                    PackageInstallObservation.CONFIRMATION_MISSING)
                return
            }
            runCatching {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirmation)
                PackageReplacementInstallEvents.results.tryEmit(PackageReplacementInstallResult(
                    packageName, false, null, reportedStatus, sessionId, PackageInstallObservation.CONFIRMATION_OPENED))
            }.onFailure { error ->
                emitFailure(
                    packageName,
                    error.message?.take(240)?.takeIf { it.isNotBlank() }
                        ?: "Android could not open its installation screen",
                    reportedStatus, sessionId, PackageInstallObservation.CONFIRMATION_OPEN_FAILED
                )
            }
            return
        }
        PackageReplacementInstallEvents.results.tryEmit(
            PackageReplacementInstallResult(
                packageName = packageName,
                successful = status == PackageInstaller.STATUS_SUCCESS,
                message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.take(240),
                status = reportedStatus, sessionId = sessionId
            )
        )
    }

    private fun emitFailure(packageName: String, message: String, status: Int?, sessionId: Int?, observation: PackageInstallObservation) {
        PackageReplacementInstallEvents.results.tryEmit(
            PackageReplacementInstallResult(
                packageName = packageName,
                successful = false,
                message = message, status = status, sessionId = sessionId, observation = observation
            )
        )
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.moodtools.hub.PACKAGE_REPLACEMENT_INSTALL_RESULT"
        const val EXTRA_PACKAGE = "replacement_package"
    }
}
