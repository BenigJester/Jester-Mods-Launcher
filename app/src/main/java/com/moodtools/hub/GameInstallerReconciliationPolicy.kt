package com.moodtools.hub

internal enum class GameInstallerReconciliationDecision {
    SUCCEEDED,
    KEEP_WAITING,
    FAILED
}

/** Keeps lifecycle transitions from outracing Package Manager's final install state. */
internal object GameInstallerReconciliationPolicy {
    fun decide(
        installed: Boolean,
        launcherLeft: Boolean,
        deadlineReached: Boolean
    ): GameInstallerReconciliationDecision = when {
        installed -> GameInstallerReconciliationDecision.SUCCEEDED
        launcherLeft || !deadlineReached -> GameInstallerReconciliationDecision.KEEP_WAITING
        else -> GameInstallerReconciliationDecision.FAILED
    }
}
