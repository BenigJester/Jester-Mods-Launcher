package com.moodtools.hub.networking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherUpdatePathTest {
    @Test
    fun acceptsCanonicalAndLegacyPathsForTheSameSignedBuild() {
        assertTrue(isTrustedStableLauncherDownloadPath(
            "/download/jester-moods-launcher?file=Jester-Moods-Root.apk",
            12,
            "root"
        ))
        assertTrue(isTrustedStableLauncherDownloadPath(
            "/api/launcher-download/12/nonroot.apk",
            12,
            "nonroot"
        ))
    }

    @Test
    fun rejectsCrossBuildCrossFlavorAndUntrustedPaths() {
        assertFalse(isTrustedStableLauncherDownloadPath("/api/launcher-download/11/root.apk", 12, "root"))
        assertFalse(isTrustedStableLauncherDownloadPath("/api/launcher-download/12/root.apk", 12, "nonroot"))
        assertFalse(isTrustedStableLauncherDownloadPath(
            "/download/jester-moods-launcher?file=Jester-Moods-Root.apk",
            12,
            "nonroot"
        ))
        assertFalse(isTrustedStableLauncherDownloadPath("https://example.test/update.apk", 12, "root"))
        assertFalse(isTrustedStableLauncherDownloadPath("/api/launcher-download/12/root.apk", 12, "debug"))
    }
}
