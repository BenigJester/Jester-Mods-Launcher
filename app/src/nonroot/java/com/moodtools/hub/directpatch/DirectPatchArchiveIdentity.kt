package com.moodtools.hub.directpatch

import java.io.File

/** Only inherited installer sessions depend on APKs outside their prepared set. */
internal fun directPatchVerificationApks(
    prepared: List<File>,
    inheritExistingInstallation: Boolean,
    installed: () -> List<File>
): List<File> {
    if (!inheritExistingInstallation) return prepared
    return (installed().associateBy { it.name } + prepared.associateBy { it.name }).values.toList()
}

internal fun validateDirectPatchArchiveIdentity(
    actualPackageName: String,
    actualVersionCode: Long,
    expectedPackageName: String,
    expectedVersionCode: Long
) {
    require(actualPackageName == expectedPackageName) { "base.apk targets a different Android package" }
    require(actualVersionCode == expectedVersionCode) { "base.apk has a different Android version" }
}
