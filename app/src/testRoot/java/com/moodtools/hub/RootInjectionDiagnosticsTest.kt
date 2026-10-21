package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RootInjectionDiagnosticsTest {
    private fun command(mode: String): List<String> = listOf(
        File(System.getProperty("java.home"), "bin/java").absolutePath, "-cp",
        File(requireNotNull(RootCommandFixture::class.java.protectionDomain).codeSource.location.toURI()).absolutePath,
        RootCommandFixture::class.java.name, mode)

    @Test fun `large output is drained without deadlock and exit code is preserved`() {
        val result = runRootCommand(command("7"), 10)
        assertFalse(result.success)
        assertFalse(result.timedOut)
        assertEquals(7, result.exitCode)
        assertEquals(128 * 1024, result.output.length)
        assertTrue(runRootCommand(command("0"), 10).success)
    }

    @Test fun `timeout is bounded and distinguished from an exit code`() {
        val result = runRootCommand(command("timeout"), 1)
        assertTrue(result.timedOut)
        assertFalse(result.success)
        assertNull(result.exitCode)
    }

    @Test fun `injection evidence retains categories not private output`() {
        val result = RootShell.Result(false, "Failed to attach to target process. Permission denied /private/token\nKilled target process.", 1)
        val summary = rootInjectionSummary(result, "30.2:MAGISKSU private-token")
        assertTrue(summary.contains("attach_failed"))
        assertTrue(summary.contains("Root provider: Magisk"))
        assertTrue(summary.contains("Root version number: 30.2"))
        assertTrue(summary.contains("Permission denial reported: true"))
        assertTrue(summary.contains("Target cleanup reported: true"))
        assertFalse(summary.contains("private"))
        assertFalse(summary.contains("/"))
        assertTrue(rootInjectionSummary(RootShell.Result(false, "unknown secret"), "secret").contains("Failure stages: unclassified"))
    }

    @Test fun `failure report uses pinned session without recreating cleared directories`() {
        fun source(path: String) = listOf(File(path), File("../$path")).first { it.isFile }.readText()
        val backend = source("app/src/root/java/com/moodtools/hub/RootLaunchDiagnostics.kt")
        val writer = backend.substringAfter("fun injectionFailed(").substringBefore("private fun sessions(")
        assertTrue(writer.contains("!session.isDirectory"))
        assertTrue(writer.contains("session.canonicalFile.parentFile != parent.canonicalFile"))
        assertFalse(writer.contains("mkdir"))
        assertTrue(backend.contains("if (crash.isBlank() && injection.isBlank()) return@mapNotNull null"))
        val bridge = source("app/src/root/java/com/moodtools/hub/ExecutionModeLaunchBridge.kt")
        assertTrue(bridge.contains("RootLaunchDiagnostics.injectionFailed(diagnosticSession, evidence)"))
        assertFalse(bridge.contains("Root launch failed: \u0024{injection.output"))
        val keys = listOf("Root add-on setup failed", "View or share a Root launch or crash report.",
            "Root mode records add-on setup failures and available game crash details. No loading checks are recorded.",
            "The add-on could not start. The game may have closed during setup. Check Diagnostics for details.")
        keys.forEach { key -> assertEquals(9, LauncherDiagnosticTranslations.entries.getValue(key).size) }
    }
}
