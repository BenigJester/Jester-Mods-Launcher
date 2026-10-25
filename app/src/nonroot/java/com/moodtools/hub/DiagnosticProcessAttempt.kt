package com.moodtools.hub

internal data class DiagnosticProcessAttempt(val pid: Int, val startedAt: Long, val events: String) {
    val resumed: Boolean get() = Regex("activity_(?:direct-)?resumed ").containsMatchIn(events)
    val activityLaunchAt: Long? get() = events.lineSequence().mapNotNull { line ->
        if (Regex("^[0-9]+ activity_(?:instantiate(?: |$)|(?:direct-)?(?:created|started|resumed) )")
                .containsMatchIn(line)) line.substringBefore(' ').toLongOrNull() else null
    }.minOrNull()
    fun resumedBefore(timestamp: Long): Boolean = events.lineSequence().any {
        (it.substringBefore(' ').toLongOrNull() ?: Long.MAX_VALUE) <= timestamp &&
            Regex("activity_(?:direct-)?resumed ").containsMatchIn(it)
    }
}

/** Unknown timestamps and other processes remain context, never selected-attempt evidence. */
internal fun diagnosticEventBelongsToAttempt(line: String, attempt: DiagnosticProcessAttempt?, nextAttempt: Long?): Boolean {
    if (attempt == null) return true
    val source = line.substringBefore(": ")
    val timestamp = line.substringAfter(": ", "").substringBefore(' ').toLongOrNull() ?: return false
    return (source == "host.txt" || source == "process-${attempt.pid}.txt") &&
        timestamp >= attempt.startedAt && (nextAttempt == null || timestamp < nextAttempt)
}

/** Adds fixed Android labels to legacy and remote exit records without reading private descriptions. */
internal fun diagnosticExitEvidence(text: String): String {
    if (text.lineSequence().any { it.startsWith("Exit category: ") }) return text
    val reason = Regex("^Exit reason: ([0-9]+);").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return text
    val firstLineEnd = text.indexOf('\n').let { if (it < 0) text.length else it }
    return text.substring(0, firstLineEnd) + "\nExit category: ${diagnosticExitCategory(reason)}\n" +
        "Exit reason alone does not identify the underlying cause.\n" + text.substring(firstLineEnd).trimStart('\n')
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

/** A session's initial request belongs to its first attempt, not every later OS restart. */
internal fun diagnosticAttemptLaunchRequest(attempt: DiagnosticProcessAttempt, previousStartedAt: Long?,
                                          sessionStartedAt: Long, remoteSession: Boolean, host: String): Long? {
    val request = host.lineSequence().mapNotNull { line ->
        if (Regex("^[0-9]+ (?:launch_requested|resuming_existing_session)$").matches(line))
            line.substringBefore(' ').toLongOrNull() else null
    }.filter { it <= attempt.startedAt && (previousStartedAt == null || it > previousStartedAt) }.maxOrNull()
    // Remote metadata is created by the authorized launcher's begin request, unlike process attach.
    return request ?: sessionStartedAt.takeIf { remoteSession && previousStartedAt == null && it <= attempt.startedAt }
}

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
                                     stopped: Boolean, died: Boolean, now: Long,
                                     launchRequestedAt: Long? = null): String {
    if (attempt.events.contains("failure ")) return "Crash or startup error recorded"
    diagnosticFailureStatus(failureTexts, attempt.resumed)?.let { return it }
    val launchAt = attempt.activityLaunchAt ?: launchRequestedAt
    if (!stopped && launchAt == null) return "Background process observed"
    return launchDiagnosticStatus(false, attempt.resumed, stopped, now - (launchAt ?: attempt.startedAt), died)
}

internal fun diagnosticFailureStatus(failureTexts: List<String>, resumed: Boolean): String? {
    val timeouts = failureTexts.filter { it.startsWith("Launch confirmation timeout\n") }
    val failures = failureTexts - timeouts.toSet()
    val anrs = failures.filter { it.startsWith("Exit reason: 6;") }
    // Android exit records also describe normal exits and system kills, not just crashes.
    val reasons = failures.map { Regex("^Exit reason: ([0-9]+);").find(it)?.groupValues?.get(1)?.toIntOrNull() }
    if (reasons.any { it == null || it in setOf(4, 5, 7) }) return "Crash or startup error recorded"
    if (anrs.isNotEmpty()) return when {
        anrs.any { it.contains("ANR context: background") } -> "Background ANR recorded"
        anrs.any { it.contains("ANR context: unknown") } -> "ANR recorded"
        anrs.any { it.contains("ANR context: after activity resume") } || resumed -> "Game ANR recorded"
        anrs.any { it.contains("ANR context: startup") } -> "Startup ANR recorded"
        else -> "ANR recorded" // Legacy exits without enough lifecycle/context evidence remain unclassified.
    }
    return when {
        failures.isNotEmpty() -> "Process exit recorded"
        timeouts.isNotEmpty() && !resumed -> "Launch confirmation timed out"
        else -> null
    }
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
