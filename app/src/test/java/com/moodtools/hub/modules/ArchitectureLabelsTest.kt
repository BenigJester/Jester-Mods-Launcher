package com.moodtools.hub.modules

import org.junit.Assert.*
import java.io.File
import org.junit.Test

class ArchitectureLabelsTest {
    @Test fun `download evidence distinguishes 32 bit game from unknown and device mismatch`() {
        val arm64 = listOf("arm64-v8a")
        val mixed = listOf("arm64-v8a", "armeabi-v7a")
        val mismatch = moduleArchitectureEvidence("armeabi-v7a", arm64, mixed)
        assertTrue(mismatch.contains("game=armeabi-v7a (32-bit)"))
        assertTrue(mismatch.contains("addOn=arm64-v8a"))
        assertTrue(mismatch.contains("result=game_abi_mismatch"))
        assertTrue(moduleArchitectureEvidence("unknown", arm64, mixed).contains("result=game_abi_unknown"))
        assertTrue(moduleArchitectureEvidence("arm64-v8a", arm64, listOf("armeabi-v7a")).contains("result=device_abi_mismatch"))
        assertTrue(moduleArchitectureEvidence("arm64-v8a", arm64, mixed).contains("(64-bit)"))
        assertTrue(moduleArchitectureEvidence("arm64-v8a", arm64, mixed).endsWith("result=compatible"))
        val private = moduleArchitectureEvidence("/private/token", listOf("account@example.com"), emptyList())
        assertFalse(private.contains("/private")); assertFalse(private.contains("account@"))
        val all = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64", "private")
        assertTrue((moduleArchitectureEvidence("armeabi-v7a", all, all) + "; recordedGameSupported=false").length < 320)
    }

    @Test fun `architecture evidence is recorded before rejection on all download paths`() {
        val root = listOf(File("."), File("..")).first { File(it, "settings.gradle").isFile }
        val source = root.resolve("app/src/main/java/com/moodtools/hub/LauncherActivity.kt").readText()
            .substringAfter("private fun applyUpdate(").substringBefore("private fun ")
        assertTrue(source.indexOf("publishDiagnostic(moduleArchitectureEvidence") >= 0)
        assertTrue(source.indexOf("publishDiagnostic(moduleArchitectureEvidence") < source.indexOf("check(DeviceArchitectureGuard.supports"))
        assertTrue(source.indexOf("publishDiagnostic(moduleArchitectureEvidence") < source.indexOf("check(game.abiSupported)"))
        assertTrue(source.contains("onDiagnostic = publishDiagnostic"))
        assertTrue(source.contains("recordedGameSupported="))
    }
    @Test
    fun `uses concise bitness labels`() {
        assertEquals("64-bit", architectureLabel("arm64-v8a"))
        assertEquals("32-bit", architectureLabel("armeabi-v7a"))
    }

    @Test
    fun `collapses multiple equivalent architectures`() {
        assertEquals("64-bit", architectureSummary(setOf("arm64-v8a", "x86_64")))
    }

    @Test
    fun `shows both bitness values only when necessary`() {
        assertEquals("64-bit, 32-bit", architectureSummary(setOf("armeabi-v7a", "arm64-v8a")))
    }
}
