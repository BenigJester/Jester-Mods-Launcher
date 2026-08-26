package com.moodtools.hub.networking

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class LauncherProofKeyManagerTest {
    @Test
    fun convertsDerEcdsaSignatureToFixedWidthP1363() {
        val der = byteArrayOf(
            0x30, 0x08,
            0x02, 0x02, 0x00, 0x80.toByte(),
            0x02, 0x02, 0x01, 0x02
        )
        val expected = ByteArray(64).apply {
            this[31] = 0x80.toByte()
            this[62] = 0x01
            this[63] = 0x02
        }

        assertArrayEquals(expected, LauncherProofKeyManager.ecdsaDerToP1363(der))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeDerInteger() {
        LauncherProofKeyManager.ecdsaDerToP1363(byteArrayOf(
            0x30, 0x06,
            0x02, 0x01, 0x80.toByte(),
            0x02, 0x01, 0x01
        ))
    }

    @Test
    fun bindsAttestationChallengeAndEvidenceToProofIdentity() {
        val nonceCanonical = LauncherProofKeyManager.attestationNonceCanonical(
            nonce = "n".repeat(43),
            installationId = "i".repeat(43),
            deviceId = "d".repeat(43),
            flavor = "root",
            accessVersion = 3,
            keyId = "k".repeat(43)
        )
        assertEquals(
            listOf(
                "moodtools-proof-attestation-challenge-v1",
                "nonce=${"n".repeat(43)}",
                "installationId=${"i".repeat(43)}",
                "deviceId=${"d".repeat(43)}",
                "flavor=root",
                "accessVersion=3",
                "keyId=${"k".repeat(43)}"
            ).joinToString("\n"),
            nonceCanonical
        )
        assertArrayEquals(
            MessageDigest.getInstance("SHA-256").digest(nonceCanonical.toByteArray()),
            LauncherProofKeyManager.attestationChallenge(
                nonce = "n".repeat(43),
                installationId = "i".repeat(43),
                deviceId = "d".repeat(43),
                flavor = "root",
                accessVersion = 3,
                keyId = "k".repeat(43)
            )
        )
        assertEquals(
            listOf(
                "moodtools-proof-attestation-evidence-v1",
                "nonce=${"n".repeat(43)}",
                "installationId=${"i".repeat(43)}",
                "deviceId=${"d".repeat(43)}",
                "flavor=root",
                "accessVersion=3",
                "keyId=${"k".repeat(43)}",
                "chainHash=${"h".repeat(43)}"
            ).joinToString("\n"),
            LauncherProofKeyManager.attestationEvidenceCanonical(
                nonce = "n".repeat(43),
                installationId = "i".repeat(43),
                deviceId = "d".repeat(43),
                flavor = "root",
                accessVersion = 3,
                keyId = "k".repeat(43),
                chainHash = "h".repeat(43)
            )
        )
    }

    @Test
    fun bindsRecoveryIdentityToProofKeyAndInstallation() {
        assertEquals(
            listOf(
                "moodtools-recovery-bind-v1",
                "nonce=${"n".repeat(43)}",
                "installationId=${"i".repeat(43)}",
                "deviceId=${"d".repeat(43)}",
                "recoveryId=${"r".repeat(43)}",
                "flavor=root",
                "accessVersion=4",
                "keyId=${"k".repeat(43)}"
            ).joinToString("\n"),
            LauncherProofKeyManager.recoveryBindingCanonical(
                nonce = "n".repeat(43),
                installationId = "i".repeat(43),
                deviceId = "d".repeat(43),
                recoveryId = "r".repeat(43),
                flavor = "root",
                accessVersion = 4,
                keyId = "k".repeat(43)
            )
        )
    }
}
