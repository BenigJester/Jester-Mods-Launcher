package com.moodtools.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherGatePresentationTest {
    @Test
    fun checkingToReadyKeepsEachAnimationFrameSelfContained() {
        val outgoing = launcherGatePresentation(
            LauncherStartupState.CheckingAccess,
            bootSettled = true
        )
        val incoming = launcherGatePresentation(
            LauncherStartupState.Ready(expiresAt = 1_800_000_000L),
            bootSettled = true
        )

        assertEquals(
            LauncherGatePresentation.Checking(root = false),
            outgoing
        )
        assertEquals(
            LauncherGatePresentation.Ready(expiresAt = 1_800_000_000L),
            incoming
        )
    }

    @Test
    fun everySettledStartupStateMapsToItsOwnGate() {
        val states = listOf(
            LauncherStartupState.CheckingRoot to LauncherGatePresentation.Checking(root = true),
            LauncherStartupState.CheckingAccess to LauncherGatePresentation.Checking(root = false),
            LauncherStartupState.RootDenied("root") to LauncherGatePresentation.RootDenied("root"),
            LauncherStartupState.SecurityBlocked("security") to
                LauncherGatePresentation.SecurityBlocked("security"),
            LauncherStartupState.ConnectionRequired("connect") to
                LauncherGatePresentation.ConnectionRequired("connect"),
            LauncherStartupState.Locked("unlock") to LauncherGatePresentation.Locked("unlock"),
            LauncherStartupState.Ready(123L) to LauncherGatePresentation.Ready(123L)
        )

        states.forEach { (state, expected) ->
            assertEquals(expected, launcherGatePresentation(state, bootSettled = true))
        }
    }

    @Test
    fun bootFrameNeverMasksImmediateSecurityOrRootFailures() {
        assertEquals(
            LauncherGatePresentation.Booting,
            launcherGatePresentation(LauncherStartupState.CheckingAccess, bootSettled = false)
        )
        assertTrue(
            launcherGatePresentation(
                LauncherStartupState.RootDenied("root"),
                bootSettled = false
            ) is LauncherGatePresentation.RootDenied
        )
        assertTrue(
            launcherGatePresentation(
                LauncherStartupState.SecurityBlocked("security"),
                bootSettled = false
            ) is LauncherGatePresentation.SecurityBlocked
        )
    }

    @Test
    fun validAccessStillWaitsForExplicitEnterLauncherAction() {
        val ready = LauncherStartupState.Ready(expiresAt = 1_800_000_000L)

        assertTrue(!shouldEnterLauncherLibrary(ready, enteredByUser = false))
        assertTrue(shouldEnterLauncherLibrary(ready, enteredByUser = true))
        assertTrue(
            !shouldEnterLauncherLibrary(
                LauncherStartupState.CheckingAccess,
                enteredByUser = true
            )
        )
    }
}
