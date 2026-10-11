package com.moodtools.hub

/** A timeout is a suspicion, never a confirmed freeze or instruction to kill the guest. */
internal fun launchDiagnosticStatus(failure: Boolean, resumed: Boolean, stopped: Boolean, ageMillis: Long, earlyDeath: Boolean = false): String = when {
    failure -> "Crash or startup error recorded"
    stopped -> "Session stopped"
    earlyDeath && !resumed -> "Guest process exited during startup"
    resumed -> "Game activity opened"
    ageMillis >= 60_000L -> "Possible startup stall"
    else -> "Startup in progress"
}
