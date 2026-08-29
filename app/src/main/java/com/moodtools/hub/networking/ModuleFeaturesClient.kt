package com.moodtools.hub.networking

import com.moodtools.hub.modules.CatalogModule
import com.moodtools.hub.modules.ModuleFeatureGroup
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModuleFeaturesClient(private val context: android.content.Context) {
    fun load(module: CatalogModule): List<ModuleFeatureGroup> {
        return runCatching { refresh(module) }.getOrElse {
            loadCached(module) ?: throw it
        }
    }

    private fun refresh(module: CatalogModule): List<ModuleFeatureGroup> {
        val summary = requireNotNull(module.features)
        val connection = open(BASE_URL + summary.path, module.privateCatalogCapability)
        return try {
            require(connection.responseCode in 200..299) {
                "Feature request failed: ${connection.responseCode}"
            }
            val envelope = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val parsed = parse(module, envelope)
            runCatching {
                cacheFile(module).parentFile?.let {
                    check(it.mkdirs() || it.isDirectory) { "Could not prepare feature cache" }
                }
                cacheFile(module).writeText(envelope.toString())
            }.onFailure { Log.w(TAG, "Could not persist module feature cache", it) }
            parsed
        } finally {
            connection.disconnect()
        }
    }

    private fun loadCached(module: CatalogModule): List<ModuleFeatureGroup>? {
        val file = cacheFile(module)
        if (!file.isFile) return null
        return runCatching { parse(module, JSONObject(file.readText())) }.getOrNull()
    }

    private fun parse(module: CatalogModule, envelope: JSONObject): List<ModuleFeatureGroup> {
        val summary = requireNotNull(module.features)
        val payload = SignedEnvelopeVerifier.payload(envelope)
        require(payload.getInt("schema") == 1)
        require(payload.getString("audience") == "moodtools-standalone-module-features")
        require(payload.getString("slug") == module.slug)
        require(payload.getString("packageName") == module.config.packageName)
        require(payload.getLong("build") == module.build)
        val groups = payload.getJSONArray("groups")
        require(groups.length() in 1..MAX_GROUPS)
        var featureCount = 0
        var characterCount = 0
        val groupTitles = HashSet<String>()
        val featureNames = HashSet<String>()
        val parsed = buildList {
            for (groupIndex in 0 until groups.length()) {
                val group = groups.getJSONObject(groupIndex)
                val title = group.getString("title").trim().also {
                    require(it.isNotEmpty() && it.length <= MAX_GROUP_TITLE_LENGTH)
                    require(groupTitles.add(it.lowercase()))
                    characterCount += it.length
                }
                val features = group.getJSONArray("features")
                require(features.length() in 1..MAX_FEATURES_PER_GROUP)
                add(ModuleFeatureGroup(title, buildList {
                    for (featureIndex in 0 until features.length()) {
                        val feature = features.getString(featureIndex).trim().also {
                            require(it.isNotEmpty() && it.length <= MAX_FEATURE_NAME_LENGTH)
                            require(featureNames.add(it.lowercase()))
                            characterCount += it.length
                        }
                        add(feature)
                        featureCount++
                    }
                }))
            }
        }
        require(featureCount == summary.count && featureCount in 1..MAX_FEATURES)
        require(characterCount <= MAX_CHARACTERS)
        return parsed
    }

    private fun cacheFile(module: CatalogModule) = File(
        File(context.filesDir, "module-features"),
        "${module.slug}-${module.build}.json"
    )

    private fun open(address: String, capability: String?): HttpURLConnection {
        val url = URL(address)
        require(url.protocol == "https" && url.host == HOST)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            capability?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }

    companion object {
        private const val TAG = "JesterMoodsFeatures"
        private const val BASE_URL = "https://jester.moodtools.workers.dev"
        private const val HOST = "jester.moodtools.workers.dev"
        private const val MAX_GROUPS = 24
        private const val MAX_FEATURES_PER_GROUP = 40
        private const val MAX_FEATURES = 200
        private const val MAX_GROUP_TITLE_LENGTH = 60
        private const val MAX_FEATURE_NAME_LENGTH = 120
        private const val MAX_CHARACTERS = 16_384
    }
}
