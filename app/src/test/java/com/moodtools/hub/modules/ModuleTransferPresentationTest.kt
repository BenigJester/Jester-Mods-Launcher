package com.moodtools.hub.modules

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleTransferPresentationTest {
    @Test
    fun `update check can be retried from the shared window`() {
        val state = ModuleUpdateUiState(intent = ModuleTransferIntent.UPDATE_CHECK, failed = true)

        assertEquals("Check again", moduleTransferPrimaryActionLabel(state))
    }

    @Test
    fun `verified update offer downloads from the same window`() {
        val state = ModuleUpdateUiState(
            intent = ModuleTransferIntent.UPDATE_CHECK,
            updateAvailable = true
        )

        assertEquals("Download update", moduleTransferPrimaryActionLabel(state))
    }

    @Test
    fun `ordinary transfer failure keeps its retry action`() {
        val state = ModuleUpdateUiState(failed = true)

        assertEquals("Try again", moduleTransferPrimaryActionLabel(state))
    }
}
