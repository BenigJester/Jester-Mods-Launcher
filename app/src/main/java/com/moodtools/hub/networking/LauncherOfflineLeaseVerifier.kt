package com.moodtools.hub.networking

import com.moodtools.hub.BuildConfig
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal data class LauncherOfflineLeaseClaims(
    val issuedAt: Long,
    val expiresAt: Long
)

internal enum class LauncherLeaseClockStatus {
    VALID,
    EXPIRED,
    ROLLED_BACK
}

internal object LauncherOfflineLeaseVerifier {
    fun verify(
        envelope: JSONObject,
        digitalKey: String,
        expectedDeviceId: String,
        expectedFlavor: String,
        expectedProofKeyId: String,
        publicKeyDerBase64: String = BuildConfig.ACCESS_LEASE_PUBLIC_KEY_DER_BASE64
    ): LauncherOfflineLeaseClaims {
        require(envelope.optString("algorithm") == ALGORITHM)
        require(envelope.optString("keyId") == KEY_ID)
        val payloadText = envelope.getString("payload")
        val signatureText = envelope.getString("signature")
        require(payloadText.length in 100..4096 && signatureText.length in 400..1024)
        val payloadBytes = decodeCanonicalBase64(payloadText)
        val signatureBytes = decodeCanonicalBase64(signatureText)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyDerBase64))
        )
        require(Signature.getInstance(ALGORITHM).run {
            initVerify(publicKey)
            update(payloadBytes)
            verify(signatureBytes)
        }) { "The offline launcher lease signature is invalid" }

        val payload = JSONObject(String(payloadBytes, Charsets.UTF_8))
        val issuedAt = payload.getLong("issuedAt")
        val expiresAt = payload.getLong("expiresAt")
        require(payload.optInt("schema") == 1 && payload.optString("audience") == AUDIENCE)
        require(payload.optInt("leaseVersion") == LEASE_VERSION)
        require(payload.optInt("accessVersion") == ACCESS_VERSION)
        require(payload.optInt("proofVersion") == LauncherProofKeyManager.PROOF_VERSION)
        require(payload.optString("grantId").length in 16..80)
        require(payload.optString("deviceId") == expectedDeviceId)
        require(payload.optString("flavor") == expectedFlavor)
        require(payload.optString("proofKeyId") == expectedProofKeyId)
        require(payload.optString("digitalKeySha256") == sha256(digitalKey))
        require(issuedAt > 0L && expiresAt > issuedAt &&
            expiresAt - issuedAt <= MAX_MANAGED_ACCESS_TTL_SECONDS)
        return LauncherOfflineLeaseClaims(issuedAt, expiresAt)
    }

    fun clockStatus(
        issuedAt: Long,
        expiresAt: Long,
        now: Long,
        lastSeen: Long,
        elapsedRealtimeMillis: Long,
        lastElapsedRealtimeMillis: Long
    ): LauncherLeaseClockStatus {
        if (now >= expiresAt) return LauncherLeaseClockStatus.EXPIRED
        if (now + CLOCK_SKEW_SECONDS < issuedAt) return LauncherLeaseClockStatus.ROLLED_BACK
        if (lastSeen > 0L && now + CLOCK_SKEW_SECONDS < lastSeen) {
            return LauncherLeaseClockStatus.ROLLED_BACK
        }
        if (lastSeen > 0L && lastElapsedRealtimeMillis > 0L &&
            elapsedRealtimeMillis >= lastElapsedRealtimeMillis) {
            val expectedWallTime = lastSeen +
                (elapsedRealtimeMillis - lastElapsedRealtimeMillis) / 1000L
            if (now + CLOCK_SKEW_SECONDS < expectedWallTime) {
                return LauncherLeaseClockStatus.ROLLED_BACK
            }
        }
        return LauncherLeaseClockStatus.VALID
    }

    private fun decodeCanonicalBase64(value: String): ByteArray {
        require(value.matches(BASE64_PATTERN))
        val decoded = Base64.getDecoder().decode(value)
        require(Base64.getEncoder().encodeToString(decoded) == value)
        return decoded
    }

    private fun sha256(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    )

    private const val ALGORITHM = "SHA256withRSA"
    private const val KEY_ID = "launcher-lease-rsa-2026-01"
    private const val AUDIENCE = "moodtools-launcher-offline-lease"
    private const val LEASE_VERSION = 1
    private const val ACCESS_VERSION = 4
    private const val MAX_MANAGED_ACCESS_TTL_SECONDS = 10L * 365L * 24L * 60L * 60L
    private const val CLOCK_SKEW_SECONDS = 5L * 60L
    private val BASE64_PATTERN = Regex("[A-Za-z0-9+/]+={0,2}")
}
