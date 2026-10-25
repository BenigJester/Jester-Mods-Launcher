package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RootInjectionDiagnosticsTest {
    @Test fun `root launch records session without waiting for crash buffer collection`() {
        val path = "app/src/root/java/com/moodtools/hub/RootLaunchDiagnostics.kt"
        val backend = listOf(File(path), File("../$path")).first { it.isFile }.readText()
        val begin = backend.substringAfter("fun begin(").substringBefore("@Synchronized fun injectionFailed(")
        assertFalse(begin.contains("reports(context)"))
        assertFalse(begin.contains("readCrashBuffer()"))
        assertTrue(begin.contains("metadata.json"))
        assertFalse(backend.contains("@Synchronized override fun reports("))
        val capture = backend.substringAfter("override fun reports(").substringBefore("/** Drain concurrently")
        val beforeRead = capture.substringBefore("val log = if (shouldRead)")
        assertTrue(beforeRead.contains("readingCrashBuffer = true"))
        assertTrue(beforeRead.contains("} else false\n        }")) // The session lock ends before logcat.
        assertTrue(capture.contains("synchronized(this) { readingCrashBuffer = false }"))
        assertTrue(capture.substringAfter("return synchronized(this)").contains("val sessions = sessions()"))
    }

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
        assertTrue(result.outputTruncated)
        assertEquals(true, result.outputReadComplete)
        assertTrue(runRootCommand(command("0"), 10).success)
    }

    @Test fun `timeout is bounded and distinguished from an exit code`() {
        val result = runRootCommand(command("timeout"), 1)
        assertTrue(result.timedOut)
        assertFalse(result.success)
        assertNull(result.exitCode)
    }

    @Test fun `timeout retains initial shell evidence and latest progress after noisy output`() {
        val result = runRootCommand(command("progress-timeout"), 3)
        assertTrue(result.timedOut)
        assertTrue(result.outputTruncated)
        assertTrue(result.output.startsWith("Root launch phase: root_shell_ready\n"))
        assertTrue(result.output.endsWith("I: Injector: Setting up breakpoint...\n"))
        assertEquals(128 * 1024, result.output.length)
        val summary = rootInjectionSummary(result, "3.4.2:KernelSU")
        assertTrue(summary.contains("Last observed injection phase: waiting_for_breakpoint\n"))
        assertTrue(summary.contains("Command output truncated: true"))
        assertFalse(summary.contains("xxxx"))
    }

    @Test fun `root phase report uses only fixed categories not private command text`() {
        val summary = rootInjectionSummary(RootShell.Result(false,
            "Root launch phase: root_shell_ready\nI: Monitoring com.example.game...\n" +
                "I: ===== Injecting /private/account-token.so...\n", timedOut = true), "unknown")
        assertTrue(summary.contains("Last observed injection phase: injecting_library\n"))
        assertFalse(summary.contains("private"))
        assertFalse(summary.contains("account-token"))
        val unknown = rootInjectionSummary(RootShell.Result(false,
            "Root launch phase: root_shell_ready private-token\nI: Injector Initialized. private-token\n"), "unknown")
        assertTrue(unknown.contains("Last observed injection phase: unavailable"))
        val shell = rootInjectionSummary(RootShell.Result(false, "Root launch phase: root_shell_ready\n", timedOut = true), "unknown")
        assertTrue(shell.contains("Last observed injection phase: root_shell_ready\n"))
        val path = "app/src/root/java/com/moodtools/hub/ExecutionModeLaunchBridge.kt"
        val bridge = listOf(File(path), File("../$path")).first { it.isFile }.readText()
        assertTrue(bridge.contains("printf '%s\\\\n' 'Root launch phase: root_shell_ready';"))
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

    @Test fun `breakpoint details are fixed categories and bounded numbers only`() {
        val categories = listOf("success", "timeout", "target_exited", "setup_failed", "cleanup_failed",
            "continue_failed", "step_failed", "registers_failed", "wait_failed", "stop_failed",
            "unexpected_stop", "target_missing", "invalid_state")
        categories.forEach { category ->
            val result = RootShell.Result(false,
                "E: Root breakpoint setup errno: 22\r\nI: Root breakpoint result: $category\r\n" +
                "Injector: Failed to wait for breakpoint! /private/token", 1)
            val summary = rootInjectionSummary(result, "31.0:MAGISKSU")
            assertTrue(summary.contains("Failure stages: breakpoint_failed"))
            assertTrue(summary.contains("Breakpoint result: $category\n"))
            assertTrue(summary.contains("Breakpoint setup errno: 22\n"))
            assertFalse(summary.contains("private"))
        }
        val untrusted = rootInjectionSummary(RootShell.Result(false,
            "Root breakpoint result: https://private/token\nRoot breakpoint setup errno: 123456\n" +
            "Root breakpoint result: timeout private-token", 1), "unknown")
        assertTrue(untrusted.contains("Breakpoint result: unavailable"))
        assertTrue(untrusted.contains("Breakpoint setup errno: unavailable"))
        assertFalse(untrusted.contains("private"))
    }

    @Test fun `native loader watches both entry points in the same wait and ships matching categories`() {
        fun source(path: String) = listOf(File(path), File("../$path")).first { it.isFile }.readText()
        val injector = source("third_party/AndKittyInjector/AndKittyInjector/src/Injector/KittyInjector.cpp")
            .substringAfter("bool KittyInjector::waitBreakpoint(").substringBefore("inject_elf_info_t KittyInjector::inject(")
        assertTrue(injector.contains("if (_rdlopen_ext) bp_addrs.push_back(_rdlopen_ext)"))
        assertTrue(injector.contains("_rdlopen != _rdlopen_ext"))
        assertTrue(injector.contains("setHardBreakpointsAndWait(\n               bp_addrs"))
        assertFalse(injector.contains("setSoftBreakpointAndWait"))
        val trace = source("third_party/AndKittyInjector/KittyMemoryEx/KittyMemoryEx/KittyTrace.cpp")
            .substringAfter("KT_BP_RESULT KittyTraceMgr::setHardBreakpointsAndWait(")
            .substringBefore("bool KittyTraceMgr::setHwBreakpoint(")
        assertTrue(trace.contains("clearHwBreakpoint(type, slot + int(--installed))"))
        assertTrue(trace.contains("if (!clear_all()) return KT_BP_CLEAR_FAILED"))
        assertTrue(trace.contains("deadline - std::chrono::steady_clock::now()"))
        assertTrue(trace.contains("std::any_of(addresses.begin(), addresses.end()"))
        assertEquals(2, Regex("observe_timeout\\(\\);\\s+if \\(!stop\\(\\)\\)").findAll(trace).count())
        listOf("arm64-v8a", "x86_64").forEach { abi ->
            val path = "app/src/root/assets/root-runtime/$abi/moodtools-injector"
            val binary = listOf(File(path), File("../$path")).first { it.isFile }
                .readBytes().toString(Charsets.ISO_8859_1)
            assertTrue("Bundled $abi injector must be rebuilt", binary.contains("Root breakpoint result: %s"))
            assertTrue(binary.contains("Injector: Trying hardware breakpoints..."))
            assertTrue("Bundled $abi injector must include thread-pause fix",
                binary.contains("Root thread pause mode: main_thread_only"))
            assertTrue(binary.contains("Root breakpoint hits: %u"))
            assertTrue(binary.contains("Root breakpoint skipped: %u"))
            assertTrue("Bundled $abi injector must include timeout snapshots", binary.contains("Root wait timeout state: %s"))
            assertTrue(binary.contains("Root wait installed slots: %u"))
            assertTrue(binary.contains("Root kernel version: %u.%u.%u"))
        }
    }

    @Test fun `injection pauses only the traced thread without process stop and continue`() {
        val path = "third_party/AndKittyInjector/AndKittyInjector/src/main.cpp"
        val main = listOf(File(path), File("../$path")).first { it.isFile }.readText()
        val inject = main.substring(main.lastIndexOf("bool inject(int pid,"))
            .substringBefore("bool inject_watch(")
        assertTrue(inject.contains("kmgr.trace.seize("))
        assertTrue(inject.contains("kmgr.trace.attach("))
        assertTrue(inject.contains("cfg.seize && !kmgr.trace.stop()"))
        assertTrue(inject.contains("Root thread pause mode: main_thread_only"))
        assertTrue(inject.contains("kmgr.trace.detach()"))
        assertFalse(inject.contains("SIGSTOP"))
        assertFalse(inject.contains("SIGCONT"))
    }

    @Test fun `thread pause and breakpoint counts do not retain untrusted text`() {
        val summary = rootInjectionSummary(RootShell.Result(false,
            "I: Root thread pause mode: main_thread_only\r\n" +
            "I: Root breakpoint result: timeout\r\n" +
            "I: Root breakpoint hits: 3\r\nI: Root breakpoint skipped: 3\r\n", 1), "31.0:MAGISKSU")
        assertTrue(summary.contains("Thread pause mode: main_thread_only\n"))
        assertTrue(summary.contains("Breakpoint hits: 3\n"))
        assertTrue(summary.contains("Breakpoint skipped: 3\n"))
        val untrusted = rootInjectionSummary(RootShell.Result(false,
            "Root thread pause mode: main_thread_only private-token\n" +
            "Root breakpoint hits: 1234567890\nRoot breakpoint skipped: /private/token\n", 1), "unknown")
        assertTrue(untrusted.contains("Thread pause mode: unavailable"))
        assertTrue(untrusted.contains("Breakpoint hits: unavailable"))
        assertTrue(untrusted.contains("Breakpoint skipped: unavailable"))
        assertFalse(untrusted.contains("private"))
        val old = rootInjectionSummary(RootShell.Result(false, "Failed to wait for breakpoint!", 1), "unknown")
        assertTrue(old.contains("Thread pause mode: unavailable"))
        assertTrue(old.contains("Breakpoint hits: unavailable"))
    }

    @Test fun `startup wait report retains only fixed categories and bounded numbers`() {
        val summary = rootInjectionSummary(RootShell.Result(false,
            "I: Root kernel version: 6.1.157\r\nI: Root trace method: seize\r\n" +
                "Root wait installed slots: 2\nRoot wait elapsed ms: 5001\n" +
                "Root wait timeout state: sleeping\nRoot wait timeout channel: futex\n" +
                "Root wait timeout threads: 37\n", 1), "11224:APatch")
        for (line in listOf("Kernel version: 6.1.157", "Trace method: seize", "Installed breakpoint slots: 2",
            "Startup wait elapsed ms: 5001", "Main thread state before timeout cleanup: sleeping",
            "Main thread wait category before timeout cleanup: futex", "Thread count at timeout: 37"))
            assertTrue(summary.contains("$line\n"))
        val untrusted = rootInjectionSummary(RootShell.Result(false,
            "Root kernel version: 6.1.157-private\nRoot trace method: seize private\n" +
                "Root wait installed slots: 5\nRoot wait elapsed ms: 1234567\n" +
                "Root wait timeout state: sleeping private\nRoot wait timeout channel: /private/wchan\n" +
                "Root wait timeout threads: 999999\n", 1), "unknown")
        assertFalse(untrusted.contains("private"))
        assertTrue(untrusted.contains("Thread count at timeout: unavailable"))
        assertTrue(untrusted.contains("Kernel version: unavailable"))
        assertTrue(untrusted.contains("Startup wait elapsed ms: unavailable"))
        val legacy = rootInjectionSummary(RootShell.Result(false, "Root breakpoint result: timeout", 1), "unknown")
        assertTrue(legacy.contains("Main thread state before timeout cleanup: unavailable"))
    }
}
