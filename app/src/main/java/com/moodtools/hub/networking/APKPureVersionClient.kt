package com.moodtools.hub.networking

import org.json.JSONArray
import org.json.JSONObject
import com.moodtools.hub.modules.GamePackageFormat
import java.net.HttpURLConnection
import java.net.URL

data class APKPureDownload(
    val url: String,
    val version: String,
    val versionCode: Long,
    val size: Long,
    val format: GamePackageFormat,
    val supportedAbis: Set<String>
)

data class APKPureVersionResult(
    val packageName: String,
    val version: String?,
    val versionCode: Long?,
    val listingUpdatedAtEpochSeconds: Long?,
    val updateAvailable: Boolean?,
    val checkedAtEpochSeconds: Long,
    val stale: Boolean,
    val download: APKPureDownload?
)

class APKPureVersionClient {
    /** Probe exact catalog builds, never substitute APKPure's latest release. */
    fun findSupportedDownload(
        module: com.moodtools.hub.modules.ModuleConfig,
        deviceAbis: List<String>,
        sdk: Int
    ): APKPureDownload? {
        val versions = module.supportedVersions.toList()
        val codes = module.supportedVersionCodes.toList()
        for (index in versions.indices.reversed()) {
            val code = codes.getOrNull(index) ?: continue
            val download = runCatching {
                val versionPath = java.net.URLEncoder.encode(versions[index], "UTF-8").replace("+", "%20")
                val page = (URL("https://apkpure.net/app/${module.packageName}/download/$versionPath")
                    .openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    setRequestProperty("User-Agent", APKPURE_USER_AGENT)
                }
                val html = try {
                    if (page.responseCode !in 200..299 || page.url.protocol != "https" ||
                        page.url.host !in setOf("apkpure.net", "apkpure.com")) return@runCatching null
                    page.inputStream.bufferedReader().use { reader ->
                        val text = StringBuilder()
                        val buffer = CharArray(4096)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            require(text.length + count <= 2 * 1024 * 1024)
                            text.append(buffer, 0, count)
                        }
                        text.toString()
                    }
                } finally { page.disconnect() }
                for (address in supportedAPKPureLinks(html, module.packageName, code,
                    deviceAbis.filter { it in module.supportedAbis }, sdk)) {
                    val gateway = (URL(address).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5_000
                        readTimeout = 5_000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", APKPURE_USER_AGENT)
                        setRequestProperty("Accept-Encoding", "identity")
                    }
                    try {
                        if (gateway.responseCode !in 300..399) continue
                        val target = URL(gateway.getHeaderField("Location") ?: continue)
                        if (runCatching { requireAPKPureDownloadTarget(target) }.isFailure) continue
                        val query = target.query.orEmpty().split('&').map { it.split('=', limit = 2) }
                            .filter { it.size == 2 }.associate {
                                it[0] to java.net.URLDecoder.decode(it[1], "UTF-8")
                            }
                        val size = query["full_size"]?.toLongOrNull() ?: continue
                        val format = if (URL(address).path.startsWith("/b/APK/")) GamePackageFormat.APK else GamePackageFormat.APKS
                        val extension = if (format == GamePackageFormat.APK) "apk" else "xapk"
                        if (query["package_name"] != module.packageName || size !in 1..MAX_GAME_BYTES ||
                            query["filename"]?.endsWith("_${versions[index]}_APKPure.$extension") != true) continue
                        val abi = URL(address).query.substringAfter("&nc=").substringBefore('&')
                        return@runCatching APKPureDownload(address, versions[index], code, size, format, setOf(abi))
                    } finally { gateway.disconnect() }
                }
                null
            }.getOrNull()
            if (download != null) return download
        }
        return null
    }

    fun load(packageNames: Set<String>): Map<String, APKPureVersionResult> {
        require(packageNames.size <= MAX_BATCH_PACKAGES && packageNames.all(PACKAGE_PATTERN::matches)) {
            "Invalid APKPure package names"
        }
        if (packageNames.isEmpty()) return emptyMap()
        val connection = open("${ModuleCatalogClient.BASE_URL}/api/launcher-apkpure-versions").apply {
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
                "APKPure batch request failed: ${connection.responseCode}"
            }
            val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            parseAPKPureVersionResults(packageNames, body)
        } finally {
            connection.disconnect()
        }
    }

    fun load(packageName: String): APKPureVersionResult? {
        if (!PACKAGE_PATTERN.matches(packageName)) return null
        val connection = open("${ModuleCatalogClient.BASE_URL}/api/launcher-apkpure-version/$packageName")
        return try {
            if (connection.responseCode !in 200..299) return null
            val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            parseAPKPureVersionResult(packageName, body)
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
        private const val MAX_BATCH_PACKAGES = 2_000
        private val PACKAGE_PATTERN = Regex("^[A-Za-z0-9_.]{3,200}$")
    }
}

internal fun parseAPKPureVersionResults(
    expectedPackageNames: Set<String>,
    body: JSONObject
): Map<String, APKPureVersionResult> {
    require(body.optBoolean("ok", false) && body.optInt("schema") == 1) {
        "Invalid APKPure batch response"
    }
    val results = body.getJSONArray("results")
    require(results.length() <= expectedPackageNames.size)
    return buildMap {
        for (index in 0 until results.length()) {
            val item = results.getJSONObject(index)
            val packageName = item.optString("packageName")
            require(packageName in expectedPackageNames && !containsKey(packageName))
            put(packageName, requireNotNull(parseAPKPureVersionResult(packageName, item)))
        }
    }
}

internal fun parseAPKPureVersionResult(
    expectedPackageName: String,
    body: JSONObject
): APKPureVersionResult? {
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
    return APKPureVersionResult(
        packageName = responsePackage,
        version = version,
        versionCode = versionCode,
        listingUpdatedAtEpochSeconds = listingUpdatedAt,
        updateAvailable = updateAvailable,
        checkedAtEpochSeconds = checkedAt,
        stale = body.optBoolean("stale", false),
        download = parseAPKPureDownload(expectedPackageName, body.optJSONObject("download"))
    )
}

internal fun parseAPKPureDownload(
    expectedPackageName: String,
    body: JSONObject?
): APKPureDownload? {
    if (body == null) return null
    val version = body.optString("version").trim()
    val versionCode = body.optLong("versionCode", 0L)
    val size = body.optLong("size", 0L)
    val format = when (body.optString("format").lowercase()) {
        "apk" -> GamePackageFormat.APK
        "xapk" -> GamePackageFormat.APKS
        else -> return null
    }
    val supportedAbis = body.optJSONArray("supportedAbis")?.let { values ->
        buildSet {
            for (index in 0 until values.length()) add(values.optString(index))
        }.takeIf { it.isNotEmpty() && it.size == values.length() && it.all(SUPPORTED_ABIS::contains) }
    } ?: return null
    if (!VERSION_PATTERN.matches(version) || versionCode <= 0L || size !in 1..MAX_GAME_BYTES) return null
    val url = runCatching { URL(body.getString("url")) }.getOrNull() ?: return null
    val expectedFormat = if (format == GamePackageFormat.APK) "APK" else "XAPK"
    val expectedAbi = supportedAbis.singleOrNull() ?: return null
    val minimumSdk = url.query
        ?.split('&')
        ?.singleOrNull { it.startsWith("sv=") }
        ?.substringAfter('=')
        ?.toIntOrNull()
        ?.takeIf { it in 1..100 }
        ?: return null
    if (url.protocol != "https" || url.host != APKPURE_DOWNLOAD_HOST ||
        url.path != "/b/$expectedFormat/$expectedPackageName" ||
        url.query != "versionCode=$versionCode&nc=$expectedAbi&sv=$minimumSdk") return null
    return APKPureDownload(url.toString(), version, versionCode, size, format, supportedAbis)
}

private val VERSION_PATTERN = Regex("^[0-9][0-9A-Za-z._()+ -]{0,63}$")
private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a")
private const val APKPURE_DOWNLOAD_HOST = "d.apkpure.net"
private const val MAX_GAME_BYTES = 2L * 1024L * 1024L * 1024L

/** Preserve the exact variant's minimum SDK rather than guessing from the device API. */
internal fun supportedAPKPureLinks(html: String, packageName: String, versionCode: Long,
    deviceAbis: List<String>, sdk: Int): List<String> {
    val links = Regex("https://d\\.apkpure\\.net/b/(?:APK|XAPK)/${Regex.escape(packageName)}" +
        "\\?versionCode=$versionCode&nc=(arm64-v8a|armeabi-v7a)&sv=([0-9]{1,3})(?=[\"'<>\\s]|$)")
        .findAll(html.replace("&amp;", "&"))
        .filter { it.groupValues[1] in deviceAbis && it.groupValues[2].toInt() in 1..minOf(sdk, 100) }
        .sortedBy { deviceAbis.indexOf(it.groupValues[1]) }
        .map { it.value }.distinct().toList()
    return links
}

private const val APKPURE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/127 Mobile Safari/537.36"
