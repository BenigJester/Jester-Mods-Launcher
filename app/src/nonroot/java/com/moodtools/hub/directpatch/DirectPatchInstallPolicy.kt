package com.moodtools.hub.directpatch

import java.io.File
import java.security.MessageDigest

/** Preserve the existing numeric/hex cache format, including when account support is disabled. */
internal fun directPatchDirectoryName(
    versionCode: Long,
    schema: Int,
    moduleBuild: Long,
    dexSha256: String,
    nativeSha256: String,
    gmsProfile: String
): String {
    val nativeFingerprint = if (gmsProfile.isEmpty()) nativeSha256 else
        MessageDigest.getInstance("SHA-256").digest("$nativeSha256:$gmsProfile".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    return "$versionCode-$schema-$moduleBuild-${dexSha256.take(16)}-${nativeFingerprint.take(16)}"
}

/** Validate before any recursive removal; a valid name alone never authorizes another location. */
internal fun validatedDirectPatchDirectory(root: File, directory: File): File {
    val target = directory.canonicalFile
    require(target.parentFile == root.canonicalFile &&
        target.name.matches(Regex("[0-9]+-[0-9]+-[0-9]+-[0-9a-f]{16}-[0-9a-f]{16}"))) {
        "Direct patch directory escaped launcher storage"
    }
    return target
}

internal data class DirectPatchRevision(
    val markerSchema: Int,
    val gameVersionCode: Long,
    val moduleBuild: Long,
    val moduleVersion: String,
    val launchGuardSchema: Int,
    val launchGuardPublicKey: String,
    val dexSha256: String,
    val nativeSha256: String,
    val gameNativeSha256: String = "",
    val gmsProfile: String = ""
)

/** Every embedded input participates, so a catalog rebuild cannot look current by accident. */
internal fun directPatchRevisionIsCurrent(
    installed: DirectPatchRevision,
    expected: DirectPatchRevision
): Boolean = installed == expected

/** Chooses the destructive first-install path only when an in-place signed update is impossible. */
internal fun directPatchRequiresUninstall(
    installedApks: List<File>,
    expectedSignerSha256: String,
    signerDigests: (File) -> Set<String>
): Boolean {
    if (installedApks.isEmpty() || expectedSignerSha256.isBlank()) return true
    return installedApks.any { apk ->
        runCatching { expectedSignerSha256 !in signerDigests(apk) }.getOrDefault(true)
    }
}
