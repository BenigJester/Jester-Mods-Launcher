package com.moodtools.hub.networking

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.security.Signature
import java.security.spec.ECGenParameterSpec

internal data class LauncherProofIdentity(
    val keyId: String,
    val publicKey: String
)

internal data class LauncherRequestProof(
    val version: Int,
    val keyId: String,
    val nonce: String,
    val signature: String
)

internal class LauncherProofKeyManager(context: Context) {
    private val appContext = context.applicationContext

    fun identity(): LauncherProofIdentity {
        val publicKey = keyPair().public.encoded
        require(publicKey.size in 80..192) { "The launcher proof public key is invalid" }
        return LauncherProofIdentity(
            keyId = base64Url(MessageDigest.getInstance("SHA-256").digest(publicKey)),
            publicKey = base64Url(publicKey)
        )
    }

    fun sign(nonce: String, canonical: String): LauncherRequestProof {
        require(nonce.matches(ID_PATTERN)) { "The launcher proof challenge is invalid" }
        val identity = identity()
        val derSignature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair().private)
            update(canonical.toByteArray(Charsets.UTF_8))
            sign()
        }
        return LauncherRequestProof(
            version = PROOF_VERSION,
            keyId = identity.keyId,
            nonce = nonce,
            signature = base64Url(ecdsaDerToP1363(derSignature))
        )
    }

    private fun keyPair(): KeyPair {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = store.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (existing != null && existing.privateKey.algorithm.equals("EC", true)) {
            return KeyPair(existing.certificate.publicKey, existing.privateKey)
        }
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)

        val preferStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (preferStrongBox) {
            try {
                return generateKeyPair(strongBox = true)
            } catch (_: ProviderException) {
                store.deleteEntry(KEY_ALIAS)
            } catch (_: GeneralSecurityException) {
                store.deleteEntry(KEY_ALIAS)
            }
        }
        return generateKeyPair(strongBox = false)
    }

    private fun generateKeyPair(strongBox: Boolean): KeyPair {
        val parameters = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && strongBox) {
                    setIsStrongBoxBacked(true)
                }
            }
            .build()
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE).run {
            initialize(parameters)
            generateKeyPair()
        }
    }

    companion object {
        const val PROOF_VERSION = 1
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jester_moods_module_proof_v1"
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]{43}")

        fun registrationCanonical(
            nonce: String,
            installationId: String,
            deviceId: String,
            flavor: String,
            accessVersion: Int,
            keyId: String,
            publicKey: String
        ): String = listOf(
            "moodtools-proof-register-v1",
            "nonce=$nonce",
            "installationId=$installationId",
            "deviceId=$deviceId",
            "flavor=$flavor",
            "accessVersion=$accessVersion",
            "keyId=$keyId",
            "publicKey=$publicKey"
        ).joinToString("\n")

        fun moduleAuthorizationCanonical(
            nonce: String,
            installationId: String,
            deviceId: String,
            flavor: String,
            accessVersion: Int,
            packageName: String,
            abi: String,
            bootstrap: Int,
            keyId: String
        ): String = listOf(
            "moodtools-module-authorize-v1",
            "nonce=$nonce",
            "installationId=$installationId",
            "deviceId=$deviceId",
            "flavor=$flavor",
            "accessVersion=$accessVersion",
            "packageName=$packageName",
            "abi=$abi",
            "bootstrap=$bootstrap",
            "keyId=$keyId"
        ).joinToString("\n")

        fun privateCatalogAuthorizationCanonical(
            nonce: String,
            installationId: String,
            deviceId: String,
            flavor: String,
            accessVersion: Int,
            keyId: String
        ): String = listOf(
            "moodtools-private-catalog-authorize-v1",
            "nonce=$nonce",
            "installationId=$installationId",
            "deviceId=$deviceId",
            "flavor=$flavor",
            "accessVersion=$accessVersion",
            "keyId=$keyId"
        ).joinToString("\n")

        fun recoveryBindingCanonical(
            nonce: String,
            installationId: String,
            deviceId: String,
            recoveryId: String,
            flavor: String,
            accessVersion: Int,
            keyId: String
        ): String = listOf(
            "moodtools-recovery-bind-v1",
            "nonce=$nonce",
            "installationId=$installationId",
            "deviceId=$deviceId",
            "recoveryId=$recoveryId",
            "flavor=$flavor",
            "accessVersion=$accessVersion",
            "keyId=$keyId"
        ).joinToString("\n")

        fun modulePayloadCanonical(
            nonce: String,
            method: String,
            path: String,
            keyId: String,
            capabilityHash: String
        ): String = listOf(
            "moodtools-module-payload-v1",
            "nonce=$nonce",
            "method=$method",
            "path=$path",
            "keyId=$keyId",
            "capabilityHash=$capabilityHash"
        ).joinToString("\n")

        fun attestationNonceCanonical(
            nonce: String,
            installationId: String,
            deviceId: String,
            flavor: String,
            accessVersion: Int,
            keyId: String
        ): String = listOf(
            "moodtools-proof-attestation-challenge-v1",
            "nonce=$nonce",
            "installationId=$installationId",
            "deviceId=$deviceId",
            "flavor=$flavor",
            "accessVersion=$accessVersion",
            "keyId=$keyId"
        ).joinToString("\n")

        fun attestationChallenge(
            nonce: String,
            installationId: String,
            deviceId: String,
            flavor: String,
            accessVersion: Int,
            keyId: String
        ): ByteArray = MessageDigest.getInstance("SHA-256").digest(
            attestationNonceCanonical(
                nonce = nonce,
                installationId = installationId,
                deviceId = deviceId,
                flavor = flavor,
                accessVersion = accessVersion,
                keyId = keyId
            ).toByteArray(Charsets.UTF_8)
        )

        fun attestationEvidenceCanonical(
            nonce: String,
            installationId: String,
            deviceId: String,
            flavor: String,
            accessVersion: Int,
            keyId: String,
            chainHash: String
        ): String = listOf(
            "moodtools-proof-attestation-evidence-v1",
            "nonce=$nonce",
            "installationId=$installationId",
            "deviceId=$deviceId",
            "flavor=$flavor",
            "accessVersion=$accessVersion",
            "keyId=$keyId",
            "chainHash=$chainHash"
        ).joinToString("\n")

        fun ecdsaDerToP1363(der: ByteArray): ByteArray {
            var offset = 0
            require(der.getOrNull(offset++) == 0x30.toByte()) { "Invalid ECDSA signature sequence" }
            val sequenceLength = readDerLength(der, offset).also { offset = it.second }.first
            require(sequenceLength == der.size - offset) { "Invalid ECDSA signature length" }
            val r = readDerInteger(der, offset).also { offset = it.second }.first
            val s = readDerInteger(der, offset).also { offset = it.second }.first
            require(offset == der.size) { "Invalid trailing ECDSA signature data" }
            return ByteArray(64).also {
                r.copyInto(it, 32 - r.size)
                s.copyInto(it, 64 - s.size)
            }
        }

        private fun readDerInteger(der: ByteArray, start: Int): Pair<ByteArray, Int> {
            var offset = start
            require(der.getOrNull(offset++) == 0x02.toByte()) { "Invalid ECDSA signature integer" }
            val lengthResult = readDerLength(der, offset)
            val length = lengthResult.first
            offset = lengthResult.second
            require(length in 1..33 && offset + length <= der.size) { "Invalid ECDSA integer length" }
            var value = der.copyOfRange(offset, offset + length)
            require((value[0].toInt() and 0x80) == 0) { "Negative ECDSA signature integer" }
            while (value.size > 1 && value[0] == 0.toByte()) value = value.copyOfRange(1, value.size)
            require(value.size <= 32) { "Oversized ECDSA signature integer" }
            return value to (offset + length)
        }

        private fun readDerLength(der: ByteArray, start: Int): Pair<Int, Int> {
            var offset = start
            val first = der.getOrNull(offset++)?.toInt()?.and(0xff)
                ?: error("Missing DER length")
            if ((first and 0x80) == 0) return first to offset
            val count = first and 0x7f
            require(count in 1..2 && offset + count <= der.size) { "Invalid DER length" }
            var length = 0
            repeat(count) { length = (length shl 8) or (der[offset++].toInt() and 0xff) }
            require(length >= 128) { "Non-canonical DER length" }
            return length to offset
        }

        private fun base64Url(bytes: ByteArray): String = Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }
}
