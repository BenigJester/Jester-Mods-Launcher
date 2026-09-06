package com.moodtools.hub

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageReplacementRecoveryStoreTest {
    @Test
    fun roundTripsPreparedReplacement() {
        val root = Files.createTempDirectory("replacement-recovery").toFile()
        try {
            val apk = File(root, "patches/game/signed/base.apk").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val store = PackageReplacementRecoveryStore(root)
            val request = PackageReplacementRequest(
                packageName = "com.example.game",
                title = "Example Game",
                versionCode = 42L,
                apks = listOf(apk),
                requiresUninstall = true,
                kind = PackageReplacementKind.IDENTITY_SHELL
            )

            store.save(request)
            val restored = requireNotNull(store.load())

            assertEquals(request.packageName, restored.packageName)
            assertEquals(request.title, restored.title)
            assertEquals(request.versionCode, restored.versionCode)
            assertEquals(request.requiresUninstall, restored.requiresUninstall)
            assertEquals(request.kind, restored.kind)
            assertEquals(listOf(apk.canonicalFile), restored.apks)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingPreparedApkInvalidatesRecoveryState() {
        val root = Files.createTempDirectory("replacement-recovery-missing").toFile()
        try {
            val apk = File(root, "shells/game/shell.apk").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(7))
            }
            val store = PackageReplacementRecoveryStore(root)
            store.save(
                PackageReplacementRequest(
                    packageName = "com.example.game",
                    title = "Example Game",
                    versionCode = 9L,
                    apks = listOf(apk),
                    requiresUninstall = true
                )
            )
            assertTrue(apk.delete())

            assertNull(store.load())
            assertFalse(File(root, "package-replacement-recovery.json").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
