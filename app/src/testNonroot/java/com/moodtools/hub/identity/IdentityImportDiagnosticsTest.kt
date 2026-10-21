package com.moodtools.hub.identity

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class IdentityImportDiagnosticsTest {
    private fun source(path: String) = listOf(File(path), File("../$path")).first { it.isFile }.readText()

    @Test fun `installer cannot return default success after an exception`() {
        val install = source("third_party/BlackBox/Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/BPackageManagerService.java")
            .substringAfter("private InstallResult installPackageAsUserLocked(").substringBefore("private String resolveDisplayName(")
        assertTrue(install.indexOf("result.success = false") < install.indexOf("try {"))
        assertEquals(1, Regex("result.success = true").findAll(install).count())
        assertTrue(install.indexOf("onPackageInstalled(") < install.indexOf("result.success = true"))
        assertTrue(install.substringAfter("catch (Throwable t)").contains("result.installError(\"install_exception:\""))
        assertFalse(install.substringAfter("catch (Throwable t)").contains("t.getMessage()"))
    }

    @Test fun `import evidence keeps bounded numeric checkpoints and no raw error text`() {
        val shell = source("identity-shell-template/src/main/java/com/moodtools/identity/IdentityShellActivity.java")
        val imports = shell.substringAfter("private void importOriginalGame(").substringBefore("private int countPayloadSplits(")
        for (event in listOf("identity_import_payload bytes=", "identity_import_install_started attempt=",
            "identity_import_install_result success=", "identity_import_installed_check installed=true expectedSplits=",
            "identity_import_exception phase=", "identity_import_verified")) assertTrue(event, imports.contains(event))
        assertTrue(imports.contains("attempt < 2"))
        assertTrue(imports.contains("check < 8"))
        assertTrue(imports.contains("if (info != null && importedSplitCount == expectedSplitCount) return true"))
        assertFalse(imports.lineSequence().filter { it.contains("DiagnosticRuntime.event") }.any {
            it.contains("getMessage()") || it.contains("getAbsolutePath()") || it.contains("targetPackage")
        })
    }
}
