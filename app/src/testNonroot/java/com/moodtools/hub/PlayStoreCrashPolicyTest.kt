package com.moodtools.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.niunaijun.blackbox.utils.PlayStoreCrashPolicy

class PlayStoreCrashPolicyTest {
    @Test
    fun `arms game initiated Play Store binds on every supported non-root Android version`() {
        assertTrue(
            PlayStoreCrashPolicy.shouldArmForServiceBind(
                26,
                "net.mobigame.zombietsunami",
                "com.android.vending"
            )
        )
        assertTrue(
            PlayStoreCrashPolicy.shouldArmForServiceBind(
                36,
                "com.os.airforce",
                "com.android.vending"
            )
        )
        assertTrue(
            PlayStoreCrashPolicy.shouldArmForServiceBind(
                36,
                "com.ctugames.km2",
                "com.android.vending"
            )
        )
        assertFalse(
            PlayStoreCrashPolicy.shouldArmForServiceBind(
                25,
                "net.mobigame.zombietsunami",
                "com.android.vending"
            )
        )
        assertTrue(
            PlayStoreCrashPolicy.shouldArmForServiceBind(
                36,
                "com.example.othergame",
                "com.android.vending"
            )
        )
        assertFalse(
            PlayStoreCrashPolicy.shouldArmForServiceBind(
                36,
                "com.google.android.gms",
                "com.android.vending"
            )
        )
        assertFalse(
            PlayStoreCrashPolicy.shouldArmForServiceBind(
                36,
                "com.android.vending",
                "com.android.vending"
            )
        )
        assertFalse(
            PlayStoreCrashPolicy.shouldArmForServiceBind(
                36,
                "net.mobigame.zombietsunami",
                "com.google.android.gms"
            )
        )
    }

    @Test
    fun `forces only armed Play Store provider queries`() {
        assertTrue(
            PlayStoreCrashPolicy.shouldForceUidMismatch(
                26,
                true,
                "com.android.vending",
                "query"
            )
        )
        assertTrue(
            PlayStoreCrashPolicy.shouldForceUidMismatch(
                36,
                true,
                "com.android.vending",
                "query"
            )
        )

        assertFalse(
            PlayStoreCrashPolicy.shouldForceUidMismatch(
                25,
                true,
                "com.android.vending",
                "query"
            )
        )
        assertFalse(
            PlayStoreCrashPolicy.shouldForceUidMismatch(
                36,
                true,
                "net.mobigame.zombietsunami",
                "query"
            )
        )
        assertFalse(
            PlayStoreCrashPolicy.shouldForceUidMismatch(
                36,
                true,
                "com.android.vending",
                "call"
            )
        )
        assertFalse(
            PlayStoreCrashPolicy.shouldForceUidMismatch(
                36,
                false,
                "com.android.vending",
                "query"
            )
        )
    }

    @Test
    fun `uses the observed Android exception message`() {
        assertEquals(
            "Package com.android.vending does not belong to 10488",
            PlayStoreCrashPolicy.uidMismatchMessage(10488)
        )
    }
}
