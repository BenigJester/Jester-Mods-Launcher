package com.moodtools.hub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherWebAddressTest {
    @Test
    fun onlyOfficialAboutProfilesAndServerAreTrusted() {
        assertTrue(isTrustedLauncherWebAddress("https://github.com/BenigJester"))
        assertTrue(isTrustedLauncherWebAddress("https://youtube.com/@jestermods3.0?si=test"))
        assertTrue(isTrustedLauncherWebAddress("https://jester.moodtools.workers.dev/launcher"))
        assertFalse(isTrustedLauncherWebAddress("https://github.com/another-user"))
        assertFalse(isTrustedLauncherWebAddress("http://youtube.com/@jestermods3.0"))
    }
}
