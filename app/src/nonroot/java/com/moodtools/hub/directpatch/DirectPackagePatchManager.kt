package com.moodtools.hub.directpatch

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
import com.moodtools.hub.modules.ModuleRepository
import com.moodtools.hub.modules.PluginLoader
import com.moodtools.hub.modules.RootMethod
import com.moodtools.hub.networking.GameObbManager
import com.moodtools.hub.RemoteLaunchDiagnostics
import com.moodtools.hub.GmsCoreSupport
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
            val verifiedModule = ModuleIntegrityVerifier().verify(directory, game.module, game.abi)
            // nativeSha256 below includes the recipe overlay, so recipe updates already
            // invalidate this lightweight Library check without inflating the ZIP here.
            !directPatchRevisionIsCurrent(
                markerRevision(marker),
                DirectPatchRevision(
                    markerSchema = PATCH_MARKER_SCHEMA,
                    gameVersionCode = game.versionCode,
                    moduleBuild = verifiedModule.build,
                    moduleVersion = verifiedModule.version,
                    launchGuardSchema = LAUNCH_GUARD_SCHEMA,
                    launchGuardPublicKey = launchGuardPublicKey(signingMaterial()),
                    dexSha256 = sha256(File(directory, game.module.dexFile)),
                    nativeSha256 = sha256(File(directory, game.module.nativeFile)),
                    gameNativeSha256 = expectedGameNativeSha256(game),
                    gmsProfile = gmsProfile()
                )
            )
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
        if (installed.longVersionCodeCompat() != request.versionCode) {
            return@runCatching false
        }
        val expectedBase = request.apks.singleOrNull { it.name == "base.apk" }
            ?: return@runCatching false
        val expectedMarker = ZipFile(expectedBase).use { archive ->
            val marker = archive.getEntry(MARKER_ENTRY) ?: return@use null
            archive.getInputStream(marker).use { JSONObject(String(it.readBytes(), Charsets.UTF_8)) }
        } ?: return@runCatching false
        val actualMarker = installedPatchMarker() ?: return@runCatching false
        val recipe = moduleRecipe()
        if (actualMarker.optString("compatibilityProfile") != recipe?.identity.orEmpty()) {
            return@runCatching false
        }
        recipe?.let { ModuleCompatibility.verify(installedSources(installed).map { it.file }, it) }
        val expectedRevision = markerRevision(expectedMarker)
        when (expectedMarker.optString("patchKind")) {
            EXTERNAL_CONTROLLER_PATCH -> {
                val expectedGameNative = expectedRevision.gameNativeSha256
                if (expectedGameNative.isBlank()) return@runCatching false
                val nativeEntry = gameNativeEntry("arm64-v8a")
                val installedHashes = installedSources(installed).mapNotNull { source ->
                    ZipFile(source.file).use { archive ->
                        archive.getEntry(nativeEntry)?.let { sha256(archive.getInputStream(it)) }
                    }
                }
                if (installedHashes.singleOrNull() != expectedGameNative) return@runCatching false
            }
            LOADER_PATCH -> if (installedFactory() != PATCH_FACTORY) return@runCatching false
            else -> return@runCatching false
        }
        expectedRevision.markerSchema == PATCH_MARKER_SCHEMA &&
            expectedRevision.moduleVersion.isNotBlank() &&
            expectedRevision.launchGuardPublicKey.isNotBlank() &&
            expectedRevision.dexSha256.isNotBlank() &&
            expectedRevision.nativeSha256.isNotBlank() &&
            directPatchRevisionIsCurrent(markerRevision(actualMarker), expectedRevision)
    }.getOrDefault(false)

    fun prepare(
        game: InstalledGame,
        onProgress: ((headline: String, detail: String) -> Unit)? = null
    ): PackageReplacementRequest {
        require(game.packageName == targetPackage) { "The direct patch request targets another game" }
        require(game.versionSupported && game.abiSupported) { "This game build is not supported" }
        require(!com.moodtools.hub.identity.IdentityShellManager(context, targetPackage).isInstalledShell()) {
            "Restore the original game before changing this device to Patch."
        }
        val gmsProfile = gmsProfile()
        if (gmsProfile.isNotEmpty()) {
            val companionState = GmsCoreSupport.state(context)
            require(companionState == GmsCoreSupport.State.READY) {
                GmsCoreSupport.setupFailureDetail(companionState)
            }
        }
        val moduleDirectory = File(context.filesDir, "menus/$targetPackage")
        val verifiedModule = ModuleIntegrityVerifier().verify(moduleDirectory, game.module, game.abi)
        val moduleDex = File(moduleDirectory, game.module.dexFile)
        val moduleNative = File(moduleDirectory, game.module.nativeFile)
        require(moduleDex.isFile && moduleDex.length() > 0L) { "Direct patch loader DEX is missing" }
        require(moduleNative.isFile && moduleNative.length() > 0L) { "Direct patch native payload is missing" }
        val externalControllerOnly = game.module.rootMethod == RootMethod.EXTERNAL_CONTROLLER
        require(externalControllerOnly || dexContainsLaunchGuard(moduleDex)) {
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
        // Fail before compatibility processing, keystore creation or touching patch output.
        val previousMarker = installedPatchMarker()
        val sourceAlreadyPatched = installedFactory() == PATCH_FACTORY || previousMarker != null
        val originalSigner = originalGameSigner(sources.first().file, previousMarker, sourceAlreadyPatched)
        requireOriginalGameSigner(gmsProfile, originalSigner)
        val compatibility = ModuleCompatibility.fromNative(moduleNative, targetPackage)?.let { recipe ->
            onProgress?.invoke("Checking game compatibility", "Verifying startup code and native split contents.")
            val plan = try {
                ModuleCompatibility.plan(sources.map { it.file }, game.versionCode, recipe)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("${error.message}\nLoaded module native SHA256: ${sha256(moduleNative)}", error)
            }
            plan.also {
                onProgress?.invoke("Game compatibility", if (it.alreadyCompatible)
                    "The installed game already has this restoration; it will not be applied again."
                else "Applying the exact verified startup restoration before embedding the menu.")
            }
        }
        val expectedGameNativeSha256 = if (externalControllerOnly) {
            expectedGameNativeSha256(game).also { require(it.isNotBlank()) }
        } else ""
        val gameNativeSource = if (externalControllerOnly) {
            findAndVerifyGameNativeSource(sources, game.abi, expectedGameNativeSha256)
        } else null

        val outputDirectory = File(patchRoot, directPatchDirectoryName(
            game.versionCode, PATCH_MARKER_SCHEMA, verifiedModule.build,
            sha256(moduleDex), sha256(moduleNative), gmsProfile + compatibility?.recipe?.identity.orEmpty()
        ))
        val finalDirectory = File(outputDirectory, "signed")
        val signingMaterial = signingMaterial()
        // PackageInfo.signingInfo is not reliable enough for this decision on every OEM. In
        // particular, some devices have reported stale or empty signer data for an installed APK.
        // Verify the actual installed split files instead: an exact signer match means Android can
        // accept this as an in-place update and preserve the game's data.
        val requiresUninstall = !installedClusterSignedBy(sources, signingMaterial.certificate)
        val inheritExistingInstallation = sourceAlreadyPatched && !requiresUninstall
        val sourcesToWrite = if (inheritExistingInstallation) {
            sources.filter { it.name == "base.apk" || it.name == gameNativeSource?.name ||
                it.file in compatibility?.replacements.orEmpty() }
        } else sources
        val expectedFiles = sourcesToWrite.map { File(finalDirectory, it.name) }
        if (expectedFiles.all(File::isFile) && runCatching {
                validateCluster(expectedFiles, game.versionCode, signingMaterial.certificate, true, inheritExistingInstallation)
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
                requiresUninstall,
                inheritExistingInstallation = inheritExistingInstallation
            )
        }

        ensureFreeSpace(sourcesToWrite)
        recreateDirectory(outputDirectory)
        finalDirectory.mkdirs()
        check(finalDirectory.isDirectory) { "Could not create direct patch storage" }
        val unsignedBase = File(outputDirectory, "base-unsigned.apk")
        val signedBase = File(finalDirectory, "base.apk")
        val embeddedGameLibrary = if (externalControllerOnly) {
            prepareEmbeddedGameLibrary(game, moduleDirectory, outputDirectory, expectedGameNativeSha256)
        } else null

        try {
            onProgress?.invoke(
                "Patching ${game.module.title}",
                if (externalControllerOnly) "Replacing the verified game library in the APK set."
                else "Embedding the verified loader into the base APK."
            )
            rewriteBase(
                sources.first().file,
                unsignedBase,
                moduleDex,
                moduleNative,
                game.versionCode,
                verifiedModule.build,
                verifiedModule.version,
                originalSigner,
                "lib/${game.abi}/libYourSaviour.so",
                externalControllerOnly,
                expectedGameNativeSha256,
                embeddedGameLibrary?.takeIf { gameNativeSource?.name == "base.apk" },
                compatibility?.replacements?.get(sources.first().file).orEmpty()
            )
            signApk(unsignedBase, signedBase, signingMaterial)
            check(unsignedBase.delete()) { "Could not clear the temporary base APK" }

            sourcesToWrite.drop(1).forEachIndexed { index, source ->
                onProgress?.invoke(
                    "Signing ${game.module.title}",
                    "Preparing APK split ${index + 1} of ${sources.size - 1}."
                )
                val signedSplit = File(finalDirectory, source.name)
                if (embeddedGameLibrary != null && source.name == gameNativeSource?.name) {
                    val unsignedSplit = File(outputDirectory, "${source.name}.unsigned")
                    rewriteNativeLibrarySplit(
                        source.file,
                        unsignedSplit,
                        "lib/${game.abi}/libil2cpp.so",
                        embeddedGameLibrary
                    )
                    signApk(unsignedSplit, signedSplit, signingMaterial)
                    check(unsignedSplit.delete()) { "Could not clear temporary native split" }
                } else if (compatibility?.replacements?.containsKey(source.file) == true) {
                    val unsignedSplit = File(outputDirectory, "${source.name}.compatibility-unsigned")
                    rewriteCompatibilitySplit(source.file, unsignedSplit, compatibility.replacements.getValue(source.file))
                    signApk(unsignedSplit, signedSplit, signingMaterial)
                    check(unsignedSplit.delete()) { "Could not clear temporary compatibility split" }
                } else {
                    signApk(source.file, signedSplit, signingMaterial)
                }
            }
            validateCluster(expectedFiles, game.versionCode, signingMaterial.certificate, true, inheritExistingInstallation)
            compatibility?.let { ModuleCompatibility.verify(
                sources.map { source -> File(finalDirectory, source.name).takeIf(File::isFile) ?: source.file }, it.recipe) }
            embeddedGameLibrary?.let { check(it.delete()) { "Could not clear the extracted game library" } }
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
                requiresUninstall,
                inheritExistingInstallation = inheritExistingInstallation
            )
        } catch (error: Throwable) {
            runCatching { recreateDirectory(outputDirectory) }
            if (error is GmsCorePatchFailure) runCatching {
                // Separate bounded events survive the setup report's 320-character/event limit.
                val evidence = listOf("Game version code: ${game.versionCode}", "GmsCore profile: $gmsProfile") +
                    error.diagnosticLines()
                evidence.forEach { onProgress?.invoke("Google account support", it) }
            }
            throw error
        }
    }

    fun install(request: PackageReplacementRequest) {
        require(request.packageName == targetPackage && request.apks.isNotEmpty()) {
            "Invalid direct package replacement request"
        }
        require(!request.inheritExistingInstallation || !request.requiresUninstall) {
            "An inherited patch update cannot require uninstall"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            require(packageManager.canRequestPackageInstalls()) {
                "Allow Jester Mods to install unknown apps first"
            }
        }
        // The first signer-changing replacement uninstalls the original, which can delete its OBBs.
        GameObbManager(context).install(targetPackage, request.versionCode)
        val signingMaterial = signingMaterial()
        validateCluster(request.apks, request.versionCode, signingMaterial.certificate, true, request.inheritExistingInstallation)
        if (request.inheritExistingInstallation) {
            require(runCatching { installedPackageInfo() }.isSuccess && isPatchedInstallation()) {
                "The patched game is no longer installed; prepare the patch again"
            }
        }

        val installer = packageManager.packageInstaller
        val installMode = if (request.inheritExistingInstallation) {
            PackageInstaller.SessionParams.MODE_INHERIT_EXISTING
        } else {
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        }
        val params = PackageInstaller.SessionParams(installMode).apply {
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
        GameObbManager(context).install(targetPackage, installedPackageInfo().longVersionCodeCompat())
        RemoteLaunchDiagnostics.begin(context, targetPackage, "direct_patch")
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
        moduleBuild: Long,
        moduleVersion: String,
        originalSigner: String,
        nativeEntry: String,
        externalControllerOnly: Boolean,
        gameNativeSha256: String,
        embeddedGameLibrary: File?,
        compatibilityEntries: Map<String, ByteArray> = emptyMap()
    ) {
        require(NATIVE_ENTRY_PATTERN.matches(nativeEntry)) { "Unsupported direct patch ABI path" }
        ZipFile(source).use { archive ->
            val entries = archive.entries().asSequence().toList()
            val dexNumbers = (entries.map { it.name } + compatibilityEntries.keys).mapNotNull { name ->
                DEX_NAME.matchEntire(name)?.groupValues?.get(1)?.let { suffix ->
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
            val diagnosticDex = previousMarker?.optString("diagnosticDex")?.takeIf { DEX_NAME.matches(it) }
                ?: "classes${(dexNumbers.maxOrNull() ?: 1) + if (externalControllerOnly) 1 else 2}.dex"
            val manifestEntry = archive.getEntry(MANIFEST_ENTRY)
                ?: error("The base APK has no AndroidManifest.xml")
            val manifest = compatibilityEntries[MANIFEST_ENTRY]
                ?: archive.getInputStream(manifestEntry).use { it.readBytes() }
            val cleanedManifest = BinaryXmlStringPool.withoutPairIpManifestEntries(manifest)
            val gmsProfile = gmsProfile()
            val previousGms = previousMarker?.optString("gmsProfile").orEmpty()
            requireOriginalGameSigner(gmsProfile, originalSigner)
            val gms = DirectGmsArchive.prepare(archive, gmsProfile.isNotEmpty(), previousMarker,
                setOf(embeddedDex, diagnosticDex), gmsRules(), compatibilityEntries.filterKeys { DEX_NAME.matches(it) })
            val gmsManifest = if (gmsProfile.isNotEmpty() || previousGms.isNotEmpty())
                BinaryXmlStringPool.withGmsCore(cleanedManifest, gmsProfile.isNotEmpty(), targetPackage, originalSigner)
                else cleanedManifest
            val patchedManifest = BinaryXmlStringPool.withDiagnosticProvider(gmsManifest, targetPackage)
            val delegateFactory = if (!externalControllerOnly) MODULE_FACTORY else {
                val factory = archiveFactory(source).orEmpty()
                if (factory == PATCH_FACTORY) archive.getEntry(RemoteLaunchDiagnostics.CONFIG)?.let {
                    archive.getInputStream(it).use { stream -> JSONObject(String(stream.readBytes())).optString("factory") }
                }.orEmpty() else factory
            }.let { factory ->
                // Do not restore a removed PairIP factory through the diagnostic delegate.
                factory.takeUnless { it.contains("pairip", ignoreCase = true) }.orEmpty()
            }

            val counting = CountingOutputStream(FileOutputStream(output))
            ZipOutputStream(counting).use { destination ->
                destination.setLevel(6)
                entries.forEach { entry ->
                    if (entry.isDirectory || entry.name == MANIFEST_ENTRY || entry.name == MARKER_ENTRY ||
                        entry.name == GMS_ORIGINAL_ENTRY || entry.name.startsWith(DirectGmsArchive.BACKUP_PREFIX) ||
                        entry.name in gms.replacements || entry.name in compatibilityEntries ||
                        entry.name == RemoteLaunchDiagnostics.CONFIG || entry.name == diagnosticDex ||
                        isJarSignature(entry.name) || (!externalControllerOnly &&
                            (entry.name == embeddedDex || entry.name == previousNativeEntry ||
                                entry.name == nativeEntry))) return@forEach
                    if (embeddedGameLibrary != null && entry.name == gameNativeEntry("arm64-v8a")) {
                        putStoredFile(destination, entry.name, embeddedGameLibrary, counting)
                    } else {
                        putCopiedEntry(archive, entry, destination, counting)
                    }
                }
                putBytes(destination, MANIFEST_ENTRY, patchedManifest, ZipEntry.STORED, counting)
                compatibilityEntries.filterKeys { it != MANIFEST_ENTRY && it !in gms.replacements }
                    .forEach { (name, bytes) -> putBytes(destination, name, bytes,
                        if (name.endsWith(".so")) ZipEntry.STORED else ZipEntry.DEFLATED, counting) }
                gms.replacements.forEach { (name, bytes) -> putBytes(destination, name, bytes, ZipEntry.DEFLATED, counting) }
                gms.originals.forEach { (name, bytes) ->
                    putBytes(destination, DirectGmsArchive.BACKUP_PREFIX + name, bytes, ZipEntry.DEFLATED, counting)
                }
                val bootstrapDex = java.util.zip.ZipInputStream(context.assets.open("diagnostic-bootstrap/template.apk")).use { template ->
                    var result: ByteArray? = null
                    while (true) {
                        val entry = template.nextEntry ?: break
                        if (entry.name == "classes.dex") { result = template.readBytes(); break }
                    }
                    requireNotNull(result).also { require(it.size in 1..(1024 * 1024)) }
                }
                putBytes(destination, diagnosticDex, bootstrapDex, ZipEntry.DEFLATED, counting)
                putBytes(destination, RemoteLaunchDiagnostics.CONFIG,
                    RemoteLaunchDiagnostics.configuration(context, "direct_patch", delegateFactory, gmsProfile), ZipEntry.DEFLATED, counting)
                if (!externalControllerOnly) {
                    putFile(destination, embeddedDex, moduleDex, ZipEntry.DEFLATED, counting)
                    // Games with extractNativeLibs=false must mmap an uncompressed, page-aligned library.
                    putStoredFile(destination, nativeEntry, moduleNative, counting)
                }
                val compatibilityProfile = ModuleCompatibility.fromNative(moduleNative, targetPackage)?.identity.orEmpty()
                val marker = JSONObject()
                    .put("schema", PATCH_MARKER_SCHEMA)
                    .put("package", targetPackage)
                    .put("versionCode", versionCode)
                    .put("moduleBuild", moduleBuild)
                    .put("moduleVersion", moduleVersion)
                    .put("patchKind", if (externalControllerOnly) EXTERNAL_CONTROLLER_PATCH else LOADER_PATCH)
                    .put("dex", if (externalControllerOnly) "" else embeddedDex)
                    .put("diagnosticDex", diagnosticDex)
                    .put("gmsProfile", gmsProfile)
                    .put("compatibilityProfile", compatibilityProfile)
                    .put("compatibilityManifestSha256", if (compatibilityProfile.isNotEmpty())
                        GmsCoreDexPatcher.hash(patchedManifest) else "")
                    .put("originalSignerSha1", originalSigner)
                    .put("gmsOriginals", JSONObject(gms.originals.mapValues { GmsCoreDexPatcher.hash(it.value) }))
                    .put("gmsPatched", JSONObject(gms.replacements.mapValues { GmsCoreDexPatcher.hash(it.value) }))
                    .put("nativeEntry", if (externalControllerOnly) "" else nativeEntry)
                    .put("dexSha256", sha256(moduleDex))
                    .put("nativeSha256", sha256(moduleNative))
                    .put("gameNativeSha256", gameNativeSha256)
                    .put("launchGuardSchema", LAUNCH_GUARD_SCHEMA)
                    .put("launchGuardPublicKey", launchGuardPublicKey(signingMaterial()))
                    .toString()
                putBytes(destination, MARKER_ENTRY, marker.toByteArray(Charsets.UTF_8), ZipEntry.DEFLATED, counting)
            }
        }
        require(output.isFile && output.length() > 0L) { "Could not create the patched base APK" }
    }

    private fun moduleRecipe(): ModuleCompatibility.Recipe? {
        val directory = File(context.filesDir, "menus/$targetPackage")
        val module = requireNotNull(ModuleRepository(context).loadCandidate(directory)) { "Module configuration is missing" }
        // The existing signed manifest authenticates the complete native file, including its
        // optional recipe overlay. Local TEST installs retain their explicit development trust.
        require(module.supportedAbis.any { abi ->
            runCatching { ModuleIntegrityVerifier().verify(directory, module, abi) }.isSuccess
        }) { "Module payload failed authentication" }
        return ModuleCompatibility.fromNative(File(directory, module.nativeFile), targetPackage)
    }

    private fun rewriteCompatibilitySplit(source: File, output: File, replacements: Map<String, ByteArray>) {
        ZipFile(source).use { archive ->
            require(replacements.keys.all { archive.getEntry(it) != null && it.endsWith(".so") })
            val counting = CountingOutputStream(FileOutputStream(output))
            ZipOutputStream(counting).use { destination ->
                archive.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory || isJarSignature(entry.name) || entry.name in replacements) return@forEach
                    putCopiedEntry(archive, entry, destination, counting)
                }
                replacements.forEach { (name, bytes) ->
                    putBytes(destination, name, bytes, ZipEntry.STORED, counting)
                }
            }
        }
    }

    private fun rewriteNativeLibrarySplit(
        source: File,
        output: File,
        nativeEntry: String,
        replacement: File
    ) {
        ZipFile(source).use { archive ->
            requireNotNull(archive.getEntry(nativeEntry)) { "Native split is missing $nativeEntry" }
            val counting = CountingOutputStream(FileOutputStream(output))
            ZipOutputStream(counting).use { destination ->
                destination.setLevel(6)
                archive.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory || entry.name == nativeEntry || isJarSignature(entry.name)) return@forEach
                    putCopiedEntry(archive, entry, destination, counting)
                }
                putStoredFile(destination, nativeEntry, replacement, counting)
            }
        }
        require(output.isFile && output.length() > 0L) { "Could not rewrite the native split" }
    }

    private fun putStoredFile(
        destination: ZipOutputStream,
        name: String,
        file: File,
        counting: CountingOutputStream
    ) {
        val crc = java.util.zip.CRC32().also { checksum ->
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    checksum.update(buffer, 0, count)
                }
            }
        }.value
        val entry = ZipEntry(name).apply {
            time = PATCH_TIMESTAMP
            method = ZipEntry.STORED
            size = file.length()
            compressedSize = size
            this.crc = crc
            extra = alignmentExtra(counting.count, name, alignmentFor(name))
        }
        destination.putNextEntry(entry)
        file.inputStream().buffered().use { it.copyTo(destination, COPY_BUFFER_SIZE) }
        destination.closeEntry()
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
            "Jester Mods game patch",
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
        requirePayload: Boolean,
        inheritExistingInstallation: Boolean
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
            val compatibilityRecipe = moduleRecipe()
            val marker = ZipFile(base).use { archive ->
                val markerEntry = requireNotNull(archive.getEntry(MARKER_ENTRY))
                val marker = archive.getInputStream(markerEntry).use {
                    JSONObject(String(it.readBytes(), Charsets.UTF_8))
                }
                require(marker.optInt("schema") == PATCH_MARKER_SCHEMA)
                require(marker.optString("package") == targetPackage)
                require(marker.optLong("versionCode", -1L) == versionCode)
                require(marker.optString("gmsProfile") == gmsProfile())
                require(marker.optString("compatibilityProfile") == compatibilityRecipe?.identity.orEmpty())
                if (marker.optString("gmsProfile").isNotEmpty()) {
                    DirectGmsArchive.verify(archive, marker)
                }
                require(marker.optLong("moduleBuild", -1L) >= 0L)
                require(marker.optString("moduleVersion").isNotBlank())
                require(marker.optInt("launchGuardSchema") == LAUNCH_GUARD_SCHEMA)
                require(marker.optString("launchGuardPublicKey") ==
                    Base64.encodeToString(certificate.publicKey.encoded, Base64.NO_WRAP))
                val diagnosticDex = marker.optString("diagnosticDex")
                require(DEX_NAME.matches(diagnosticDex) && archive.getEntry(diagnosticDex)?.size?.let { it > 0 } == true)
                require(archive.getEntry(RemoteLaunchDiagnostics.CONFIG) != null)
                require(archiveFactory(base) == PATCH_FACTORY)
                marker
            }
            when (marker.optString("patchKind")) {
                EXTERNAL_CONTROLLER_PATCH -> {
                    require(targetPackage == FREE_FIRE_PACKAGE)
                    val expectedGameNative = marker.optString("gameNativeSha256")
                    require(expectedGameNative.isNotBlank())
                    val nativeEntry = gameNativeEntry("arm64-v8a")
                    val actualHashes = apks.mapNotNull { apk ->
                        ZipFile(apk).use { archive ->
                            archive.getEntry(nativeEntry)?.let { entry ->
                                sha256(archive.getInputStream(entry))
                            }
                        }
                    }
                    require(actualHashes.singleOrNull() == expectedGameNative) {
                        "Patched APK set does not contain the verified $nativeEntry"
                    }
                }
                LOADER_PATCH -> {
                    ZipFile(base).use { archive ->
                        require(archive.entries().asSequence().any { NATIVE_ENTRY_PATTERN.matches(it.name) })
                        require(archive.entries().asSequence().any { DEX_NAME.matches(it.name) })
                    }
                    require(archiveFactory(base) == PATCH_FACTORY)
                }
                else -> error("Unsupported direct patch payload")
            }
            compatibilityRecipe?.let { recipe ->
                // Full replacements must work after uninstall, using only the prepared set.
                // Only inherited updates need native siblings from the installed package.
                val complete = directPatchVerificationApks(apks, inheritExistingInstallation) {
                    installedSources(installedPackageInfo()).map { it.file }
                }
                ModuleCompatibility.verify(complete, recipe)
            }
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

    private fun markerRevision(marker: JSONObject): DirectPatchRevision = DirectPatchRevision(
        markerSchema = marker.optInt("schema"),
        gameVersionCode = marker.optLong("versionCode", -1L),
        moduleBuild = marker.optLong("moduleBuild", -1L),
        moduleVersion = marker.optString("moduleVersion"),
        launchGuardSchema = marker.optInt("launchGuardSchema"),
        launchGuardPublicKey = marker.optString("launchGuardPublicKey"),
        dexSha256 = marker.optString("dexSha256"),
        nativeSha256 = marker.optString("nativeSha256"),
        gameNativeSha256 = marker.optString("gameNativeSha256"),
        gmsProfile = marker.optString("gmsProfile")
    )

    private fun gmsProfile(): String {
        if (!GmsCoreSupport.enabled(context, targetPackage)) return ""
        val config = JSONObject(File(context.filesDir, "menus/$targetPackage/config.json").readText())
        if (!com.moodtools.hub.modules.moduleGmsCoreSupport(config, "gmscore_support")) return ""
        val rules = com.moodtools.hub.modules.GmsCorePatchRules.fromJson(config, "gmscore_patch")
        return GmsCoreSupport.PROFILE + (rules?.let { ":${it.revision}:${it.fingerprint()}" } ?: "")
    }

    private fun gmsRules(): com.moodtools.hub.modules.GmsCorePatchRules? =
        com.moodtools.hub.modules.GmsCorePatchRules.fromJson(
            JSONObject(File(context.filesDir, "menus/$targetPackage/config.json").readText()), "gmscore_patch")

    private fun originalGameSigner(source: File, marker: JSONObject?, alreadyPatched: Boolean): String {
        marker?.optString("originalSignerSha1")?.takeIf { it.matches(Regex("[0-9a-f]{40}")) }?.let { return it }
        if (!alreadyPatched) return runCatching {
            val result = ApkVerifier.Builder(source).setMinCheckedPlatformVersion(26).build().verify()
            require(result.isVerified && result.signerCertificates.size == 1)
            MessageDigest.getInstance("SHA-1").digest(result.signerCertificates.single().encoded)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }.getOrDefault("")
        // Migrate the released v1 adapter using its existing per-client metadata; never guess a signer.
        if (marker?.optString("gmsProfile") == LegacyGmsPatchMigration.PROFILE) return runCatching {
            packageManager.getApplicationInfo(targetPackage, PackageManager.GET_META_DATA)
                .metaData?.getString("${GmsCoreSupport.PACKAGE}.SPOOFED_PACKAGE_SIGNATURE").orEmpty()
        }.getOrDefault("")
        return ""
    }

    private fun expectedGameNativeSha256(game: InstalledGame): String =
        if (game.module.rootMethod == RootMethod.EXTERNAL_CONTROLLER) {
            PluginLoader(context).embeddedLibrarySha256(game.module)
        } else ""

    private fun findAndVerifyGameNativeSource(
        sources: List<SourceApk>,
        abi: String,
        expectedPatchedSha256: String
    ): SourceApk {
        require(targetPackage == FREE_FIRE_PACKAGE) {
            "Library-only external-controller patching is not configured for $targetPackage"
        }
        val entryName = gameNativeEntry(abi)
        val matches = sources.filter { source ->
            ZipFile(source.file).use { it.getEntry(entryName) != null }
        }
        val source = matches.singleOrNull()
            ?: error("Installed APK set must contain exactly one $entryName")
        val actualSha256 = ZipFile(source.file).use { archive ->
            sha256(archive.getInputStream(requireNotNull(archive.getEntry(entryName))))
        }
        require(actualSha256 == FREE_FIRE_ORIGINAL_IL2CPP_SHA256 ||
            actualSha256 == expectedPatchedSha256) {
            "Installed Free Fire libil2cpp.so is an unknown build: $actualSha256"
        }
        return source
    }

    private fun prepareEmbeddedGameLibrary(
        game: InstalledGame,
        moduleDirectory: File,
        outputDirectory: File,
        expectedSha256: String
    ): File {
        val output = File(outputDirectory, "embedded-libil2cpp.so")
        val loader = PluginLoader(context)
        check(loader.loadNativeForExtraction(game.module, File(moduleDirectory, game.module.nativeFile)) &&
            loader.extractEmbeddedLibrary(game.module, output)) {
            "Embedded game library extraction failed"
        }
        check(sha256(output) == expectedSha256) { "Embedded game library hash mismatch" }
        return output
    }

    private fun gameNativeEntry(abi: String) = "lib/$abi/libil2cpp.so"

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
                    .setCertificateSubject(X500Principal("CN=Jester Mods Game Patch"))
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
        val target = validatedDirectPatchDirectory(patchRoot, directory)
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
        sha256(input)
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().toHex()
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
        const val PATCH_FACTORY = "com.moodtools.diagnostics.DiagnosticFactory"
        private const val MODULE_FACTORY = "com.android.support.ModComponentFactory"
        private const val ORIGINAL_FACTORY = "androidx.core.app.CoreComponentFactory"
        private const val MANIFEST_ENTRY = "AndroidManifest.xml"
        private const val LEGACY_NATIVE_ENTRY = "lib/arm64-v8a/libYourSaviour.so"
        // These legacy identifiers must remain stable so already-patched Soul Knight installs
        // retain their embedded marker and Android Keystore signer during universalization.
        private const val MARKER_ENTRY = "assets/moodtools/soul-patch.json"
        private const val GMS_ORIGINAL_ENTRY = "assets/moodtools/gms-original.dex"
        private const val PATCH_DIRECTORY = "soul-knight-patches"
        private const val KEY_ALIAS = "jester_moods_soul_knight_patch_v1"
        private const val PATCH_MARKER_SCHEMA = 5
        private const val LAUNCH_GUARD_SCHEMA = 1
        private const val LAUNCH_NONCE_BYTES = 16
        private const val LAUNCH_MESSAGE_PREFIX = "jester-direct-patch-launch-v1"
        private const val LAUNCH_GUARD_DEX_DESCRIPTOR = "Lcom/android/support/DirectLaunchGuard;"
        private const val EXTERNAL_CONTROLLER_PATCH = "external_controller_library"
        private const val LOADER_PATCH = "loader"
        private const val FREE_FIRE_PACKAGE = "com.dts.freefireth"
        private const val FREE_FIRE_ORIGINAL_IL2CPP_SHA256 =
            "bf70daa86a1c0224b6e0191918a7912829615858f31eb2c4fd644d020effbdff"
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
        private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_.]{3,200}")
    }
}
