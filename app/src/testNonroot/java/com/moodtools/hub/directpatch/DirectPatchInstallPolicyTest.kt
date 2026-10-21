package com.moodtools.hub.directpatch

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectPatchInstallPolicyTest {
    @get:Rule val temporary = TemporaryFolder()
    private val base = File("base.apk")
    private val split = File("split_config.arm64_v8a.apk")

    @Test fun generatedDirectoriesPassProductionGuardForEnabledAndDisabledProfiles() {
        val root = temporary.newFolder("patches")
        val dex = "a".repeat(64)
        val native = "b".repeat(64)
        fun name(profile: String) = directPatchDirectoryName(80700, 5, 42, dex, native, profile)
        val disabled = name("")
        val enabled = name("soul-knight-8.7.0-gms-v1")
        assertEquals("80700-5-42-${dex.take(16)}-${native.take(16)}", disabled)
        assertEquals(enabled, name("soul-knight-8.7.0-gms-v1"))
        assertNotEquals(disabled, enabled)
        assertNotEquals(enabled, name("soul-knight-8.7.0-gms-v2"))
        for (profile in listOf("", "soul-knight-8.7.0-gms-v1", "soul-knight-8.7.0-gms-v2")) {
            val directory = File(root, name(profile))
            assertEquals(directory.canonicalFile, validatedDirectPatchDirectory(root, directory))
        }
    }

    @Test fun directoryGuardStillRejectsTraversalNestedPathsAndUnsafeNames() {
        val root = temporary.newFolder("patches")
        val valid = directPatchDirectoryName(80700, 5, 42, "a".repeat(64), "b".repeat(64), "")
        for (directory in listOf(root, File(root.parentFile, valid), File(root, "nested/$valid"),
                File(root, "../$valid"), File(root, "$valid-no-gms"),
                File(root, "$valid-soul-knight-8.7.0-gms-v1"))) {
            assertThrows(IllegalArgumentException::class.java) { validatedDirectPatchDirectory(root, directory) }
        }
    }

    @Test
    fun matchingInstalledClusterUsesDataPreservingUpdate() {
        assertFalse(
            directPatchRequiresUninstall(listOf(base, split), PATCH_SIGNER) { setOf(PATCH_SIGNER) }
        )
    }

    @Test
    fun differentSignerRequiresFirstReplacement() {
        assertTrue(
            directPatchRequiresUninstall(listOf(base), PATCH_SIGNER) { setOf("play-signer") }
        )
    }

    @Test
    fun mismatchedSplitCannotBeTreatedAsAnUpdate() {
        assertTrue(
            directPatchRequiresUninstall(listOf(base, split), PATCH_SIGNER) { apk ->
                if (apk == base) setOf(PATCH_SIGNER) else setOf("other-signer")
            }
        )
    }

    @Test
    fun verificationFailureFailsClosedToReplacement() {
        assertTrue(
            directPatchRequiresUninstall(listOf(base), PATCH_SIGNER) { error("unreadable APK") }
        )
    }

    @Test
    fun exactPatchRevisionIsCurrent() {
        assertTrue(directPatchRevisionIsCurrent(CURRENT_REVISION, CURRENT_REVISION.copy()))
    }

    @Test fun googleSupportOptInAndOptOutRequireReapply() {
        val enabled = CURRENT_REVISION.copy(gmsProfile = "soul-knight-8.7.0-gms-v1")
        assertFalse(directPatchRevisionIsCurrent(CURRENT_REVISION, enabled))
        assertFalse(directPatchRevisionIsCurrent(enabled, CURRENT_REVISION))
        assertFalse(directPatchRevisionIsCurrent(enabled, enabled.copy(gmsProfile = "future-profile")))
    }

    @Test
    fun moduleOrPatchFormatChangeNeedsReapply() {
        assertFalse(directPatchRevisionIsCurrent(CURRENT_REVISION.copy(moduleBuild = 41), CURRENT_REVISION))
        assertFalse(directPatchRevisionIsCurrent(CURRENT_REVISION.copy(markerSchema = 1), CURRENT_REVISION))
        assertFalse(directPatchRevisionIsCurrent(CURRENT_REVISION.copy(markerSchema = 2), CURRENT_REVISION))
        assertFalse(directPatchRevisionIsCurrent(CURRENT_REVISION.copy(markerSchema = 3), CURRENT_REVISION))
        assertFalse(directPatchRevisionIsCurrent(
            CURRENT_REVISION.copy(nativeSha256 = "old-native"),
            CURRENT_REVISION
        ))
        assertFalse(directPatchRevisionIsCurrent(
            CURRENT_REVISION.copy(gameNativeSha256 = "old-game-native"),
            CURRENT_REVISION
        ))
    }

    private companion object {
        const val PATCH_SIGNER = "jester-patch-signer"
        val CURRENT_REVISION = DirectPatchRevision(
            markerSchema = 4,
            gameVersionCode = 80512,
            moduleBuild = 42,
            moduleVersion = "8.5.1-r2",
            launchGuardSchema = 1,
            launchGuardPublicKey = "guard-key",
            dexSha256 = "dex",
            nativeSha256 = "native",
            gameNativeSha256 = "game-native"
        )
    }
}
