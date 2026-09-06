package com.moodtools.hub.soulpatch

import java.io.File

internal data class DirectPatchRevision(
    val markerSchema: Int,
    val gameVersionCode: Long,
    val moduleBuild: Long,
    val moduleVersion: String,
    val launchGuardSchema: Int,
    val launchGuardPublicKey: String,
    val dexSha256: String,
    val nativeSha256: String
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
