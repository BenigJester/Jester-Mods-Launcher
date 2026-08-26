package com.moodtools.hub.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NonRootMethodTest {
    @Test
    fun missingMethodDefaultsToInjection() {
        assertEquals(NonRootMethod.INJECTION, NonRootMethod.fromJson(null, "com.example.game"))
    }

    @Test
    fun legacySoulKnightMetadataKeepsDirectPatchRouting() {
        assertEquals(
            NonRootMethod.DIRECT_PATCH,
            NonRootMethod.fromJson(null, "com.ChillyRoom.DungeonShooter")
        )
    }

    @Test
    fun explicitMetadataOverridesLegacyDefaultsAndRejectsUnknownMethods() {
        assertEquals(
            NonRootMethod.INJECTION,
            NonRootMethod.fromJson("injection", "com.ChillyRoom.DungeonShooter")
        )
        assertEquals(NonRootMethod.DIRECT_PATCH, NonRootMethod.fromJson("direct_patch"))
        assertThrows(IllegalArgumentException::class.java) {
            NonRootMethod.fromJson("virtual_magic")
        }
    }

    @Test
    fun directPatchKeepsItsWireValueWhileUsingClearLauncherWording() {
        assertEquals("direct_patch", NonRootMethod.DIRECT_PATCH.jsonValue)
        assertEquals("Patch", NonRootMethod.DIRECT_PATCH.displayName)
    }

    @Test
    fun rootPresentationAlwaysUsesInjection() {
        val presentation = launcherMethodPresentation(NonRootMethod.DIRECT_PATCH, rootMode = true)

        assertEquals(NonRootMethod.INJECTION, presentation.method)
        assertEquals("INJECTION", presentation.badgeLabel)
        assertEquals("ROOT SETUP", presentation.setupLabel)
        assertEquals("Root method", presentation.fieldLabel)
        assertEquals("How root injection works", presentation.explanationTitle)
    }

    @Test
    fun nonRootPresentationUsesConfiguredMethod() {
        val presentation = launcherMethodPresentation(NonRootMethod.DIRECT_PATCH, rootMode = false)

        assertEquals(NonRootMethod.DIRECT_PATCH, presentation.method)
        assertEquals("PATCH", presentation.badgeLabel)
        assertEquals("NON-ROOT SETUP", presentation.setupLabel)
        assertEquals("Non-root method", presentation.fieldLabel)
        assertEquals("How patched install works", presentation.explanationTitle)
    }
}
