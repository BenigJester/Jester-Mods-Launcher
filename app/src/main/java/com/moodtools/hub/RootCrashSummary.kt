package com.moodtools.hub

/** Only structural crash data is retained, never exception messages or raw logcat. */
internal fun rootCrashSummary(log: String, packageName: String, start: Long, end: Long): String {
    val prefix = Regex("^\\s*(\\d+\\.\\d+)\\s+(\\d+)\\s+(\\d+)\\s+[VDIWEF]\\s+(AndroidRuntime|DEBUG)\\s*:\\s?(.*)$")
    val groups = mutableMapOf<String, MutableList<String>>()
    val completed = mutableListOf<List<String>>()
    log.lineSequence().forEach { line ->
        val m = prefix.matchEntire(line) ?: return@forEach
        val time = (m.groupValues[1].toDoubleOrNull()?.times(1000))?.toLong() ?: return@forEach
        if (time < start || time >= end) return@forEach
        val key = "${m.groupValues[2]}:${m.groupValues[3]}:${m.groupValues[4]}"
        val body = m.groupValues[5].trim()
        if (body.startsWith("FATAL EXCEPTION:") || body.startsWith("*** ***")) {
            groups.remove(key)?.let(completed::add)
        }
        groups.getOrPut(key) { mutableListOf() }.add(body.take(2000))
    }
    completed.addAll(groups.values)
    val escaped = Regex.escape(packageName)
    val identity = Regex("^(?:Process: |Cmdline: )($escaped(?::[A-Za-z0-9_.-]+)?)(?:, PID: [0-9]+)?$")
    val nativeIdentity = Regex("^pid: ([0-9]+), tid: ([0-9]+),.*>>> ($escaped(?::[A-Za-z0-9_.-]+)?) <<<$")
    return completed.filter { lines ->
        lines.any { identity.matches(it) || nativeIdentity.matches(it) } &&
            lines.any { it.startsWith("FATAL EXCEPTION:") || it.startsWith("signal ") }
    }
        .joinToString("\n\n") { lines ->
            buildString {
                appendLine("Process: $packageName")
                lines.forEach { body ->
                    when {
                        body.startsWith("FATAL EXCEPTION:") -> appendLine("Uncaught Java exception")
                        body.matches(Regex("(?:Caused by: )?[A-Za-z_$][A-Za-z0-9_.$]*(?:Exception|Error)(?::.*)?")) ->
                            appendLine(body.removePrefix("Caused by: ").substringBefore(':').take(240))
                        body.matches(Regex("at [A-Za-z0-9_.$<>]+\\([A-Za-z0-9_.$ :?-]+\\)")) -> appendLine(body.take(300))
                        body.matches(Regex("signal [0-9]+ \\([A-Z0-9]+\\), code -?[0-9]+ \\([A-Z0-9_]+\\), fault addr (?:0x)?[a-fA-F0-9-]+")) -> appendLine(body)
                        nativeIdentity.matches(body) -> nativeIdentity.matchEntire(body)?.let {
                            appendLine("pid: ${it.groupValues[1]}, tid: ${it.groupValues[2]}")
                        }
                        body.matches(Regex("(?:[a-zA-Z][a-zA-Z0-9]*\\s+[a-fA-F0-9]{8,16}\\s*)+")) -> appendLine(body)
                        body.startsWith("#") -> {
                            val frame = Regex("^(#[0-9]+)\\s+pc\\s+([a-fA-F0-9]+)\\s+(.+)$").matchEntire(body)
                            if (frame != null) {
                                val rest = frame.groupValues[3]
                                val library = Regex("[A-Za-z0-9_.+-]+\\.(?:so|apk)").findAll(rest).lastOrNull()?.value ?: "unknown library"
                                val build = Regex("BuildId: ([a-fA-F0-9]+)").find(rest)?.groupValues?.get(1).orEmpty()
                                appendLine("${frame.groupValues[1]} pc ${frame.groupValues[2]} $library BuildId: $build")
                            }
                        }
                    }
                }
            }.trim()
        }.take(64 * 1024)
}
