package com.moodtools.hub

/** Fixed categories only; never export command output, paths or arbitrary error messages. */
internal fun rootInjectionSummary(result: RootShell.Result, rootVersion: String): String {
    val output = result.output.take(128 * 1024)
    val stages = linkedMapOf(
        "Failed to launch app" to "game_launch_failed",
        "Failed to attach to target process" to "attach_failed",
        "Failed to stop target process threads" to "stop_failed",
        "Failed to interrupt target process" to "interrupt_failed",
        "Couldn't initialize injector" to "injector_initialization_failed",
        "Failed to wait for breakpoint" to "breakpoint_failed",
        "Failed to inject " to "library_injection_failed",
        "Failed to wait syscall for detach" to "detach_wait_failed",
        "Failed to detach" to "detach_failed",
        "Failed to resume target process threads" to "resume_failed",
        "am_process_start_cb:" to "process_event_reader_failed",
        "Failed to monitor process start." to "process_monitor_failed"
    )
    return buildString {
        appendLine("Root add-on injection failed")
        appendLine("Command exit code: ${result.exitCode ?: "unavailable"}")
        appendLine("Command timed out: ${result.timedOut}")
        appendLine("Command output truncated: ${result.outputTruncated}")
        appendLine("Command output read complete: ${result.outputReadComplete ?: "unavailable"}")
        fun boundedTime(value: Long?): String = value?.takeIf { it in 0..604_800_000 }?.toString() ?: "unavailable"
        appendLine("Command elapsed ms: ${boundedTime(result.durationMs)}")
        appendLine("Command timeout ms: ${boundedTime(result.timeoutMs)}")
        appendLine("Last command output age ms: ${boundedTime(result.lastOutputAgeMs)}")
        val phases = mapOf(
            "Root launch phase: root_shell_ready" to "root_shell_ready",
            "I: ======== INJECTION ARGS ========" to "injector_started",
            "I: Attached to target process successfully." to "target_attached",
            "I: Initializing Injector..." to "initializing",
            "I: Injector Initialized." to "initialized",
            "I: Injector: Setting up breakpoint..." to "waiting_for_breakpoint",
            "I: Injector: Breakpoint triggered successfully." to "breakpoint_reached",
            "I: Dettached from target process successfully." to "target_detached",
            "I: Injection succeeded." to "completed"
        )
        val phase = output.lineSequence().mapNotNull { line ->
            val clean = line.trimEnd('\r')
            phases[clean] ?: when {
                clean in setOf("I: Root process watch phase: event_reader_ready",
                    "I: Root process watch phase: initial_event_received",
                    "I: Root process watch phase: launch_requested",
                    "I: Root process watch phase: launch_command_returned",
                    "I: Root process watch phase: target_event_received") -> clean.substringAfterLast(": ")
                clean.matches(Regex("I: Monitoring [A-Za-z0-9_.]+\\.\\.\\.")) -> "waiting_for_process"
                clean.startsWith("I: ===== Injecting ") && clean.endsWith("...") -> "injecting_library"
                else -> null
            }
        }.lastOrNull()
        appendLine("Last observed injection phase: ${phase ?: "unavailable"}")
        appendLine("A recorded phase confirms progress only, not where or why the command stopped.")
        appendLine("A returned launch command does not confirm game startup; output age measures command activity, not game responsiveness.")
        appendLine("Root provider: ${if (rootVersion.contains("MAGISK", true)) "Magisk" else if (rootVersion.contains("KernelSU", true)) "KernelSU" else if (rootVersion.contains("APatch", true)) "APatch" else "unidentified"}")
        appendLine("Root version number: ${Regex("^[0-9]+(?:\\.[0-9]+){0,3}").find(rootVersion.trim())?.value ?: "unavailable"}")
        val failures = stages.filterKeys(output::contains).values
        appendLine("Failure stages: ${failures.joinToString().ifEmpty { "unclassified" }}")
        val breakpointResults = setOf("success", "timeout", "target_exited", "setup_failed", "cleanup_failed",
            "continue_failed", "step_failed", "registers_failed", "wait_failed", "stop_failed",
            "unexpected_stop", "target_missing", "invalid_state")
        val breakpoint = Regex("Root breakpoint result: ([a-z_]+)(?=\\r?$)", RegexOption.MULTILINE)
            .findAll(output).map { it.groupValues[1] }.lastOrNull { it in breakpointResults }
        appendLine("Breakpoint result: ${breakpoint ?: "unavailable"}")
        val setupErrno = Regex("Root breakpoint setup errno: ([0-9]{1,4})(?=\\r?$)", RegexOption.MULTILINE)
            .findAll(output).lastOrNull()?.groupValues?.get(1)
        appendLine("Breakpoint setup errno: ${setupErrno ?: "unavailable"}")
        val pauseMode = Regex("Root thread pause mode: (main_thread_only)(?=\\r?$)", RegexOption.MULTILINE)
            .find(output)?.groupValues?.get(1)
        appendLine("Thread pause mode: ${pauseMode ?: "unavailable"}")
        for (field in listOf("hits", "skipped")) {
            val count = Regex("Root breakpoint $field: ([0-9]{1,9})(?=\\r?$)", RegexOption.MULTILINE)
                .findAll(output).lastOrNull()?.groupValues?.get(1)
            appendLine("Breakpoint $field: ${count ?: "unavailable"}")
        }
        fun fixedField(field: String, pattern: String): String =
            Regex("Root ${Regex.escape(field)}: ($pattern)(?=\\r?$)", RegexOption.MULTILINE)
                .findAll(output).lastOrNull()?.groupValues?.get(1) ?: "unavailable"
        appendLine("Kernel version: ${fixedField("kernel version", "[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}")}")
        appendLine("Trace method: ${fixedField("trace method", "seize|attach")}")
        appendLine("Installed breakpoint slots: ${fixedField("wait installed slots", "[1-4]")}")
        appendLine("Startup wait elapsed ms: ${fixedField("wait elapsed ms", "[0-9]{1,6}")}")
        appendLine("Main thread state before timeout cleanup: ${fixedField("wait timeout state", "running|sleeping|uninterruptible|stopped|tracing_stop|zombie|dead|idle|unavailable")}")
        appendLine("Main thread wait category before timeout cleanup: ${fixedField("wait timeout channel", "futex|binder|poll|trace_stop|other|unavailable")}")
        appendLine("Thread count at timeout: ${fixedField("wait timeout threads", "[1-9][0-9]{0,4}")}")
        appendLine("Wait observations are separate snapshots, not proof of a deadlock or its cause. Zero or hidden wait symbols are unavailable.")
        appendLine("Architecture mismatch reported: ${Regex("Injector is (?:32|64)bit but target app is (?:32|64)bit").containsMatchIn(output)}")
        appendLine("Permission denial reported: ${output.contains("Permission denied", true) || output.contains("Operation not permitted", true)}")
        appendLine("Target cleanup reported: ${output.contains("Killed target process.")}")
        appendLine("An injection failure can close a game after it opens. This is not proof of a game crash or a root-provider defect.")
    }
}
