package com.moodtools.hub.networking

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PlayStoreVersionResult(
    val packageName: String,
    val version: String?,
    val listingUpdatedAtEpochSeconds: Long?,
    val updateAvailable: Boolean?,
    val checkedAtEpochSeconds: Long,
    val stale: Boolean
)

class PlayStoreVersionClient {
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
        listingUpdatedAtEpochSeconds = listingUpdatedAt,
        updateAvailable = updateAvailable,
        checkedAtEpochSeconds = checkedAt,
        stale = body.optBoolean("stale", false)
    )
}

private val VERSION_PATTERN = Regex("^[0-9][0-9A-Za-z._()+ -]{0,63}$")
