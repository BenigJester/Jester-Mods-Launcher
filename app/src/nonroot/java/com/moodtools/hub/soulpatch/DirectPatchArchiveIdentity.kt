package com.moodtools.hub.soulpatch

internal fun validateDirectPatchArchiveIdentity(
    actualPackageName: String,
    actualVersionCode: Long,
    expectedPackageName: String,
    expectedVersionCode: Long
) {
    require(actualPackageName == expectedPackageName) { "base.apk targets a different Android package" }
    require(actualVersionCode == expectedVersionCode) { "base.apk has a different Android version" }
}
