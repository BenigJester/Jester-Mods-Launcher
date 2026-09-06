package com.moodtools.hub.networking

import com.moodtools.hub.modules.ModuleIntegrityVerifier
import com.moodtools.hub.modules.ModuleRepository
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateClientTest {
    @Test
    fun signedCatalogActivationRemovesStaleLocalTestIdentity() {
        val root = Files.createTempDirectory("catalog-activation").toFile()
        try {
            val client = UpdateClient(root)
            val next = stagedPayload(root)
            File(root, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).writeText("{}")

            client.commitStandalonePayload(
                next.native,
                next.dex,
                next.config,
                next.manifest,
                next.update,
                next.privateMarker
            )

            assertFalse(File(root, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).exists())
            assertEquals("new-native", File(root, "libmenu_native.so").readText())
            assertEquals("new-dex", File(root, "classes.dex").readText())
            assertEquals("new-config", File(root, "config.json").readText())
            assertEquals(
                "new-manifest",
                File(root, ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE).readText()
            )
            assertEquals("new-update", File(root, "update.json").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedCatalogActivationRestoresLocalTestIdentityAndPayload() {
        val root = Files.createTempDirectory("catalog-rollback").toFile()
        try {
            val client = UpdateClient(root)
            File(root, "libmenu_native.so").writeText("old-native")
            File(root, "classes.dex").writeText("old-dex")
            File(root, "config.json").writeText("old-config")
            File(root, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).writeText("old-marker")
            val next = stagedPayload(root)
            assertTrue(next.manifest.delete())

            val result = runCatching {
                client.commitStandalonePayload(
                    next.native,
                    next.dex,
                    next.config,
                    next.manifest,
                    next.update,
                    next.privateMarker
                )
            }

            assertTrue(result.isFailure)
            assertEquals("old-native", File(root, "libmenu_native.so").readText())
            assertEquals("old-dex", File(root, "classes.dex").readText())
            assertEquals("old-config", File(root, "config.json").readText())
            assertEquals(
                "old-marker",
                File(root, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).readText()
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun catalogRepairReplacesMalformedTargetsWithoutClearingOtherModules() {
        val menus = Files.createTempDirectory("catalog-repair").toFile()
        val root = File(menus, "com.example.game").apply { mkdirs() }
        val other = File(menus, "com.example.other").apply { mkdirs() }
        try {
            File(root, "classes.dex").mkdir()
            File(root, "update.json").mkdir()
            File(other, "keep.txt").writeText("untouched")
            val client = UpdateClient(root)
            val next = stagedPayload(root)

            client.commitStandalonePayload(
                next.native,
                next.dex,
                next.config,
                next.manifest,
                next.update,
                next.privateMarker
            )

            assertEquals("new-dex", File(root, "classes.dex").readText())
            assertEquals("new-update", File(root, "update.json").readText())
            assertEquals("untouched", File(other, "keep.txt").readText())
        } finally {
            menus.deleteRecursively()
        }
    }

    private fun stagedPayload(root: File): StagedPayload = StagedPayload(
        native = File(root, "libmenu_native.so.next").apply { writeText("new-native") },
        dex = File(root, "classes.dex.next").apply { writeText("new-dex") },
        config = File(root, "config.json.next").apply { writeText("new-config") },
        manifest = File(root, "${ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE}.next").apply {
            writeText("new-manifest")
        },
        update = File(root, "update.json.next").apply { writeText("new-update") },
        privateMarker = null
    )

    private data class StagedPayload(
        val native: File,
        val dex: File,
        val config: File,
        val manifest: File,
        val update: File,
        val privateMarker: File?
    )
}
