package com.moodtools.hub.soulpatch

import org.junit.Assert.assertThrows
import org.junit.Test

class DirectPatchArchiveIdentityTest {
    @Test
    fun `accepts the expected package and version after independent APK verification`() {
        validateDirectPatchArchiveIdentity(
            actualPackageName = "com.ChillyRoom.DungeonShooter",
            actualVersionCode = 70032L,
            expectedPackageName = "com.ChillyRoom.DungeonShooter",
            expectedVersionCode = 70032L
        )
    }

    @Test
    fun `rejects a different package`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateDirectPatchArchiveIdentity(
                actualPackageName = "com.example.impostor",
                actualVersionCode = 70032L,
                expectedPackageName = "com.ChillyRoom.DungeonShooter",
                expectedVersionCode = 70032L
            )
        }
    }

    @Test
    fun `rejects a different version`() {
        assertThrows(IllegalArgumentException::class.java) {
            validateDirectPatchArchiveIdentity(
                actualPackageName = "com.ChillyRoom.DungeonShooter",
                actualVersionCode = 70031L,
                expectedPackageName = "com.ChillyRoom.DungeonShooter",
                expectedVersionCode = 70032L
            )
        }
    }
}
