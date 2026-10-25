package com.moodtools.hub

/** Fixed categories only. Aborted is not proof that the user pressed Cancel. */
enum class PackageInstallObservation {
    RESULT, CONFIRMATION_OPENED, CONFIRMATION_MISSING, CONFIRMATION_OPEN_FAILED,
    RESULT_NOT_OBSERVED, SUCCESS_IDENTITY_NOT_VISIBLE
}

internal fun packageInstallEvidence(status: Int?, sessionId: Int?, observation: PackageInstallObservation): String {
    val category = when (status) {
        -1 -> "pending_user_action"
        0 -> "success"
        1 -> "failure"
        2 -> "blocked"
        3 -> "aborted"
        4 -> "invalid"
        5 -> "conflict"
        6 -> "storage"
        7 -> "incompatible"
        8 -> "timeout"
        null -> "unavailable"
        else -> "unknown"
    }
    return "installerObservation=${observation.name.lowercase(java.util.Locale.ROOT)} " +
        "status=${status ?: "unavailable"} category=$category sessionId=${sessionId?.takeIf { it >= 0 } ?: "unavailable"}"
}
