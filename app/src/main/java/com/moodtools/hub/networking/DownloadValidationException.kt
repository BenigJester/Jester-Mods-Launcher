package com.moodtools.hub.networking

/** Fixed diagnostic vocabulary only: never include URLs, headers, tokens or server text. */
internal enum class DownloadCheck {
    SOURCE_URL, REDIRECT_STATUS, REDIRECT_MISSING, REDIRECT_URL, REDIRECT_PACKAGE,
    REDIRECT_SIZE, CONTENT_LENGTH, RANGE_METADATA, RANGE_MISMATCH, BODY_SIZE
}

internal class DownloadValidationException(
    check: DownloadCheck, expected: Long? = null, actual: Long? = null
) : IllegalArgumentException("check=${check.name.lowercase(java.util.Locale.ROOT)}" +
    (expected?.let { "; expected=$it" } ?: "") + (actual?.let { "; actual=$it" } ?: ""))
