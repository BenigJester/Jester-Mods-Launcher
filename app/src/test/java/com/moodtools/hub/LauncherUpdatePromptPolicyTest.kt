package com.moodtools.hub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherUpdatePromptPolicyTest {
    @Test
    fun cachedReleaseOpensInAFreshLauncherSession() {
        assertTrue(
            LauncherUpdatePromptPolicy.shouldOpen(
                releaseBuild = 125L,
                dismissedBuild = null,
                currentBuild = 0L,
                currentlyOpen = false
            )
        )
    }

    @Test
    fun backgroundRefreshDoesNotReopenAReleaseDismissedThisSession() {
        assertFalse(
            LauncherUpdatePromptPolicy.shouldOpen(
                releaseBuild = 125L,
                dismissedBuild = 125L,
                currentBuild = 125L,
                currentlyOpen = false
            )
        )
    }

    @Test
    fun newerReleaseCanOpenAfterAnOlderReleaseWasDismissed() {
        assertTrue(
            LauncherUpdatePromptPolicy.shouldOpen(
                releaseBuild = 126L,
                dismissedBuild = 125L,
                currentBuild = 125L,
                currentlyOpen = false
            )
        )
    }

    @Test
    fun refreshKeepsAnAlreadyVisiblePromptOpen() {
        assertTrue(
            LauncherUpdatePromptPolicy.shouldOpen(
                releaseBuild = 125L,
                dismissedBuild = 125L,
                currentBuild = 125L,
                currentlyOpen = true
            )
        )
    }

    @Test
    fun cachedAddOnUpdatesOpenInAFreshLauncherSession() {
        assertTrue(
            InstalledModuleUpdatePromptPolicy.shouldOpen(
                updateKeys = setOf("com.example.game:7"),
                dismissedKeys = emptySet(),
                currentlyOpen = false
            )
        )
    }

    @Test
    fun sameAddOnBuildStaysDismissedUntilNextSession() {
        assertFalse(
            InstalledModuleUpdatePromptPolicy.shouldOpen(
                updateKeys = setOf("com.example.game:7"),
                dismissedKeys = setOf("com.example.game:7"),
                currentlyOpen = false
            )
        )
    }

    @Test
    fun newlyDetectedAddOnBuildCanOpenInTheCurrentSession() {
        assertTrue(
            InstalledModuleUpdatePromptPolicy.shouldOpen(
                updateKeys = setOf("com.example.game:8"),
                dismissedKeys = setOf("com.example.game:7"),
                currentlyOpen = false
            )
        )
    }
}
