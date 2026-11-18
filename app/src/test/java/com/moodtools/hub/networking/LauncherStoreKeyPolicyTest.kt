package com.moodtools.hub.networking

import org.junit.Assert.*
import org.junit.Test

class LauncherStoreKeyPolicyTest {
    @Test fun launcherOnlyKeyHasThirtyDaysAndNoPrivateEntitlement() {
        assertTrue(validStoreKeyEntitlement("launcher-only-30d", null, 30, null, 100))
        assertFalse(validStoreKeyEntitlement("launcher-only-30d", null, 7, null, 100))
        assertFalse(validStoreKeyEntitlement("launcher-only-30d", null, 30, 200, 100))
        assertFalse(validStoreKeyEntitlement("unknown", null, 30, null, 100))
    }

    @Test fun bundledKeysStillRequireValidFreeFireAccess() {
        for (days in listOf(7, 15, 30)) {
            assertTrue(validStoreKeyEntitlement("launcher-free-fire-${days}d", "free-fire", days, 200, 100))
            assertFalse(validStoreKeyEntitlement("launcher-free-fire-${days}d", null, days, null, 100))
        }
        assertFalse(validStoreKeyEntitlement("launcher-free-fire-30d", "free-fire", 30, null, 100))
        assertFalse(validStoreKeyEntitlement("launcher-free-fire-30d", "free-fire", 30, 100, 100))
        assertFalse(validStoreKeyEntitlement("launcher-only-30d", "free-fire", 30, 200, 100))
        assertFalse(validStoreKeyEntitlement("launcher-only-30d", "unknown", 30, 200, 100))
    }
}
