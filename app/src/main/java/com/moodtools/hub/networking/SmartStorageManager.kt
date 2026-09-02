package com.moodtools.hub.networking

import java.io.File

/**
 * Owns retention for files that can be downloaded again. Installed add-on payloads are never
 * treated as cache: only interrupted transaction files inside their directories are reconciled.
 */
internal class SmartStorageManager(
    private val filesDirectory: File,
    private val cacheDirectory: File
) {
    fun cleanStartup(
        installedLauncherBuild: Long,
        installedGameVersion: (String) -> Long?
    ): StorageCleanupResult {
        val result = MutableCleanupResult()
        cleanLauncherDownloads(result) { artifact -> artifact.build <= installedLauncherBuild }
        cleanGameDownloads(result) { artifact ->
            installedGameVersion(artifact.packageName)?.let { it >= artifact.versionCode } == true
        }
        reconcileModuleTransactions(result)
        reconcileDirectPatchArtifacts(result)
        deleteDirectoryContents(File(cacheDirectory, GAME_VALIDATION_CACHE), result)
        CACHE_LIMITS.forEach { (name, limit) ->
            trimCache(File(filesDirectory, name), limit, result)
        }
        return result.snapshot()
    }

    /** A verified launcher APK survives failures until its install succeeds or a newer build exists. */
    fun onLauncherReleaseDetected(
        build: Long,
        testChannel: Boolean,
        flavor: String
    ): StorageCleanupResult {
        require(build > 0L)
        require(flavor == "root" || flavor == "nonroot")
        val result = MutableCleanupResult()
        cleanLauncherDownloads(result) { artifact ->
            artifact.testChannel == testChannel && artifact.flavor == flavor && artifact.build < build
        }
        return result.snapshot()
    }

    fun onLauncherInstallSucceeded(build: Long): StorageCleanupResult {
        require(build > 0L)
        val result = MutableCleanupResult()
        cleanLauncherDownloads(result) { artifact -> artifact.build <= build }
        return result.snapshot()
    }

    /** Older game packages become unusable once the signed catalog advertises a newer version. */
    fun onGameReleaseDetected(packageName: String, versionCode: Long): StorageCleanupResult {
        require(PACKAGE_NAME.matches(packageName))
        require(versionCode > 0L)
        val result = MutableCleanupResult()
        cleanGameDownloads(result) { artifact ->
            artifact.packageName == packageName && artifact.versionCode < versionCode
        }
        return result.snapshot()
    }

    fun onGameInstallSucceeded(packageName: String, versionCode: Long): StorageCleanupResult {
        require(PACKAGE_NAME.matches(packageName))
        require(versionCode > 0L)
        val result = MutableCleanupResult()
        cleanGameDownloads(result) { artifact ->
            artifact.packageName == packageName && artifact.versionCode <= versionCode
        }
        return result.snapshot()
    }

    /**
     * Package Installer owns its own copy after a successful commit, so launcher-built APKs can
     * be reclaimed without touching the installed game or its private data.
     */
    fun onDirectPatchInstallSucceeded(packageName: String): StorageCleanupResult =
        clearDirectPatchArtifacts(packageName)

    /** The official-game migration no longer needs any retryable direct-patch generations. */
    fun onDirectPatchMigrationSucceeded(packageName: String): StorageCleanupResult =
        clearDirectPatchArtifacts(packageName)

    /**
     * The shell owns an imported copy after this acknowledgement, so keeping the large original
     * APK cluster in launcher storage would double the game's storage cost.
     */
    fun onIdentityGameImportSucceeded(packageName: String): StorageCleanupResult {
        require(PACKAGE_NAME.matches(packageName))
        val result = MutableCleanupResult()
        val root = File(filesDirectory, IDENTITY_SHELLS).canonicalFile
        val packageDirectory = File(root, packageName).canonicalFile
        require(packageDirectory.parentFile == root && packageDirectory.name == packageName) {
            "Identity shell directory escaped launcher storage"
        }
        delete(File(packageDirectory, IDENTITY_GAME_PAYLOAD), result)
        delete(File(packageDirectory, "$IDENTITY_GAME_PAYLOAD.incoming"), result)
        return result.snapshot()
    }

    /** Removing launcher support also removes retryable patch builds owned by that add-on. */
    fun onAddOnRemoved(packageName: String): StorageCleanupResult =
        clearDirectPatchArtifacts(packageName)

    private fun cleanLauncherDownloads(
        result: MutableCleanupResult,
        shouldDelete: (LauncherArtifact) -> Boolean
    ) {
        val directory = File(filesDirectory, LAUNCHER_DOWNLOADS)
        directory.listFiles()?.forEach { file ->
            val artifact = parseLauncherArtifact(file.name)
            when {
                artifact != null && shouldDelete(artifact) -> delete(file, result)
                file.isFile && file.name.endsWith(PART_SUFFIX) -> delete(file, result)
            }
        }
        deleteIfEmpty(directory)
    }

    private fun cleanGameDownloads(
        result: MutableCleanupResult,
        shouldDelete: (GameArtifact) -> Boolean
    ) {
        val directory = File(filesDirectory, GAME_DOWNLOADS)
        directory.listFiles()?.forEach { file ->
            val artifact = parseGameArtifact(file.name)
            when {
                artifact != null && shouldDelete(artifact) -> delete(file, result)
                file.isFile && file.name.endsWith(PART_SUFFIX) -> delete(file, result)
            }
        }
        deleteIfEmpty(directory)
    }

    private fun reconcileModuleTransactions(result: MutableCleanupResult) {
        val menuDirectory = File(filesDirectory, MENUS)
        menuDirectory.listFiles()?.filter(File::isDirectory)?.forEach { moduleDirectory ->
            val targets = TRANSACTION_TARGETS.map { name -> File(moduleDirectory, name) }
            val backups = targets.associateWith { target -> File(moduleDirectory, "${target.name}.bak") }
            val hasInterruptedTransaction = backups.values.any(File::isFile)
            if (hasInterruptedTransaction && targets.all(File::isFile)) {
                // Every new target reached its final name; the process only died before backup cleanup.
                backups.values.forEach { delete(it, result) }
            } else if (hasInterruptedTransaction) {
                // A partial target set is never trusted. Restore the complete previous generation.
                targets.forEach targetLoop@{ target ->
                    val backup = backups.getValue(target)
                    if (target.isFile) delete(target, result)
                    if (target.exists()) return@targetLoop
                    if (backup.isFile && backup.renameTo(target)) result.recordRecovered()
                }
            }
            TRANSACTION_TARGETS.forEach { targetName ->
                delete(File(moduleDirectory, "$targetName.next"), result)
                delete(File(moduleDirectory, "$targetName.next.part"), result)
            }
            moduleDirectory.listFiles()
                ?.filter { it.isFile && (it.name.endsWith(PART_SUFFIX) || it.name.endsWith(NEXT_SUFFIX)) }
                ?.forEach { delete(it, result) }
        }
    }

    private fun reconcileDirectPatchArtifacts(result: MutableCleanupResult) {
        val root = File(filesDirectory, DIRECT_PATCHES)
        root.listFiles()?.filter(File::isDirectory)?.forEach { packageDirectory ->
            if (!PACKAGE_NAME.matches(packageDirectory.name)) {
                deleteTree(packageDirectory, result)
                return@forEach
            }
            val completeGenerations = mutableListOf<File>()
            packageDirectory.listFiles()?.filter(File::isDirectory)?.forEach { generation ->
                generation.walkBottomUp()
                    .filter { it.isFile && (it.name.endsWith(PART_SUFFIX) || it.name == PATCH_UNSIGNED_BASE) }
                    .forEach { delete(it, result) }
                val signed = File(generation, PATCH_SIGNED_DIRECTORY)
                val complete = signed.isDirectory &&
                    signed.listFiles()?.any { it.isFile && it.extension.equals("apk", ignoreCase = true) } == true
                if (complete) completeGenerations += generation else deleteTree(generation, result)
            }
            completeGenerations
                .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
                .drop(1)
                .forEach { deleteTree(it, result) }
            deleteIfEmpty(packageDirectory)
        }
        deleteIfEmpty(root)
    }

    private fun clearDirectPatchArtifacts(packageName: String): StorageCleanupResult {
        require(PACKAGE_NAME.matches(packageName))
        val result = MutableCleanupResult()
        val root = File(filesDirectory, DIRECT_PATCHES).canonicalFile
        val packageDirectory = File(root, packageName).canonicalFile
        require(packageDirectory.parentFile == root && packageDirectory.name == packageName) {
            "Direct patch directory escaped launcher storage"
        }
        deleteTree(packageDirectory, result)
        deleteIfEmpty(root)
        return result.snapshot()
    }

    private fun trimCache(directory: File, limit: CacheLimit, result: MutableCleanupResult) {
        if (!directory.isDirectory) return
        directory.listFiles()?.filter(File::isFile)?.filter { it.name.endsWith(PART_SUFFIX) }
            ?.forEach { delete(it, result) }
        val newestFirst = directory.listFiles()?.filter(File::isFile)
            ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .orEmpty()
        var keptFiles = 0
        var keptBytes = 0L
        newestFirst.forEach { file ->
            val fits = keptFiles < limit.maxFiles && file.length() <= limit.maxBytes - keptBytes
            if (fits) {
                keptFiles++
                keptBytes += file.length()
            } else {
                delete(file, result)
            }
        }
        deleteIfEmpty(directory)
    }

    private fun deleteDirectoryContents(directory: File, result: MutableCleanupResult) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirectoryContents(file, result)
                deleteIfEmpty(file)
            } else {
                delete(file, result)
            }
        }
        deleteIfEmpty(directory)
    }

    private fun delete(file: File, result: MutableCleanupResult) {
        if (!file.isFile) return
        val bytes = file.length()
        if (file.delete()) result.recordDeleted(bytes)
    }

    private fun deleteTree(directory: File, result: MutableCleanupResult) {
        if (!directory.exists()) return
        if (directory.isFile) {
            delete(directory, result)
            return
        }
        directory.listFiles()?.forEach { child ->
            if (child.isDirectory) deleteTree(child, result) else delete(child, result)
        }
        deleteIfEmpty(directory)
    }

    private fun deleteIfEmpty(directory: File) {
        if (directory.isDirectory && directory.listFiles().isNullOrEmpty()) directory.delete()
    }

    private fun parseLauncherArtifact(name: String): LauncherArtifact? {
        val match = LAUNCHER_ARTIFACT.matchEntire(name) ?: return null
        return LauncherArtifact(
            testChannel = match.groupValues[1].isNotEmpty(),
            build = match.groupValues[2].toLongOrNull() ?: return null,
            flavor = match.groupValues[3]
        )
    }

    private fun parseGameArtifact(name: String): GameArtifact? {
        val match = GAME_ARTIFACT.matchEntire(name) ?: return null
        return GameArtifact(
            packageName = match.groupValues[1],
            versionCode = match.groupValues[3].toLongOrNull() ?: return null
        )
    }

    private data class LauncherArtifact(val testChannel: Boolean, val build: Long, val flavor: String)
    private data class GameArtifact(val packageName: String, val versionCode: Long)
    private data class CacheLimit(val maxFiles: Int, val maxBytes: Long)

    private class MutableCleanupResult {
        private var deletedFiles = 0
        private var reclaimedBytes = 0L
        private var recoveredFiles = 0

        fun recordDeleted(bytes: Long) {
            deletedFiles++
            reclaimedBytes += bytes.coerceAtLeast(0L)
        }

        fun recordRecovered() {
            recoveredFiles++
        }

        fun snapshot() = StorageCleanupResult(deletedFiles, reclaimedBytes, recoveredFiles)
    }

    companion object {
        private const val LAUNCHER_DOWNLOADS = "launcher-updates"
        private const val GAME_DOWNLOADS = "game-downloads"
        private const val GAME_VALIDATION_CACHE = "game-apk-validation"
        private const val MENUS = "menus"
        private const val IDENTITY_SHELLS = "identity-shells"
        private const val IDENTITY_GAME_PAYLOAD = "game.apks"
        // Retain the established on-disk name while treating it as the generic direct-patch store.
        private const val DIRECT_PATCHES = "soul-knight-patches"
        private const val PATCH_SIGNED_DIRECTORY = "signed"
        private const val PATCH_UNSIGNED_BASE = "base-unsigned.apk"
        private const val PART_SUFFIX = ".part"
        private const val NEXT_SUFFIX = ".next"
        private val PACKAGE_NAME = Regex("[A-Za-z0-9_.]{3,200}")
        private val LAUNCHER_ARTIFACT = Regex("launcher-(test-)?([0-9]+)-(root|nonroot)\\.apk")
        private val GAME_ARTIFACT = Regex("([A-Za-z0-9_.]{3,200})-([a-z0-9][a-z0-9-]{0,63})-([0-9]+)\\.(apk|apks)")
        private val TRANSACTION_TARGETS = listOf(
            "libmenu_native.so",
            "classes.dex",
            "config.json",
            "${com.moodtools.hub.modules.ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE}"
        )
        private val CACHE_LIMITS = mapOf(
            "launcher-module-icons" to CacheLimit(256, 32L * 1024L * 1024L),
            "module-changelogs" to CacheLimit(256, 16L * 1024L * 1024L),
            "module-features" to CacheLimit(256, 8L * 1024L * 1024L)
        )
    }
}

internal data class StorageCleanupResult(
    val deletedFiles: Int,
    val reclaimedBytes: Long,
    val recoveredFiles: Int
) {
    operator fun plus(other: StorageCleanupResult) = StorageCleanupResult(
        deletedFiles + other.deletedFiles,
        reclaimedBytes + other.reclaimedBytes,
        recoveredFiles + other.recoveredFiles
    )
}
