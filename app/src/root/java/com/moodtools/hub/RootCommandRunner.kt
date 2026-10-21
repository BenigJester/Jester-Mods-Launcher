package com.moodtools.hub

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Drain while waiting: verbose injectors must not block on a full stdout pipe. */
internal fun runRootCommand(arguments: List<String>, timeoutSeconds: Long = 60): RootShell.Result {
    var process: Process? = null
    val reader = Executors.newSingleThreadExecutor { task -> Thread(task, "RootCommandReader").apply { isDaemon = true } }
    val output = StringBuilder()
    return try {
        val running = ProcessBuilder(arguments).redirectErrorStream(true).start()
        process = running
        val reading = reader.submit {
            running.inputStream.bufferedReader().use { input ->
                val buffer = CharArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    synchronized(output) {
                        // Keep draining even after the retention limit is reached.
                        val remaining = 128 * 1024 - output.length
                        if (remaining > 0) output.append(buffer, 0, minOf(count, remaining))
                    }
                }
            }
        }
        val completed = running.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) running.destroyForcibly()
        runCatching { reading.get(1, TimeUnit.SECONDS) }
        RootShell.Result(completed && running.exitValue() == 0,
            synchronized(output) { output.toString() }, if (completed) running.exitValue() else null, !completed)
    } catch (error: Exception) {
        RootShell.Result(false, "Root command unavailable")
    } finally {
        process?.destroyForcibly()
        runCatching { process?.inputStream?.close() }
        reader.shutdownNow()
    }
}
