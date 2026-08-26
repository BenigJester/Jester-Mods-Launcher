package com.moodtools.hub.networking

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
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

    fun isDownloaded(game: CatalogModule): Boolean {
        val source = game.installSource as? GameInstallSource.DirectDownload ?: return false
        storage.onGameReleaseDetected(game.config.packageName, source.versionCode)
        val packageFile = downloadedFile(game)
        return packageFile.isFile && packageFile.length() == source.size &&
            sha256(packageFile) == source.sha256 &&
            runCatching { validatePackage(packageFile, game, source) }.isSuccess
    }

    fun download(game: CatalogModule, onProgress: (Long, Long) -> Unit): File {
        val source = game.installSource as? GameInstallSource.DirectDownload
            ?: error("This game is installed through the Play Store")
        if (isDownloaded(game)) {
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
                    open(ModuleCatalogClient.BASE_URL + source.path).apply {
                        range?.let { setRequestProperty("Range", "bytes=${it.first}-${it.last}") }
                    }
                },
                onProgress = onProgress
            )
            require(sha256(temporary) == source.sha256) { "Game download verification failed" }
            validatePackage(temporary, game, source)
            if (target.exists()) require(target.delete()) { "Could not replace the previous game download" }
            require(temporary.renameTo(target)) { "Could not save the game download" }
            return target
        } finally {
            temporary.delete()
        }
    }

    fun install(game: CatalogModule) {
        val source = game.installSource as? GameInstallSource.DirectDownload
            ?: error("This game is installed through the Play Store")
        val packageFile = downloadedFile(game)
        require(packageFile.isFile && packageFile.length() == source.size &&
            sha256(packageFile) == source.sha256) { "Download the verified game first" }
        val installApks = validatePackage(packageFile, game, source)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            require(context.packageManager.canRequestPackageInstalls()) {
                "Allow Jester Moods to install games in Android settings"
            }
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
                if (source.format == GamePackageFormat.APK) {
                    packageFile.inputStream().use { input ->
                        writeApk(session, installApks.single(), input)
                    }
                } else {
                    ZipFile(packageFile).use { archive ->
                        installApks.forEach { installApk ->
                            val entry = archive.getEntry(installApk.archiveEntry)
                                ?: error("The verified APK set changed before installation")
                            archive.getInputStream(entry).use { input ->
                                writeApk(session, installApk, input)
                            }
                        }
                    }
                }
                val callback = Intent(context, GameInstallReceiver::class.java)
                    .setAction(GameInstallReceiver.ACTION_INSTALL_RESULT)
                    .putExtra(GameInstallReceiver.EXTRA_PACKAGE, game.config.packageName)
                    .putExtra(GameInstallReceiver.EXTRA_VERSION_CODE, source.versionCode)
                val sender = PendingIntent.getBroadcast(
                    context,
                    game.config.packageName.hashCode() xor source.versionCode.hashCode(),
                    callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                ).intentSender
                session.commit(sender)
            }
        } catch (error: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }

    fun clearDownload(packageName: String, versionCode: Long) {
        if (!packageName.matches(PACKAGE_PATTERN) || versionCode <= 0) return
        storage.onGameInstallSucceeded(packageName, versionCode)
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
        source: GameInstallSource.DirectDownload
    ): List<InstallApk> = when (source.format) {
        GamePackageFormat.APK -> {
            validateApk(packageFile, game, source)
            listOf(InstallApk(archiveEntry = null, sessionName = "base.apk", size = packageFile.length()))
        }
        GamePackageFormat.APKS -> validateApkSet(packageFile, game, source)
    }

    private fun validateApkSet(
        packageFile: File,
        game: CatalogModule,
        source: GameInstallSource.DirectDownload
    ): List<InstallApk> {
        val validationDirectory = File(context.cacheDir, "game-apk-validation").apply { mkdirs() }
        return try {
            ZipFile(packageFile).use { archive ->
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
                        extractedBase.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                    }
                    require(extractedBase.length() == baseEntry.size) {
                        "The APK set base package could not be extracted safely"
                    }
                    validateApk(extractedBase, game, source)
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
        input: InputStream
    ) {
        session.openWrite(installApk.sessionName, 0L, installApk.size).use { output ->
            input.copyTo(output, 64 * 1024)
            session.fsync(output)
        }
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun open(address: String): HttpURLConnection {
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
        }
    }

    companion object {
        private const val HOST = "jester.moodtools.workers.dev"
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
