package com.moodtools.hub.modules

import org.junit.Assert.assertEquals
import org.junit.Test

class ArchitectureLabelsTest {
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
