package com.moodtools.hub

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.security.MessageDigest

/** Account setup belongs to the companion. Never inspect accounts, credentials or tokens. */
internal object GmsCoreSupport {
    const val PACKAGE = "app.revanced.android.gms"
    const val RELEASE_URL = "https://github.com/ReVanced/GmsCore/releases/tag/v0.3.13.2.250932"
    const val SIGNER = "d732e4055c97f8d7a43170a6dc737dc71b6f3e0410940e1b3706c6ade1ff3488"
    const val MIN_VERSION = 250932004L
    const val PROFILE = "google-sdk-routing-v2"

    enum class State(val label: String) {
        MISSING("Install GmsCore"), UNTRUSTED("GmsCore could not be verified"),
        DISABLED("Enable GmsCore"), OLD("Update GmsCore"), READY("GmsCore is ready")
    }

    fun supports(module: com.moodtools.hub.modules.ModuleConfig) =
        !BuildConfig.IS_ROOT_MODE && module.gmsCoreSupport &&
            com.moodtools.hub.modules.NonRootMethod.DIRECT_PATCH in module.nonRootMethods

    @Suppress("DEPRECATION")
    fun state(context: Context): State = try {
        val info = context.packageManager.getPackageInfo(PACKAGE,
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val hashes = signers.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        classify(hashes, info.applicationInfo?.enabled == true,
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
    } catch (_: PackageManager.NameNotFoundException) { State.MISSING }
      catch (_: Exception) { State.UNTRUSTED }

    internal fun classify(signers: List<String>, enabled: Boolean, version: Long): State = when {
        signers != listOf(SIGNER) -> State.UNTRUSTED
        !enabled -> State.DISABLED
        version < MIN_VERSION -> State.OLD
        else -> State.READY
    }

    fun openSettings(context: Context) {
        val current = state(context)
        check(current in setOf(State.READY, State.DISABLED, State.OLD))
        val intent = (if (current == State.DISABLED) null else context.packageManager.getLaunchIntentForPackage(PACKAGE))
            ?: Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$PACKAGE"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
