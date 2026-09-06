package com.moodtools.hub.identity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityShellStatusTest {
    @Test
    fun currentShellBoundToThisLauncherIsReady() {
        assertTrue(
            isIdentityShellReady(
                identityShell = true,
                protocolVersion = IdentityShellManager.CURRENT_SHELL_VERSION,
                payloadAuthority = "com.example.launcher.identity-payload",
                expectedPayloadAuthority = "com.example.launcher.identity-payload"
            )
        )
    }

    @Test
    fun legacyOrDifferentlyBoundShellNeedsRepair() {
        assertFalse(
            isIdentityShellReady(
                identityShell = true,
                protocolVersion = 2,
                payloadAuthority = "com.example.launcher.identity-payload",
                expectedPayloadAuthority = "com.example.launcher.identity-payload"
            )
        )
        assertFalse(
            isIdentityShellReady(
                identityShell = true,
                protocolVersion = 0,
                payloadAuthority = null,
                expectedPayloadAuthority = "com.example.launcher.identity-payload"
            )
        )
        assertFalse(
            isIdentityShellReady(
                identityShell = true,
                protocolVersion = IdentityShellManager.CURRENT_SHELL_VERSION,
                payloadAuthority = "com.old.launcher.identity-payload",
                expectedPayloadAuthority = "com.example.launcher.identity-payload"
            )
        )
    }
}
