package com.moodtools.hub.directpatch

import com.moodtools.hub.modules.GmsCorePatchRules
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Local proprietary fixtures are optional and are never committed. Run with a 256 MiB test heap. */
class GmsCoreMemoryTest {
    @Test fun `large multidex patch and in-place reapply preserve the prior output under a bounded heap`() {
        fun fixture(name: String): File? = System.getenv(name)?.let(::File)?.takeIf(File::isFile)
        val apk = fixture("JESTER_GMS_FIXTURE_APK")
        val config = fixture("JESTER_GMS_FIXTURE_CONFIG")
        val installed = fixture("JESTER_GMS_INSTALLED_APK")
        assumeTrue("Local Shadow Fight 2 build 30000121 fixtures required", apk != null && config != null && installed != null)
        assertTrue("Run this fixture check with a 256 MiB test heap", Runtime.getRuntime().maxMemory() <= 256L * 1024 * 1024)
        val rules = requireNotNull(GmsCorePatchRules.fromJson(JSONObject(config!!.readText()), "gmscore_patch"))
        assertEquals("shadow-fight2-30000121-gms-v1", rules.revision)
        val fresh = ZipFile(apk!!).use { DirectGmsArchive.prepare(it, true, null, emptySet(), rules) }
        fun checkOutput(patch: DirectGmsArchive.Patch) {
            assertEquals(setOf("classes3.dex"), patch.replacements.keys)
            assertEquals(rules.dexSha256.keys, patch.originals.keys)
            assertEquals(rules.dexSha256.getValue("classes3.dex"), GmsCoreDexPatcher.hash(patch.originals.getValue("classes3.dex")))
            // Recorded from the already-installed, previously verified patch, before this memory fix.
            assertEquals("0f5bec74b51872e3c0089cc07f50b55fc77ea08fb8633033ef00690b3289dcd6",
                GmsCoreDexPatcher.hash(patch.replacements.getValue("classes3.dex")))
        }
        checkOutput(fresh)
        ZipFile(installed!!).use { archive ->
            val marker = JSONObject(archive.getInputStream(archive.getEntry("assets/moodtools/soul-patch.json"))
                .use { String(it.readBytes(), Charsets.UTF_8) })
            val excluded = setOf(marker.getString("dex"), marker.getString("diagnosticDex"))
            val reapplied = DirectGmsArchive.prepare(archive, true, marker, excluded, rules)
            checkOutput(reapplied)
            assertArrayEquals(fresh.replacements.getValue("classes3.dex"), reapplied.replacements.getValue("classes3.dex"))
        }
    }
}
