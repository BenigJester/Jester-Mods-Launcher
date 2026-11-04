package com.moodtools.hub

/** Keep all three bounded capture classifications ahead of stack frames in short reports. */
internal fun diagnosticFailureEvidenceOverview(text: String): String {
    val allowed = setOf(
        "Failure capture: first_observed", "Failure capture: later_observed",
        "Failure phase: activity_teardown", "Failure phase: unclassified",
        "Failure hint: network_callback_cleanup", "Failure hint: parcel_class_not_found", "Failure hint: unclassified"
    )
    val lines = text.take(64 * 1024).lineSequence().filter { it in allowed }.take(9).toList()
    if (lines.isEmpty()) return ""
    return lines.joinToString("\n") + "\nFirst observed is not necessarily the original cause." +
        if ("Failure phase: activity_teardown" in lines)
            "\nA shutdown failure does not establish why shutdown began." else ""
}
