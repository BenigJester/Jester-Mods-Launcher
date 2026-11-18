package com.moodtools.hub

import kotlinx.coroutines.CompletableDeferred

internal data class GameReplacementRequest(
    val packageName: String,
    val title: String,
    val version: String,
    val uninstallRequested: Boolean = false,
    val completion: CompletableDeferred<Boolean> = CompletableDeferred()
) {
    fun completeAfterUninstall(installedVersionCode: Long?) {
        // A success result from Android alone is insufficient: the old package must be absent.
        completion.complete(installedVersionCode == null)
    }
}

internal enum class GameInstallerReconciliationDecision {
    SUCCEEDED,
    KEEP_WAITING,
    FAILED
}

/** Keeps lifecycle transitions from outracing Package Manager's final install state. */
internal object GameInstallerReconciliationPolicy {
    fun matchesTargetBuild(installedVersionCode: Long?, targetVersionCode: Long): Boolean =
        targetVersionCode > 0L && installedVersionCode == targetVersionCode

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
