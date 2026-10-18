package com.moodtools.hub.networking

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class GameExpansionFilesTest {
    private val pkg = "com.ea.game.pvz2_row"
    private val main = "main.1063.$pkg.obb"
    private val patch = "patch.1060.$pkg.obb" // Expansion versions need not equal APK versions.

    @Test fun stagesMainAndPatchThenDeliversToSystemAndBlackBoxWithoutApks() = withRoot { root ->
        val archive = archive(root, mapOf("Android/obb/$pkg/$main" to "main-data", patch to "patch-data", "base.apk" to "apk"))
        val stage = File(root, "retained/1063")
        ZipFile(archive).use {
            assertEquals(2, GameExpansionFiles.inspect(it, pkg).size)
            GameExpansionFiles.stage(it, pkg, stage)
        }
        listOf("system", "blackbox").forEach { location ->
            val target = File(root, location)
            GameExpansionFiles.deliver(stage, pkg, target)
            assertEquals("main-data", File(target, main).readText())
            assertEquals("patch-data", File(target, patch).readText())
            assertFalse(File(target, "base.apk").exists())
            val stamp = File(target, main).lastModified()
            GameExpansionFiles.deliver(stage, pkg, target)
            assertEquals(stamp, File(target, main).lastModified())
        }
        val system = File(root, "system")
        File(system, main).delete() // Android clears OBBs during first patch replacement.
        GameExpansionFiles.deliver(stage, pkg, system)
        assertEquals("main-data", File(system, main).readText())
    }

    @Test fun rejectsForeignTraversalDuplicateAndMalformedFiles() = withRoot { root ->
        listOf(
            mapOf("Android/obb/other.game/$main" to "data"),
            mapOf("../$main" to "data"),
            mapOf("Android/obb/$pkg/../$main" to "data"),
            mapOf("other.obb" to "data"),
            mapOf(main to ""),
            mapOf(main to "data", "main.1062.$pkg.obb" to "duplicate"),
            mapOf(main to "data", "Android/obb/$pkg/$main" to "duplicate")
        ).forEach { files ->
            ZipFile(archive(root, files)).use { zip ->
                assertThrows(IllegalArgumentException::class.java) { GameExpansionFiles.inspect(zip, pkg) }
            }
        }
    }

    @Test fun cancellationPreservesPreviousGenerationAndDestination() = withRoot { root ->
        val stage = File(root, "retained/1063")
        ZipFile(archive(root, mapOf(main to "original"))).use { GameExpansionFiles.stage(it, pkg, stage) }
        ZipFile(archive(root, mapOf(main to "replacement"))).use {
            assertThrows(GameInstallCancelledException::class.java) {
                GameExpansionFiles.stage(it, pkg, stage, { true })
            }
        }
        val target = File(root, "system")
        GameExpansionFiles.deliver(stage, pkg, target)
        assertEquals("original", File(target, main).readText())
        assertThrows(GameInstallCancelledException::class.java) {
            GameExpansionFiles.deliver(stage, pkg, File(root, "cancelled"), { true })
        }
        assertFalse(File(root, "cancelled/$main").exists())
        assertEquals(listOf("1063"), stage.parentFile!!.list()!!.toList())
    }

    @Test fun corruptedRetainedObbCannotReplaceExistingGameData() = withRoot { root ->
        val stage = File(root, "retained/1063")
        ZipFile(archive(root, mapOf(main to "original"))).use { GameExpansionFiles.stage(it, pkg, stage) }
        File(stage, main).writeText("tampered")
        val target = File(root, "system").apply { mkdirs() }
        File(target, main).writeText("existing")
        assertThrows(IllegalArgumentException::class.java) { GameExpansionFiles.deliver(stage, pkg, target) }
        assertEquals("existing", File(target, main).readText())
        assertEquals(listOf(main), target.list()!!.toList())
    }

    @Test fun packagesWithoutObbNeedNoStorageOrPermission() = withRoot { root ->
        val stage = File(root, "retained/1063")
        ZipFile(archive(root, mapOf("base.apk" to "apk"))).use { GameExpansionFiles.stage(it, pkg, stage) }
        GameExpansionFiles.deliver(stage, pkg, File(root, "system"))
        assertFalse(stage.exists())
        assertFalse(File(root, "system").exists())
        assertThrows(IllegalArgumentException::class.java) { GameExpansionFiles.requirePackage("../outside") }
    }

    @Test fun recoversProcessDeathAndPrunesOnlyOlderRetainedVersions() = withRoot { root ->
        val stage = File(root, "retained/1063")
        ZipFile(archive(root, mapOf(main to "original"))).use { GameExpansionFiles.stage(it, pkg, stage) }
        assertTrue(stage.renameTo(File(stage.parentFile, "1063-previous")))
        val incoming = File(stage.parentFile, "1063-incoming-interrupted").apply { mkdir() }
        File(incoming, main).writeText("incomplete")
        val target = File(root, "system")
        GameExpansionFiles.deliver(stage, pkg, target)
        assertEquals("original", File(target, main).readText())
        assertFalse(incoming.exists())
        val newer = File(stage.parentFile, "1064")
        ZipFile(archive(root, mapOf(main to "new data"))).use { GameExpansionFiles.stage(it, pkg, newer) }
        assertFalse(stage.exists())
        assertTrue(File(newer, main).isFile)
        assertEquals("original", File(target, main).readText())
    }

    @Test fun retryReusesVerifiedDataButRepairsCorruptedRetainedData() = withRoot { root ->
        val stage = File(root, "game-obb/$pkg/1063")
        ZipFile(archive(root, mapOf(main to "original"))).use { zip ->
            GameExpansionFiles.stage(zip, pkg, stage)
            val stamp = File(stage, main).lastModified()
            GameExpansionFiles.stage(zip, pkg, stage)
            assertEquals(stamp, File(stage, main).lastModified())
            File(stage, main).writeText("tampered")
            GameExpansionFiles.stage(zip, pkg, stage)
            assertEquals("original", File(stage, main).readText())
        }
        SmartStorageManager(root, File(root, "cache")).onGameInstallSucceeded(pkg, 1063)
        assertEquals("original", File(stage, main).readText())
    }

    private fun archive(root: File, entries: Map<String, String>): File = File.createTempFile("package-", ".zip", root).also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("jester-obb-test-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}
