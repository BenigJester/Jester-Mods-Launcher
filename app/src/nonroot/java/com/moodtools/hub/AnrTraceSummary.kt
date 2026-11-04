package com.moodtools.hub

/** Only thread IDs/states, Java method identities and native library offsets leave the trace. */
internal object AnrTraceSummary {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private val process = Regex("^----- pid ([0-9]+) at .+ -----$")
    private val thread = Regex("^\"([^\"]{1,256})\" .*?tid=([0-9]+) ([A-Za-z]+).*$")
    private val javaFrame = Regex("^at ([A-Za-z0-9_.$<>-]{1,240})\\([^()]*\\)$")
    private val nativeFrame = Regex("^(?:native: )?#([0-9]{1,3})\\s+pc\\s+([0-9a-fA-F]{1,16})\\s+(\\S+).*$")
    private val lockOwner = Regex(".*held by thread ([0-9]+).*")
    private val states = setOf("Native", "Runnable", "Blocked", "Waiting", "TimedWaiting", "Sleeping", "Suspended")
    private data class ThreadTrace(val id: Int, val main: Boolean, val state: String,
        val frames: MutableList<String> = mutableListOf(), var owner: Int? = null)

    fun decode(bytes: ByteArray, expectedPid: Int): String {
        if (bytes.size > MAX_BYTES) return "ANR system trace exceeds size limit"
        val threads = linkedMapOf<Int, ThreadTrace>()
        var selected = false
        var foundProcess = false
        var current: ThreadTrace? = null
        for (raw in bytes.toString(Charsets.UTF_8).lineSequence()) {
            if (raw.length > 4096) continue
            val line = raw.trim()
            if (line.startsWith("----- pid ")) { selected = false; current = null }
            process.matchEntire(line)?.let { match ->
                selected = match.groupValues[1].toIntOrNull() == expectedPid
                foundProcess = foundProcess || selected
                current = null
            }
            if (line.startsWith("----- end ")) { selected = false; current = null }
            if (!selected) continue
            val header = thread.matchEntire(line)
            if (header != null) {
                current = null
                val id = header.groupValues[2].toIntOrNull() ?: continue
                if (threads.size >= 256 || id in threads) continue
                current = ThreadTrace(id, id == 1 && header.groupValues[1] == "main",
                    header.groupValues[3].takeIf(states::contains) ?: "unknown").also { threads[id] = it }
                continue
            }
            val target = current ?: continue
            lockOwner.matchEntire(line)?.let { target.owner = it.groupValues[1].toIntOrNull() }
            if (target.frames.size >= 24) continue
            val java = javaFrame.matchEntire(line)
            if (java != null) { target.frames += "at ${java.groupValues[1]}"; continue }
            nativeFrame.matchEntire(line)?.let { match ->
                val leaf = match.groupValues[3].substringAfterLast('/').substringAfterLast('!')
                if (Regex("[A-Za-z0-9_.-]{1,100}\\.(?:so|apk|oat|odex)").matches(leaf)) {
                    target.frames += "#${match.groupValues[1]} $leaf +0x${match.groupValues[2]}"
                }
            }
        }
        if (!foundProcess) return "ANR system trace has no matching process"
        val main = threads.values.firstOrNull { it.main } ?: return "ANR system trace has no main thread"
        val selectedThreads = linkedSetOf(main)
        // Follow only the main thread's explicit lock-owner chain, bounded even for cyclic waits.
        var owner = main.owner
        repeat(7) {
            val next = threads[owner] ?: return@repeat
            if (selectedThreads.add(next)) owner = next.owner else owner = null
        }
        return buildString {
            appendLine("ANR system thread snapshot (not proof of the original cause)")
            for (entry in selectedThreads) {
                appendLine("Thread ${entry.id}${if (entry.main) " main" else " lock owner"}: ${entry.state}")
                entry.owner?.let { appendLine("waiting_for_thread=$it") }
                entry.frames.forEach(::appendLine)
                if (entry.frames.isEmpty()) appendLine("No supported stack frames available")
            }
        }.trimEnd()
    }
}

/** Classify Android's description without exporting intents, paths or account text. */
internal fun diagnosticAnrTrigger(description: String?): String {
    val text = description.orEmpty().take(4096).lowercase(java.util.Locale.ROOT)
    return when {
        "input dispatching timed out" in text -> "input_dispatch_timeout"
        "broadcast of intent" in text || "broadcast timeout" in text -> "broadcast_timeout"
        "executing service" in text || "service timeout" in text -> "service_timeout"
        "contentprovider not responding" in text || "content provider not responding" in text -> "provider_timeout"
        else -> "unclassified_or_unavailable"
    }
}
