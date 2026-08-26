package com.moodtools.hub.networking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SmartStorageManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun failedLauncherInstallStaysUntilNewerReleaseIsDetected() {
        val files = temporaryFolder.newFolder("files")
        val cache = temporaryFolder.newFolder("cache")
        val updates = File(files, "launcher-updates").apply { mkdirs() }
        val failed = artifact(updates, "launcher-6-root.apk")
        val newerUnexpected = artifact(updates, "launcher-8-root.apk")

        val storage = SmartStorageManager(files, cache)
        storage.cleanStartup(installedLauncherBuild = 5L) { null }
        assertTrue(failed.isFile)

        storage.onLauncherReleaseDetected(build = 6L, testChannel = false, flavor = "root")
        assertTrue(failed.isFile)

        storage.onLauncherReleaseDetected(build = 7L, testChannel = false, flavor = "root")
        assertFalse(failed.exists())
        assertTrue(newerUnexpected.isFile)
    }

    @Test
    fun launcherSuccessDeletesInstalledBuildButNotAnotherChannelsNewerBuild() {
        val files = temporaryFolder.newFolder("files")
        val updates = File(files, "launcher-updates").apply { mkdirs() }
        val installed = artifact(updates, "launcher-6-root.apk")
        val testBuild = artifact(updates, "launcher-test-7-root.apk")

        SmartStorageManager(files, temporaryFolder.newFolder("cache"))
            .onLauncherInstallSucceeded(6L)

        assertFalse(installed.exists())
        assertTrue(testBuild.isFile)
    }

    @Test
    fun gameDownloadsStayAfterFailureAndPruneOnSuccessOrNewerCatalogVersion() {
        val files = temporaryFolder.newFolder("files")
        val downloads = File(files, "game-downloads").apply { mkdirs() }
        val failed = artifact(downloads, "com.example.game-example-game-100.apks")
        val current = artifact(downloads, "com.example.game-example-game-101.apks")
        val other = artifact(downloads, "com.example.other-other-game-20.apk")
        val storage = SmartStorageManager(files, temporaryFolder.newFolder("cache"))

        storage.cleanStartup(installedLauncherBuild = 1L) { null }
        assertTrue(failed.isFile)
        assertTrue(current.isFile)

        storage.onGameReleaseDetected("com.example.game", 101L)
        assertFalse(failed.exists())
        assertTrue(current.isFile)
        assertTrue(other.isFile)

        storage.onGameInstallSucceeded("com.example.game", 101L)
        assertFalse(current.exists())
        assertTrue(other.isFile)
    }

    @Test
    fun startupRestoresBackupsAndRemovesInterruptedModuleDownloads() {
        val files = temporaryFolder.newFolder("files")
        val module = File(files, "menus/com.example.game").apply { mkdirs() }
        val target = artifact(module, "classes.dex", "new")
        val backup = artifact(module, "classes.dex.bak", "known-good")
        val partial = artifact(module, "libmenu_native.so.next.part", "partial")

        val result = SmartStorageManager(files, temporaryFolder.newFolder("cache"))
            .cleanStartup(installedLauncherBuild = 1L) { null }

        assertTrue(target.isFile)
        assertTrue(target.readText() == "known-good")
        assertFalse(backup.exists())
        assertFalse(partial.exists())
        assertTrue(result.recoveredFiles == 1)
        assertTrue(result.deletedFiles >= 2)
    }

    @Test
    fun startupKeepsFullyCommittedModuleGenerationAndDropsItsOldBackups() {
        val files = temporaryFolder.newFolder("files")
        val module = File(files, "menus/com.example.game").apply { mkdirs() }
        val targets = listOf("libmenu_native.so", "classes.dex", "config.json", "signed-manifest.json")
        targets.forEach { name ->
            artifact(module, name, "new-$name")
            artifact(module, "$name.bak", "old-$name")
        }

        val result = SmartStorageManager(files, temporaryFolder.newFolder("cache"))
            .cleanStartup(installedLauncherBuild = 1L) { null }

        targets.forEach { name ->
            assertTrue(File(module, name).readText() == "new-$name")
            assertFalse(File(module, "$name.bak").exists())
        }
        assertTrue(result.recoveredFiles == 0)
    }

    @Test
    fun startupKeepsNewestRetryablePatchAndRemovesIncompleteAndSupersededBuilds() {
        val files = temporaryFolder.newFolder("files")
        val packageDirectory = File(files, "soul-knight-patches/com.example.game").apply { mkdirs() }
        val oldGeneration = File(packageDirectory, "100-old")
        val oldSigned = File(oldGeneration, "signed").apply { mkdirs() }
        artifact(oldSigned, "base.apk")
        oldGeneration.setLastModified(100L)
        val currentGeneration = File(packageDirectory, "101-current")
        val currentSigned = File(currentGeneration, "signed").apply { mkdirs() }
        artifact(currentSigned, "base.apk")
        currentGeneration.setLastModified(200L)
        val interrupted = File(packageDirectory, "102-interrupted").apply { mkdirs() }
        artifact(interrupted, "base-unsigned.apk")

        SmartStorageManager(files, temporaryFolder.newFolder("cache"))
            .cleanStartup(installedLauncherBuild = 1L) { null }

        assertFalse(oldGeneration.exists())
        assertTrue(currentGeneration.isDirectory)
        assertFalse(interrupted.exists())
    }

    @Test
    fun successfulPatchInstallAndAddOnRemovalReclaimOnlyLauncherOwnedPatchFiles() {
        val files = temporaryFolder.newFolder("files")
        val patchRoot = File(files, "soul-knight-patches")
        val installedPatch = artifact(
            File(patchRoot, "com.example.game/101-current/signed").apply { mkdirs() },
            "base.apk"
        )
        val otherPatch = artifact(
            File(patchRoot, "com.example.other/20-current/signed").apply { mkdirs() },
            "base.apk"
        )
        val storage = SmartStorageManager(files, temporaryFolder.newFolder("cache"))

        val installedCleanup = storage.onDirectPatchInstallSucceeded("com.example.game")

        assertFalse(installedPatch.exists())
        assertTrue(otherPatch.isFile)
        assertTrue(installedCleanup.deletedFiles == 1)

        storage.onAddOnRemoved("com.example.other")
        assertFalse(otherPatch.exists())
        assertFalse(patchRoot.exists())
    }

    private fun artifact(directory: File, name: String, contents: String = "verified"): File {
        return File(directory, name).apply { writeText(contents) }
    }
}
