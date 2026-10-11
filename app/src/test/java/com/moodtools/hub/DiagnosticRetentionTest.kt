package com.moodtools.hub

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DiagnosticRetentionTest {
    @Test fun `starting either launcher flavor preserves history and keeps manual clear`() {
        for (flavor in listOf("nonroot" to "NonRoot", "root" to "Root")) {
            val path = "src/${flavor.first}/java/com/moodtools/hub/${flavor.second}LaunchDiagnostics.kt"
            val source = listOf(File(path), File("app/$path")).first { it.isFile }.readText()
            val begin = source.substringAfter("fun begin(").substringBefore("private fun ")
            assertFalse("${flavor.first} startup must not delete reports", begin.contains("delete"))
            assertFalse("${flavor.first} startup must not prune reports", begin.contains("take(2)"))
            assertTrue(source.substringAfter("override fun clear(").contains("deleteDiagnosticSession"))
        }
    }
}
