package com.moodtools.hub.soulpatch

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectPatchInstallPolicyTest {
    private val base = File("base.apk")
    private val split = File("split_config.arm64_v8a.apk")

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

    @Test
    fun moduleOrPatchFormatChangeNeedsReapply() {
        assertFalse(directPatchRevisionIsCurrent(CURRENT_REVISION.copy(moduleBuild = 41), CURRENT_REVISION))
        assertFalse(directPatchRevisionIsCurrent(CURRENT_REVISION.copy(markerSchema = 1), CURRENT_REVISION))
        assertFalse(directPatchRevisionIsCurrent(
            CURRENT_REVISION.copy(nativeSha256 = "old-native"),
            CURRENT_REVISION
        ))
    }

    private companion object {
        const val PATCH_SIGNER = "jester-patch-signer"
        val CURRENT_REVISION = DirectPatchRevision(
            markerSchema = 2,
            gameVersionCode = 80512,
            moduleBuild = 42,
            moduleVersion = "8.5.1-r2",
            launchGuardSchema = 1,
            launchGuardPublicKey = "guard-key",
            dexSha256 = "dex",
            nativeSha256 = "native"
        )
    }
}
