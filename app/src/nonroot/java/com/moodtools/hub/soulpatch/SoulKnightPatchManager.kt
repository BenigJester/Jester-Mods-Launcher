package com.moodtools.hub.soulpatch

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.moodtools.hub.PackageReplacementInstallReceiver
import com.moodtools.hub.PackageReplacementRequest
import com.moodtools.hub.modules.InstalledGame
import com.moodtools.hub.modules.ModuleIntegrityVerifier
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal
import org.json.JSONObject

/** Creates and installs a verified direct-package patch for an opted-in module. */
internal class DirectPackagePatchManager(
    private val context: Context,
    private val targetPackage: String
) {
    private val packageManager = context.packageManager
    private val patchRoot = File(File(context.filesDir, PATCH_DIRECTORY), targetPackage)

    init {
        require(targetPackage.matches(PACKAGE_NAME_PATTERN)) { "Invalid direct patch package name" }
    }

    fun requiresReplacement(game: InstalledGame): Boolean {
        if (game.packageName != targetPackage) return false
        if (!isPatchedInstallation()) return true
        return runCatching {
            val directory = File(context.filesDir, "menus/$targetPackage")
            val marker = installedPatchMarker() ?: return@runCatching true
            marker.optInt("launchGuardSchema") != LAUNCH_GUARD_SCHEMA ||
                marker.optString("launchGuardPublicKey") != launchGuardPublicKey(signingMaterial()) ||
                marker.optString("dexSha256") != sha256(File(directory, game.module.dexFile)) ||
                marker.optString("nativeSha256") != sha256(File(directory, game.module.nativeFile))
        }.getOrDefault(true)
    }

    fun isPatchedInstallation(): Boolean =
        installedFactory() == PATCH_FACTORY || installedPatchMarker()?.optString("package") == targetPackage

    /**
     * Reconciles Package Installer state when its terminal broadcast is delayed or lost.
     * Checking the embedded payload hashes avoids mistaking a cancelled update of an older
     * launcher-signed build for a successful replacement.
     */
    fun isRequestInstalled(request: PackageReplacementRequest): Boolean = runCatching {
        if (request.packageName != targetPackage) return@runCatching false
        val installed = installedPackageInfo()
        if (installed.longVersionCodeCompat() != request.versionCode ||
            installedFactory() != PATCH_FACTORY) {
            return@runCatching false
        }
        val expectedBase = request.apks.singleOrNull { it.name == "base.apk" }
            ?: return@runCatching false
        val expectedMarker = ZipFile(expectedBase).use { archive ->
            val marker = archive.getEntry(MARKER_ENTRY) ?: return@use null
            archive.getInputStream(marker).use { JSONObject(String(it.readBytes(), Charsets.UTF_8)) }
        } ?: return@runCatching false
        val actualMarker = installedPatchMarker() ?: return@runCatching false
        expectedMarker.optString("dexSha256").isNotBlank() &&
            expectedMarker.optString("dexSha256") == actualMarker.optString("dexSha256") &&
            expectedMarker.optString("nativeSha256").isNotBlank() &&
            expectedMarker.optString("nativeSha256") == actualMarker.optString("nativeSha256") &&
            expectedMarker.optInt("launchGuardSchema") == LAUNCH_GUARD_SCHEMA &&
            expectedMarker.optString("launchGuardPublicKey").isNotBlank() &&
            expectedMarker.optString("launchGuardPublicKey") == actualMarker.optString("launchGuardPublicKey")
    }.getOrDefault(false)

    fun prepare(
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)? = null
    ): PackageReplacementRequest {
        require(game.packageName == targetPackage) { "The direct patch request targets another game" }
        require(game.versionSupported && game.abiSupported) { "This game build is not supported" }
        val moduleDirectory = File(context.filesDir, "menus/$targetPackage")
        ModuleIntegrityVerifier().verify(moduleDirectory, game.module, game.abi)
        val moduleDex = File(moduleDirectory, game.module.dexFile)
        val moduleNative = File(moduleDirectory, game.module.nativeFile)
        require(moduleDex.isFile && moduleDex.length() > 0L) { "Direct patch loader DEX is missing" }
        require(moduleNative.isFile && moduleNative.length() > 0L) { "Direct patch native payload is missing" }
        require(dexContainsLaunchGuard(moduleDex)) {
            "This add-on must be updated before it can use guarded direct launch"
        }

        onProgress?.invoke("Inspecting ${game.module.title}", "Reading the installed APK split set.")
        val installed = installedPackageInfo()
        require(installed.longVersionCodeCompat() == game.versionCode) {
            "The installed game changed while the patch was being prepared"
        }
        val sources = installedSources(installed)
        require(sources.first().name == "base.apk") { "The base APK is unavailable" }
        require(sources.all { it.file.isFile && it.file.canRead() && it.file.length() > 0L }) {
            "Android did not expose the installed game APK set"
        }

        val moduleFingerprint = "${sha256(moduleDex).take(16)}-${sha256(moduleNative).take(16)}"
        val outputDirectory = File(patchRoot, "${game.versionCode}-$moduleFingerprint")
        val finalDirectory = File(outputDirectory, "signed")
        val expectedFiles = sources.map { File(finalDirectory, it.name) }
        val signingMaterial = signingMaterial()
        // PackageInfo.signingInfo is not reliable enough for this decision on every OEM. In
        // particular, some devices have reported stale or empty signer data for an installed APK.
        // Verify the actual installed split files instead: an exact signer match means Android can
        // accept this as an in-place update and preserve the game's data.
        val requiresUninstall = !installedClusterSignedBy(sources, signingMaterial.certificate)
        val sourceAlreadyPatched = installedFactory() == PATCH_FACTORY || installedPatchMarker() != null
        if (expectedFiles.all(File::isFile) && runCatching {
                validateCluster(expectedFiles, game.versionCode, signingMaterial.certificate, true)
            }.isSuccess) {
            onProgress?.invoke(
                "Patch ready",
                if (requiresUninstall) {
                    "The verified ${game.module.title} package is ready to replace the original installation."
                } else {
                    "The verified ${game.module.title} update is ready and will keep its local data."
                }
            )
            return PackageReplacementRequest(
                targetPackage,
                game.module.title,
                game.versionCode,
                expectedFiles,
                requiresUninstall
            )
        }

        ensureFreeSpace(sources)
        recreateDirectory(outputDirectory)
        finalDirectory.mkdirs()
        check(finalDirectory.isDirectory) { "Could not create direct patch storage" }
        val unsignedBase = File(outputDirectory, "base-unsigned.apk")
        val signedBase = File(finalDirectory, "base.apk")

        try {
            onProgress?.invoke("Patching ${game.module.title}", "Embedding the verified loader into the base APK.")
            rewriteBase(
                sources.first().file,
                unsignedBase,
                moduleDex,
                moduleNative,
                game.versionCode,
                sourceAlreadyPatched,
                "lib/${game.abi}/libYourSaviour.so"
            )
            signApk(unsignedBase, signedBase, signingMaterial)
            check(unsignedBase.delete()) { "Could not clear the temporary base APK" }

            sources.drop(1).forEachIndexed { index, source ->
                onProgress?.invoke(
                    "Signing ${game.module.title}",
                    "Preparing APK split ${index + 1} of ${sources.size - 1}."
                )
                signApk(source.file, File(finalDirectory, source.name), signingMaterial)
            }
            validateCluster(expectedFiles, game.versionCode, signingMaterial.certificate, true)
            onProgress?.invoke(
                "Patch ready",
                if (requiresUninstall) {
                    "Android will ask before replacing the original ${game.module.title} installation."
                } else {
                    "Android will update the patched ${game.module.title} installation and keep its local data."
                }
            )
            return PackageReplacementRequest(
                targetPackage,
                game.module.title,
                game.versionCode,
                expectedFiles,
                requiresUninstall
            )
        } catch (error: Throwable) {
            runCatching { recreateDirectory(outputDirectory) }
            throw error
        }
    }

    fun install(request: PackageReplacementRequest) {
        require(request.packageName == targetPackage && request.apks.isNotEmpty()) {
            "Invalid direct package replacement request"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            require(packageManager.canRequestPackageInstalls()) {
                "Allow Jester Mods to install unknown apps first"
            }
        }
        val signingMaterial = signingMaterial()
        validateCluster(request.apks, request.versionCode, signingMaterial.certificate, true)

        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(targetPackage)
            setSize(request.apks.sumOf(File::length))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                request.apks.forEach { apk ->
                    session.openWrite(apk.name, 0L, apk.length()).use { output ->
                        apk.inputStream().use { input -> input.copyTo(output, COPY_BUFFER_SIZE) }
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, PackageReplacementInstallReceiver::class.java)
                    .setAction(PackageReplacementInstallReceiver.ACTION_INSTALL_RESULT)
                    .putExtra(PackageReplacementInstallReceiver.EXTRA_PACKAGE, targetPackage)
                val sender = PendingIntent.getBroadcast(
                    context,
                    targetPackage.hashCode(),
                    callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                ).intentSender
                session.commit(sender)
            }
        } catch (error: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }

    fun authorizeLaunch(intent: Intent): Intent {
        val issuedAt = System.currentTimeMillis()
        val nonceBytes = ByteArray(LAUNCH_NONCE_BYTES).also(SecureRandom()::nextBytes)
        val nonce = nonceBytes.joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
        val material = signingMaterial()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(material.privateKey)
            update(launchAuthorizationMessage(targetPackage, issuedAt, nonce))
            sign()
        }
        return intent
            .putExtra(EXTRA_LAUNCH_SCHEMA, LAUNCH_GUARD_SCHEMA)
            .putExtra(EXTRA_LAUNCH_ISSUED_AT, issuedAt)
            .putExtra(EXTRA_LAUNCH_NONCE, nonce)
            .putExtra(EXTRA_LAUNCH_SIGNATURE, Base64.encodeToString(signature, Base64.NO_WRAP))
    }

    private fun rewriteBase(
        source: File,
        output: File,
        moduleDex: File,
        moduleNative: File,
        versionCode: Long,
        alreadyPatched: Boolean,
        nativeEntry: String
    ) {
        require(NATIVE_ENTRY_PATTERN.matches(nativeEntry)) { "Unsupported direct patch ABI path" }
        ZipFile(source).use { archive ->
            val entries = archive.entries().asSequence().toList()
            val dexNumbers = entries.mapNotNull { entry ->
                DEX_NAME.matchEntire(entry.name)?.groupValues?.get(1)?.let { suffix ->
                    if (suffix.isEmpty()) 1 else suffix.toIntOrNull()
                }
            }
            val previousMarker = archive.getEntry(MARKER_ENTRY)?.let { marker ->
                runCatching {
                    archive.getInputStream(marker).bufferedReader().use { JSONObject(it.readText()) }
                }.getOrNull()
            }
            val previousDex = previousMarker?.optString("dex")
                ?.takeIf { DEX_NAME.matches(it) }
            val previousNativeEntry = previousMarker?.optString("nativeEntry")
                ?.takeIf { NATIVE_ENTRY_PATTERN.matches(it) }
                ?: LEGACY_NATIVE_ENTRY
            val embeddedDex = previousDex ?: "classes${(dexNumbers.maxOrNull() ?: 1) + 1}.dex"
            val manifestEntry = archive.getEntry(MANIFEST_ENTRY)
                ?: error("The base APK has no AndroidManifest.xml")
            val manifest = archive.getInputStream(manifestEntry).use { it.readBytes() }
            val patchedManifest = if (alreadyPatched) manifest else BinaryXmlStringPool.replaceExact(
                manifest,
                ORIGINAL_FACTORY,
                PATCH_FACTORY
            )

            val counting = CountingOutputStream(FileOutputStream(output))
            ZipOutputStream(counting).use { destination ->
                destination.setLevel(6)
                entries.forEach { entry ->
                    if (entry.isDirectory || entry.name == MANIFEST_ENTRY || entry.name == embeddedDex ||
                        entry.name == previousNativeEntry || entry.name == nativeEntry || entry.name == MARKER_ENTRY ||
                        isJarSignature(entry.name)) return@forEach
                    putCopiedEntry(archive, entry, destination, counting)
                }
                putBytes(destination, MANIFEST_ENTRY, patchedManifest, ZipEntry.STORED, counting)
                putFile(destination, embeddedDex, moduleDex, ZipEntry.DEFLATED, counting)
                putFile(destination, nativeEntry, moduleNative, ZipEntry.DEFLATED, counting)
                val marker = JSONObject()
                    .put("schema", 1)
                    .put("package", targetPackage)
                    .put("versionCode", versionCode)
                    .put("dex", embeddedDex)
                    .put("nativeEntry", nativeEntry)
                    .put("dexSha256", sha256(moduleDex))
                    .put("nativeSha256", sha256(moduleNative))
                    .put("launchGuardSchema", LAUNCH_GUARD_SCHEMA)
                    .put("launchGuardPublicKey", launchGuardPublicKey(signingMaterial()))
                    .toString()
                putBytes(destination, MARKER_ENTRY, marker.toByteArray(Charsets.UTF_8), ZipEntry.DEFLATED, counting)
            }
        }
        require(output.isFile && output.length() > 0L) { "Could not create the patched base APK" }
    }

    private fun putCopiedEntry(
        source: ZipFile,
        entry: ZipEntry,
        destination: ZipOutputStream,
        counting: CountingOutputStream
    ) {
        val copy = ZipEntry(entry.name).apply {
            time = entry.time
            method = entry.method
            if (entry.method == ZipEntry.STORED) {
                size = entry.size
                compressedSize = entry.size
                crc = entry.crc
                extra = alignmentExtra(counting.count, entry.name, alignmentFor(entry.name))
            }
        }
        destination.putNextEntry(copy)
        source.getInputStream(entry).use { input -> input.copyTo(destination, COPY_BUFFER_SIZE) }
        destination.closeEntry()
    }

    private fun putFile(
        destination: ZipOutputStream,
        name: String,
        file: File,
        method: Int,
        counting: CountingOutputStream
    ) = putBytes(destination, name, file.readBytes(), method, counting)

    private fun putBytes(
        destination: ZipOutputStream,
        name: String,
        bytes: ByteArray,
        method: Int,
        counting: CountingOutputStream
    ) {
        val entry = ZipEntry(name).apply {
            time = PATCH_TIMESTAMP
            this.method = method
            if (method == ZipEntry.STORED) {
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                crc = java.util.zip.CRC32().apply { update(bytes) }.value
                extra = alignmentExtra(counting.count, name, alignmentFor(name))
            }
        }
        destination.putNextEntry(entry)
        destination.write(bytes)
        destination.closeEntry()
    }

    private fun signApk(input: File, output: File, material: SigningMaterial) {
        val temporary = File(output.parentFile, "${output.name}.part")
        temporary.delete()
        val signer = ApkSigner.SignerConfig.Builder(
            "Jester Mods Soul Knight patch",
            material.privateKey,
            listOf(material.certificate)
        ).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(temporary)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setV4SigningEnabled(false)
            .setOtherSignersSignaturesPreserved(false)
            .build()
            .sign()
        if (output.exists()) check(output.delete()) { "Could not replace ${output.name}" }
        check(temporary.renameTo(output)) { "Could not finalize ${output.name}" }
    }

    private fun validateCluster(
        apks: List<File>,
        versionCode: Long,
        certificate: X509Certificate,
        requirePayload: Boolean
    ) {
        require(apks.isNotEmpty() && apks.all { isInside(it, patchRoot) && it.isFile && it.length() > 0L })
        val expectedSigner = sha256(certificate.encoded)
        apks.forEach { apk ->
            val verification = ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(26)
                .build()
                .verify()
            require(verification.isVerified) { "${apk.name} signature did not verify" }
            require(verification.signerCertificates.any { sha256(it.encoded) == expectedSigner }) {
                "${apk.name} has a different patch signer"
            }
        }
        val base = apks.single { it.name == "base.apk" }
        val baseInfo = archivePackageInfo(base)
        validateDirectPatchArchiveIdentity(
            actualPackageName = baseInfo.packageName,
            actualVersionCode = baseInfo.longVersionCodeCompat(),
            expectedPackageName = targetPackage,
            expectedVersionCode = versionCode
        )
        // ApkVerifier above is the signer authority for generated artifacts. Some OEM package
        // managers (including EMUI 12) return stale or inconsistent SigningInfo while parsing an
        // archive path, even though Android's apksig verification reports the expected signer.
        // Keep PackageManager here only for manifest identity; installation still performs the
        // platform's own signature validation before accepting the APK cluster.
        if (requirePayload) {
            ZipFile(base).use { archive ->
                require(archive.entries().asSequence().any { NATIVE_ENTRY_PATTERN.matches(it.name) })
                val markerEntry = requireNotNull(archive.getEntry(MARKER_ENTRY))
                val marker = archive.getInputStream(markerEntry).use {
                    JSONObject(String(it.readBytes(), Charsets.UTF_8))
                }
                require(marker.optInt("launchGuardSchema") == LAUNCH_GUARD_SCHEMA)
                require(marker.optString("launchGuardPublicKey") ==
                    Base64.encodeToString(certificate.publicKey.encoded, Base64.NO_WRAP))
                require(archive.entries().asSequence().any { DEX_NAME.matches(it.name) })
            }
            require(archiveFactory(base) == PATCH_FACTORY)
        }
    }

    private fun installedSources(info: PackageInfo): List<SourceApk> {
        val application = requireNotNull(info.applicationInfo)
        val result = mutableListOf(SourceApk("base.apk", File(application.sourceDir)))
        val splitPaths = application.splitSourceDirs.orEmpty()
        val splitNames = application.splitNames.orEmpty()
        require(splitPaths.size == splitNames.size) { "Installed game split metadata is inconsistent" }
        splitPaths.indices.forEach { index ->
            val safeName = splitNames[index].replace(Regex("[^A-Za-z0-9_.-]"), "_").take(100)
            result += SourceApk("split_$safeName.apk", File(splitPaths[index]))
        }
        return result
    }

    private fun installedFactory(): String? = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@runCatching null
        installedPackageInfo().applicationInfo?.appComponentFactory
    }.getOrNull()

    private fun archiveFactory(apk: File): String? = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@runCatching null
        archivePackageInfo(apk).applicationInfo?.appComponentFactory
    }.getOrNull()

    private fun installedPatchMarker(): JSONObject? = runCatching {
        val base = File(requireNotNull(installedPackageInfo().applicationInfo).sourceDir)
        ZipFile(base).use { archive ->
            val marker = archive.getEntry(MARKER_ENTRY) ?: return@runCatching null
            archive.getInputStream(marker).bufferedReader().use { JSONObject(it.readText()) }
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo =
        packageManager.getPackageInfo(targetPackage, signingCertificateFlags())

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(apk: File): PackageInfo =
        requireNotNull(packageManager.getPackageArchiveInfo(apk.absolutePath, signingCertificateFlags())) {
            "${apk.name} is not a valid APK"
        }

    @Suppress("DEPRECATION")
    private fun signingCertificateFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    private fun signingMaterial(): SigningMaterial {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val now = System.currentTimeMillis()
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(X500Principal("CN=Jester Mods Soul Knight Patch"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(Date(now - 86_400_000L))
                    .setCertificateNotAfter(Date(now + KEY_VALIDITY_MILLIS))
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            ?: error("Direct patch signing key is unavailable")
        val certificate = keyStore.getCertificate(KEY_ALIAS) as? X509Certificate
            ?: error("Direct patch certificate is unavailable")
        return SigningMaterial(privateKey, certificate)
    }

    private fun launchGuardPublicKey(material: SigningMaterial): String =
        Base64.encodeToString(material.certificate.publicKey.encoded, Base64.NO_WRAP)

    private fun launchAuthorizationMessage(packageName: String, issuedAt: Long, nonce: String): ByteArray =
        "$LAUNCH_MESSAGE_PREFIX\n$packageName\n$issuedAt\n$nonce".toByteArray(Charsets.UTF_8)

    private fun dexContainsLaunchGuard(file: File): Boolean {
        val needle = LAUNCH_GUARD_DEX_DESCRIPTOR.toByteArray(Charsets.UTF_8)
        val bytes = file.readBytes()
        if (needle.isEmpty() || bytes.size < needle.size) return false
        for (start in 0..bytes.size - needle.size) {
            var index = 0
            while (index < needle.size && bytes[start + index] == needle[index]) index++
            if (index == needle.size) return true
        }
        return false
    }

    private fun ensureFreeSpace(sources: List<SourceApk>) {
        val sourceBytes = sources.sumOf { it.file.length() }
        val baseBytes = sources.first().file.length()
        val required = sourceBytes + baseBytes + MIN_FREE_MARGIN
        require(StatFs(context.filesDir.absolutePath).availableBytes >= required) {
            "Direct patching needs about ${formatBytes(required)} of free storage"
        }
    }

    private fun recreateDirectory(directory: File) {
        val root = patchRoot.canonicalFile
        val target = directory.canonicalFile
        require(target.parentFile == root && target.name.matches(SAFE_DIRECTORY)) {
            "Direct patch directory escaped launcher storage"
        }
        if (target.exists()) check(target.deleteRecursively()) { "Could not clear an old direct patch" }
        check(target.mkdirs()) { "Could not create direct patch storage" }
    }

    private fun isInside(file: File, parent: File): Boolean {
        val childPath = file.canonicalPath
        val parentPath = parent.canonicalPath
        return childPath.startsWith(parentPath + File.separator)
    }

    private fun installedClusterSignedBy(sources: List<SourceApk>, certificate: X509Certificate): Boolean {
        val expectedSigner = sha256(certificate.encoded)
        return !directPatchRequiresUninstall(
            sources.map(SourceApk::file),
            expectedSigner
        ) { apk ->
            val verification = ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(26)
                .build()
                .verify()
            if (verification.isVerified) {
                verification.signerCertificates.mapTo(mutableSetOf()) { sha256(it.encoded) }
            } else {
                emptySet()
            }
        }
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }

    private fun PackageInfo.longVersionCodeCompat(): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION") versionCode.toLong()
    }

    private fun alignmentFor(name: String): Int = when {
        name.endsWith(".so", ignoreCase = true) -> 16 * 1024
        else -> 4
    }

    private fun alignmentExtra(offset: Long, name: String, alignment: Int): ByteArray? {
        val nameLength = name.toByteArray(Charsets.UTF_8).size
        val dataWithoutExtra = offset + 30L + nameLength
        var totalExtra = ((alignment - dataWithoutExtra % alignment) % alignment).toInt()
        if (totalExtra == 0) return null
        if (totalExtra < 4) totalExtra += alignment
        val dataLength = totalExtra - 4
        return ByteArray(totalExtra).also { extra ->
            extra[0] = 0x4d
            extra[1] = 0x54
            extra[2] = (dataLength and 0xff).toByte()
            extra[3] = ((dataLength shr 8) and 0xff).toByte()
        }
    }

    private fun isJarSignature(name: String): Boolean {
        if (!name.startsWith("META-INF/", ignoreCase = true)) return false
        val leaf = name.substringAfterLast('/').uppercase(Locale.US)
        return leaf == "MANIFEST.MF" || leaf.endsWith(".SF") || leaf.endsWith(".RSA") || leaf.endsWith(".DSA") ||
            leaf.endsWith(".EC")
    }

    private fun formatBytes(bytes: Long): String = String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)

    private data class SourceApk(val name: String, val file: File)
    private data class SigningMaterial(val privateKey: PrivateKey, val certificate: X509Certificate)

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var count: Long = 0L
            private set

        override fun write(value: Int) {
            out.write(value)
            count++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            count += length
        }
    }

    companion object {
        const val PATCH_FACTORY = "com.android.support.ModComponentFactory"
        private const val ORIGINAL_FACTORY = "androidx.core.app.CoreComponentFactory"
        private const val MANIFEST_ENTRY = "AndroidManifest.xml"
        private const val LEGACY_NATIVE_ENTRY = "lib/arm64-v8a/libYourSaviour.so"
        // These legacy identifiers must remain stable so already-patched Soul Knight installs
        // retain their embedded marker and Android Keystore signer during universalization.
        private const val MARKER_ENTRY = "assets/moodtools/soul-patch.json"
        private const val PATCH_DIRECTORY = "soul-knight-patches"
        private const val KEY_ALIAS = "jester_moods_soul_knight_patch_v1"
        private const val LAUNCH_GUARD_SCHEMA = 1
        private const val LAUNCH_NONCE_BYTES = 16
        private const val LAUNCH_MESSAGE_PREFIX = "jester-direct-patch-launch-v1"
        private const val LAUNCH_GUARD_DEX_DESCRIPTOR = "Lcom/android/support/DirectLaunchGuard;"
        private const val EXTRA_LAUNCH_SCHEMA = "com.moodtools.directpatch.guard.SCHEMA"
        private const val EXTRA_LAUNCH_ISSUED_AT = "com.moodtools.directpatch.guard.ISSUED_AT"
        private const val EXTRA_LAUNCH_NONCE = "com.moodtools.directpatch.guard.NONCE"
        private const val EXTRA_LAUNCH_SIGNATURE = "com.moodtools.directpatch.guard.SIGNATURE"
        private const val COPY_BUFFER_SIZE = 64 * 1024
        private const val MIN_FREE_MARGIN = 128L * 1024L * 1024L
        private const val PATCH_TIMESTAMP = 1_700_000_000_000L
        private const val KEY_VALIDITY_MILLIS = 25L * 365L * 24L * 60L * 60L * 1000L
        private val DEX_NAME = Regex("classes(\\d*)\\.dex")
        private val NATIVE_ENTRY_PATTERN = Regex("lib/(arm64-v8a|armeabi-v7a)/libYourSaviour\\.so")
        private val SAFE_DIRECTORY = Regex("[0-9]+-[0-9a-f]{16}-[0-9a-f]{16}")
        private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_.]{3,200}")
    }
}
