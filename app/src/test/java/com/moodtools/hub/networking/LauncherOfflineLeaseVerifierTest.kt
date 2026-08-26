package com.moodtools.hub.networking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

class LauncherOfflineLeaseVerifierTest {
    @Test
    fun verifiesSignedClaimsAndRejectsDigitalKeyRebinding() {
        val digitalKey = "v1.${"a".repeat(120)}.${"b".repeat(43)}"
        val deviceId = "d".repeat(43)
        val proofKeyId = "p".repeat(43)
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val payloadBytes = JSONObject()
            .put("schema", 1)
            .put("audience", "moodtools-launcher-offline-lease")
            .put("leaseVersion", 1)
            .put("accessVersion", 4)
            .put("grantId", "grant-signed-test")
            .put("deviceId", deviceId)
            .put("flavor", "root")
            .put("proofVersion", 1)
            .put("proofKeyId", proofKeyId)
            .put("digitalKeySha256", Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(digitalKey.toByteArray())
            ))
            .put("issuedAt", 10_000L)
            .put("expiresAt", 10_000L + 30L * 24L * 60L * 60L)
            .toString()
            .toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private)
            update(payloadBytes)
            sign()
        }
        val envelope = JSONObject()
            .put("algorithm", "SHA256withRSA")
            .put("keyId", "launcher-lease-rsa-2026-01")
            .put("payload", Base64.getEncoder().encodeToString(payloadBytes))
            .put("signature", Base64.getEncoder().encodeToString(signature))
        val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)

        val claims = LauncherOfflineLeaseVerifier.verify(
            envelope = envelope,
            digitalKey = digitalKey,
            expectedDeviceId = deviceId,
            expectedFlavor = "root",
            expectedProofKeyId = proofKeyId,
            publicKeyDerBase64 = publicKey
        )
        assertEquals(10_000L, claims.issuedAt)
        assertEquals(10_000L + 30L * 24L * 60L * 60L, claims.expiresAt)
        assertTrue(runCatching {
            LauncherOfflineLeaseVerifier.verify(
                envelope = envelope,
                digitalKey = "$digitalKey-tampered",
                expectedDeviceId = deviceId,
                expectedFlavor = "root",
                expectedProofKeyId = proofKeyId,
                publicKeyDerBase64 = publicKey
            )
        }.isFailure)
    }

    @Test
    fun acceptsActiveLeaseAndExactExpiryIsExpired() {
        assertEquals(
            LauncherLeaseClockStatus.VALID,
            LauncherOfflineLeaseVerifier.clockStatus(
                issuedAt = 1_000L,
                expiresAt = 2_000L,
                now = 1_500L,
                lastSeen = 1_400L,
                elapsedRealtimeMillis = 50_000L,
                lastElapsedRealtimeMillis = 40_000L
            )
        )
        assertEquals(
            LauncherLeaseClockStatus.EXPIRED,
            LauncherOfflineLeaseVerifier.clockStatus(
                issuedAt = 1_000L,
                expiresAt = 2_000L,
                now = 2_000L,
                lastSeen = 1_900L,
                elapsedRealtimeMillis = 60_000L,
                lastElapsedRealtimeMillis = 50_000L
            )
        )
    }

    @Test
    fun rejectsWallClockRollbackBeyondSkew() {
        assertEquals(
            LauncherLeaseClockStatus.ROLLED_BACK,
            LauncherOfflineLeaseVerifier.clockStatus(
                issuedAt = 1_000L,
                expiresAt = 100_000L,
                now = 1_699L,
                lastSeen = 2_000L,
                elapsedRealtimeMillis = 80_000L,
                lastElapsedRealtimeMillis = 70_000L
            )
        )
    }

    @Test
    fun monotonicClockDetectsRollbackWithinSameBoot() {
        assertEquals(
            LauncherLeaseClockStatus.ROLLED_BACK,
            LauncherOfflineLeaseVerifier.clockStatus(
                issuedAt = 1_000L,
                expiresAt = 100_000L,
                now = 1_200L,
                lastSeen = 1_000L,
                elapsedRealtimeMillis = 700_000L,
                lastElapsedRealtimeMillis = 100_000L
            )
        )
    }

    @Test
    fun rebootFallsBackToPersistedWallClockHighWaterMark() {
        assertEquals(
            LauncherLeaseClockStatus.VALID,
            LauncherOfflineLeaseVerifier.clockStatus(
                issuedAt = 1_000L,
                expiresAt = 100_000L,
                now = 2_100L,
                lastSeen = 2_000L,
                elapsedRealtimeMillis = 5_000L,
                lastElapsedRealtimeMillis = 700_000L
            )
        )
    }
}
