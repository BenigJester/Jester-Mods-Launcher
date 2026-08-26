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

    private companion object {
        const val PATCH_SIGNER = "jester-patch-signer"
    }
}
