package com.moodtools.hub.networking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class LauncherPrivateLeaseVerifierTest {
    @Test
    fun verifiesExactDeviceBoundPrivateScope() {
        val fixture = privateLeaseFixture()

        val lease = LauncherPrivateLeaseVerifier.verify(
            envelope = fixture.envelope,
            expectedScope = fixture.scope,
            expectedDeviceId = fixture.deviceId,
            expectedRecoveryId = fixture.recoveryId,
            expectedFlavor = "nonroot",
            expectedProofKeyId = fixture.proofKeyId,
            now = 11_000L,
            publicKeyDerBase64 = fixture.publicKey
        )

        assertEquals(fixture.scope, lease.scope)
        assertEquals(10_000L, lease.issuedAt)
        assertEquals(20_000L, lease.expiresAt)
        assertEquals(200_000L, lease.grantExpiresAt)
    }

    @Test
    fun acceptsLegacyLeaseAndUsesItsOfflineExpiryAsTheDisplayedGrantExpiry() {
        val fixture = privateLeaseFixture(includeGrantExpiry = false)

        val lease = LauncherPrivateLeaseVerifier.verify(
            envelope = fixture.envelope,
            expectedScope = fixture.scope,
            expectedDeviceId = fixture.deviceId,
            expectedRecoveryId = fixture.recoveryId,
            expectedFlavor = "nonroot",
            expectedProofKeyId = fixture.proofKeyId,
            now = 11_000L,
            publicKeyDerBase64 = fixture.publicKey
        )

        assertEquals(20_000L, lease.expiresAt)
        assertEquals(20_000L, lease.grantExpiresAt)
    }

    @Test
    fun rejectsAnotherScopeDeviceOrRecoveryIdentity() {
        val fixture = privateLeaseFixture()

        listOf(
            Triple("another-scope", fixture.deviceId, fixture.recoveryId),
            Triple(fixture.scope, "x".repeat(43), fixture.recoveryId),
            Triple(fixture.scope, fixture.deviceId, "y".repeat(43))
        ).forEach { (scope, deviceId, recoveryId) ->
            assertTrue(runCatching {
                LauncherPrivateLeaseVerifier.verify(
                    envelope = fixture.envelope,
                    expectedScope = scope,
                    expectedDeviceId = deviceId,
                    expectedRecoveryId = recoveryId,
                    expectedFlavor = "nonroot",
                    expectedProofKeyId = fixture.proofKeyId,
                    now = 11_000L,
                    publicKeyDerBase64 = fixture.publicKey
                )
            }.isFailure)
        }
    }

    @Test
    fun rejectsExpiredPrivateLease() {
        val fixture = privateLeaseFixture()

        assertTrue(runCatching {
            LauncherPrivateLeaseVerifier.verify(
                envelope = fixture.envelope,
                expectedScope = fixture.scope,
                expectedDeviceId = fixture.deviceId,
                expectedRecoveryId = fixture.recoveryId,
                expectedFlavor = "nonroot",
                expectedProofKeyId = fixture.proofKeyId,
                now = 20_000L,
                publicKeyDerBase64 = fixture.publicKey
            )
        }.isFailure)
    }

    private fun privateLeaseFixture(includeGrantExpiry: Boolean = true): PrivateLeaseFixture {
        val scope = "friends-zombie"
        val deviceId = "d".repeat(43)
        val recoveryId = "r".repeat(43)
        val proofKeyId = "p".repeat(43)
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val payload = JSONObject()
            .put("schema", 1)
            .put("audience", "moodtools-private-module-lease")
            .put("leaseVersion", 1)
            .put("accessVersion", 4)
            .put("proofVersion", 1)
            .put("grantId", "grant-private-test")
            .put("scope", scope)
            .put("deviceId", deviceId)
            .put("recoveryId", recoveryId)
            .put("flavor", "nonroot")
            .put("proofKeyId", proofKeyId)
            .put("issuedAt", 10_000L)
            .put("expiresAt", 20_000L)
        if (includeGrantExpiry) payload.put("grantExpiresAt", 200_000L)
        val payloadBytes = payload
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
        return PrivateLeaseFixture(
            envelope = envelope,
            publicKey = Base64.getEncoder().encodeToString(keys.public.encoded),
            scope = scope,
            deviceId = deviceId,
            recoveryId = recoveryId,
            proofKeyId = proofKeyId
        )
    }

    private data class PrivateLeaseFixture(
        val envelope: JSONObject,
        val publicKey: String,
        val scope: String,
        val deviceId: String,
        val recoveryId: String,
        val proofKeyId: String
    )
}
