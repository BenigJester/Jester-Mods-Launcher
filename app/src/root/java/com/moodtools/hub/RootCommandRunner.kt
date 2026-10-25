package com.moodtools.hub

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Drain while waiting: verbose injectors must not block on a full stdout pipe. */
internal fun runRootCommand(arguments: List<String>, timeoutSeconds: Long = 60): RootShell.Result {
    var process: Process? = null
    val reader = Executors.newSingleThreadExecutor { task -> Thread(task, "RootCommandReader").apply { isDaemon = true } }
    val output = StringBuilder()
    var outputTruncated = false
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
                        output.append(buffer, 0, count)
                        if (output.length > 128 * 1024) {
                            // Keep initial setup and latest progress, not only a noisy prefix.
                            // A newline prevents the two retained fragments forming a false field.
                            output.delete(64 * 1024, 64 * 1024 + output.length - 128 * 1024 + 1)
                            output.insert(64 * 1024, '\n')
                            outputTruncated = true
                        }
                    }
                }
            }
        }
        val completed = running.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) running.destroyForcibly()
        val outputReadComplete = runCatching { reading.get(1, TimeUnit.SECONDS) }.isSuccess
        RootShell.Result(completed && running.exitValue() == 0,
            synchronized(output) { output.toString() }, if (completed) running.exitValue() else null, !completed,
            synchronized(output) { outputTruncated }, outputReadComplete)
    } catch (error: Exception) {
        RootShell.Result(false, "Root command unavailable")
    } finally {
        process?.destroyForcibly()
        runCatching { process?.inputStream?.close() }
        reader.shutdownNow()
    }
}
