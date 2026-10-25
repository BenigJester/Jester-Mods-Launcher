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
    const val OPTIONAL_SETUP_GUIDANCE =
        "If you do not need Google login, turn off Use GmsCore and retry setup."

    enum class State(val label: String) {
        MISSING("Install GmsCore"), UNTRUSTED("GmsCore could not be verified"),
        DISABLED("Enable GmsCore"), OLD("Update GmsCore"), READY("GmsCore is ready")
    }

    fun setupFailureDetail(state: State): String =
        "${state.label}\nSet up GmsCore from Google account support first\n$OPTIONAL_SETUP_GUIDANCE"

    /** Fixed values captured before preparation, not a query of account/sign-in state. */
    fun setupDiagnostics(supported: Boolean, selected: Boolean, state: State): List<String> = listOf(
        "GmsCore setup snapshot: before package preparation",
        "gmscoreSupported: $supported",
        "gmscoreSelected: $selected",
        "gmscoreRequired: ${supported && selected}",
        "gmscoreState: ${state.name}",
        "Companion availability does not confirm account sign-in."
    )

    fun supports(module: com.moodtools.hub.modules.ModuleConfig) =
        !BuildConfig.IS_ROOT_MODE && module.gmsCoreSupport &&
            module.effectiveNonRootMethod == com.moodtools.hub.modules.NonRootMethod.DIRECT_PATCH &&
            com.moodtools.hub.modules.NonRootMethod.DIRECT_PATCH in module.nonRootMethods

    fun enabled(context: Context, packageName: String) =
        !BuildConfig.IS_ROOT_MODE && context.getSharedPreferences("gmscore_support", Context.MODE_PRIVATE)
            .getBoolean(packageName, false)

    fun select(context: Context, module: com.moodtools.hub.modules.ModuleConfig, enabled: Boolean) {
        require(supports(module))
        context.getSharedPreferences("gmscore_support", Context.MODE_PRIVATE).edit()
            .putBoolean(module.packageName, enabled).apply()
    }

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
