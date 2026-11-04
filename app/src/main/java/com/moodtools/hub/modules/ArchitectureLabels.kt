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

/** Fixed ABI tokens only: never include APK paths or arbitrary metadata in support reports. */
internal fun moduleArchitectureEvidence(gameAbi: String, addOnAbis: Iterable<String>, deviceAbis: Iterable<String>): String {
    val known = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    fun tokens(values: Iterable<String>) = values.map { if (it in known) it else "unknown" }
        .distinct().sorted().joinToString(",").ifEmpty { "none" }
    val addOn = addOnAbis.toSet()
    val device = deviceAbis.toSet()
    val result = when {
        gameAbi !in known -> "game_abi_unknown"
        addOn.none { it in device } -> "device_abi_mismatch"
        gameAbi !in addOn -> "game_abi_mismatch"
        else -> "compatible"
    }
    return "Architecture check: game=${tokens(listOf(gameAbi))} (${architectureLabel(gameAbi)}); " +
        "addOn=${tokens(addOn)}; device=${tokens(device)}; result=$result"
}
