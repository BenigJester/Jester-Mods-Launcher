package com.moodtools.hub.networking

import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.moodtools.hub.modules.CatalogModule
import com.moodtools.hub.modules.GameInstallSource
import com.moodtools.hub.modules.GamePackageFormat
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

class GameInstallClient(private val context: Context) {
    private val downloadDirectory = File(context.filesDir, "game-downloads")
    private val storage = SmartStorageManager(context.filesDir, context.cacheDir)

    fun isDownloaded(
        game: CatalogModule,
        onVerificationProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Boolean {
        val source = game.installSource as? GameInstallSource.DirectDownload ?: return false
        storage.onGameReleaseDetected(game.config.packageName, source.versionCode)
        val packageFile = downloadedFile(game)
        if (!packageFile.isFile || packageFile.length() != source.size) return false
        onVerificationProgress(0.04f)
        if (sha256(packageFile, isCancelled) { completed, total ->
                onVerificationProgress(progressBetween(0.04f, 0.78f, completed, total))
            } != source.sha256
        ) return false
        onVerificationProgress(0.82f)
        val valid = runCatching { validatePackage(packageFile, game, source, isCancelled) }.isSuccess
        if (valid) onVerificationProgress(1f)
        return valid
    }

    fun download(
        game: CatalogModule,
        onProgress: (Long, Long) -> Unit,
        onVerificationProgress: (Float) -> Unit = {},
        onDiagnostic: (String) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): File {
        val source = game.installSource as? GameInstallSource.DirectDownload
            ?: error("This game is installed through the Play Store")
        if (isDownloaded(game, isCancelled = isCancelled)) {
            onProgress(source.size, source.size)
            return downloadedFile(game)
        }

        downloadDirectory.mkdirs()
        val target = downloadedFile(game)
        val temporary = File(
            downloadDirectory,
            "${game.config.packageName}-${game.slug}-${source.versionCode}.${source.format.extension}.part"
        )
        try {
            FastFileDownloader.download(
                destination = temporary,
                expectedBytes = source.size,
                openConnection = { range ->
                    open(ModuleCatalogClient.BASE_URL + source.path, game.privateCatalogCapability).apply {
                        range?.let { setRequestProperty("Range", "bytes=${it.first}-${it.last}") }
                    }
                },
                onProgress = onProgress,
                onDiagnostic = { message ->
                    Log.w("JesterMoodsGameInstall", message)
                    onDiagnostic(message)
                },
                isCancelled = isCancelled
            )
            ensureNotCancelled(isCancelled)
            onDiagnostic("Verifying the signed SHA-256 digest")
            onVerificationProgress(0.04f)
            require(sha256(temporary, isCancelled) { completed, total ->
                onVerificationProgress(progressBetween(0.04f, 0.78f, completed, total))
            } == source.sha256) { "Game download verification failed" }
            ensureNotCancelled(isCancelled)
            onDiagnostic("Checking package identity, version, and signing certificate")
            onVerificationProgress(0.82f)
            validatePackage(temporary, game, source, isCancelled)
            ensureNotCancelled(isCancelled)
            onVerificationProgress(0.96f)
            if (target.exists()) require(target.delete()) { "Could not replace the previous game download" }
            require(temporary.renameTo(target)) { "Could not save the game download" }
            onVerificationProgress(1f)
            return target
        } catch (error: Throwable) {
            if (temporary.length() == source.size) temporary.delete()
            throw error
        }
    }

    fun install(
        game: CatalogModule,
        onPreparationProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Int? {
        val source = game.installSource as? GameInstallSource.DirectDownload
            ?: error("This game is installed through the Play Store")
        val packageFile = downloadedFile(game)
        onPreparationProgress(0.04f)
        require(packageFile.isFile && packageFile.length() == source.size &&
            sha256(packageFile, isCancelled) { completed, total ->
                onPreparationProgress(progressBetween(0.04f, 0.48f, completed, total))
            } == source.sha256) { "Download the verified game first" }
        ensureNotCancelled(isCancelled)
        onPreparationProgress(0.52f)
        val installApks = validatePackage(packageFile, game, source, isCancelled)
        ensureNotCancelled(isCancelled)
        onPreparationProgress(0.68f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            require(context.packageManager.canRequestPackageInstalls()) {
                "Allow Jester Mods to install games in Android settings"
            }
        }
        onPreparationProgress(0.74f)

        // A verified standalone APK can be handed directly to Android from the foreground
        // user action. This avoids the PackageInstaller broadcast-to-activity hop that is
        // slow or occasionally suppressed on low-memory/OEM Android builds. Split packages
        // still need a PackageInstaller session so Android can receive every APK atomically.
        if (source.format == GamePackageFormat.APK) {
            ensureNotCancelled(isCancelled)
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.launcher-updates",
                packageFile
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, APK_MIME_TYPE)
                    clipData = ClipData.newRawUri("verified_original_game", apkUri)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
            onPreparationProgress(1f)
            return null
        }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(game.config.packageName)
            setSize(installApks.sumOf { it.size })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                val totalInstallBytes = installApks.sumOf { it.size }.coerceAtLeast(1L)
                var preparedBytes = 0L
                var lastPreparedPercent = -1
                val reportPreparedBytes: (Int) -> Unit = { count ->
                    preparedBytes += count
                    val preparedPercent = ((preparedBytes * 100L) / totalInstallBytes).toInt()
                    if (preparedPercent != lastPreparedPercent || preparedBytes >= totalInstallBytes) {
                        lastPreparedPercent = preparedPercent
                        onPreparationProgress(
                            progressBetween(0.74f, 0.96f, preparedBytes, totalInstallBytes)
                        )
                    }
                }
                if (source.format == GamePackageFormat.APK) {
                    packageFile.inputStream().use { input ->
                        writeApk(session, installApks.single(), input, isCancelled, reportPreparedBytes)
                    }
                } else {
                    ZipFile(packageFile).use { archive ->
                        installApks.forEach { installApk ->
                            ensureNotCancelled(isCancelled)
                            val entry = archive.getEntry(installApk.archiveEntry)
                                ?: error("The verified APK set changed before installation")
                            archive.getInputStream(entry).use { input ->
                                writeApk(session, installApk, input, isCancelled, reportPreparedBytes)
                            }
                        }
                    }
                }
                ensureNotCancelled(isCancelled)
                onPreparationProgress(0.98f)
                val callback = Intent(context, GameInstallReceiver::class.java)
                    .setAction(GameInstallReceiver.ACTION_INSTALL_RESULT)
                    .putExtra(GameInstallReceiver.EXTRA_PACKAGE, game.config.packageName)
                    .putExtra(GameInstallReceiver.EXTRA_VERSION_CODE, source.versionCode)
                    .putExtra(GameInstallReceiver.EXTRA_SESSION_ID, sessionId)
                val sender = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                ).intentSender
                session.commit(sender)
                onPreparationProgress(1f)
            }
        } catch (error: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
        return sessionId
    }

    fun isInstalledAtLeast(packageName: String, versionCode: Long): Boolean {
        if (!packageName.matches(PACKAGE_PATTERN) || versionCode <= 0L) return false
        val info = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }.getOrNull() ?: return false
        val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        return installedVersion >= versionCode
    }

    fun clearDownload(packageName: String, versionCode: Long) {
        if (!packageName.matches(PACKAGE_PATTERN) || versionCode <= 0) return
        storage.onGameInstallSucceeded(packageName, versionCode)
    }

    fun cancelInstall(sessionId: Int) {
        if (sessionId < 0) return
        runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
            .onFailure { error ->
                Log.w("JesterMoodsGameInstall", "Could not abandon installer session $sessionId", error)
            }
    }

    private fun downloadedFile(game: CatalogModule): File {
        val source = game.installSource as GameInstallSource.DirectDownload
        return File(
            downloadDirectory,
            "${game.config.packageName}-${game.slug}-${source.versionCode}.${source.format.extension}"
        )
    }

    private fun validatePackage(
        packageFile: File,
        game: CatalogModule,
        source: GameInstallSource.DirectDownload,
        isCancelled: () -> Boolean = { false }
    ): List<InstallApk> = when (source.format) {
        GamePackageFormat.APK -> {
            ensureNotCancelled(isCancelled)
            validateApk(packageFile, game, source)
            listOf(InstallApk(archiveEntry = null, sessionName = "base.apk", size = packageFile.length()))
        }
        GamePackageFormat.APKS -> validateApkSet(packageFile, game, source, isCancelled)
    }

    private fun validateApkSet(
        packageFile: File,
        game: CatalogModule,
        source: GameInstallSource.DirectDownload,
        isCancelled: () -> Boolean
    ): List<InstallApk> {
        val validationDirectory = File(context.cacheDir, "game-apk-validation").apply { mkdirs() }
        return try {
            ZipFile(packageFile).use { archive ->
                ensureNotCancelled(isCancelled)
                val entries = archive.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                    .toList()
                require(entries.isNotEmpty()) { "The APK set does not contain any Android packages" }
                require(entries.size <= MAX_APKS_ENTRIES) { "The APK set contains too many package splits" }
                val baseEntries = entries.filter { entry ->
                    val name = entry.name.substringAfterLast('/')
                    name.equals("base.apk", ignoreCase = true) ||
                        name.equals("base-master.apk", ignoreCase = true)
                }
                require(baseEntries.size == 1) { "The APK set must contain exactly one base APK" }
                val totalSize = entries.fold(0L) { total, entry ->
                    require(entry.size > 0L) { "The APK set contains an empty or invalid split" }
                    Math.addExact(total, entry.size)
                }
                require(totalSize <= MAX_EXTRACTED_APKS_BYTES) { "The APK set is too large after extraction" }

                // A configuration split is not a standalone Android app, and some Android builds
                // therefore return null when PackageManager is asked to parse one by itself. The
                // signed catalog SHA-256 authenticates the entire APKS archive, including every
                // split. Validate the base APK's identity and signer here; PackageInstaller then
                // enforces that all streamed splits belong to the same signed package.
                val baseEntry = baseEntries.single()
                val extractedBase = File.createTempFile("base-", ".apk", validationDirectory)
                try {
                    archive.getInputStream(baseEntry).use { input ->
                        extractedBase.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureNotCancelled(isCancelled)
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    require(extractedBase.length() == baseEntry.size) {
                        "The APK set base package could not be extracted safely"
                    }
                    validateApk(extractedBase, game, source)
                    ensureNotCancelled(isCancelled)
                } finally {
                    extractedBase.delete()
                }

                entries.mapIndexed { index, entry ->
                    val isBase = entry.name == baseEntry.name
                    val archiveName = entry.name.substringAfterLast('/').removeSuffix(".apk")
                    InstallApk(
                        archiveEntry = entry.name,
                        sessionName = if (isBase) "base.apk" else "split-$index-${safeSessionName(archiveName)}.apk",
                        size = entry.size
                    )
                }
            }
        } finally {
            if (validationDirectory.listFiles().isNullOrEmpty()) validationDirectory.delete()
        }
    }

    private fun validateApk(
        apk: File,
        game: CatalogModule,
        source: GameInstallSource.DirectDownload
    ) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("The downloaded file is not a valid Android app")
        require(archive.packageName == game.config.packageName) {
            "The downloaded app belongs to a different game"
        }
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION") archive.versionCode.toLong()
        }
        require(archiveVersion == source.versionCode) {
            "The downloaded game version does not match its signed catalog entry"
        }
        require(source.signingCertificateSha256 in signingDigests(archive)) {
            "The downloaded game has an unexpected signing certificate"
        }
    }

    private fun writeApk(
        session: PackageInstaller.Session,
        installApk: InstallApk,
        input: InputStream,
        isCancelled: () -> Boolean,
        onBytesWritten: (Int) -> Unit = {}
    ) {
        session.openWrite(installApk.sessionName, 0L, installApk.size).use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                ensureNotCancelled(isCancelled)
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                onBytesWritten(count)
            }
            session.fsync(output)
        }
    }

    private fun ensureNotCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw GameInstallCancelledException()
    }

    private fun safeSessionName(splitName: String): String = splitName
        .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        .take(120)

    private fun signingDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: error("Game signing information is unavailable")
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        return signatures.orEmpty().mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    private fun sha256(
        file: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val total = file.length().coerceAtLeast(1L)
        var completed = 0L
        var lastReportedPercent = -1
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                ensureNotCancelled(isCancelled)
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                completed += count
                val completedPercent = ((completed * 100L) / total).toInt()
                if (completedPercent != lastReportedPercent || completed >= total) {
                    lastReportedPercent = completedPercent
                    onProgress(completed, total)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun progressBetween(
        start: Float,
        end: Float,
        completed: Long,
        total: Long
    ): Float {
        val fraction = if (total > 0L) {
            completed.toFloat().div(total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        return start + ((end - start) * fraction)
    }

    private fun open(address: String, capability: String?): HttpURLConnection {
        val url = URL(address)
        require(url.protocol == "https" && url.host == HOST)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.android.package-archive")
            setRequestProperty("Accept-Encoding", "identity")
            capability?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }

    companion object {
        private const val HOST = "jester.moodtools.workers.dev"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val MAX_APKS_ENTRIES = 256
        private const val MAX_EXTRACTED_APKS_BYTES = 4L * 1024L * 1024L * 1024L
        private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_.]{3,200}")
    }

    private data class InstallApk(
        val archiveEntry: String?,
        val sessionName: String,
        val size: Long
    )
}

internal class GameInstallCancelledException : java.io.IOException("Game installation cancelled")
