package com.moodtools.hub.networking

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PlayStoreVersionResult(
    val packageName: String,
    val version: String,
    val checkedAtEpochSeconds: Long
)

class PlayStoreVersionClient {
    fun load(packageName: String): PlayStoreVersionResult? {
        if (!PACKAGE_PATTERN.matches(packageName)) return null
        val connection = open("${ModuleCatalogClient.BASE_URL}/api/launcher-play-store-version/$packageName")
        return try {
            if (connection.responseCode !in 200..299) return null
            val body = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (!body.optBoolean("ok", false)) return null
            val responsePackage = body.optString("packageName")
            val version = body.optString("version").trim()
            val checkedAt = body.optLong("checkedAt", 0L)
            if (responsePackage != packageName || !VERSION_PATTERN.matches(version) || checkedAt <= 0L) {
                return null
            }
            PlayStoreVersionResult(
                packageName = responsePackage,
                version = version,
                checkedAtEpochSeconds = checkedAt
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
        private val PACKAGE_PATTERN = Regex("^[A-Za-z0-9_.]{3,200}$")
        private val VERSION_PATTERN = Regex("^[0-9][0-9A-Za-z._()+ -]{0,63}$")
    }
}
