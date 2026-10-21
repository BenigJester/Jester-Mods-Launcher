package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.niunaijun.blackbox.utils.LaunchSessionRecorder
import java.io.File

class LaunchSessionRecorderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `Binder resource samples retain only bounded numeric counters`() {
        val recorder = top.niunaijun.blackbox.utils.SessionResourceRecorder()
        val sample = recorder.record(1, 2, 3, 4, 5, 6, 0, 10, 20, 30, 40, -1)
        assertTrue(sample.contains("binderLocal=10 binderProxy=20 binderDeath=30 binderSentTotal=40 binderReceivedTotal=-1"))
        val legacy = recorder.record(2, 3, 4, 5, 6, 7, 0)
        assertTrue(legacy.lines().last { it.isNotBlank() }.contains("binderLocal=-1 binderProxy=-1 binderDeath=-1"))
    }

    @Test fun `resource history keeps latest samples in order with fixed numeric fields`() {
        val recorder = top.niunaijun.blackbox.utils.SessionResourceRecorder()
        var history = ""
        repeat(1000) { index ->
            recorder.foreground(index % 2 == 0)
            history = recorder.record(index.toLong(), index * 15_000L, index * 500L, 123, 45, 67, 0)
        }
        val lines = history.trim().lines()
        assertEquals(24, lines.size)
        assertTrue(lines.first().startsWith("976 "))
        assertTrue(lines.last().startsWith("999 "))
        assertTrue(lines.last().contains("activityResumed=false"))
        assertTrue(history.toByteArray().size < 16 * 1024)
        val session = temporary.newFolder("resource-session")
        top.niunaijun.blackbox.utils.SessionResourceRecorder.persist(session, 123, history)
        assertEquals(history, File(session, "resources-123.txt").readText())
        top.niunaijun.blackbox.utils.SessionResourceRecorder.persist(session, 123, "replacement\n")
        assertEquals("replacement\n", File(session, "resources-123.txt").readText())
        assertFalse(File(session, "resources-123.pending").exists())
        session.deleteRecursively()
        assertTrue(runCatching { top.niunaijun.blackbox.utils.SessionResourceRecorder.persist(session, 123, history) }.isFailure)
        assertFalse(session.exists())
    }

    @Test fun `dynamic code rejection classification confirms writable files without exposing paths`() {
        val dexFrame = StackTraceElement("dalvik.system.DexFile", "openDexFileNative", "DexFile.java", -2)
        val error = SecurityException("Writable dex file '/private/account/secret.jar' is not allowed.").apply {
            stackTrace = arrayOf(dexFrame)
        }
        val report = LaunchSessionRecorder.exceptionFrames(error)
        assertTrue(report.contains("dynamic_code_rejection: writable_file_blocked"))
        assertFalse(report.contains("secret.jar"))
        val unknown = SecurityException("credential-token").apply { stackTrace = arrayOf(dexFrame) }
        assertEquals("unclassified_security_rejection", LaunchSessionRecorder.dynamicCodeRejection(unknown))
        assertFalse(LaunchSessionRecorder.exceptionFrames(unknown).contains("credential-token"))
        assertNull(LaunchSessionRecorder.dynamicCodeRejection(SecurityException(error.message).apply { stackTrace = emptyArray() }))
        assertNull(LaunchSessionRecorder.dynamicCodeRejection(RuntimeException(error.message).apply { stackTrace = arrayOf(dexFrame) }))
    }

    @Test fun `session writes are isolated persisted bounded and newline safe`() {
        val old = temporary.newFolder("old")
        val fresh = temporary.newFolder("fresh")
        LaunchSessionRecorder.hostEvent(old, "application_started")
        LaunchSessionRecorder.hostEvent(fresh, "launch_requested\nnot_a_new_event")
        assertTrue(File(old, "host.txt").readText().contains("application_started"))
        assertFalse(File(fresh, "host.txt").readText().contains("application_started"))
        assertEquals(1, File(fresh, "host.txt").readLines().size)
        repeat(1000) { LaunchSessionRecorder.hostEvent(fresh, "event " + "a".repeat(100)) }
        assertTrue(File(fresh, "host.txt").length() <= LaunchSessionRecorder.MAX_EVENT_BYTES)
    }
    @Test fun `recorder does not recreate a pruned session or accept path traversal`() {
        val absent = File(temporary.root, "pruned")
        LaunchSessionRecorder.hostEvent(absent, "late_process_event")
        assertFalse(absent.exists())
        for (name in listOf("..", ".", "pkg/../../other", "pkg..name")) {
            assertTrue(runCatching { LaunchSessionRecorder.packageDirectory(name) }.isFailure)
        }
    }

    @Test fun `deep proxy chains expose underlying cause first without private messages`() {
        val root = SecurityException("private-account-token").apply { stackTrace = emptyArray() }
        var failure: Throwable = root
        repeat(12) { failure = java.lang.reflect.InvocationTargetException(failure, "private-url").apply { stackTrace = emptyArray() } }
        val report = LaunchSessionRecorder.exceptionFrames(failure)
        assertTrue(report.indexOf("cause java.lang.SecurityException") < report.indexOf("cause java.lang.reflect.InvocationTargetException"))
        assertEquals(13, report.lineSequence().count { it.startsWith("cause ") })
        assertFalse(report.contains("private-account-token"))
        assertFalse(report.contains("private-url"))
    }

    @Test fun `cause cycles depth and frame output are bounded and marked`() {
        val first = RuntimeException()
        val second = RuntimeException(first)
        first.initCause(second)
        assertTrue(LaunchSessionRecorder.exceptionFrames(first).contains("cyclic"))
        var deep: Throwable = IllegalStateException().apply { stackTrace = emptyArray() }
        repeat(100) { deep = RuntimeException(deep).apply { stackTrace = emptyArray() } }
        val bounded = LaunchSessionRecorder.exceptionFrames(deep)
        assertEquals(32, bounded.lineSequence().count { it.startsWith("cause ") })
        assertTrue(bounded.contains("chain truncated"))
        val huge = RuntimeException().apply {
            stackTrace = Array(100) { StackTraceElement("界".repeat(20_000), "method", "private-path", 1) }
        }
        val frames = LaunchSessionRecorder.exceptionFrames(huge)
        assertTrue(frames.toByteArray(Charsets.UTF_8).size <= LaunchSessionRecorder.MAX_EVENT_BYTES)
        assertTrue(frames.contains("frames truncated"))
        assertFalse(frames.contains("private-path"))
    }

    @Test fun `audio service has one registered proxy and normalizes before forwarding`() {
        val path = "third_party/BlackBox/Bcore/src/main/java/top/niunaijun/blackbox/fake/"
        fun source(name: String): String = listOf(File(path + name), File("../$path$name"))
            .first { it.isFile }.readText()
        val manager = source("hook/HookManager.java")
        assertFalse(manager.contains("addInjector(new IAudioServiceProxy())"))
        assertEquals(1, Regex("addInjector\\(new AudioPermissionProxy\\(\\)\\)").findAll(manager).count())
        val proxy = source("service/AudioPermissionProxy.java").substringAfter("public Object invoke(")
            .substringBefore("@ProxyMethod")
        assertTrue(proxy.indexOf("replaceAllAppPkg(args)") < proxy.indexOf("super.invoke(proxy, method, args)"))
        assertTrue(proxy.contains("throw error.getTargetException()"))
        assertFalse(proxy.contains("AUDIOFOCUS_REQUEST_GRANTED"))
    }
}
