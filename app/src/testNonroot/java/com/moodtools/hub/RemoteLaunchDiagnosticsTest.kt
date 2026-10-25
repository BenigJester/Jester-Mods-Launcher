package com.moodtools.hub

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteLaunchDiagnosticsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val pkg = "com.example.game"
    private val metadata = """{"package":"$pkg","title":"Game","started":100,"runtime":"direct_patch"}"""
    private fun archive(vararg files: Pair<String, ByteArray>): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
        return ByteArrayInputStream(out.toByteArray())
    }
    @Test fun `imports remote evidence without retaining raw native traces`() {
        val directory = File(temporary.root, "100-abcd")
        RemoteLaunchDiagnostics.importSnapshot(archive("metadata.txt" to metadata.toByteArray(),
            "process-123.txt" to "100 guest_attached main=true\n101 activity_resumed game.Main\n".toByteArray(),
            "connections-123.txt" to "100 operation=bind call=1\n  at game.Client.start:42\n".toByteArray(),
            "exit-123-200.txt" to "Exit reason: 5; status: 0;\n".toByteArray(),
            "exit-123-200.pb" to byteArrayOf(0x0f)), directory, pkg)
        assertTrue(File(directory, "process-123.txt").readText().contains("activity_resumed"))
        assertTrue(File(directory, "connections-123.txt").readText().contains("game.Client.start:42"))
        assertFalse(File(directory, "exit-123-200.pb").exists())
        assertTrue(File(directory, "exit-123-200.txt").readText().contains("unreadable"))
    }
    @Test fun `malformed foreign and oversized exports fail before writing`() {
        val directory = File(temporary.root, "100-abcd")
        assertThrows(IllegalArgumentException::class.java) {
            RemoteLaunchDiagnostics.importSnapshot(archive("../escape.txt" to byteArrayOf(1)), directory, pkg)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteLaunchDiagnostics.importSnapshot(archive("metadata.txt" to metadata.replace(pkg, "other.game").toByteArray()), directory, pkg)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RemoteLaunchDiagnostics.importSnapshot(archive("metadata.txt" to metadata.toByteArray(), "process-123.txt" to ByteArray(65537)), directory, pkg)
        }
        assertFalse(directory.exists())
    }
    @Test fun `clear boundary prevents late imports from recreating reports`() {
        val directory = File(temporary.root, "100-abcd")
        RemoteLaunchDiagnostics.importSnapshot(archive("metadata.txt" to metadata.toByteArray()), directory, pkg) { false }
        assertFalse(directory.exists())
    }
}
