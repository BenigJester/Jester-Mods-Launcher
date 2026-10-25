package com.moodtools.hub.networking

import org.json.JSONObject

/** Per-response HTTPS Date anchor. Never persisted or used to extend offline access. */
internal class LauncherOnlineResponse(
    body: JSONObject,
    private val serverTimeMillis: Long,
    private val startedElapsedMillis: Long
) : JSONObject(body, body.keys().asSequence().toList().toTypedArray()) {
    fun nowSeconds(elapsedMillis: Long, wallTimeMillis: Long): Long {
        if (serverTimeMillis <= 0L) return wallTimeMillis / 1000L
        val elapsed = elapsedMillis - startedElapsedMillis
        if (elapsed < 0L || serverTimeMillis > Long.MAX_VALUE - elapsed - 999L) {
            throw LauncherServiceException("PROOF_RESPONSE_TIME_INVALID", "Download check failed: check=response_time")
        }
        // Include the whole request duration conservatively; never extend a challenge by network delay.
        return (serverTimeMillis + elapsed + 999L) / 1000L
    }

    val hasServerTime: Boolean get() = serverTimeMillis > 0L
}

internal fun validateLauncherProofChallenge(response: JSONObject, keyId: String, nonce: String,
    nowSeconds: Long, hasServerTime: Boolean) {
    fun reject(code: String, check: String): Nothing =
        throw LauncherServiceException(code, "Download check failed: check=$check")
    if (response.optInt("proofVersion") != LauncherProofKeyManager.PROOF_VERSION)
        reject("PROOF_CHALLENGE_VERSION", "proof_version")
    if (response.optString("keyId") != keyId) reject("PROOF_CHALLENGE_KEY", "proof_key")
    if (!nonce.matches(Regex("[A-Za-z0-9_-]{43}"))) reject("PROOF_CHALLENGE_NONCE", "proof_nonce")
    val expiresAt = response.optLong("expiresAt", -1L)
    if (expiresAt <= 0L) reject("PROOF_CHALLENGE_EXPIRY", "proof_expiry")
    if (expiresAt <= nowSeconds) {
        if (!hasServerTime) reject("PROOF_CHALLENGE_CLOCK", "phone_clock_or_expired")
        reject("PROOF_CHALLENGE_EXPIRED", "proof_expired")
    }
    if (expiresAt - nowSeconds > 120L + 300L)
        reject("PROOF_CHALLENGE_WINDOW", if (hasServerTime) "proof_window" else "phone_clock_or_window")
}

/** Only refetch an expired, unsubmitted challenge. Never retry authorization/signing or malformed input. */
internal fun <T> freshLauncherProofChallenge(fetch: () -> T, validate: (T) -> Unit): T {
    repeat(2) { attempt ->
        val response = fetch()
        try {
            validate(response)
            return response
        } catch (error: LauncherServiceException) {
            if (attempt != 0 || error.code != "PROOF_CHALLENGE_EXPIRED") throw error
        }
    }
    error("Unreachable challenge retry")
}
