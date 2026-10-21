package com.moodtools.hub

import java.io.File

internal class LaunchConfirmationTimeoutException(message: String) : IllegalStateException(message)

/** Either fresh guest callback evidence or fresh live task state confirms dispatch, not menu readiness. */
internal fun launchResumeConfirmed(session: File?, requestedAt: Long, expectedPid: Int? = null, live: () -> Boolean): Boolean =
    (session != null && sessionResumedSince(session, requestedAt, expectedPid)) || live()

/** Only guest lifecycle callbacks confirm a launch, not optimistic host messages. */
internal fun sessionResumedSince(session: File, requestedAt: Long, expectedPid: Int? = null): Boolean = session.listFiles().orEmpty()
    .filter { it.name.matches(Regex("process-[0-9]+\\.txt")) &&
        (expectedPid == null || it.name == "process-$expectedPid.txt") }.take(64).any { file ->
        runCatching {
            file.bufferedReader().useLines { lines -> lines.any { line ->
                (line.substringBefore(' ').toLongOrNull() ?: 0) >= requestedAt &&
                    Regex("^activity_(?:direct-)?resumed ").containsMatchIn(line.substringAfter(' '))
            } }
        }.getOrDefault(false)
    }
