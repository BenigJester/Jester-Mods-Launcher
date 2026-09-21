package com.moodtools.hub.modules

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedLocalTestModuleInstallerTest {
    @Test
    fun externalStageTakesPrecedenceOnlyWhenComplete() {
        val directory = Files.createTempDirectory("external-module-stage").toFile()
        try {
            File(directory, "config.json").writeText("{}")
            File(directory, "classes.dex").writeText("dex")
            File(directory, "libmenu_native.so").writeText("native")
            File(directory, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).writeText("{}")

            assertTrue(EmbeddedLocalTestModuleInstaller.externalStagePresent(directory))

            File(directory, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).writeText(
                """{"source":"embedded-launcher"}"""
            )
            assertFalse(EmbeddedLocalTestModuleInstaller.externalStagePresent(directory))

            File(directory, "classes.dex").delete()
            File(directory, ModuleRepository.LOCAL_TEST_INSTALL_MARKER).writeText("{}")
            assertFalse(EmbeddedLocalTestModuleInstaller.externalStagePresent(directory))
        } finally {
            directory.deleteRecursively()
        }
    }
}
