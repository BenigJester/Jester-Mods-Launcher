package com.moodtools.hub.networking

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LauncherProofChallengeTest {
    private val key = "k".repeat(43)
    private val nonce = "n".repeat(43)
    private fun body(expiry: Long = 1_800_000_120) = JSONObject()
        .put("ok", true).put("proofVersion", 1).put("keyId", key).put("nonce", nonce).put("expiresAt", expiry)

    @Test fun `HTTPS server time and elapsed duration ignore phone clock skew and jumps`() {
        val response = LauncherOnlineResponse(body(), 1_800_000_000_000, 1000)
        for (wall in listOf(0L, 1_900_000_000_000, 1_700_000_000_000)) {
            val now = response.nowSeconds(2000, wall)
            assertEquals(1_800_000_001, now)
            validateLauncherProofChallenge(response, key, nonce, now, response.hasServerTime)
        }
        assertEquals(1_800_000_120, response.nowSeconds(121000, 0))
        assertEquals("PROOF_CHALLENGE_EXPIRED", assertThrows(LauncherServiceException::class.java) {
            validateLauncherProofChallenge(response, key, nonce, response.nowSeconds(121000, 0), true)
        }.code)
        assertThrows(LauncherServiceException::class.java) { response.nowSeconds(999, 0) }
        assertTrue(response.keys().asSequence().none { it.contains("Time") || it.contains("Elapsed") })
    }

    @Test fun `missing Date preserves wall clock checks and provides an explicit clock hint`() {
        val response = LauncherOnlineResponse(body(), 0, 1000)
        assertFalse(response.hasServerTime)
        validateLauncherProofChallenge(response, key, nonce, response.nowSeconds(1000, 1_800_000_000_000), false)
        val expired = assertThrows(LauncherServiceException::class.java) {
            validateLauncherProofChallenge(response, key, nonce, response.nowSeconds(1000, 1_900_000_000_000), false)
        }
        assertEquals("PROOF_CHALLENGE_CLOCK", expired.code)
        assertTrue(expired.message!!.contains("phone_clock_or_expired"))
        assertEquals("PROOF_CHALLENGE_WINDOW", assertThrows(LauncherServiceException::class.java) {
            validateLauncherProofChallenge(response, key, nonce, 1_700_000_000, false)
        }.code)
    }

    @Test fun `validation identifies exact failure without including response secrets`() {
        val invalid = listOf(
            body().put("proofVersion", 2) to "PROOF_CHALLENGE_VERSION",
            body().put("keyId", "private-key") to "PROOF_CHALLENGE_KEY",
            body().put("nonce", "private-nonce") to "PROOF_CHALLENGE_NONCE",
            body().put("expiresAt", "private-token") to "PROOF_CHALLENGE_EXPIRY",
            body(1_800_000_421) to "PROOF_CHALLENGE_WINDOW"
        )
        invalid.forEach { (response, code) ->
            val error = assertThrows(LauncherServiceException::class.java) {
                validateLauncherProofChallenge(response, key, response.optString("nonce"), 1_800_000_000, true)
            }
            assertEquals(code, error.code)
            assertFalse(error.message!!.contains("private"))
        }
    }

    @Test fun `only one new expired challenge is requested and invalid challenges never retry`() {
        var requests = 0
        val result = freshLauncherProofChallenge(fetch = { ++requests }, validate = {
            if (it == 1) throw LauncherServiceException("PROOF_CHALLENGE_EXPIRED", "fixed")
        })
        assertEquals(2, result)
        assertEquals(2, requests)
        requests = 0
        assertThrows(LauncherServiceException::class.java) {
            freshLauncherProofChallenge(fetch = { ++requests }, validate = {
                throw LauncherServiceException("PROOF_CHALLENGE_EXPIRED", "fixed")
            })
        }
        assertEquals(2, requests)
        for (code in listOf("PROOF_CHALLENGE_KEY", "PROOF_CHALLENGE_VERSION", "PROOF_CHALLENGE_NONCE",
            "PROOF_CHALLENGE_WINDOW", "PROOF_CHALLENGE_CLOCK", "AUTHORIZATION_FAILED")) {
            requests = 0
            assertThrows(LauncherServiceException::class.java) {
                freshLauncherProofChallenge(fetch = { ++requests }, validate = {
                    throw LauncherServiceException(code, "fixed")
                })
            }
            assertEquals(code, 1, requests)
        }
    }

    @Test fun `download authorization expiration keeps using its response clock`() {
        var elapsed = 2000L
        val response = LauncherOnlineResponse(body(), 1_800_000_000_000, 1000)
        val authorization = LauncherModuleAuthorization(JSONObject(), "unused", 1_800_000_600, null, key,
            payloadProofProvider = { _, _ -> error("Signing is not part of an expiry check") },
            nowSeconds = { response.nowSeconds(elapsed, 1_900_000_000_000) })
        assertFalse(authorization.isExpired())
        elapsed = 601000
        assertTrue(authorization.isExpired())
    }
}
