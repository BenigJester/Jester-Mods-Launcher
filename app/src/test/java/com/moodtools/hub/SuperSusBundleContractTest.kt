package com.moodtools.hub

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual transaction in a temp folder. Android commands/metadata are mocked;
 * real cp/mv/hash/file guards execute. No su, live game paths, or device. */
class SuperSusBundleContractTest {
    @get:Rule val temporary = TemporaryFolder()
    private val original = "original-bundle-fixture".toByteArray()
    private val patched = "patched-bundle-fixture".toByteArray()
    private val resource = "resource-415-fixture".toByteArray()

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private class Fixture(val root: File) {
        val live = File(root, "live.ab")
        val staged = File(root, "stage'quoted.ab")
        val resource = File(root, "assetsversion.json")
        val backupDir = File(root, "backup")
        val backup = File(backupDir, "original.ab")
        val next = File(root, "live.ab.jester-next")
    }

    private fun fixture(): Fixture = Fixture(temporary.newFolder()).also {
        it.live.writeBytes(original)
        it.staged.writeBytes(patched)
        it.resource.writeBytes(resource)
    }

    private fun execute(f: Fixture, restore: Boolean = false, badMetadata: Boolean = false,
                        running: Boolean = false, ownerFailure: Boolean = false): Pair<Int, String> {
        val bash = listOf(File("C:/Program Files/Git/bin/bash.exe"), File("/bin/bash")).firstOrNull(File::isFile)
        assumeTrue("Bash required for sandboxed transaction execution", bash != null)
        fun path(file: File): String {
            val absolute = file.absolutePath.replace('\\', '/')
            return if (absolute.length > 2 && absolute[1] == ':')
                "/" + absolute[0].lowercaseChar() + absolute.substring(2)
            else absolute
        }
        val target = SuperSusBundleContract.livePath(0)
        var script = if (restore) SuperSusBundleContract.restoreCommand(0)
            else SuperSusBundleContract.installCommand(0, path(f.staged))
        script = script.replace(target, path(f.live))
            .replace(target.substringBeforeLast("/data/code/") + "/assetsversion.json", path(f.resource))
            .replace(SuperSusBundleContract.backupDirectory(0), path(f.backupDir))
            .replace(SuperSusBundleContract.ORIGINAL, hash(original))
            .replace(SuperSusBundleContract.PATCHED, hash(patched))
            .replace(SuperSusBundleContract.RESOURCE_MANIFEST, hash(resource))
        script = script.lineSequence().joinToString("\n") { line ->
            if (line.trimStart().startsWith("metadata()")) {
                if (badMetadata) "metadata() { case \"\$1\" in *.jester-next) echo mismatch;; *) echo expected;; esac; }"
                else "metadata() { echo expected; }"
            } else line
        }
        assertFalse(script.contains("/storage/emulated/"))
        assertFalse(script.contains("/data/local/tmp/"))
        // Android mksh reserves hash as an alias; keep that collision in every transaction test.
        val mocks = "shopt -s expand_aliases\nalias hash=':'\n" +
            "id() { echo 0; }; am() { :; }; chown() { :; }; chmod() { :; }; sync() { :; }; " +
            "pidof() { return " + (if (running) "0" else "1") + "; };\n"
        val ownerMock = if (ownerFailure)
            "chown() { case \"\$2\" in *.jester-next) return 1;; *) return 0;; esac; };\n" else ""
        val process = ProcessBuilder(bash!!.absolutePath, "-s").redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { it.write(mocks + ownerMock + script + "\n") }
        try {
            assertTrue("Sandbox transaction timed out", process.waitFor(15, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            println("Sandbox exit=${process.exitValue()}: $output")
            return process.exitValue() to output
        } finally { process.destroyForcibly() }
    }

    @Test fun `install retry and restore preserve the immutable original`() {
        val f = fixture()
        assertEquals(0, execute(f).first)
        assertArrayEquals(patched, f.live.readBytes())
        assertArrayEquals(original, f.backup.readBytes())
        assertEquals(0, execute(f).first)
        assertArrayEquals(original, f.backup.readBytes())
        f.resource.writeText("updated-resource-manifest")
        assertEquals(0, execute(f, restore = true).first)
        assertArrayEquals(original, f.live.readBytes())
        assertEquals(0, execute(f, restore = true).first)
    }

    @Test fun `unknown live bundle is never overwritten`() {
        val f = fixture()
        f.live.writeText("another build")
        assertTrue(execute(f).second.contains("unexpected-live-hash"))
        assertEquals("another build", f.live.readText())
        assertFalse(f.backup.exists())
    }

    @Test fun `staged corruption preserves live original and backup`() {
        val f = fixture()
        f.staged.writeText("corrupt")
        assertTrue(execute(f).second.contains("staged-hash-mismatch"))
        assertArrayEquals(original, f.live.readBytes())
        assertArrayEquals(original, f.backup.readBytes())
    }

    @Test fun `resource update blocks installation without changing either bundle`() {
        val f = fixture()
        f.resource.writeText("new resource version")
        assertTrue(execute(f).second.contains("unsupported-resource-build"))
        assertArrayEquals(original, f.live.readBytes())
        assertFalse(f.backup.exists())
    }

    @Test fun `bad rollback copy cannot be replaced or used`() {
        val f = fixture()
        f.backupDir.mkdirs()
        f.backup.writeText("bad backup")
        assertTrue(execute(f).second.contains("invalid-original-backup"))
        assertEquals("bad backup", f.backup.readText())
        assertArrayEquals(original, f.live.readBytes())
    }

    @Test fun `metadata mismatch fails before atomic rename`() {
        val f = fixture()
        assertTrue(execute(f, badMetadata = true).second.contains("next-metadata-mismatch"))
        assertArrayEquals(original, f.live.readBytes())
    }

    @Test fun `owner application failure preserves the original and backup`() {
        val f = fixture()
        assertTrue(execute(f, ownerFailure = true).second.contains("next-owner-failed"))
        assertArrayEquals(original, f.live.readBytes())
        assertArrayEquals(original, f.backup.readBytes())
    }

    @Test fun `unexpected pending data is not overwritten`() {
        val f = fixture()
        f.next.writeText("unrelated pending data")
        assertTrue(execute(f).second.contains("unexpected-next-file"))
        assertEquals("unrelated pending data", f.next.readText())
        assertArrayEquals(original, f.live.readBytes())
    }

    @Test fun `running game aborts before backup or replacement`() {
        val f = fixture()
        assertTrue(execute(f, running = true).second.contains("game-still-running"))
        assertFalse(f.backup.exists())
        assertArrayEquals(original, f.live.readBytes())
    }

    @Test fun `paths and quoting are constrained without mounts or broad deletion`() {
        assertThrows(IllegalArgumentException::class.java) { SuperSusBundleContract.livePath(-1) }
        assertThrows(IllegalArgumentException::class.java) { SuperSusBundleContract.installCommand(0, "../relative") }
        val script = SuperSusBundleContract.installCommand(10, "/data/user/10/app/a'b")
        assertTrue(script.contains("am force-stop --user 10 'com.je.supersus'"))
        assertTrue(script.contains("a'\\''b"))
        assertTrue(script.contains("[ ! -L"))
        assertFalse(script.contains("mount"))
        assertFalse(script.contains("rm -"))
        assertFalse(script.contains("setenforce"))
        assertFalse(script.contains("@LIVE@"))
    }
}
