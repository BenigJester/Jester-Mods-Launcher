package com.moodtools.hub.networking

import android.content.Context
import android.util.Log
import com.moodtools.hub.modules.CatalogIcon
import com.moodtools.hub.modules.CatalogModule
import com.moodtools.hub.modules.GameInstallSource
import com.moodtools.hub.modules.GamePackageFormat
import com.moodtools.hub.modules.ModuleConfig
import com.moodtools.hub.modules.ModuleUpdateStatus
import com.moodtools.hub.modules.NonRootMethod
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModuleCatalogClient(private val context: Context) {
    private val cacheFile = File(context.filesDir, "launcher-module-catalog.json")
    @Volatile
    private var memoryCache: List<CatalogModule>? = null

    fun load(): List<CatalogModule> {
        return runCatching { refresh() }
            .getOrElse { loadCached() ?: throw it }
    }

    /** Returns verified in-memory/disk data without waiting for the network. */
    fun loadCached(): List<CatalogModule>? {
        memoryCache?.let { return it }
        if (!cacheFile.isFile) return null
        return runCatching { parse(JSONObject(cacheFile.readText())) }
            .getOrNull()
            ?.also { memoryCache = it }
    }

    /** Refreshes the cache from the signed launcher endpoint. */
    fun refresh(): List<CatalogModule> {
        val remote = requestCatalog()
        val parsed = parse(remote)
        memoryCache = parsed
        runCatching { cacheFile.writeText(remote.toString()) }
            .onFailure { Log.w(TAG, "Could not persist module catalog cache", it) }
        return parsed
    }

    private fun requestCatalog(): JSONObject {
        val connection = open(
            "$BASE_URL/api/launcher-modules?refresh=${System.currentTimeMillis()}"
        )
        return try {
            require(connection.responseCode in 200..299) {
                "Catalog request failed: ${connection.responseCode}"
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(envelope: JSONObject): List<CatalogModule> {
        val payload = SignedEnvelopeVerifier.payload(envelope)
        require(payload.getInt("schema") == 1)
        require(payload.getString("audience") == "moodtools-standalone")
        val modules = payload.getJSONArray("modules")
        require(modules.length() in 0..MAX_CATALOG_MODULES)
        return buildList {
            for (index in 0 until modules.length()) {
                val item = modules.getJSONObject(index)
                val packageName = item.getString("packageName")
                val slug = item.getString("slug")
                require(!item.has("privateScope")) {
                    "Private modules must never appear in the public catalog"
                }
                require(packageName.matches(PACKAGE_PATTERN))
                require(slug.matches(SLUG_PATTERN))
                val versions = item.getJSONArray("supportedVersions")
                val abis = item.getJSONArray("supportedAbis")
                val supportedVersions = buildSet {
                    for (versionIndex in 0 until versions.length()) add(versions.getString(versionIndex))
                }
                val supportedAbis = buildSet {
                    for (abiIndex in 0 until abis.length()) add(abis.getString(abiIndex))
                }
                require(supportedVersions.isNotEmpty())
                require(supportedAbis.isNotEmpty() && supportedAbis.all { it in SUPPORTED_ABIS })
                val installSource = parseInstallSource(item, packageName, slug)
                val build = item.getLong("build").also { require(it > 0) }
                val updateStatusValue = item.optString("updateStatus").takeIf(String::isNotBlank)
                val updateStatus = ModuleUpdateStatus.fromCatalog(updateStatusValue)
                val statusChangedAt = parseEpochSeconds(item, "statusChangedAt")
                require((updateStatusValue == null) == (statusChangedAt == null))
                add(
                    CatalogModule(
                        config = ModuleConfig(
                            packageName = packageName,
                            title = item.getString("title"),
                            supportedVersions = supportedVersions,
                            supportedAbis = supportedAbis,
                            entryPoint = item.optString("entryPoint").takeIf(String::isNotBlank),
                            dexFile = "classes.dex",
                            nativeFile = "libmenu_native.so",
                            iconFile = null,
                            nonRootMethod = NonRootMethod.fromJson(
                                item.optString("nonrootMethod").takeIf { it.isNotBlank() },
                                packageName
                            )
                        ),
                        slug = slug,
                        build = build,
                        version = item.getString("version"),
                        notes = item.optString("notes").trim().takeIf(String::isNotEmpty),
                        icon = parseIcon(item, slug, build),
                        installSource = installSource,
                        category = item.optString("category", DEFAULT_CATEGORY).trim().also {
                            require(it.isNotEmpty() && it.length <= MAX_CATEGORY_LENGTH)
                        },
                        tags = parseTags(item),
                        featured = item.optBoolean("featured", false),
                        popularity = item.optLong("popularity", 0L).also {
                            require(it in 0..MAX_POPULARITY)
                        },
                        publishedAtEpochSeconds = parseEpochSeconds(item, "publishedAt"),
                        updatedAtEpochSeconds = parseEpochSeconds(item, "updatedAt"),
                        updateStatus = updateStatus,
                        statusChangedAtEpochSeconds = statusChangedAt,
                        features = parseFeatures(item, slug, build),
                        downloadSizeByAbi = parseDownloadSizes(item, supportedAbis)
                    )
                )
            }
        }.also { parsed ->
            require(parsed.map { it.config.packageName }.distinct().size == parsed.size)
            require(parsed.map { it.slug }.distinct().size == parsed.size)
        }
    }

    private fun parseFeatures(
        item: JSONObject,
        slug: String,
        build: Long
    ): com.moodtools.hub.modules.CatalogModuleFeatures? {
        val features = item.optJSONObject("features") ?: return null
        val path = features.getString("path")
        require(path == "/api/launcher-module-features/$slug/$build")
        return com.moodtools.hub.modules.CatalogModuleFeatures(
            path = path,
            count = features.getInt("count").also { require(it in 1..MAX_FEATURES) }
        )
    }

    private fun parseDownloadSizes(item: JSONObject, supportedAbis: Set<String>): Map<String, Long> {
        val sizes = item.optJSONObject("downloadSizeByAbi") ?: return emptyMap()
        require(sizes.length() == supportedAbis.size)
        return buildMap {
            supportedAbis.forEach { abi ->
                require(sizes.has(abi))
                put(abi, sizes.getLong(abi).also { require(it in 1..MAX_MODULE_DOWNLOAD_BYTES) })
            }
        }
    }

    private fun parseTags(item: JSONObject): Set<String> {
        val tags = item.optJSONArray("tags") ?: return emptySet()
        require(tags.length() <= MAX_TAGS)
        return buildSet {
            for (index in 0 until tags.length()) {
                add(tags.getString(index).trim().lowercase().also {
                    require(it.isNotEmpty() && it.length <= MAX_TAG_LENGTH)
                })
            }
        }
    }

    private fun parseEpochSeconds(item: JSONObject, field: String): Long? {
        if (!item.has(field)) return null
        return item.getLong(field).also { require(it in MIN_CATALOG_EPOCH_SECONDS..MAX_CATALOG_EPOCH_SECONDS) }
    }

    private fun parseIcon(item: JSONObject, slug: String, build: Long): CatalogIcon? {
        val icon = item.optJSONObject("icon") ?: return null
        val path = icon.getString("path")
        require(path == "/api/launcher-module-icon/$slug/$build")
        val sha256 = icon.getString("sha256").lowercase().also {
            require(it.matches(SHA256_PATTERN))
        }
        val cachePath = icon.optString("cachePath").takeIf(String::isNotBlank)
        cachePath?.let {
            require(it == "/api/launcher-module-icon/$slug/$build/$sha256")
        }
        return CatalogIcon(
            path = path,
            cachePath = cachePath,
            sha256 = sha256,
            size = icon.getLong("size").also { require(it in 1..MAX_ICON_BYTES) }
        )
    }

    private fun parseInstallSource(
        item: JSONObject,
        packageName: String,
        slug: String
    ): GameInstallSource {
        val install = item.optJSONObject("install")
        if (install == null) {
            return GameInstallSource.PlayStore(playStoreUrl(packageName))
        }
        return when (install.getString("source")) {
            "play_store" -> {
                val url = install.optString("url", playStoreUrl(packageName))
                val parsed = URL(url)
                require(parsed.protocol == "https" && parsed.host == PLAY_STORE_HOST)
                require(parsed.path == "/store/apps/details")
                require(parsed.query.orEmpty().split('&').any { it == "id=$packageName" })
                GameInstallSource.PlayStore(url)
            }
            "direct" -> {
                val versionCode = install.getLong("versionCode").also { require(it > 0) }
                val path = install.getString("path")
                val format = when (install.optString("format").trim().lowercase()) {
                    "" -> if (path.endsWith(".apks")) GamePackageFormat.APKS else GamePackageFormat.APK
                    GamePackageFormat.APK.catalogValue -> GamePackageFormat.APK
                    GamePackageFormat.APKS.catalogValue -> GamePackageFormat.APKS
                    else -> error("Unsupported game package format")
                }
                require(path == "/api/launcher-game-download/$slug/$versionCode.${format.extension}")
                GameInstallSource.DirectDownload(
                    path = path,
                    versionCode = versionCode,
                    version = install.getString("version").also {
                        require(it.isNotBlank() && it.length <= 64)
                    },
                    size = install.getLong("size").also { require(it in 1..MAX_GAME_APK_BYTES) },
                    sha256 = install.getString("sha256").lowercase().also {
                        require(it.matches(SHA256_PATTERN))
                    },
                    signingCertificateSha256 = install.getString("signingCertificateSha256")
                        .lowercase()
                        .also { require(it.matches(SHA256_PATTERN)) },
                    format = format
                )
            }
            else -> error("Unsupported game install source")
        }
    }

    private fun open(address: String): HttpURLConnection {
        val url = URL(address)
        require(url.protocol == "https" && url.host == HOST)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache, no-store")
            setRequestProperty("Pragma", "no-cache")
        }
    }

    companion object {
        private const val TAG = "JesterMoodsCatalog"
        const val BASE_URL = "https://jester.moodtools.workers.dev"
        private const val HOST = "jester.moodtools.workers.dev"
        private const val PLAY_STORE_HOST = "play.google.com"
        private const val MAX_GAME_APK_BYTES = 2L * 1024L * 1024L * 1024L
        private const val MAX_ICON_BYTES = 5L * 1024L * 1024L
        private const val MAX_MODULE_DOWNLOAD_BYTES = 80L * 1024L * 1024L
        private const val MAX_CATALOG_MODULES = 2_000
        private const val DEFAULT_CATEGORY = "Other"
        private const val MAX_CATEGORY_LENGTH = 40
        private const val MAX_TAGS = 12
        private const val MAX_TAG_LENGTH = 32
        private const val MAX_POPULARITY = 1_000_000_000L
        private const val MAX_FEATURES = 200
        private const val MIN_CATALOG_EPOCH_SECONDS = 1_577_836_800L // 2020-01-01
        private const val MAX_CATALOG_EPOCH_SECONDS = 4_102_444_800L // 2100-01-01
        private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_.]{3,200}")
        private val SLUG_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a")

        private fun playStoreUrl(packageName: String) =
            "https://play.google.com/store/apps/details?id=$packageName"
    }
}
