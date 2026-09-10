package com.moodtools.hub.networking

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.moodtools.hub.modules.CatalogModule
import com.moodtools.hub.modules.GameInstallSource
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class PlayStoreVersionResult(
    val packageName: String,
    val version: String?,
    val versionCode: Long?,
    val listingUpdatedAtEpochSeconds: Long?,
    val updateAvailable: Boolean?,
    val checkedAtEpochSeconds: Long,
    val stale: Boolean
)

data class PlayStoreBuildObservation(
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val installerPackageName: String,
    val signingCertificateSha256: String
) {
    val key: String get() = "$packageName|$version|$versionCode"
}

data class PlayStoreObservationResponse(
    val status: PlayStoreVersionResult,
    val acknowledged: Boolean
)

internal fun shouldReportPlayStoreBuild(
    observation: PlayStoreBuildObservation,
    knownVersion: String?,
    knownVersionCode: Long?,
    updateAvailable: Boolean?,
    acknowledgedKey: String?
): Boolean = acknowledgedKey != observation.key && knownVersion == observation.version &&
    updateAvailable == true && observation.versionCode > (knownVersionCode ?: 0L)

class PlayStoreVersionClient(private val context: Context? = null) {
    fun load(packageNames: Set<String>): Map<String, PlayStoreVersionResult> {
        require(packageNames.size <= MAX_BATCH_PACKAGES && packageNames.all(PACKAGE_PATTERN::matches)) {
            "Invalid Play Store package names"
        }
        if (packageNames.isEmpty()) return emptyMap()
        val connection = open("${ModuleCatalogClient.BASE_URL}/api/launcher-play-store-versions").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            val request = JSONObject().put("packageNames", JSONArray(packageNames.sorted()))
            connection.outputStream.use { output ->
                output.write(request.toString().toByteArray(Charsets.UTF_8))
            }
            require(connection.responseCode in 200..299) {
                "Play Store batch request failed: ${connection.responseCode}"
            }
            val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            parsePlayStoreVersionResults(packageNames, body)
        } finally {
            connection.disconnect()
        }
    }

    fun load(packageName: String): PlayStoreVersionResult? {
        if (!PACKAGE_PATTERN.matches(packageName)) return null
        val connection = open("${ModuleCatalogClient.BASE_URL}/api/launcher-play-store-version/$packageName")
        return try {
            if (connection.responseCode !in 200..299) return null
            val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            parsePlayStoreVersionResult(packageName, body)
        } finally {
            connection.disconnect()
        }
    }

    fun installedObservation(module: CatalogModule): PlayStoreBuildObservation? {
        val appContext = context ?: return null
        val expectedSigner = (module.installSource as? GameInstallSource.DirectDownload)
            ?.signingCertificateSha256 ?: return null
        return runCatching {
            val packageManager = appContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(
                module.config.packageName,
                PackageManager.GET_META_DATA
            )
            if (applicationInfo.metaData?.getBoolean(IDENTITY_SHELL_METADATA, false) == true) return null
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(module.config.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(module.config.packageName)
            }
            if (installer != PLAY_STORE_PACKAGE) return null
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
            }
            val packageInfo = packageManager.getPackageInfo(module.config.packageName, flags)
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo ?: return null
                if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners.toList()
                else signingInfo.signingCertificateHistory.toList()
            } else {
                @Suppress("DEPRECATION") packageInfo.signatures?.toList().orEmpty()
            }
            val signer = signatures.asSequence()
                .map { signature -> sha256(signature.toByteArray()) }
                .firstOrNull { digest -> digest == expectedSigner } ?: return null
            val version = packageInfo.versionName?.trim()?.takeIf(VERSION_PATTERN::matches) ?: return null
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
            }
            if (versionCode <= 0L) return null
            PlayStoreBuildObservation(module.config.packageName, version, versionCode, installer, signer)
        }.getOrNull()
    }

    fun report(observation: PlayStoreBuildObservation): PlayStoreObservationResponse? {
        val connection = open("${ModuleCatalogClient.BASE_URL}/api/launcher-play-store-observation").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            val request = JSONObject()
                .put("packageName", observation.packageName)
                .put("version", observation.version)
                .put("versionCode", observation.versionCode)
                .put("installerPackageName", observation.installerPackageName)
                .put("signingCertificateSha256", observation.signingCertificateSha256)
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return null
            val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            PlayStoreObservationResponse(
                status = parsePlayStoreVersionResult(observation.packageName, body) ?: return null,
                acknowledged = body.optBoolean("acknowledged", false)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun open(address: String): HttpURLConnection {
        val url = URL(address)
        require(url.protocol == "https" && url.host == HOST)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }
    }

    companion object {
        private const val HOST = "jester.moodtools.workers.dev"
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
        private const val IDENTITY_SHELL_METADATA = "com.moodtools.identity_shell"
        private const val MAX_BATCH_PACKAGES = 2_000
        private val PACKAGE_PATTERN = Regex("^[A-Za-z0-9_.]{3,200}$")

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

internal fun parsePlayStoreVersionResults(
    expectedPackageNames: Set<String>,
    body: JSONObject
): Map<String, PlayStoreVersionResult> {
    require(body.optBoolean("ok", false) && body.optInt("schema") == 1) {
        "Invalid Play Store batch response"
    }
    val results = body.getJSONArray("results")
    require(results.length() <= expectedPackageNames.size)
    return buildMap {
        for (index in 0 until results.length()) {
            val item = results.getJSONObject(index)
            val packageName = item.optString("packageName")
            require(packageName in expectedPackageNames && !containsKey(packageName))
            put(packageName, requireNotNull(parsePlayStoreVersionResult(packageName, item)))
        }
    }
}

internal fun parsePlayStoreVersionResult(
    expectedPackageName: String,
    body: JSONObject
): PlayStoreVersionResult? {
    if (!body.optBoolean("ok", false)) return null
    val responsePackage = body.optString("packageName")
    val version = if (body.has("version") && !body.isNull("version")) {
        body.getString("version").trim().takeIf(String::isNotEmpty)
    } else null
    val versionCode = if (body.has("versionCode") && !body.isNull("versionCode")) {
        body.getLong("versionCode").takeIf { it > 0L } ?: return null
    } else null
    val listingUpdatedAt = body.optLong("listingUpdatedAt", 0L).takeIf { it > 0L }
    val updateAvailable = if (body.has("updateAvailable") && !body.isNull("updateAvailable")) {
        body.getBoolean("updateAvailable")
    } else null
    val checkedAt = body.optLong("checkedAt", 0L)
    if (responsePackage != expectedPackageName ||
        (version != null && !VERSION_PATTERN.matches(version)) ||
        (version == null && listingUpdatedAt == null) || checkedAt <= 0L
    ) {
        return null
    }
    return PlayStoreVersionResult(
        packageName = responsePackage,
        version = version,
        versionCode = versionCode,
        listingUpdatedAtEpochSeconds = listingUpdatedAt,
        updateAvailable = updateAvailable,
        checkedAtEpochSeconds = checkedAt,
        stale = body.optBoolean("stale", false)
    )
}

private val VERSION_PATTERN = Regex("^[0-9][0-9A-Za-z._()+ -]{0,63}$")
