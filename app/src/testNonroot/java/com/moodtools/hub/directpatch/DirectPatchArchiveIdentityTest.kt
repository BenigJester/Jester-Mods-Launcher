package com.moodtools.hub.directpatch

import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class DirectPatchArchiveIdentityTest {
    @Test fun `full replacement verifies prepared APKs without looking up the removed package`() {
        val prepared = listOf(File("prepared/base.apk"), File("prepared/split_config.arm64_v8a.apk"))
        assertSame(prepared, directPatchVerificationApks(prepared, false) {
            error("The original package was already uninstalled")
        })
    }

    @Test fun `inherited update verifies retained splits with prepared replacements taking precedence`() {
        val prepared = listOf(File("prepared/base.apk"), File("prepared/split_install_pack.apk"))
        val installed = listOf(File("installed/base.apk"), File("installed/split_config.arm64_v8a.apk"))
        assertEquals(setOf(prepared[0], prepared[1], installed[1]),
            directPatchVerificationApks(prepared, true) { installed }.toSet())
        assertThrows(IllegalStateException::class.java) {
            directPatchVerificationApks(prepared, true) { error("Inherited game is missing") }
        }
    }

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
