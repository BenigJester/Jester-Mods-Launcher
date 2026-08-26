package com.moodtools.hub.networking

import com.moodtools.hub.BuildConfig
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal object SignedEnvelopeVerifier {
    fun payload(
        envelope: JSONObject,
        publicKeyDerBase64: String = BuildConfig.UPDATE_PUBLIC_KEY_DER_BASE64
    ): JSONObject {
        require(envelope.optString("algorithm") == "SHA256withRSA") {
            "Unsupported signature algorithm"
        }
        val bytes = decodeCanonicalBase64(envelope.getString("payload"))
        require(bytes.size in 2..MAX_PAYLOAD_BYTES) { "Signed payload size is invalid" }
        val keyBytes = decodeCanonicalBase64(publicKeyDerBase64)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(key)
        verifier.update(bytes)
        require(verifier.verify(decodeCanonicalBase64(envelope.getString("signature")))) {
            "Signed response verification failed"
        }
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    private fun decodeCanonicalBase64(value: String): ByteArray {
        require(value.isNotEmpty() && value.length <= MAX_BASE64_TEXT_BYTES && value.matches(BASE64_PATTERN)) {
            "Signed response encoding is invalid"
        }
        val decoded = Base64.getDecoder().decode(value)
        require(Base64.getEncoder().encodeToString(decoded) == value) {
            "Signed response encoding is not canonical"
        }
        return decoded
    }

    private const val MAX_PAYLOAD_BYTES = 256 * 1024
    private const val MAX_BASE64_TEXT_BYTES = 512 * 1024
    private val BASE64_PATTERN = Regex("[A-Za-z0-9+/]+={0,2}")
}
