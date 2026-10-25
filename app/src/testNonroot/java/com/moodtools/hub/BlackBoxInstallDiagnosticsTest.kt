package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test
import top.niunaijun.blackbox.utils.InstallFailureDiagnostics
import top.niunaijun.blackbox.utils.LaunchSessionRecorder
import java.io.File
import java.nio.file.Files

class BlackBoxInstallDiagnosticsTest {
    @Test fun `install result categories persist without private error content`() {
        val session = Files.createTempDirectory("install-diagnostics").toFile()
        try {
            val cases = mapOf(
                "install_exception:parse:permission" to "install_exception:parse:permission",
                "install apk error." to "package_copy_failed",
                "parser apk error." to "package_parse_failed",
                "getPackageArchiveInfo error.Please check whether APK is normal." to "archive_inspection_failed",
                "Remote exception during install" to "service_unavailable",
                "private /data/user/account@example.com/token" to "unclassified",
                "install_exception:parse:permission\nsecret" to "unclassified"
            )
            for ((message, expected) in cases) {
                val category = InstallFailureDiagnostics.resultCategory(message)
                assertEquals(expected, category)
                LaunchSessionRecorder.hostEvent(session, "guest_install_result success=false category=$category")
            }
            assertEquals("unclassified", InstallFailureDiagnostics.resultCategory(null))
            assertEquals("permission", InstallFailureDiagnostics.exceptionKind(SecurityException("secret")))
            val report = File(session, "host.txt").readText()
            assertTrue(report.contains("category=package_copy_failed"))
            for (privateText in listOf("/data/", "account@", "token", "secret")) assertFalse(report.contains(privateText))
        } finally {
            session.deleteRecursively()
        }
    }

    @Test fun `both game install modes record result before failure is thrown`() {
        val source = listOf(File("src/nonroot/java/com/moodtools/hub/NonRootBlackBoxRuntime.kt"),
            File("app/src/nonroot/java/com/moodtools/hub/NonRootBlackBoxRuntime.kt")).first { it.isFile }.readText()
        val install = source.substringAfter("\"guest_install_started mode=").substringBefore("\"guest_package_installed\"")
        assertTrue(install.contains("core.installPackageAsUserWithPrivateNativeLibraries"))
        assertTrue(install.contains("core.installPackageAsUser("))
        assertTrue(install.contains("guest_install_exception category="))
        assertTrue(install.indexOf("guest_install_result success=") < install.indexOf("if (!install.success)"))
        assertTrue(install.contains("InstallFailureDiagnostics.resultCategory(install.msg)"))
    }
}
