package com.moodtools.hub.networking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.moodtools.hub.modules.CatalogModule
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class CatalogIconClient(context: Context) {
    private val cacheDirectory = File(context.filesDir, "launcher-module-icons")

    fun load(module: CatalogModule): Bitmap? {
        val icon = module.icon ?: return null
        memoryCache[icon.sha256]?.let { return it }
        return synchronized(cacheLock) {
            memoryCache[icon.sha256]?.let { return@synchronized it }
            cacheDirectory.mkdirs()
            val target = File(
                cacheDirectory,
                "${module.slug}-${module.build}-${icon.sha256.take(16)}.img"
            )
            val cachedFile = target.takeIf {
                it.isFile && it.length() == icon.size && sha256(it) == icon.sha256
            }
            val cached: Bitmap? = cachedFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
            if (cached != null) {
                memoryCache[icon.sha256] = cached
                return@synchronized cached
            }
            target.delete()

            val connection = open(BASE_URL + (icon.cachePath ?: icon.path))
            val temporary = File(cacheDirectory, "${target.name}.part")
            try {
                require(connection.responseCode in 200..299) {
                    "Game icon request failed: ${connection.responseCode}"
                }
                connection.contentLengthLong.takeIf { it >= 0L }?.let {
                    require(it == icon.size) { "Game icon size does not match the catalog" }
                }
                var received = 0L
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            require(received <= icon.size) { "Game icon is larger than expected" }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                require(received == icon.size && sha256(temporary) == icon.sha256) {
                    "Game icon verification failed"
                }
                val decoded = BitmapFactory.decodeFile(temporary.absolutePath)
                    ?: error("Game icon could not be decoded")
                require(decoded.width in 1..4096 && decoded.height in 1..4096) {
                    "Game icon dimensions are invalid"
                }
                require(temporary.renameTo(target)) { "Game icon could not be cached" }
                cacheDirectory.listFiles()?.forEach { file ->
                    if (file.isFile && file != target && file.name.startsWith("${module.slug}-")) {
                        file.delete()
                    }
                }
                memoryCache[icon.sha256] = decoded
                decoded
            } finally {
                connection.disconnect()
                temporary.delete()
            }
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
            setRequestProperty("Accept", "image/png,image/jpeg,image/webp")
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

    private companion object {
        const val BASE_URL = "https://jester.moodtools.workers.dev"
        const val HOST = "jester.moodtools.workers.dev"
        val memoryCache = ConcurrentHashMap<String, Bitmap>()
        val cacheLock = Any()
    }
}
