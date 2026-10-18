package com.moodtools.hub.soulpatch

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectPatchNativePackagingTest {
    @Test
    fun `injected library uses stored aligned writer and invalidates old patches`() {
        val path = "src/nonroot/java/com/moodtools/hub/soulpatch/SoulKnightPatchManager.kt"
        val source = listOf(File(path), File("app/$path")).first { it.isFile }.readText()
        assertTrue(source.contains("putStoredFile(destination, nativeEntry, moduleNative, counting)"))
        assertFalse(source.contains("putFile(destination, nativeEntry, moduleNative, ZipEntry.DEFLATED"))
        val writer = source.substringAfter("private fun putStoredFile(").substringBefore("private fun putCopiedEntry(")
        assertTrue(writer.contains("method = ZipEntry.STORED"))
        assertTrue(writer.contains("extra = alignmentExtra(counting.count, name, alignmentFor(name))"))
        assertTrue(source.contains("name.endsWith(\".so\", ignoreCase = true) -> 16 * 1024"))
        assertTrue(source.contains("private const val PATCH_MARKER_SCHEMA = 4"))
        assertTrue(source.contains("\"\${game.versionCode}-\$PATCH_MARKER_SCHEMA-\$moduleFingerprint\""))
    }
}
