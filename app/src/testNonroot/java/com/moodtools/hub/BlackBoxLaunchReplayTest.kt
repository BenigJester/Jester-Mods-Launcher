package com.moodtools.hub

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BlackBoxLaunchReplayTest {
    @Test fun `nonroot launches bypass the recreatable BlackBox splash`() {
        val application = source("HubApplication.java")
        val policy = application.substringAfter("public boolean isEnableLauncherActivity() {")
            .substringBefore("\n            }")
        assertTrue(policy.contains("return false;"))
        val core = repoFile("third_party/BlackBox/Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java").readText()
        val dispatch = core.substringAfter("public void startActivity(Intent intent, int userId)")
            .substringBefore("public static BJobManager")
        assertTrue(dispatch.contains("if (mClientConfiguration.isEnableLauncherActivity())"))
        assertTrue(dispatch.contains("} else {\n            getBActivityManager().startActivity(intent, userId);"))
    }

    @Test fun `one resume request cannot fall through into a second fresh launch`() {
        val runtime = source("NonRootBlackBoxRuntime.kt")
        val resume = runtime.substringAfter("if (resumePid > 0")
            .substringBefore("if (running) NonRootLaunchDiagnostics.stopSession")
        assertEquals(1, Regex("launchApk\\(").findAll(resume).count())
        assertTrue(resume.contains("if (!confirmed) {"))
        val unconfirmed = resume.substringAfter("if (!confirmed) {").substringBefore("\n                }")
        assertTrue(unconfirmed.contains("resume_unconfirmed_no_restart"))
        assertTrue(unconfirmed.contains("error("))
        assertFalse(resume.contains("stopPackage("))
        assertFalse(resume.contains("stopSession("))
        assertFalse(resume.contains("clearModulePayload("))
        assertFalse(resume.contains("NonRootLaunchDiagnostics.begin("))
        assertTrue(resume.contains("return@runCatching true"))
        assertFalse(runtime.contains("resume_unconfirmed_fresh_retry"))
    }

    private fun source(name: String) = repoFile("app/src/nonroot/java/com/moodtools/hub/$name").readText()
    private fun repoFile(path: String): File = listOf(File(path), File("../$path")).first { it.isFile }
}
