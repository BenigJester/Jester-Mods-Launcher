package com.moodtools.hub

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleLanguageHandoffTest {
    @Test
    fun acceptsSupportedLanguagesAndFallsBackToEnglish() {
        (0..8).forEach { assertEquals(it, normalizeModuleLanguage(it)) }
        assertEquals(0, normalizeModuleLanguage(-1))
        assertEquals(0, normalizeModuleLanguage(9))
    }
}
