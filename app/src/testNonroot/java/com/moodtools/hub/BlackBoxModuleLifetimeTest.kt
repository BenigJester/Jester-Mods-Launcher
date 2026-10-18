package com.moodtools.hub

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Contract checks for Android lifecycle code that cannot execute in a plain JVM test. */
class BlackBoxModuleLifetimeTest {
    @Test fun `resume uses the initialized entry class without rereading staged configuration`() {
        val source = source("HubApplication.java")
        val attach = source.substringAfter("private void startModule(")
            .substringBefore("private void checkMenuAttachment(")
        assertTrue(source.contains("Map<String, Class<?>> moduleEntries = new ConcurrentHashMap<>()"))
        assertTrue(attach.contains("Class<?> main = moduleEntries.get(packageName)"))
        assertTrue(attach.contains("ensureModuleLoaded(packageName, 0)"))
        assertTrue(attach.contains("main.getMethod(\"StartRoot\""))
        assertFalse(attach.contains("Class.forName"))
        assertFalse(source.contains("findEntryPoint("))

        val load = source.substringAfter("private synchronized boolean ensureModuleLoaded(")
        assertTrue(load.contains("Class<?> main = Class.forName(entryPoint, true, loader)"))
        assertTrue(load.indexOf("moduleEntries.put(packageName, main)") >
            load.indexOf("runtime.getMethod(\"loadNative\""))
        assertTrue(load.indexOf("moduleEntries.containsKey(packageName)") <
            load.indexOf("File moduleDirectory"))
    }

    @Test fun `launch failure retains payload while fresh launch still replaces it`() {
        val source = source("NonRootBlackBoxRuntime.kt")
        val launch = source.substringAfter("fun launch(context:").substringBefore("private fun launchApk(")
        val failure = launch.substringAfter("}.onFailure { error ->")
        assertTrue(failure.contains("NonRootLaunchDiagnostics.failure(diagnosticSession, error)"))
        assertTrue(failure.contains("lastFailureDetail ="))
        assertFalse(failure.contains("clearModulePayload("))
        assertFalse(failure.contains("stopPackage("))
        val staging = launch.substringBefore("}.onFailure { error ->")
        assertEquals(2, Regex("clearModulePayload\\(").findAll(staging).count())
        assertTrue(staging.indexOf("core.stopPackage(game.packageName, userId)") <
            staging.indexOf("clearModulePayload(game.packageName, userId)"))
        assertTrue(staging.indexOf("stageModulePayload(context, game, userId)") <
            staging.indexOf("check(launchApk("))
    }

    private fun source(name: String): String {
        val path = "src/nonroot/java/com/moodtools/hub/$name"
        return listOf(File(path), File("app/$path")).first { it.isFile }.readText()
    }
}
