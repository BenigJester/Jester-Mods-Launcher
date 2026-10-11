package com.moodtools.hub

import java.io.File

/** Only guest lifecycle callbacks confirm a launch, not optimistic host messages. */
internal fun sessionResumedSince(session: File, requestedAt: Long): Boolean = session.listFiles().orEmpty()
    .filter { it.name.matches(Regex("process-[0-9]+\\.txt")) }.take(64).any { file ->
        runCatching {
            file.bufferedReader().useLines { lines -> lines.any { line ->
                (line.substringBefore(' ').toLongOrNull() ?: 0) >= requestedAt &&
                    Regex("^activity_(?:direct-)?resumed ").containsMatchIn(line.substringAfter(' '))
            } }
        }.getOrDefault(false)
    }
