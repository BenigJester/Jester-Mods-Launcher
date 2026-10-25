package com.moodtools.hub.directpatch

/** Keep the translated user message separate from fixed, path-free diagnostic evidence. */
internal class GmsCorePatchFailure(
    val check: Check,
    val dexEntry: String? = null,
    val expectedSha256: String? = null,
    val actualSha256: String? = null
) : IllegalArgumentException(if (check == Check.SDK_SHAPE_UNSUPPORTED)
    "This game's Google login is not supported by this add-on. Update the add-on and try again."
else "Google account support does not match this game build") {
    enum class Check {
        DEX_MISSING, DEX_HASH_MISMATCH, SDK_SHAPE_UNSUPPORTED, UNPINNED_DEX_CHANGED,
        ACCOUNT_ROUTE_MISSING, ALREADY_ROUTED, AVAILABILITY_SHAPE_MISMATCH,
        SIGNATURE_SHAPE_MISMATCH, ARCHIVE_LIMIT_EXCEEDED
    }

    fun diagnosticLines(): List<String> = buildList {
        add("gmsPatchCheck: ${check.name.lowercase(java.util.Locale.ROOT)}")
        // Validate again at the report boundary; never export arbitrary names or exception text.
        dexEntry?.takeIf { it.matches(Regex("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) }
            ?.let { add("gmsPatchDex: $it") }
        expectedSha256?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            ?.let { add("gmsPatchExpectedSha256: $it") }
        actualSha256?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            ?.let { add("gmsPatchActualSha256: $it") }
    }
}
