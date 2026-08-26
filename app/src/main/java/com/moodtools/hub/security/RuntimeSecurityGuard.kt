package com.moodtools.hub.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.util.Log
import com.moodtools.hub.BuildConfig
import com.moodtools.hub.nativebridge.NativeLinker
import java.security.MessageDigest

data class RuntimeSecurityInputs(
    val enforced: Boolean,
    val signerConfigured: Boolean,
    val signerMatched: Boolean,
    val appDebuggable: Boolean,
    val debuggerConnected: Boolean,
    val nativeAvailable: Boolean,
    val nativeSignals: Int
)

data class RuntimeSecurityDecision(
    val allowed: Boolean,
    val blockingSignals: Set<String>,
    val advisorySignals: Set<String>
)

object RuntimeSecurityPolicy {
    const val NATIVE_TRACER = 1 shl 0
    const val NATIVE_INSTRUMENTATION_MAP = 1 shl 1
    const val NATIVE_HOOK_FRAMEWORK_MAP = 1 shl 2
    const val NATIVE_INSTRUMENTATION_THREAD = 1 shl 3
    const val NATIVE_WRITABLE_EXECUTABLE_MAP = 1 shl 4

    private const val BLOCKING_NATIVE_MASK =
        NATIVE_TRACER or NATIVE_INSTRUMENTATION_MAP or NATIVE_INSTRUMENTATION_THREAD

    fun evaluate(input: RuntimeSecurityInputs): RuntimeSecurityDecision {
        val blocking = linkedSetOf<String>()
        val advisory = linkedSetOf<String>()
        if (!input.signerConfigured) blocking += "signer-pin-missing"
        else if (!input.signerMatched) blocking += "signer-mismatch"
        if (input.appDebuggable) blocking += "debuggable-build"
        if (input.debuggerConnected) blocking += "debugger-connected"
        if (!input.nativeAvailable) blocking += "native-guard-unavailable"
        if (input.nativeSignals and BLOCKING_NATIVE_MASK != 0) blocking += "native-instrumentation"
        if (input.nativeSignals and NATIVE_HOOK_FRAMEWORK_MAP != 0) advisory += "hook-framework-map"
        if (input.nativeSignals and NATIVE_WRITABLE_EXECUTABLE_MAP != 0) advisory += "writable-executable-map"
        return RuntimeSecurityDecision(
            allowed = !input.enforced || blocking.isEmpty(),
            blockingSignals = blocking,
            advisorySignals = advisory
        )
    }
}

object RuntimeSecurityGuard {
    fun inspect(context: Context): RuntimeSecurityDecision {
        val expectedPins = BuildConfig.TRUSTED_SIGNER_SHA256
            .split(',', ';')
            .map { it.trim().lowercase() }
            .filter { it.matches(Regex("[0-9a-f]{64}")) }
            .toSet()
        val installedPins = runCatching { signingDigests(context) }.getOrDefault(emptySet())
        val nativeInspection = runCatching { NativeLinker.inspectRuntime() }
        val input = RuntimeSecurityInputs(
            enforced = BuildConfig.RUNTIME_SECURITY_ENFORCED,
            signerConfigured = expectedPins.isNotEmpty(),
            signerMatched = expectedPins.any(installedPins::contains),
            appDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            debuggerConnected = Debug.isDebuggerConnected() || Debug.waitingForDebugger(),
            nativeAvailable = nativeInspection.isSuccess,
            nativeSignals = nativeInspection.getOrDefault(0)
        )
        val decision = RuntimeSecurityPolicy.evaluate(input)
        if (decision.blockingSignals.isNotEmpty() || decision.advisorySignals.isNotEmpty()) {
            Log.w(
                "JesterMoodsSecurity",
                "Runtime trust signals channel=${BuildConfig.SECURITY_BUILD_CHANNEL} " +
                    "blocking=${decision.blockingSignals.joinToString()} " +
                    "advisory=${decision.advisorySignals.joinToString()} native=${input.nativeSignals}"
            )
        }
        return decision
    }

    private fun signingDigests(context: Context): Set<String> {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = requireNotNull(info.signingInfo)
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners.toList()
            else signingInfo.signingCertificateHistory.toList()
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty().toList()
        }
        return signatures.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
