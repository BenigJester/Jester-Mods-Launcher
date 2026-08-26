package com.moodtools.hub.modules

/** Converts implementation-level Android ABI names into concise user-facing labels. */
internal fun architectureLabel(abi: String): String = when (abi) {
    "arm64-v8a", "x86_64" -> "64-bit"
    "armeabi-v7a", "x86" -> "32-bit"
    else -> "Unknown architecture"
}

/** Collapses equivalent ABIs so, for example, two 64-bit ABIs display only as "64-bit". */
internal fun architectureSummary(abis: Iterable<String>): String {
    val labels = abis.map(::architectureLabel).toSet()
    return listOf("64-bit", "32-bit", "Unknown architecture")
        .filter(labels::contains)
        .joinToString(", ")
}
