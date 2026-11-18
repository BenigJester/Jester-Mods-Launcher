package com.moodtools.hub

import org.junit.Assert.assertEquals
import org.junit.Test

class GameInstallerReconciliationPolicyTest {
    @Test
    fun aNewerInstalledBuildCannotSatisfyDowngradeVerification() {
        assertEquals(true, GameInstallerReconciliationPolicy.matchesTargetBuild(100, 100))
        for (installed in listOf(null, 99L, 101L)) {
            assertEquals(false, GameInstallerReconciliationPolicy.matchesTargetBuild(installed, 100))
        }
        assertEquals(false, GameInstallerReconciliationPolicy.matchesTargetBuild(0, 0))
    }

    @Test
    fun replacementDoesNotProceedWhileAnyGameBuildIsStillInstalled() = kotlinx.coroutines.runBlocking {
        for (installed in listOf(101L, 100L, 99L)) {
            val request = GameReplacementRequest("com.example.game", "Example", "1.0")
            assertEquals(false, request.completion.isCompleted)
            request.completeAfterUninstall(installed)
            assertEquals(false, request.completion.await())
        }
    }

    @Test
    fun replacementResumesOnlyAfterUninstallAndCancellationCannotBeOverridden() = kotlinx.coroutines.runBlocking {
        val request = GameReplacementRequest("com.example.game", "Example", "1.0")
        val confirmed = request.copy(uninstallRequested = true)
        confirmed.completeAfterUninstall(null)
        assertEquals(true, request.completion.await())
        val cancelled = GameReplacementRequest("com.example.game", "Example", "1.0")
        cancelled.completion.complete(false)
        cancelled.completeAfterUninstall(null)
        assertEquals(false, cancelled.completion.await())
    }

    @Test
    fun installedPackageAlwaysWinsOverAStaleLifecycleReturn() {
        assertEquals(
            GameInstallerReconciliationDecision.SUCCEEDED,
            GameInstallerReconciliationPolicy.decide(
                installed = true,
                launcherLeft = false,
                deadlineReached = true
            )
        )
    }

    @Test
    fun returningFromAndroidDoesNotImmediatelyBecomeFailure() {
        assertEquals(
            GameInstallerReconciliationDecision.KEEP_WAITING,
            GameInstallerReconciliationPolicy.decide(
                installed = false,
                launcherLeft = false,
                deadlineReached = false
            )
        )
    }

    @Test
    fun backgroundInstallerIsNeverFailedByTheLauncherTimeout() {
        assertEquals(
            GameInstallerReconciliationDecision.KEEP_WAITING,
            GameInstallerReconciliationPolicy.decide(
                installed = false,
                launcherLeft = true,
                deadlineReached = true
            )
        )
    }

    @Test
    fun foregroundWaitFailsOnlyAfterItsDeadline() {
        assertEquals(
            GameInstallerReconciliationDecision.FAILED,
            GameInstallerReconciliationPolicy.decide(
                installed = false,
                launcherLeft = false,
                deadlineReached = true
            )
        )
    }
}
