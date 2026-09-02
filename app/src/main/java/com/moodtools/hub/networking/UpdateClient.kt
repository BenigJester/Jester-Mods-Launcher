package com.moodtools.hub.networking

import android.util.Base64
import android.util.Log
import com.moodtools.hub.BuildConfig
import com.moodtools.hub.modules.ModuleIntegrityVerifier
import com.moodtools.hub.modules.ModuleRepository
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

data class UpdateRequest(
    val packageName: String,
    val grant: String?,
    val nonce: String?,
    val buildHint: String?,
    val slug: String? = null
)

data class UpdateResult(
    val build: Long,
    val version: String?,
    val changelog: String?
)

enum class StandaloneUpdateStage {
    PREPARING,
    DOWNLOADING,
    VERIFYING,
    ACTIVATING
}

class ReleaseVerificationRequired(
    val releasePath: String,
    val updateBuild: Long,
    val requiredReleaseBuild: Long
) : Exception("Release verification is required")

class ModuleDownloadAuthorizationExpired(cause: Throwable? = null) :
    Exception("The module download authorization expired", cause)

class UpdateClient(private val moduleRoot: File) {
    private val baseUrl = "https://jester.moodtools.workers.dev"

    fun apply(request: UpdateRequest, abi: String, bootstrap: Int = 1): UpdateResult {
        require(request.packageName.matches(Regex("[A-Za-z0-9_.]{3,200}")))
        require(abi == "arm64-v8a" || abi == "armeabi-v7a")
        require(request.grant == null || (request.grant.length <= 4096
            && request.grant.matches(Regex("[A-Za-z0-9_.-]+")))) { "Invalid release grant" }
        require(request.nonce == null || request.nonce.matches(Regex("[A-Za-z0-9_-]{43}"))) {
            "Invalid release nonce"
        }
        require(request.buildHint == null || request.buildHint.matches(Regex("[0-9]{1,19}"))) {
            "Invalid update build hint"
        }

        val manifestUrl = "$baseUrl/api/mod-update?package=${request.packageName}&abi=$abi&bootstrap=$bootstrap"
        val envelope = getJson(manifestUrl)
        require(envelope.optString("algorithm") == "SHA256withRSA") { "Unsupported update signature algorithm" }
        val payloadBytes = Base64.decode(envelope.getString("payload"), Base64.DEFAULT)
        verifyEnvelope(payloadBytes, envelope.getString("signature"))

        val payload = JSONObject(String(payloadBytes, Charsets.UTF_8))
        require(payload.getString("packageName") == request.packageName)
        if (request.buildHint != null) require(payload.getLong("build").toString() == request.buildHint)
        require(payload.getInt("schema") == 3) { "The update is not a launcher module payload" }
        require(payload.optInt("minimumBootstrap", 1) <= bootstrap) {
            "This update requires a newer launcher bootstrap"
        }

        val build = payload.getLong("build")
        require(build > 0) { "Invalid update build number" }
        val version = payload.optString("version").trim().takeIf { it.isNotEmpty() }
        require(version != null && version.length <= 64) { "Invalid update version label" }
        val notes = payload.optString("notes", payload.optString("changelog")).trim()
        require(notes.length <= 8_000) { "The signed update changelog is too large" }
        val updateType = payload.optString("updateType", "minor").lowercase()
        require(updateType == "minor" || updateType == "release") { "Unsupported update type" }
        val requiredReleaseBuild = payload.optLong(
            "requiredReleaseBuild",
            if (updateType == "release") build else 0L
        )
        require(requiredReleaseBuild in 0..build) { "Invalid release gate requirement" }
        val installedBuild = runCatching {
            val local = File(moduleRoot, "update.json")
            if (local.isFile) JSONObject(local.readText()).optLong("build", 0L) else 0L
        }.getOrDefault(0L)
        if (requiredReleaseBuild > installedBuild && request.grant.isNullOrBlank()) {
            val releasePath = payload.optString("releasePath")
            require(releasePath.matches(Regex("^/mod-update-release/[a-z0-9][a-z0-9-]{0,63}/[0-9]+$"))) {
                "Invalid release verification route"
            }
            throw ReleaseVerificationRequired(releasePath, build, requiredReleaseBuild)
        }

        val files = payload.optJSONObject("files") ?: JSONObject()
        val nativeFiles = files.optJSONObject("native")
        val native = nativeFiles?.optJSONObject(abi)
        val dex = files.optJSONObject("dex")
        require(native != null && dex != null) {
            "The update must contain both native and DEX module payloads"
        }

        moduleRoot.mkdirs()
        val nextNative = File(moduleRoot, "libmenu_native.so.next")
        val nextDex = File(moduleRoot, "classes.dex.next")
        nextNative.delete()
        nextDex.delete()
        try {
            download(
                path = native.getString("path"),
                expectedBytes = native.getLong("size"),
                expectedSha256 = native.getString("sha256"),
                output = nextNative,
                grant = request.grant
            )
            download(
                path = dex.getString("path"),
                expectedBytes = dex.getLong("size"),
                expectedSha256 = dex.getString("sha256"),
                output = nextDex,
                grant = request.grant
            )
            commitPayloadPair(nextNative, nextDex)
            File(moduleRoot, "update.json").writeText(payload.toString())
        } finally {
            nextNative.delete()
            nextDex.delete()
        }
        return UpdateResult(
            build = build,
            version = version,
            changelog = notes.takeIf { it.isNotBlank() }
        )
    }

    /** Downloads a free standalone launcher module from the catalog-backed channel. */
    fun applyStandalone(
        packageName: String,
        slug: String,
        abi: String,
        authorization: LauncherModuleAuthorization,
        bootstrap: Int = 1,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
        onStage: (StandaloneUpdateStage) -> Unit = {},
        onDiagnostic: (String) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): UpdateResult {
        onStage(StandaloneUpdateStage.PREPARING)
        ensureNotCancelled(isCancelled)
        require(packageName.matches(Regex("[A-Za-z0-9_.]{3,200}")))
        require(slug.matches(SLUG_PATTERN))
        require(abi == "arm64-v8a" || abi == "armeabi-v7a")
        require(authorization.capability.length in 80..4096)
        if (authorization.expiresAt <= System.currentTimeMillis() / 1000L) {
            throw ModuleDownloadAuthorizationExpired()
        }
        val envelope = authorization.manifest
        val payload = SignedEnvelopeVerifier.payload(envelope)
        require(payload.getInt("schema") == 1)
        require(payload.getString("audience") == "moodtools-standalone")
        require(payload.getString("packageName") == packageName)
        require(payload.getString("slug") == slug)
        val privateScope = payload.optString("privateScope").takeIf(String::isNotBlank)
        privateScope?.let { require(it.matches(PRIVATE_SCOPE_PATTERN)) }
        require(privateScope == authorization.privateScope) {
            "The module authorization does not match its signed private scope"
        }
        require(payload.optInt("minimumBootstrap", 1) <= bootstrap)
        val build = payload.getLong("build").also { require(it > 0) }
        val version = payload.getString("version").trim().also { require(it.isNotEmpty() && it.length <= 64) }
        val notes = payload.optString("notes").trim().also { require(it.length <= 8_000) }

        val moduleConfig = payload.getJSONObject("moduleConfig")
        require(moduleConfig.getString("packageName") == packageName)
        val supportedVersions = moduleConfig.getJSONArray("supportedVersions")
        val supportedAbis = moduleConfig.getJSONArray("supportedAbis")
        require(supportedVersions.length() > 0 && supportedAbis.length() > 0)
        require((0 until supportedAbis.length()).any { supportedAbis.getString(it) == abi })
        require(moduleConfig.getString("dexFile") == "classes.dex")
        require(moduleConfig.getString("nativeFile") == "libmenu_native.so")
        val entryPoint = moduleConfig.optString("entryPoint").trim()
        require(entryPoint.isNotEmpty() && entryPoint.length <= 240)
        val nonRootMethod = com.moodtools.hub.modules.NonRootMethod.fromJson(
            moduleConfig.optString("nonrootMethod").takeIf { it.isNotBlank() },
            packageName
        )

        val files = payload.getJSONObject("files")
        val native = files.getJSONObject("native").getJSONObject(abi)
        val dex = files.getJSONObject("dex")
        val nativeSize = native.getLong("size").also { require(it > 0L) }
        val dexSize = dex.getLong("size").also { require(it > 0L) }
        val totalSize = Math.addExact(nativeSize, dexSize)
        onDiagnostic("Signed manifest accepted for build $build")
        onStage(StandaloneUpdateStage.DOWNLOADING)
        onProgress?.invoke(0L, totalSize)
        moduleRoot.mkdirs()
        val nextNative = File(moduleRoot, "libmenu_native.so.next")
        val nextDex = File(moduleRoot, "classes.dex.next")
        val nextConfig = File(moduleRoot, "config.json.next")
        val nextSignedManifest = File(moduleRoot, "${ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE}.next")
        val nextPrivateMarker = privateScope?.let { File(moduleRoot, "${ModuleRepository.PRIVATE_INSTALL_MARKER}.next") }
        nextConfig.delete()
        nextSignedManifest.delete()
        nextPrivateMarker?.delete()
        try {
            download(
                path = native.getString("path"),
                expectedBytes = nativeSize,
                expectedSha256 = native.getString("sha256"),
                output = nextNative,
                grant = null,
                moduleAuthorization = authorization,
                allowedPrefix = "/api/launcher-module-payload/",
                progressBaseBytes = 0L,
                progressTotalBytes = totalSize,
                onProgress = onProgress,
                onDiagnostic = onDiagnostic,
                isCancelled = isCancelled
            )
            download(
                path = dex.getString("path"),
                expectedBytes = dexSize,
                expectedSha256 = dex.getString("sha256"),
                output = nextDex,
                grant = null,
                moduleAuthorization = authorization,
                allowedPrefix = "/api/launcher-module-payload/",
                progressBaseBytes = nativeSize,
                progressTotalBytes = totalSize,
                onProgress = onProgress,
                onDiagnostic = onDiagnostic,
                isCancelled = isCancelled
            )
            ensureNotCancelled(isCancelled)
            onStage(StandaloneUpdateStage.VERIFYING)
            onDiagnostic("Payload hashes and signed identity verified")
            val config = JSONObject()
                .put("package_name", packageName)
                .put("module_slug", slug)
                .put("title", moduleConfig.getString("title"))
                .put("supported_versions", supportedVersions)
                .put("supported_abis", supportedAbis)
                .put("entry_point", entryPoint)
                .put("dex_file", "classes.dex")
                .put("native_file", "libmenu_native.so")
                .also {
                    if (moduleConfig.has("nonrootMethod")) {
                        it.put("nonroot_method", nonRootMethod.jsonValue)
                    }
                }
            nextConfig.writeText(config.toString())
            nextSignedManifest.writeText(envelope.toString())
            nextPrivateMarker?.writeText(
                JSONObject()
                    .put("schema", 1)
                    .put("packageName", packageName)
                    .put("scope", privateScope)
                    .toString()
            )
            ensureNotCancelled(isCancelled)
            onStage(StandaloneUpdateStage.ACTIVATING)
            onDiagnostic("Activating the verified add-on atomically")
            commitStandalonePayload(nextNative, nextDex, nextConfig, nextSignedManifest, nextPrivateMarker)
            File(moduleRoot, "update.json").writeText(payload.toString())
        } finally {
            nextConfig.delete()
            nextSignedManifest.delete()
            nextPrivateMarker?.delete()
        }
        return UpdateResult(build, version, notes.takeIf(String::isNotBlank))
    }

    private fun commitStandalonePayload(
        nextNative: File,
        nextDex: File,
        nextConfig: File,
        nextSignedManifest: File,
        nextPrivateMarker: File?
    ) {
        val requiredTargets = listOf(
            nextNative to File(moduleRoot, "libmenu_native.so"),
            nextDex to File(moduleRoot, "classes.dex"),
            nextConfig to File(moduleRoot, "config.json"),
            nextSignedManifest to File(moduleRoot, ModuleIntegrityVerifier.SIGNED_MANIFEST_FILE)
        )
        val privateTarget = File(moduleRoot, ModuleRepository.PRIVATE_INSTALL_MARKER)
        val allTargets = requiredTargets.map { it.second } + privateTarget
        val backups = allTargets.map { target -> target to File(moduleRoot, "${target.name}.bak") }
        backups.forEach { (_, backup) -> backup.delete() }
        try {
            backups.forEach { (target, backup) ->
                if (target.isFile) require(target.renameTo(backup)) { "Could not back up ${target.name}" }
            }
            requiredTargets.forEach { (next, target) ->
                require(next.renameTo(target)) { "Could not commit ${target.name}" }
            }
            nextPrivateMarker?.let {
                require(it.renameTo(privateTarget)) { "Could not commit ${privateTarget.name}" }
            }
            backups.forEach { (_, backup) -> backup.delete() }
        } catch (error: Throwable) {
            allTargets.forEach(File::delete)
            backups.forEach { (target, backup) -> if (backup.isFile) backup.renameTo(target) }
            throw error
        } finally {
            backups.forEach { (_, backup) -> backup.delete() }
        }
    }

    private fun commitPayloadPair(nextNative: File, nextDex: File) {
        val native = File(moduleRoot, "libmenu_native.so")
        val dex = File(moduleRoot, "classes.dex")
        val nativeBackup = File(moduleRoot, "libmenu_native.so.bak")
        val dexBackup = File(moduleRoot, "classes.dex.bak")
        nativeBackup.delete()
        dexBackup.delete()

        val hadNative = native.isFile
        val hadDex = dex.isFile
        if (hadNative) require(native.renameTo(nativeBackup)) { "Could not stage native payload backup" }
        if (hadDex && !dex.renameTo(dexBackup)) {
            if (hadNative) nativeBackup.renameTo(native)
            error("Could not stage DEX payload backup")
        }

        try {
            require(nextNative.renameTo(native)) { "Could not commit native payload" }
            require(nextDex.renameTo(dex)) { "Could not commit DEX payload" }
            nativeBackup.delete()
            dexBackup.delete()
        } catch (error: Throwable) {
            native.delete()
            dex.delete()
            if (hadNative) nativeBackup.renameTo(native)
            if (hadDex) dexBackup.renameTo(dex)
            throw error
        } finally {
            nativeBackup.delete()
            dexBackup.delete()
        }
    }

    private fun verifyEnvelope(payload: ByteArray, signatureBase64: String) {
        val keyText = BuildConfig.UPDATE_PUBLIC_KEY_DER_BASE64
        require(keyText.isNotBlank()) { "Update signing public key is not configured" }
        val keyBytes = Base64.decode(keyText, Base64.DEFAULT)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(key)
        verifier.update(payload)
        require(verifier.verify(Base64.decode(signatureBase64, Base64.DEFAULT))) {
            "Update manifest signature verification failed"
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = open(address)
        connection.requestMethod = "GET"
        return try {
            require(connection.responseCode in 200..299) { "Manifest request failed: ${connection.responseCode}" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun download(path: String, expectedBytes: Long, expectedSha256: String,
                         output: File, grant: String?, moduleAuthorization: LauncherModuleAuthorization? = null,
                         allowedPrefix: String = "/api/mod-update-payload/",
                         progressBaseBytes: Long = 0L,
                         progressTotalBytes: Long = 0L,
                         onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
                         onDiagnostic: (String) -> Unit = {},
                         isCancelled: () -> Boolean = { false }) {
        ensureNotCancelled(isCancelled)
        require(path.startsWith(allowedPrefix))
        require(expectedSha256.matches(Regex("[0-9a-fA-F]{64}")))
        output.parentFile?.mkdirs()
        if (output.isFile && output.length() == expectedBytes &&
            sha256(output, isCancelled) == expectedSha256.lowercase()
        ) {
            if (progressTotalBytes > 0L) {
                onProgress?.invoke(
                    (progressBaseBytes + expectedBytes).coerceAtMost(progressTotalBytes),
                    progressTotalBytes
                )
            }
            return
        }
        output.delete()
        val temporary = File(
            output.parentFile,
            "${output.name}.${expectedSha256.lowercase().take(16)}.part"
        )
        output.parentFile?.listFiles()?.forEach { candidate ->
            if (candidate != temporary && candidate.isFile &&
                candidate.name.startsWith("${output.name}.") && candidate.name.endsWith(".part")
            ) {
                candidate.delete()
            }
        }
        try {
            FastFileDownloader.download(
                destination = temporary,
                expectedBytes = expectedBytes,
                openConnection = { range ->
                    val payloadProof = moduleAuthorization?.payloadProof("GET", path)
                    open(baseUrl + path).apply {
                        grant?.let { setRequestProperty("X-MoodTools-Update-Grant", it) }
                        moduleAuthorization?.let {
                            setRequestProperty("Authorization", "Bearer ${it.capability}")
                            setRequestProperty("X-MoodTools-Proof-Key", payloadProof!!.keyId)
                            setRequestProperty("X-MoodTools-Proof-Nonce", payloadProof.nonce)
                            setRequestProperty("X-MoodTools-Proof-Signature", payloadProof.signature)
                        }
                        range?.let { setRequestProperty("Range", "bytes=${it.first}-${it.last}") }
                    }
                },
                onProgress = { fileBytes, _ ->
                    if (progressTotalBytes > 0L) {
                        onProgress?.invoke(
                            (progressBaseBytes + fileBytes).coerceAtMost(progressTotalBytes),
                            progressTotalBytes
                        )
                    }
                },
                onDiagnostic = { message ->
                    Log.w("JesterMoodsDownload", message)
                    onDiagnostic(message)
                },
                isCancelled = isCancelled
            )
            ensureNotCancelled(isCancelled)
            require(sha256(temporary, isCancelled) == expectedSha256.lowercase()) { "Payload hash verification failed" }
            if (output.exists()) require(output.delete())
            require(temporary.renameTo(output)) { "Could not commit downloaded payload" }
        } catch (error: Throwable) {
            if (temporary.length() == expectedBytes) temporary.delete()
            if (error.hasHttpStatus(401, 403)) {
                throw ModuleDownloadAuthorizationExpired(error)
            }
            throw error
        }
    }

    private fun sha256(file: File, isCancelled: () -> Boolean = { false }): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                ensureNotCancelled(isCancelled)
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ensureNotCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw FastFileDownloader.DownloadCancelledException()
    }

    private fun open(address: String): HttpURLConnection {
        val url = URL(address)
        require(url.protocol == "https" && url.host == "jester.moodtools.workers.dev")
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json, application/octet-stream")
            setRequestProperty("Accept-Encoding", "identity")
        }
    }

    companion object {
        private val PRIVATE_SCOPE_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,63}")
        private val SLUG_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,63}")
    }
}

private fun Throwable.hasHttpStatus(vararg statuses: Int): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is FastFileDownloader.HttpDownloadException && current.status in statuses) return true
        current = current.cause?.takeUnless { it === current }
    }
    return false
}
