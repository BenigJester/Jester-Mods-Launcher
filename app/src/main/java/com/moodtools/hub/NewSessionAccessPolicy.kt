package com.moodtools.hub

import com.moodtools.hub.networking.LauncherLease

internal enum class NewSessionAccessDecision {
    ALLOW,
    REQUIRE_UNLOCK,
    RETRY
}

/**
 * Applies access expiry only when the user requests a new protected action.
 * An already-running game is intentionally outside this policy.
 */
internal object NewSessionAccessPolicy {
    fun decide(lease: LauncherLease?, failure: Throwable? = null): NewSessionAccessDecision = when {
        failure != null -> NewSessionAccessDecision.RETRY
        lease == null -> NewSessionAccessDecision.REQUIRE_UNLOCK
        else -> NewSessionAccessDecision.ALLOW
    }
}
