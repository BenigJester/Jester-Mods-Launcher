package com.moodtools.hub

internal data class DiagnosticProcessAttempt(val pid: Int, val startedAt: Long, val events: String) {
    val resumed: Boolean get() = Regex("activity_(?:direct-)?resumed ").containsMatchIn(events)
    fun resumedBefore(timestamp: Long): Boolean = events.lineSequence().any {
        (it.substringBefore(' ').toLongOrNull() ?: Long.MAX_VALUE) <= timestamp &&
            Regex("activity_(?:direct-)?resumed ").containsMatchIn(it)
    }
}

/** Main-process attempts only. Legacy logs infer identity from activity events or recorded main death. */
internal fun diagnosticProcessAttempts(logs: Map<Int, String>, host: String): List<DiagnosticProcessAttempt> =
    logs.flatMap { (pid, text) ->
        val lines = text.lines()
        val starts = lines.indices.filter { lines[it].substringAfter(' ').startsWith("guest_attached") }
        starts.mapIndexedNotNull { index, from ->
            val events = lines.subList(from, starts.getOrNull(index + 1) ?: lines.size).joinToString("\n")
            val main = when {
                lines[from].contains("main=false") -> false
                lines[from].contains("main=true") -> true
                else -> events.contains("activity_") || Regex("main_process_died pid=$pid(?:\\s|$)").containsMatchIn(host)
            }
            if (!main) null else lines[from].substringBefore(' ').toLongOrNull()?.let {
                DiagnosticProcessAttempt(pid, it, events)
            }
        }
    }.sortedBy { it.startedAt }

internal fun diagnosticAnrContext(background: Boolean, resumed: Boolean): String = when {
    background -> "background"
    resumed -> "after activity resume"
    else -> "startup"
}

internal fun diagnosticMenuState(events: String): String? = events.lineSequence()
    .mapNotNull { Regex("^([0-9]+) menu_window (checking|attached|not_attached|unsupported|probe_error)$").matchEntire(it) }
    .maxByOrNull { it.groupValues[1].toLongOrNull() ?: 0 }?.groupValues?.get(2)

internal fun diagnosticStatusWithMenu(status: String, state: String?): String =
    if (status == "Game activity opened" && state == "not_attached") "Menu window not attached" else status

internal fun diagnosticAttemptStatus(attempt: DiagnosticProcessAttempt, failureTexts: List<String>,
                                     stopped: Boolean, died: Boolean, now: Long): String {
    if (attempt.events.contains("failure ")) return "Crash or startup error recorded"
    diagnosticFailureStatus(failureTexts, attempt.resumed)?.let { return it }
    return launchDiagnosticStatus(false, attempt.resumed, stopped, now - attempt.startedAt, died)
}

internal fun diagnosticFailureStatus(failureTexts: List<String>, resumed: Boolean): String? {
    val anrs = failureTexts.filter { it.startsWith("Exit reason: 6;") }
    // Android exit records also describe normal exits and system kills, not just crashes.
    val reasons = failureTexts.map { Regex("^Exit reason: ([0-9]+);").find(it)?.groupValues?.get(1)?.toIntOrNull() }
    if (reasons.any { it == null || it in setOf(4, 5, 7) }) return "Crash or startup error recorded"
    if (anrs.isNotEmpty()) return when {
        anrs.any { it.contains("ANR context: background") } -> "Background ANR recorded"
        anrs.any { it.contains("ANR context: unknown") } -> "ANR recorded"
        anrs.any { it.contains("ANR context: after activity resume") } || resumed -> "Game ANR recorded"
        anrs.any { it.contains("ANR context: startup") } -> "Startup ANR recorded"
        else -> "ANR recorded" // Legacy exits without enough lifecycle/context evidence remain unclassified.
    }
    return if (failureTexts.isNotEmpty()) "Process exit recorded" else null
}

/** Stable ApplicationExitInfo reason values; fixed labels avoid collecting private system descriptions. */
internal fun diagnosticExitCategory(reason: Int): String = when (reason) {
    1 -> "self-exit"
    2 -> "OS signal (not proof of a crash or low memory)"
    3 -> "system low-memory kill"
    4 -> "Java crash"
    5 -> "native crash"
    6 -> "ANR"
    7 -> "initialization failure"
    8 -> "permission change"
    9 -> "excessive resource usage"
    10 -> "user-requested termination"
    11 -> "Android user stopped"
    12 -> "dependency died"
    13 -> "other system termination"
    14 -> "app freezer termination"
    15 -> "package state change"
    16 -> "package updated"
    else -> "unknown"
}
