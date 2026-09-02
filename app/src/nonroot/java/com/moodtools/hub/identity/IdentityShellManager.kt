package com.moodtools.hub.identity

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.TypedValue
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.moodtools.hub.PackageReplacementInstallReceiver
import com.moodtools.hub.PackageReplacementKind
import com.moodtools.hub.PackageReplacementRequest
import com.moodtools.hub.modules.InstalledGame
import com.moodtools.hub.modules.ModuleIntegrityVerifier
import com.moodtools.hub.soulpatch.BinaryXmlStringPool
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
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal
import org.json.JSONObject

/** Builds a branded exact-package shell with an original-game payload retained only until import. */
internal class IdentityShellManager(
    private val context: Context,
    private val targetPackage: String
) {
    private val packageManager = context.packageManager
    private val root = File(context.filesDir, "identity-shells/$targetPackage")

    init {
        require(PACKAGE_PATTERN.matches(targetPackage)) { "Invalid identity-shell package" }
    }

    fun isInstalledShell(): Boolean = runCatching {
        val info = packageManager.getApplicationInfo(targetPackage, PackageManager.GET_META_DATA)
        info.metaData?.getBoolean(IDENTITY_METADATA, false) == true
    }.getOrDefault(false)

    fun requiresReplacement(game: InstalledGame): Boolean =
        game.packageName == targetPackage && !isInstalledShell()

    /** Removes launcher-owned shell material only after Android no longer has the shell installed. */
    fun removePreparedArtifacts() {
        check(!isInstalledShell()) { "The installed identity shell must be uninstalled first" }
        val storageRoot = File(context.filesDir, "identity-shells").canonicalFile
        val packageRoot = root.canonicalFile
        require(packageRoot.parentFile == storageRoot && packageRoot.name == targetPackage) {
            "Identity-shell directory escaped launcher storage"
        }
        if (packageRoot.exists()) {
            check(packageRoot.deleteRecursively() && !packageRoot.exists()) {
                "Could not remove identity-shell files for $targetPackage"
            }
        }
        if (storageRoot.isDirectory && storageRoot.listFiles().isNullOrEmpty()) {
            storageRoot.delete()
        }
    }

    fun prepare(
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)? = null
    ): PackageReplacementRequest {
        require(game.packageName == targetPackage) { "The shell request targets another game" }
        require(game.versionSupported && game.abiSupported) { "This game build is not supported" }
        require(!isInstalledShell()) {
            "The identity shell is already installed. Open it directly or repair it from Jester Mods."
        }
        val moduleDirectory = File(context.filesDir, "menus/$targetPackage")
        ModuleIntegrityVerifier().verify(moduleDirectory, game.module, game.abi)

        onProgress?.invoke("Staging ${game.module.title}", "Temporarily copying the untouched APK split set for shell import.")
        val installed = installedPackageInfo()
        val sources = installedSources(installed)
        require(sources.all { it.file.isFile && it.file.canRead() && it.file.length() > 0L }) {
            "Android did not expose the complete installed game package"
        }
        if (!root.mkdirs() && !root.isDirectory) error("Could not create identity-shell storage")
        val gamePayload = File(root, GAME_PAYLOAD)
        writeGamePayload(sources, gamePayload)

        val label = packageManager.getApplicationLabel(requireNotNull(installed.applicationInfo))
            .toString().trim().ifEmpty { game.module.title }
        val branding = renderBranding(packageManager.getApplicationIcon(targetPackage))
        writeMetadata(game, label, gamePayload)

        onProgress?.invoke("Creating $label shell", "Applying the original game name and icon.")
        val unsigned = File(root, "shell-unsigned.apk")
        val signed = File(root, "shell.apk")
        context.assets.open(TEMPLATE_ASSET).use { input ->
            val template = File(root, "shell-template.apk")
            FileOutputStream(template).use { output -> input.copyTo(output, COPY_BUFFER_SIZE) }
            rewriteTemplate(template, unsigned, label, branding)
            check(template.delete()) { "Could not clear the temporary shell template" }
        }
        val material = signingMaterial()
        signApk(unsigned, signed, material)
        check(unsigned.delete()) { "Could not clear the unsigned shell" }
        validateShell(signed, material.certificate)

        onProgress?.invoke(
            "Shell ready",
            "Android will replace the original installation with a branded compatibility shell."
        )
        return PackageReplacementRequest(
            packageName = targetPackage,
            title = label,
            versionCode = game.versionCode,
            apks = listOf(signed),
            requiresUninstall = true,
            kind = PackageReplacementKind.IDENTITY_SHELL
        )
    }

    fun install(request: PackageReplacementRequest) {
        require(request.kind == PackageReplacementKind.IDENTITY_SHELL &&
            request.packageName == targetPackage && request.apks.size == 1) {
            "Invalid identity-shell installation request"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            require(packageManager.canRequestPackageInstalls()) {
                "Allow Jester Mods to install unknown apps first"
            }
        }
        validateShell(request.apks.single(), signingMaterial().certificate)
        val installer = packageManager.packageInstaller
        val apk = request.apks.single()
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(targetPackage)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0L, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output, COPY_BUFFER_SIZE) }
                    session.fsync(output)
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

    /** Issues a short-lived proof that this shell launch originated in Jester Mods. */
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

    private fun writeGamePayload(sources: List<SourceApk>, output: File) {
        val incoming = File(root, "$GAME_PAYLOAD.incoming")
        incoming.delete()
        ZipOutputStream(FileOutputStream(incoming).buffered()).use { zip ->
            zip.setLevel(1)
            sources.forEach { source ->
                zip.putNextEntry(ZipEntry(source.name).apply { time = source.file.lastModified() })
                source.file.inputStream().buffered().use { it.copyTo(zip, COPY_BUFFER_SIZE) }
                zip.closeEntry()
            }
        }
        check(incoming.length() > 0L) { "The original game backup is empty" }
        if (output.exists()) check(output.delete()) { "Could not replace the original game backup" }
        check(incoming.renameTo(output)) { "Could not finalize the original game backup" }
    }

    private fun rewriteTemplate(template: File, output: File, label: String, branding: Branding) {
        val brandingEntries = resolveBrandingEntries(template)
        ZipFile(template).use { source ->
            val counting = CountingOutputStream(FileOutputStream(output))
            ZipOutputStream(counting).use { destination ->
                destination.setLevel(6)
                source.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory || isJarSignature(entry.name)) return@forEach
                    val replacement = when {
                        entry.name == MANIFEST_ENTRY -> {
                            val manifest = source.getInputStream(entry).use { it.readBytes() }
                            BinaryXmlStringPool.replaceSubstring(
                                BinaryXmlStringPool.replaceExact(manifest, LABEL_MARKER, label),
                                TEMPLATE_PACKAGE,
                                targetPackage
                            )
                        }
                        entry.name == brandingEntries.legacy -> branding.legacy
                        entry.name == brandingEntries.foreground -> branding.foreground
                        entry.name == brandingEntries.background -> branding.background
                        else -> null
                    }
                    if (replacement != null) {
                        putBytes(destination, entry.name, replacement, entry.method, counting)
                    } else {
                        putCopiedEntry(source, entry, destination, counting)
                    }
                }
            }
        }
        require(output.isFile && output.length() > 0L) { "Could not create the identity shell" }
    }

    private fun resolveBrandingEntries(template: File): BrandingEntries {
        val archiveInfo = requireNotNull(packageManager.getPackageArchiveInfo(template.absolutePath, 0)) {
            "Identity-shell template is not a valid APK"
        }
        val application = requireNotNull(archiveInfo.applicationInfo).apply {
            sourceDir = template.absolutePath
            publicSourceDir = template.absolutePath
        }
        val resources = packageManager.getResourcesForApplication(application)
        fun resourcePath(name: String): String {
            val id = resources.getIdentifier(name, "drawable", TEMPLATE_PACKAGE)
            require(id != 0) { "Identity-shell template is missing drawable/$name" }
            val value = TypedValue()
            resources.getValue(id, value, true)
            val path = value.string?.toString().orEmpty()
            require(path.startsWith("res/") && path.endsWith(".png", ignoreCase = true)) {
                "Identity-shell drawable/$name is not a replaceable PNG"
            }
            return path
        }
        val result = BrandingEntries(
            legacy = resourcePath(LEGACY_ICON_RESOURCE),
            foreground = resourcePath(FOREGROUND_ICON_RESOURCE),
            background = resourcePath(BACKGROUND_ICON_RESOURCE)
        )
        require(setOf(result.legacy, result.foreground, result.background).size == 3) {
            "Identity-shell branding resources must use distinct entries"
        }
        ZipFile(template).use { archive ->
            listOf(result.legacy, result.foreground, result.background).forEach { path ->
                require(archive.getEntry(path) != null) {
                    "Identity-shell branding entry is missing: $path"
                }
            }
        }
        return result
    }

    private fun renderBranding(icon: Drawable): Branding {
        val legacy = renderDrawable(icon, ICON_SIZE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable) {
            return Branding(
                legacy = legacy,
                foreground = renderDrawable(icon.foreground, ICON_SIZE),
                background = renderDrawable(icon.background, ICON_SIZE)
            )
        }
        return Branding(
            legacy = legacy,
            foreground = legacy,
            background = renderDrawable(ColorDrawable(Color.BLACK), ICON_SIZE)
        )
    }

    private fun renderDrawable(drawable: Drawable, size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not render the game icon"
            }
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun writeMetadata(game: InstalledGame, label: String, payload: File) {
        val metadata = JSONObject()
            .put("schema", 1)
            .put("package", targetPackage)
            .put("label", label)
            .put("versionName", game.versionName)
            .put("versionCode", game.versionCode)
            .put("abi", game.abi)
            .put("payloadSha256", sha256(payload))
        File(root, METADATA_FILE).writeText(metadata.toString())
    }

    private fun validateShell(apk: File, certificate: X509Certificate) {
        require(apk.canonicalFile.parentFile == root.canonicalFile && apk.isFile && apk.length() > 0L)
        val archiveInfo = requireNotNull(packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_META_DATA
        )) { "Generated shell is not a valid APK" }
        require(archiveInfo.packageName == targetPackage) { "Generated shell has the wrong package identity" }
        require(archiveInfo.applicationInfo?.metaData?.getBoolean(IDENTITY_METADATA, false) == true) {
            "Generated package is not an identity shell"
        }
        val result = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(26).build().verify()
        require(result.isVerified) { "Generated identity shell signature is invalid" }
        val expected = sha256(certificate.encoded)
        require(result.signerCertificates.any { sha256(it.encoded) == expected }) {
            "Generated identity shell has the wrong signer"
        }
    }

    private fun installedSources(info: PackageInfo): List<SourceApk> {
        val application = requireNotNull(info.applicationInfo)
        val result = mutableListOf(SourceApk("base.apk", File(application.sourceDir)))
        val paths = application.splitSourceDirs.orEmpty()
        val names = application.splitNames.orEmpty()
        require(paths.size == names.size) { "Installed game split metadata is inconsistent" }
        paths.indices.forEach { index ->
            val safeName = names[index].replace(Regex("[^A-Za-z0-9_.-]"), "_").take(100)
            result += SourceApk("split_$safeName.apk", File(paths[index]))
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo = packageManager.getPackageInfo(
        targetPackage,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
    )

    private fun signingMaterial(): SigningMaterial {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) {
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
                    .setCertificateSubject(X500Principal("CN=Jester Mods Identity Shell"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(Date(now - 86_400_000L))
                    .setCertificateNotAfter(Date(now + KEY_VALIDITY_MILLIS))
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }
        val key = store.getKey(KEY_ALIAS, null) as? PrivateKey
            ?: error("Identity-shell signing key is unavailable")
        val certificate = store.getCertificate(KEY_ALIAS) as? X509Certificate
            ?: error("Identity-shell certificate is unavailable")
        return SigningMaterial(key, certificate)
    }

    private fun signApk(input: File, output: File, material: SigningMaterial) {
        val temporary = File(root, "${output.name}.part")
        temporary.delete()
        val signer = ApkSigner.SignerConfig.Builder(
            "Jester Mods identity shell",
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
        if (output.exists()) check(output.delete()) { "Could not replace the generated shell" }
        check(temporary.renameTo(output)) { "Could not finalize the generated shell" }
    }

    private fun launchAuthorizationMessage(
        packageName: String,
        issuedAt: Long,
        nonce: String
    ): ByteArray =
        "$LAUNCH_MESSAGE_PREFIX\n$packageName\n$issuedAt\n$nonce".toByteArray(Charsets.UTF_8)

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
        source.getInputStream(entry).use { it.copyTo(destination, COPY_BUFFER_SIZE) }
        destination.closeEntry()
    }

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
                crc = CRC32().apply { update(bytes) }.value
                extra = alignmentExtra(counting.count, name, alignmentFor(name))
            }
        }
        destination.putNextEntry(entry)
        destination.write(bytes)
        destination.closeEntry()
    }

    private fun alignmentFor(name: String): Int =
        if (name.endsWith(".so", ignoreCase = true)) 16 * 1024 else 4

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
        return leaf == "MANIFEST.MF" || leaf.endsWith(".SF") || leaf.endsWith(".RSA") ||
            leaf.endsWith(".DSA") || leaf.endsWith(".EC")
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

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }

    private data class SourceApk(val name: String, val file: File)
    private data class Branding(val legacy: ByteArray, val foreground: ByteArray, val background: ByteArray)
    private data class BrandingEntries(val legacy: String, val foreground: String, val background: String)
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
        const val IDENTITY_METADATA = "com.moodtools.identity_shell"
        const val METADATA_FILE = "metadata.json"
        private const val TEMPLATE_ASSET = "identity-shell/template.apk"
        private const val TEMPLATE_PACKAGE = "com.moodtools.identity.template"
        private const val LABEL_MARKER = "__IDENTITY_SHELL_LABEL__"
        private const val GAME_PAYLOAD = "game.apks"
        private const val MANIFEST_ENTRY = "AndroidManifest.xml"
        private const val LEGACY_ICON_RESOURCE = "identity_shell_icon"
        private const val FOREGROUND_ICON_RESOURCE = "identity_shell_icon_foreground_bitmap"
        private const val BACKGROUND_ICON_RESOURCE = "identity_shell_icon_background_bitmap"
        private const val KEY_ALIAS = "jester_moods_identity_shell_v1"
        private const val LAUNCH_GUARD_SCHEMA = 1
        private const val LAUNCH_NONCE_BYTES = 16
        private const val LAUNCH_MESSAGE_PREFIX = "jester-identity-shell-launch-v1"
        private const val EXTRA_LAUNCH_SCHEMA = "com.moodtools.identity.guard.SCHEMA"
        private const val EXTRA_LAUNCH_ISSUED_AT = "com.moodtools.identity.guard.ISSUED_AT"
        private const val EXTRA_LAUNCH_NONCE = "com.moodtools.identity.guard.NONCE"
        private const val EXTRA_LAUNCH_SIGNATURE = "com.moodtools.identity.guard.SIGNATURE"
        private const val COPY_BUFFER_SIZE = 128 * 1024
        private const val ICON_SIZE = 432
        private const val PATCH_TIMESTAMP = 1_700_000_000_000L
        private const val KEY_VALIDITY_MILLIS = 25L * 365L * 24L * 60L * 60L * 1000L
        private val PACKAGE_PATTERN = Regex("[A-Za-z0-9_.]{3,200}")
    }
}
