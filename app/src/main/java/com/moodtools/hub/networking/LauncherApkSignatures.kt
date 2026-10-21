package com.moodtools.hub.networking

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest

/** Never treat absent signer metadata, an unsigned APK, or failed verification as trusted. */
internal fun verifiedLauncherSigningDigests(apk: File, api: Int, label: String): Set<String> {
    val result = try {
        ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(api).setMaxCheckedPlatformVersion(api)
            .build().verify()
    } catch (error: Exception) {
        throw IllegalStateException("$label APK signature could not be read (API $api)", error)
    }
    check(result.isVerified && result.signerCertificates.isNotEmpty()) {
        "$label APK signature could not be verified (API $api)"
    }
    return result.signerCertificates.mapTo(mutableSetOf()) { certificate ->
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
    }
}
