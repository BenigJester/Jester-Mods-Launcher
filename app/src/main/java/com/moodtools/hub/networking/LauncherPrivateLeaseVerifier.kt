package com.moodtools.hub.networking

import com.moodtools.hub.BuildConfig
import org.json.JSONObject

internal data class LauncherPrivateLease(
    val scope: String,
    val issuedAt: Long,
    val expiresAt: Long
)

internal object LauncherPrivateLeaseVerifier {
    fun verify(
        envelope: JSONObject,
        expectedScope: String,
        expectedDeviceId: String,
        expectedRecoveryId: String,
        expectedFlavor: String,
        expectedProofKeyId: String,
        now: Long = System.currentTimeMillis() / 1_000L,
        publicKeyDerBase64: String = BuildConfig.ACCESS_LEASE_PUBLIC_KEY_DER_BASE64
    ): LauncherPrivateLease {
        require(envelope.optString("keyId") == KEY_ID) {
            "The private access lease uses an unknown signing key"
        }
        val payload = SignedEnvelopeVerifier.payload(
            envelope,
            publicKeyDerBase64
        )
        val issuedAt = payload.getLong("issuedAt")
        val expiresAt = payload.getLong("expiresAt")
        require(payload.optInt("schema") == 1 && payload.optString("audience") == AUDIENCE)
        require(payload.optInt("leaseVersion") == LEASE_VERSION)
        require(payload.optInt("accessVersion") == ACCESS_VERSION)
        require(payload.optInt("proofVersion") == LauncherProofKeyManager.PROOF_VERSION)
        require(payload.optString("scope") == expectedScope)
        require(payload.optString("deviceId") == expectedDeviceId)
        require(payload.optString("recoveryId") == expectedRecoveryId)
        require(payload.optString("flavor") == expectedFlavor)
        require(payload.optString("proofKeyId") == expectedProofKeyId)
        require(payload.optString("grantId").length in 16..80)
        require(issuedAt > 0L && expiresAt > issuedAt)
        require(expiresAt - issuedAt <= MAX_OFFLINE_TTL_SECONDS)
        require(now + CLOCK_SKEW_SECONDS >= issuedAt && now < expiresAt)
        return LauncherPrivateLease(expectedScope, issuedAt, expiresAt)
    }

    private const val KEY_ID = "launcher-lease-rsa-2026-01"
    private const val AUDIENCE = "moodtools-private-module-lease"
    private const val LEASE_VERSION = 1
    private const val ACCESS_VERSION = 4
    private const val MAX_OFFLINE_TTL_SECONDS = 7L * 24L * 60L * 60L
    private const val CLOCK_SKEW_SECONDS = 5L * 60L
}
