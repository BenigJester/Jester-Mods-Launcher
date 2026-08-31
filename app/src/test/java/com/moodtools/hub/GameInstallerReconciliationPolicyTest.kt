package com.moodtools.hub

import org.junit.Assert.assertEquals
import org.junit.Test

class GameInstallerReconciliationPolicyTest {
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
