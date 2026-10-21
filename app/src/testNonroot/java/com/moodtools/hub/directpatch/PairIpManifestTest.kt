package com.moodtools.hub.directpatch

import java.io.File
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PairIpManifestTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root = listOf(File("."), File("..")).first { File(it, "settings.gradle").isFile }
    private val sdk = File(Properties().apply {
        File(root, "local.properties").inputStream().use(::load)
    }.getProperty("sdk.dir"))
    private val aapt = File(sdk, "build-tools").listFiles()!!.sortedByDescending { it.name }
        .map { File(it, "aapt2.exe") }.first { it.isFile }

    private fun run(vararg args: String): String {
        val process = ProcessBuilder(listOf(aapt.path) + args).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        assertEquals(text, 0, process.waitFor())
        return text
    }

    private fun compile(body: String): ByteArray {
        val folder = temporary.newFolder()
        val manifest = File(folder, "AndroidManifest.xml").apply {
            writeText("""<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.game" android:versionCode="7" android:versionName="1.2">
                <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
                $body
                </manifest>""")
        }
        val apk = File(folder, "fixture.apk")
        run("link", "-o", apk.path, "--manifest", manifest.path,
            "-I", File(sdk, "platforms/android-35/android.jar").path)
        return ZipFile(apk).use { it.getInputStream(it.getEntry("AndroidManifest.xml")).readBytes() }
    }

    private fun dump(bytes: ByteArray): String {
        val apk = temporary.newFile()
        ZipOutputStream(apk.outputStream()).use {
            it.putNextEntry(ZipEntry("AndroidManifest.xml")); it.write(bytes); it.closeEntry()
        }
        return run("dump", "xmltree", "--file", "AndroidManifest.xml", apk.path)
    }

    @Test fun `ports only PairIP manifest cleanup and retains unrelated declarations`() {
        val original = compile("""
            <queries><package android:name="com.PaIrIp.service"/></queries>
            <queries><package android:name="com.android.vending"/><package android:name="com.pairip.other"/></queries>
            <uses-permission android:name="android.permission.INTERNET"/>
            <application android:name="com.pairip.application.Application" android:label="Keep label"
                android:appComponentFactory="com.pairip.Factory" android:allowBackup="false">
                <activity android:name="com.example.Main" android:exported="true">
                    <intent-filter><action android:name="android.intent.action.MAIN"/>
                        <category android:name="android.intent.category.LAUNCHER"/></intent-filter>
                </activity>
                <activity android:name="com.pairip.licensecheck.LicenseActivity">
                    <intent-filter><action android:name="remove.entire.subtree"/></intent-filter>
                </activity>
                <meta-data android:name="keep.key" android:value="keep.value"/>
                <meta-data android:name="remove.key" android:value="PAIRIP entry"/>
                <service android:name="com.example.KeepService"/>
            </application>""")
        val cleaned = BinaryXmlStringPool.withoutPairIpManifestEntries(original)
        assertArrayEquals(cleaned, BinaryXmlStringPool.withoutPairIpManifestEntries(cleaned))
        assertEquals(BinaryXmlStringPool.manifestPackageVersion(original), BinaryXmlStringPool.manifestPackageVersion(cleaned))
        val text = dump(cleaned)
        assertFalse(text, text.contains("pairip", ignoreCase = true))
        assertFalse(text, text.contains("remove.entire.subtree"))
        assertFalse(text, text.contains("remove.key"))
        for (preserved in listOf("com.example.Main", "com.example.KeepService", "keep.value",
                "Keep label", "com.android.vending", "android.permission.INTERNET", "android.intent.action.MAIN"))
            assertTrue(text, text.contains(preserved))
        assertEquals(text, 1, Regex("E: queries").findAll(text).count())
        val patched = BinaryXmlStringPool.withDiagnosticProvider(cleaned, "com.example.game")
        val output = dump(patched)
        assertTrue(output, output.contains("com.moodtools.diagnostics.DiagnosticFactory"))
        assertTrue(output, output.contains("com.example.Main"))
        assertFalse(output, output.contains("pairip", ignoreCase = true))
    }

    @Test fun `GmsCore metadata composes with diagnostics and can be removed without touching the game`() {
        val original = compile("""<queries><package android:name="com.example.keep"/></queries>
            <application android:name="com.example.RealApplication" android:label="Keep label">
            <meta-data android:name="keep.key" android:value="keep.value"/>
            <activity android:name="com.example.Main"/></application>""")
        assertArrayEquals(original, BinaryXmlStringPool.withGmsCore(original, false, "com.ChillyRoom.DungeonShooter", "dd96d1d8a18aa4165136f002cd56adaecfd7955f"))
        val enabled = BinaryXmlStringPool.withGmsCore(original, true, "com.ChillyRoom.DungeonShooter", "dd96d1d8a18aa4165136f002cd56adaecfd7955f")
        assertArrayEquals(enabled, BinaryXmlStringPool.withGmsCore(enabled, true, "com.ChillyRoom.DungeonShooter", "dd96d1d8a18aa4165136f002cd56adaecfd7955f"))
        val text = dump(enabled)
        assertTrue(text, text.contains("app.revanced.android.gms.SPOOFED_PACKAGE_NAME"))
        assertTrue(text, text.contains("dd96d1d8a18aa4165136f002cd56adaecfd7955f"))
        assertTrue(text, text.contains("com.example.RealApplication"))
        assertTrue(text, text.contains("keep.value"))
        val diagnostic = BinaryXmlStringPool.withDiagnosticProvider(enabled, "com.example.game")
        val disabled = BinaryXmlStringPool.withGmsCore(diagnostic, false, "com.ChillyRoom.DungeonShooter", "dd96d1d8a18aa4165136f002cd56adaecfd7955f")
        val removed = dump(disabled)
        assertFalse(removed, removed.contains("SPOOFED_PACKAGE_"))
        assertTrue(removed, removed.contains("com.example.Main"))
        assertTrue(removed, removed.contains("com.example.keep"))
        assertTrue(removed, removed.contains("com.moodtools.diagnostics.DiagnosticFactory"))
        assertArrayEquals(disabled, BinaryXmlStringPool.withGmsCore(disabled, false, "com.ChillyRoom.DungeonShooter", "dd96d1d8a18aa4165136f002cd56adaecfd7955f"))
        assertEquals(BinaryXmlStringPool.manifestPackageVersion(original), BinaryXmlStringPool.manifestPackageVersion(disabled))
        val anotherGame = BinaryXmlStringPool.withGmsCore(original, true, "com.example.futuregame", "a".repeat(40))
        val anotherText = dump(anotherGame)
        assertTrue(anotherText, anotherText.contains("com.example.futuregame"))
        assertTrue(anotherText, anotherText.contains("a".repeat(40)))
        assertFalse(anotherText, anotherText.contains("com.ChillyRoom.DungeonShooter"))
        assertArrayEquals(anotherGame, BinaryXmlStringPool.withGmsCore(anotherGame, true, "com.example.futuregame", "a".repeat(40)))
    }

    @Test fun `unchanged manifests stay byte identical and malformed input fails closed`() {
        val original = compile("""<application android:name="com.example.RealApplication">
            <activity android:name="com.example.Main"/></application>""")
        assertArrayEquals(original, BinaryXmlStringPool.withoutPairIpManifestEntries(original))
        assertTrue(runCatching { BinaryXmlStringPool.withoutPairIpManifestEntries(original.copyOf(original.size - 1)) }.isFailure)
    }
}
