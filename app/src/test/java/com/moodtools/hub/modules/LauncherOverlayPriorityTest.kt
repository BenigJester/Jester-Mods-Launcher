package com.moodtools.hub.modules

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherOverlayPriorityTest {
    @Test
    fun launcherUpdateWinsEveryCompetingWindow() {
        assertEquals(
            LauncherOverlay.LAUNCHER_UPDATE,
            selectLauncherOverlay(
                launcherUpdateOpen = true,
                directPatchOpen = true,
                installedAddOnUpdatesOpen = true,
                addOnTransferOpen = true,
                gameCompanionInstallOpen = true,
                packageSetupOpen = true
            )
        )
    }

    @Test
    fun addOnUpdateCenterWinsCompanionInstaller() {
        assertEquals(
            LauncherOverlay.INSTALLED_ADD_ON_UPDATES,
            selectLauncherOverlay(
                launcherUpdateOpen = false,
                directPatchOpen = false,
                installedAddOnUpdatesOpen = true,
                addOnTransferOpen = false,
                gameCompanionInstallOpen = true,
                packageSetupOpen = true
            )
        )
    }

    @Test
    fun activeAddOnTransferWinsCompanionInstaller() {
        assertEquals(
            LauncherOverlay.ADD_ON_TRANSFER,
            selectLauncherOverlay(
                launcherUpdateOpen = false,
                directPatchOpen = false,
                installedAddOnUpdatesOpen = false,
                addOnTransferOpen = true,
                gameCompanionInstallOpen = true,
                packageSetupOpen = true
            )
        )
    }

    @Test
    fun companionInstallerReturnsWhenNoHigherPriorityWindowIsOpen() {
        assertEquals(
            LauncherOverlay.GAME_COMPANION_INSTALL,
            selectLauncherOverlay(
                launcherUpdateOpen = false,
                directPatchOpen = false,
                installedAddOnUpdatesOpen = false,
                addOnTransferOpen = false,
                gameCompanionInstallOpen = true,
                packageSetupOpen = true
            )
        )
    }

    @Test
    fun packageSetupReturnsWhenTransferWindowsAreClosed() {
        assertEquals(
            LauncherOverlay.PACKAGE_SETUP,
            selectLauncherOverlay(
                launcherUpdateOpen = false,
                directPatchOpen = false,
                installedAddOnUpdatesOpen = false,
                addOnTransferOpen = false,
                gameCompanionInstallOpen = false,
                packageSetupOpen = true
            )
        )
    }
}
