package com.moodtools.hub.soulpatch

import java.io.File

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
