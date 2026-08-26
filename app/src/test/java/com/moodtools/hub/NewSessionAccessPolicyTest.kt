package com.moodtools.hub

import com.moodtools.hub.networking.LauncherLease
import org.junit.Assert.assertEquals
import org.junit.Test

class NewSessionAccessPolicyTest {
    @Test
    fun activeLeaseAllowsANewProtectedAction() {
        assertEquals(
            NewSessionAccessDecision.ALLOW,
            NewSessionAccessPolicy.decide(LauncherLease(issuedAt = 1_000L, expiresAt = 2_000L))
        )
    }

    @Test
    fun expiredOrMissingLeaseRequiresUnlockAtTheNextActionBoundary() {
        assertEquals(
            NewSessionAccessDecision.REQUIRE_UNLOCK,
            NewSessionAccessPolicy.decide(lease = null)
        )
    }

    @Test
    fun transientCheckFailureDoesNotRevokeTheSession() {
        assertEquals(
            NewSessionAccessDecision.RETRY,
            NewSessionAccessPolicy.decide(
                lease = null,
                failure = IllegalStateException("network unavailable")
            )
        )
    }
}
