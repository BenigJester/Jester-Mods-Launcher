package com.moodtools.hub.soulpatch

import java.io.File
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticManifestTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `injects isolated provider and factory into compiled manifests without replacing game declarations`() {
        val root = listOf(File("."), File("..")).first { File(it, "settings.gradle").isFile }
        val template = File(root, "diagnostic-bootstrap/build/outputs/apk/release").listFiles()!!.single { it.extension == "apk" }
        val manifest = ZipFile(template).use { zip -> zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readBytes() }
        // Existing unrelated provider, no appComponentFactory: exercises insertion rather than an idempotent return.
        val original = BinaryXmlStringPool.replaceExact(manifest,
            "com.moodtools.diagnostics.DiagnosticProvider", "com.example.ExistingProvider")
        val patched = BinaryXmlStringPool.withDiagnosticProvider(original, "com.example.game")
        assertArrayEquals(patched, BinaryXmlStringPool.withDiagnosticProvider(patched, "com.example.game"))
        assertEquals(BinaryXmlStringPool.manifestPackageVersion(original), BinaryXmlStringPool.manifestPackageVersion(patched))
        val apk = temporary.newFile("manifest.apk")
        ZipOutputStream(apk.outputStream()).use { it.putNextEntry(ZipEntry("AndroidManifest.xml")); it.write(patched); it.closeEntry() }
        val props = Properties().apply { File(root, "local.properties").inputStream().use(::load) }
        val tools = File(props.getProperty("sdk.dir"), "build-tools").listFiles()!!.sortedByDescending { it.name }
        val aapt = tools.map { File(it, "aapt2.exe") }.first { it.isFile }
        val process = ProcessBuilder(aapt.path, "dump", "xmltree", "--file", "AndroidManifest.xml", apk.path).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        assertEquals(text, 0, process.waitFor())
        assertTrue(text, text.contains("com.example.ExistingProvider"))
        assertTrue(text, text.contains("com.example.game.jester-diagnostics"))
        assertTrue(text, text.contains("com.moodtools.diagnostics.DiagnosticFactory"))
        assertTrue(text, text.contains("appComponentFactory(0x0101057a)"))
        assertTrue(text, text.contains(":jester_diagnostics"))
    }
}
