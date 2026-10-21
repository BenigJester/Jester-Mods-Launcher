package com.moodtools.hub

import com.android.apksig.ApkSigner
import com.moodtools.hub.networking.verifiedLauncherSigningDigests
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LauncherApkSignaturesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `Android 10 verifies actual APK signers and rejects unsigned or modified bytes`() {
        val root = listOf(File("."), File("..")).first { File(it, "settings.gradle").isFile }
        val unsigned = File(root, "diagnostic-bootstrap/build/outputs/apk/release").listFiles()!!.single { it.extension == "apk" }
        val store = File(temporary.root, "test.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool.exe")
        fun sign(alias: String): Pair<File, String> {
            val command = ProcessBuilder(keytool.path, "-genkeypair", "-alias", alias, "-keystore", store.path,
                "-storepass", "changeit", "-keypass", "changeit", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "2", "-dname", "CN=Diagnostic Test", "-noprompt").redirectErrorStream(true).start()
            assertTrue(command.waitFor(30, TimeUnit.SECONDS))
            assertEquals(command.inputStream.bufferedReader().readText(), 0, command.exitValue())
            val keys = KeyStore.getInstance("PKCS12").apply { store.inputStream().use { load(it, "changeit".toCharArray()) } }
            val cert = keys.getCertificate(alias) as X509Certificate
            val signer = ApkSigner.SignerConfig.Builder(alias, keys.getKey(alias, "changeit".toCharArray()) as PrivateKey, listOf(cert)).build()
            val signed = File(temporary.root, "$alias.apk")
            ApkSigner.Builder(listOf(signer)).setInputApk(unsigned).setOutputApk(signed)
                .setMinSdkVersion(28).setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(true).build().sign()
            val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
            return signed to digest
        }
        val (signed, digest) = sign("first")
        assertEquals(setOf(digest), verifiedLauncherSigningDigests(signed, 29, "Downloaded launcher"))
        val (other, _) = sign("other")
        assertNotEquals(verifiedLauncherSigningDigests(signed, 29, "Installed launcher"),
            verifiedLauncherSigningDigests(other, 29, "Downloaded launcher"))
        val modified = File(temporary.root, "modified.apk")
        signed.copyTo(modified)
        // Alter signed content in place, retaining the APK signing block.
        RandomAccessFile(modified, "rw").use { file ->
            assertEquals(0x504b0304, file.readInt())
            file.seek(26)
            val nameLength = file.readUnsignedByte() or (file.readUnsignedByte() shl 8)
            val extraLength = file.readUnsignedByte() or (file.readUnsignedByte() shl 8)
            val offset = 30L + nameLength + extraLength
            file.seek(offset)
            val value = file.readUnsignedByte()
            file.seek(offset)
            file.writeByte(value xor 1)
        }
        for (apk in listOf(unsigned, modified)) {
            val failure = runCatching { verifiedLauncherSigningDigests(apk, 29, "Downloaded launcher") }.exceptionOrNull()
            assertNotNull("Unverified APK accepted", failure)
            assertTrue(failure!!.message.orEmpty().contains("Downloaded launcher APK signature"))
        }
        val source = File(root, "app/src/main/java/com/moodtools/hub/networking/LauncherUpdateClient.kt").readText()
        assertTrue(source.contains("signingDigests(current).ifEmpty"))
        assertTrue(source.contains("File(context.applicationInfo.sourceDir)"))
        assertTrue(source.contains("require(downloadedSigners == installedSigners)"))
        assertFalse(source.contains("signingCertificateHistory"))
    }
}
