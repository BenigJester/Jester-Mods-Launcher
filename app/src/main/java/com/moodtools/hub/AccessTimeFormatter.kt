package com.moodtools.hub

internal fun formatRemainingAccessPrimary(milliseconds: Long): String {
    if (milliseconds <= 0L) return "Expired"
    var minutes = (milliseconds + 59_999L) / 60_000L
    val days = minutes / 1_440L
    minutes %= 1_440L
    val hours = minutes / 60L
    minutes %= 60L
    return buildList {
        if (days > 0L) add("${days}d")
        if (days > 0L || hours > 0L) add("${hours}h")
        if (days == 0L) add("${minutes}m")
    }.joinToString(" ")
}
