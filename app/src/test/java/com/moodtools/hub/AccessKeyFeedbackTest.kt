package com.moodtools.hub

import com.moodtools.hub.networking.LauncherServiceException
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessKeyFeedbackTest {
    @Test
    fun turnsExpectedFailuresIntoUserFacingGuidance() {
        assertEquals(ACCESS_KEY_INVALID, accessKeyErrorFeedback(
            IllegalArgumentException("Enter a valid Jester Mods access key")
        ).code)
        assertEquals(ACCESS_KEY_IN_USE, accessKeyErrorFeedback(
            LauncherServiceException("AUTHORIZATION_FAILED", "This access key is already bound to another device.")
        ).code)
        assertEquals("ROOT_LAUNCHER_REQUIRED", accessKeyErrorFeedback(
            LauncherServiceException("ROOT_LAUNCHER_REQUIRED", "internal details")
        ).code)
        assertEquals(ACCESS_KEY_RETRY, accessKeyErrorFeedback(
            IllegalStateException("The launcher proof was rejected.")
        ).code)
    }
}
