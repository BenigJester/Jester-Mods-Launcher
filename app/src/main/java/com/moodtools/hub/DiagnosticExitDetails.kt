package com.moodtools.hub

/** Fixed hints only. Android descriptions can contain intents, paths and account data. */
internal fun diagnosticExitHint(description: String?): String {
    val text = description.orEmpty().take(4096).lowercase(java.util.Locale.ROOT)
    return when {
        text.contains("excessive cpu") || text.contains("excessive background cpu") -> "excessive_cpu_reported"
        text.startsWith("bg anr:") -> "background_anr_reported"
        text.contains("low memory") -> "low_memory_mentioned"
        text.contains("stop idle") -> "idle_stop_mentioned"
        else -> "unclassified_or_unavailable"
    }
}
