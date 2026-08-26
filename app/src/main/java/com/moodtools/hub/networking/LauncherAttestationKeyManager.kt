package com.moodtools.hub.networking

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.ProviderException
import java.security.spec.ECGenParameterSpec

internal data class LauncherAttestationEvidence(
    val certificateChain: List<String>,
    val chainHash: String
)

/**
 * Creates a disposable, challenge-bound Android Keystore key for server-side attestation.
 *
 * This key is deliberately separate from the persistent module proof key. Adding or retrying
 * attestation must never rotate a key that already protects an active launcher access grant.
 */
internal class LauncherAttestationKeyManager(context: Context) {
    private val appContext = context.applicationContext

    fun createEvidence(challenge: ByteArray): LauncherAttestationEvidence {
        require(challenge.size == 32) { "The launcher attestation challenge is invalid" }
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)

        val preferStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (preferStrongBox) {
            try {
                generateKey(challenge, strongBox = true)
            } catch (_: ProviderException) {
                store.deleteEntry(KEY_ALIAS)
                generateKey(challenge, strongBox = false)
            } catch (_: GeneralSecurityException) {
                store.deleteEntry(KEY_ALIAS)
                generateKey(challenge, strongBox = false)
            }
        } else {
            generateKey(challenge, strongBox = false)
        }

        val chain = store.getCertificateChain(KEY_ALIAS)?.toList().orEmpty()
        require(chain.size in 2..MAX_CERTIFICATES) { "Hardware key attestation is unavailable" }
        val certificates = chain.map { certificate ->
            val encoded = certificate.encoded
            require(encoded.size in MIN_CERTIFICATE_BYTES..MAX_CERTIFICATE_BYTES) {
                "The launcher attestation certificate is invalid"
            }
            Base64.encodeToString(encoded, Base64.NO_WRAP)
        }
        require(certificates.sumOf(String::length) <= MAX_CHAIN_TEXT_BYTES) {
            "The launcher attestation chain is too large"
        }
        return LauncherAttestationEvidence(
            certificateChain = certificates,
            chainHash = base64Url(
                MessageDigest.getInstance("SHA-256")
                    .digest(certificates.joinToString("\n").toByteArray(Charsets.UTF_8))
            )
        )
    }

    private fun generateKey(challenge: ByteArray, strongBox: Boolean) {
        val parameters = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .setAttestationChallenge(challenge)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && strongBox) {
                    setIsStrongBoxBacked(true)
                }
            }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE).run {
            initialize(parameters)
            generateKeyPair()
        }
    }

    companion object {
        const val ATTESTATION_VERSION = 1
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jester_moods_attestation_v1"
        private const val MAX_CERTIFICATES = 8
        private const val MIN_CERTIFICATE_BYTES = 128
        private const val MAX_CERTIFICATE_BYTES = 12 * 1024
        private const val MAX_CHAIN_TEXT_BYTES = 64 * 1024

        private fun base64Url(bytes: ByteArray): String = Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }
}
