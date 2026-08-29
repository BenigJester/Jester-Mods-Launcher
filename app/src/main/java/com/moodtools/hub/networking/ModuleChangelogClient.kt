package com.moodtools.hub.networking

import com.moodtools.hub.modules.CatalogModule
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ModuleChangelogEntry(
    val build: Long,
    val version: String,
    val updateType: String,
    val notes: String
)

data class ModuleChangelog(
    val packageName: String,
    val title: String,
    val gameVersion: String,
    val currentBuild: Long,
    val entries: List<ModuleChangelogEntry>,
    val updatedAtEpochSeconds: Long? = null
)

class ModuleChangelogClient(private val context: android.content.Context) {
    fun summary(module: CatalogModule): ModuleChangelog = fallback(module)

    fun load(module: CatalogModule): ModuleChangelog {
        return runCatching { refresh(module) }.getOrElse {
            loadCached(module) ?: fallback(module)
        }
    }

    private fun refresh(module: CatalogModule): ModuleChangelog {
        val connection = open(
            "$BASE_URL/api/launcher-module-changelog/${module.slug}/${module.build}",
            module.privateCatalogCapability
        )
        return try {
            require(connection.responseCode in 200..299)
            val envelope = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val parsed = parse(module, envelope)
            runCatching {
                cacheFile(module).parentFile?.let {
                    check(it.mkdirs() || it.isDirectory) { "Could not prepare changelog cache" }
                }
                cacheFile(module).writeText(envelope.toString())
            }.onFailure { Log.w(TAG, "Could not persist module changelog cache", it) }
            parsed
        } finally {
            connection.disconnect()
        }
    }

    private fun loadCached(module: CatalogModule): ModuleChangelog? {
        val file = cacheFile(module)
        if (!file.isFile) return null
        return runCatching { parse(module, JSONObject(file.readText())) }.getOrNull()
    }

    private fun parse(module: CatalogModule, envelope: JSONObject): ModuleChangelog {
        val payload = SignedEnvelopeVerifier.payload(envelope)
        require(payload.getInt("schema") == 1)
        require(payload.getString("audience") == "moodtools-standalone-module-changelog")
        require(payload.getString("slug") == module.slug)
        require(payload.getString("packageName") == module.config.packageName)
        require(payload.getLong("currentBuild") == module.build)
        val supportedVersions = payload.getJSONArray("supportedVersions")
        require(supportedVersions.length() in 1..50)
        val gameVersion = buildList {
            for (index in 0 until supportedVersions.length()) {
                add(supportedVersions.getString(index).also {
                    require(it.isNotBlank() && it.length <= 64)
                })
            }
        }.joinToString(", ")
        val source = payload.getJSONArray("entries")
        require(source.length() in 1..50)
        var previousBuild = Long.MAX_VALUE
        var previousPublishedAt = Long.MAX_VALUE
        var totalCharacters = 0
        var updatedAt: Long? = null
        val entries = buildList {
            for (index in 0 until source.length()) {
                val item = source.getJSONObject(index)
                val build = item.getLong("build")
                val version = item.getString("version")
                val updateType = item.getString("updateType")
                val notes = item.getString("notes")
                val publishedAt = item.getLong("publishedAt")
                totalCharacters += notes.length
                require(build > 0 && build < previousBuild)
                require(version.isNotBlank() && version.length <= 64)
                require(updateType == "release" || updateType == "minor")
                require(notes.length <= 8_000 && totalCharacters <= 128 * 1024)
                require(publishedAt in 1_577_836_800..4_102_444_800 && publishedAt < previousPublishedAt)
                add(ModuleChangelogEntry(build, version, updateType, notes))
                if (index == 0) updatedAt = publishedAt
                previousBuild = build
                previousPublishedAt = publishedAt
            }
        }
        require(entries.first().build == module.build)
        return ModuleChangelog(
            module.config.packageName,
            module.config.title,
            gameVersion,
            module.build,
            entries,
            updatedAt
        )
    }

    private fun fallback(module: CatalogModule) = ModuleChangelog(
        packageName = module.config.packageName,
        title = module.config.title,
        gameVersion = module.config.supportedVersions.sorted().joinToString(", "),
        currentBuild = module.build,
        updatedAtEpochSeconds = module.updatedAtEpochSeconds ?: module.publishedAtEpochSeconds,
        entries = listOf(
            ModuleChangelogEntry(
                build = module.build,
                version = module.version,
                updateType = "minor",
                notes = module.notes.orEmpty()
            )
        )
    )

    private fun cacheFile(module: CatalogModule) = File(
        File(context.filesDir, "module-changelogs"),
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
        private const val TAG = "JesterMoodsModuleHistory"
        private const val BASE_URL = "https://jester.moodtools.workers.dev"
        private const val HOST = "jester.moodtools.workers.dev"
    }
}
