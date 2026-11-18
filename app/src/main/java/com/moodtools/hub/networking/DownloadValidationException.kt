package com.moodtools.hub.networking

/** Fixed diagnostic vocabulary only: never include URLs, headers, tokens or server text. */
internal enum class DownloadCheck {
    SOURCE_URL, SOURCE_UNAVAILABLE, REDIRECT_STATUS, REDIRECT_MISSING, REDIRECT_URL, REDIRECT_PACKAGE,
    REDIRECT_SIZE, CONTENT_LENGTH, RANGE_METADATA, RANGE_MISMATCH, BODY_SIZE
}

internal class DownloadValidationException(
    val check: DownloadCheck, expected: Long? = null, actual: Long? = null
) : IllegalArgumentException("check=${check.name.lowercase(java.util.Locale.ROOT)}" +
    (expected?.let { "; expected=$it" } ?: "") + (actual?.let { "; actual=$it" } ?: ""))

/** APKPure uses its homepage redirect when its gateway cannot supply a file. */
internal fun requireAPKPureDownloadTarget(target: java.net.URL) {
    if (target.protocol == "https" && target.host in setOf("apkpure.com", "apkpure.net") &&
        target.path in setOf("", "/")) {
        throw DownloadValidationException(DownloadCheck.SOURCE_UNAVAILABLE)
    }
    if (target.protocol != "https" || target.host != "data.winudf.com") {
        throw DownloadValidationException(DownloadCheck.REDIRECT_URL)
    }
}

internal fun isUnavailableAPKPureDownload(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.take(16).any {
        it is DownloadValidationException && it.check == DownloadCheck.SOURCE_UNAVAILABLE
    }
