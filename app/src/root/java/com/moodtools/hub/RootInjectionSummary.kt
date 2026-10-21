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
        "Failed to resume target process threads" to "resume_failed"
    )
    return buildString {
        appendLine("Root add-on injection failed")
        appendLine("Command exit code: ${result.exitCode ?: "unavailable"}")
        appendLine("Command timed out: ${result.timedOut}")
        appendLine("Root provider: ${if (rootVersion.contains("MAGISK", true)) "Magisk" else if (rootVersion.contains("KernelSU", true)) "KernelSU" else if (rootVersion.contains("APatch", true)) "APatch" else "unidentified"}")
        appendLine("Root version number: ${Regex("^[0-9]+(?:\\.[0-9]+){0,3}").find(rootVersion.trim())?.value ?: "unavailable"}")
        val failures = stages.filterKeys(output::contains).values
        appendLine("Failure stages: ${failures.joinToString().ifEmpty { "unclassified" }}")
        appendLine("Architecture mismatch reported: ${Regex("Injector is (?:32|64)bit but target app is (?:32|64)bit").containsMatchIn(output)}")
        appendLine("Permission denial reported: ${output.contains("Permission denied", true) || output.contains("Operation not permitted", true)}")
        appendLine("Target cleanup reported: ${output.contains("Killed target process.")}")
        appendLine("An injection failure can close a game after it opens. This is not proof of a game crash or a root-provider defect.")
    }
}
