package com.moodtools.hub

/** Rank already-sanitized samples, not blame: local Binder counts are not system_server UID totals. */
internal fun prioritizedSupportingResources(lines: List<String>): List<String> {
    fun number(line: String, field: String): Long =
        Regex("(?:^| )${Regex.escape(field)}=(-?[0-9]+)(?= |$)").find(line)
            ?.groupValues?.get(1)?.toLongOrNull() ?: -1L
    fun timestamp(line: String): Long = line.substringAfter(": ").substringBefore(' ').toLongOrNull() ?: -1L
    return lines.groupBy { it.substringBefore(": ") }.values
        .mapNotNull { samples -> samples.maxByOrNull(::timestamp) }
        .sortedWith(compareByDescending<String> {
            maxOf(number(it, "binderLocal"), number(it, "binderProxy"))
        }.thenByDescending { timestamp(it) }.thenBy { it.substringBefore(": ") })
}

/** Last two sampled sites, not the most frequent callers or proof of resource ownership. */
internal fun latestConnectionSiteSamples(text: String): String = text.take(16 * 1024)
    .split("\n\n").filter { it.lineSequence().firstOrNull()
        ?.matches(Regex("[0-9]+ operation=[a-z_]+ call=[0-9]+")) == true }
    .takeLast(2).joinToString("\n\n") { block -> block.lineSequence().take(4).joinToString("\n") {
        // Keep these few sampled callers when GitHub compaction removes long exception stacks.
        if (it.startsWith("  at ")) "sampledCaller: " + it.removePrefix("  at ") else it
    } }
