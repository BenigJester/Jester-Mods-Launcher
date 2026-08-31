package com.moodtools.hub.networking

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class LauncherRelease(
    val build: Long,
    val version: String,
    val notes: String?,
    val path: String,
    val sha256: String,
    val size: Long,
    val testChannel: Boolean = false
)

data class LauncherChangelogEntry(
    val build: Long,
    val version: String,
    val notes: String,
    val publishedAtEpochSeconds: Long
)

internal fun isTrustedStableLauncherDownloadPath(path: String, build: Long, flavor: String): Boolean {
    val canonicalFile = when (flavor) {
        "root" -> "Jester-Moods-Root.apk"
        "nonroot" -> "Jester-Moods-NonRoot.apk"
        else -> return false
    }
    return path == "/download/jester-moods-launcher?file=$canonicalFile" ||
        path == "/api/launcher-download/$build/$flavor.apk"
}

class LauncherUpdateClient(private val context: Context) {
    private val updateDirectory = File(context.filesDir, "launcher-updates")
    private val storage = SmartStorageManager(context.filesDir, context.cacheDir)

    fun installedBuild(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    fun refresh(testChannel: Boolean = false): LauncherRelease {
        val endpoint = if (testChannel) "/api/launcher-test-release/${flavor()}" else "/api/launcher-release"
        val connection = open(BASE_URL + endpoint, "application/json")
        return try {
            require(connection.responseCode in 200..299) {
                "Launcher update check failed: ${connection.responseCode}"
            }
            val envelope = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val release = parse(envelope, testChannel)
            runCatching {
                check(updateDirectory.mkdirs() || updateDirectory.isDirectory) {
                    "Could not prepare launcher update storage"
                }
                cachedEnvelope(testChannel).writeText(envelope.toString())
                storage.onLauncherReleaseDetected(release.build, release.testChannel, flavor())
            }.onFailure { Log.w(TAG, "Could not persist launcher release cache", it) }
            release
        } finally {
            connection.disconnect()
        }
    }

    fun loadCached(testChannel: Boolean = false): LauncherRelease? {
        val cache = cachedEnvelope(testChannel)
        if (!cache.isFile) return null
        return runCatching { parse(JSONObject(cache.readText()), testChannel) }
            .onSuccess { storage.onLauncherReleaseDetected(it.build, it.testChannel, flavor()) }
            .getOrNull()
    }

    fun refreshChangelog(): List<LauncherChangelogEntry> {
        val connection = open("$BASE_URL/api/launcher-changelog", "application/json")
        return try {
            require(connection.responseCode in 200..299) {
                "Launcher changelog request failed: ${connection.responseCode}"
            }
            val envelope = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val entries = parseChangelog(envelope)
            runCatching {
                changelogCache().writeText(envelope.toString())
            }.onFailure { Log.w(TAG, "Could not persist launcher changelog cache", it) }
            entries
        } finally {
            connection.disconnect()
        }
    }

    fun loadCachedChangelog(): List<LauncherChangelogEntry>? {
        val cache = changelogCache()
        if (!cache.isFile) return null
        return runCatching { parseChangelog(JSONObject(cache.readText())) }.getOrNull()
    }

    fun isDownloaded(release: LauncherRelease): Boolean {
        val apk = downloadedFile(release)
        return apk.isFile && apk.length() == release.size &&
            sha256(apk) == release.sha256 && runCatching { validateApk(apk, release) }.isSuccess
    }

    fun download(release: LauncherRelease, onProgress: (Long, Long) -> Unit): File {
        if (isDownloaded(release)) {
            onProgress(release.size, release.size)
            return downloadedFile(release)
        }
        updateDirectory.mkdirs()
        val target = downloadedFile(release)
        val temporary = File(
            updateDirectory,
            "launcher-${if (release.testChannel) "test-" else ""}${release.build}-${flavor()}.apk.part"
        )
        try {
            FastFileDownloader.download(
                destination = temporary,
                expectedBytes = release.size,
                openConnection = { range ->
                    open(BASE_URL + release.path, "application/vnd.android.package-archive").apply {
                        range?.let { setRequestProperty("Range", "bytes=${it.first}-${it.last}") }
                    }
                },
                onProgress = onProgress,
                onDiagnostic = { message -> Log.w(TAG, message) }
            )
            require(sha256(temporary) == release.sha256) { "Launcher download verification failed" }
            validateApk(temporary, release)
            if (target.exists()) require(target.delete()) { "Could not replace the previous launcher download" }
            require(temporary.renameTo(target)) { "Could not save the launcher update" }
            return target
        } catch (error: Throwable) {
            if (temporary.length() == release.size) temporary.delete()
            throw error
        }
    }

    fun install(release: LauncherRelease) {
        val apk = downloadedFile(release)
        require(isDownloaded(release)) { "Download the verified launcher update first" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            require(context.packageManager.canRequestPackageInstalls()) {
                "Allow Jester Mods to install app updates in Android settings"
            }
        }
        // A PackageInstaller session has to stage the APK and then deliver a broadcast before
        // its confirmation UI can open. That extra hop is noticeably slow on low-end devices,
        // and some OEMs restrict the receiver's attempt to bring the installer to the front.
        // The verified single APK can instead be handed straight to Android's installer from
        // the foreground user action. FileProvider grants access to this file only for the
        // lifetime of the installer intent; the rest of internal launcher storage stays private.
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.launcher-updates",
            apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                clipData = ClipData.newRawUri("verified_launcher_update", apkUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    fun markInstallSucceeded(build: Long) {
        storage.onLauncherInstallSucceeded(build)
    }

    fun downloadedFile(release: LauncherRelease): File = File(
        updateDirectory,
        "launcher-${if (release.testChannel) "test-" else ""}${release.build}-${flavor()}.apk"
    )

    private fun cachedEnvelope(testChannel: Boolean) = File(
        updateDirectory,
        if (testChannel) "test-release-${flavor()}.json" else "release.json"
    )

    private fun changelogCache() = File(context.filesDir, "launcher-changelog.json")

    private fun parseChangelog(envelope: JSONObject): List<LauncherChangelogEntry> {
        val payload = SignedEnvelopeVerifier.payload(envelope)
        require(payload.getInt("schema") == 1)
        require(payload.getString("audience") == "moodtools-standalone-launcher-changelog")
        val currentBuild = payload.getLong("currentBuild").also { require(it > 0) }
        val source = payload.getJSONArray("entries")
        require(source.length() in 1..MAX_CHANGELOG_ENTRIES)
        var previousBuild = Long.MAX_VALUE
        var previousPublishedAt = Long.MAX_VALUE
        var totalCharacters = 0
        return buildList {
            for (index in 0 until source.length()) {
                val item = source.getJSONObject(index)
                val build = item.getLong("build")
                val version = item.getString("version")
                val notes = item.getString("notes")
                val publishedAt = item.getLong("publishedAt")
                totalCharacters += notes.length
                require(build > 0 && build < previousBuild)
                require(version.isNotBlank() && version.length <= 64)
                require(notes.length <= MAX_CHANGELOG_ENTRY_CHARACTERS)
                require(totalCharacters <= MAX_CHANGELOG_CHARACTERS)
                require(publishedAt < previousPublishedAt)
                require(publishedAt in MIN_CHANGELOG_EPOCH_SECONDS..MAX_CHANGELOG_EPOCH_SECONDS)
                add(LauncherChangelogEntry(build, version, notes, publishedAt))
                previousBuild = build
                previousPublishedAt = publishedAt
            }
        }.also { require(it.first().build == currentBuild) }
    }

    private fun parse(envelope: JSONObject, testChannel: Boolean): LauncherRelease {
        val payload = SignedEnvelopeVerifier.payload(envelope)
        require(payload.getInt("schema") == 1) { "Unsupported launcher release format" }
        val expectedAudience = if (testChannel) "moodtools-standalone-launcher-test" else "moodtools-standalone-launcher"
        require(payload.getString("audience") == expectedAudience) {
            "This release is not for the standalone launcher"
        }
        val build = payload.getLong("build").also { require(it > 0) }
        val version = payload.getString("version").also { require(it.isNotBlank() && it.length <= 64) }
        if (testChannel) require(payload.getString("flavor") == flavor()) { "This test release is for another launcher mode" }
        val file = if (testChannel) payload.getJSONObject("file") else payload.getJSONObject("files").getJSONObject(flavor())
        val path = file.getString("path")
        val activeFlavor = flavor()
        val trustedPath = if (testChannel) {
            path == "/api/launcher-test-download/$build/$activeFlavor.apk"
        } else {
            isTrustedStableLauncherDownloadPath(path, build, activeFlavor)
        }
        require(trustedPath) { "Invalid launcher download path" }
        val hash = file.getString("sha256").lowercase()
        require(hash.matches(SHA256_PATTERN)) { "Invalid launcher download hash" }
        val size = file.getLong("size").also { require(it in 1..MAX_APK_BYTES) }
        return LauncherRelease(
            build = build,
            version = version,
            notes = payload.optString("notes").trim().takeIf(String::isNotEmpty)?.take(8_000),
            path = path,
            sha256 = hash,
            size = size,
            testChannel = testChannel
        )
    }

    private fun validateApk(apk: File, release: LauncherRelease) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("The downloaded file is not a valid Android app")
        require(archive.packageName == context.packageName) { "The update belongs to a different launcher" }
        val archiveBuild = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else @Suppress("DEPRECATION") archive.versionCode.toLong()
        require(archiveBuild == release.build) { "The update build does not match its signed release" }
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        require(signingDigests(archive) == signingDigests(current)) {
            "The update is not signed by this launcher"
        }
    }

    private fun signingDigests(info: android.content.pm.PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: error("App signing information is unavailable")
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        return signatures.orEmpty().mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
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

    private fun flavor(): String = when (com.moodtools.hub.BuildConfig.FLAVOR) {
        "root" -> "root"
        "nonroot" -> "nonroot"
        else -> error("Unsupported launcher build flavor")
    }

    private fun open(address: String, accept: String): HttpURLConnection {
        val url = URL(address)
        require(url.protocol == "https" && url.host == HOST)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Encoding", "identity")
        }
    }

    companion object {
        private const val BASE_URL = "https://jester.moodtools.workers.dev"
        private const val TAG = "JesterMoodsLauncherUpdate"
        private const val HOST = "jester.moodtools.workers.dev"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val MAX_APK_BYTES = 250L * 1024L * 1024L
        private const val MAX_CHANGELOG_ENTRIES = 50
        private const val MAX_CHANGELOG_ENTRY_CHARACTERS = 8_000
        private const val MAX_CHANGELOG_CHARACTERS = 128 * 1024
        private const val MIN_CHANGELOG_EPOCH_SECONDS = 1_577_836_800L
        private const val MAX_CHANGELOG_EPOCH_SECONDS = 4_102_444_800L
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
