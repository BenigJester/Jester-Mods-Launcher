package com.moodtools.hub.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeSecurityPolicyTest {
    @Test
    fun trustedHardenedRuntimeIsAllowed() {
        assertTrue(decision().allowed)
    }

    @Test
    fun signerMismatchIsRejected() {
        assertFalse(decision(signerMatched = false).allowed)
    }

    @Test
    fun debuggableHardenedBuildIsRejected() {
        assertFalse(decision(appDebuggable = true).allowed)
    }

    @Test
    fun nativeInstrumentationIsRejected() {
        assertFalse(decision(nativeSignals = RuntimeSecurityPolicy.NATIVE_INSTRUMENTATION_MAP).allowed)
    }

    @Test
    fun hookFrameworkAloneIsAdvisory() {
        val result = decision(nativeSignals = RuntimeSecurityPolicy.NATIVE_HOOK_FRAMEWORK_MAP)
        assertTrue(result.allowed)
        assertTrue("hook-framework-map" in result.advisorySignals)
    }

    @Test
    fun debugBuildRecordsButDoesNotEnforceSignals() {
        val result = decision(
            enforced = false,
            signerMatched = false,
            debuggerConnected = true,
            nativeAvailable = false
        )
        assertTrue(result.allowed)
        assertTrue(result.blockingSignals.isNotEmpty())
    }

    private fun decision(
        enforced: Boolean = true,
        signerConfigured: Boolean = true,
        signerMatched: Boolean = true,
        appDebuggable: Boolean = false,
        debuggerConnected: Boolean = false,
        nativeAvailable: Boolean = true,
        nativeSignals: Int = 0
    ) = RuntimeSecurityPolicy.evaluate(
        RuntimeSecurityInputs(
            enforced,
            signerConfigured,
            signerMatched,
            appDebuggable,
            debuggerConnected,
            nativeAvailable,
            nativeSignals
        )
    )
}
