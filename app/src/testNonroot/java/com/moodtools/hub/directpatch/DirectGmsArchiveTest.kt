package com.moodtools.hub.directpatch

import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.immutable.*
import org.jf.dexlib2.immutable.instruction.*
import org.jf.dexlib2.immutable.reference.ImmutableStringReference
import org.jf.dexlib2.writer.io.MemoryDataStore
import org.jf.dexlib2.writer.pool.DexPool
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class DirectGmsArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun method(owner: String, name: String, parameters: List<String>, result: String,
        registers: Int, vararg instructions: org.jf.dexlib2.iface.instruction.Instruction) =
        ImmutableMethod(owner, name, parameters.map { ImmutableMethodParameter(it, emptySet(), null) },
            result, 9, emptySet(), emptySet(), ImmutableMethodImplementation(registers, instructions.toList(), emptyList(), emptyList()))

    private fun dex(type: String, methods: List<ImmutableMethod>): ByteArray {
        val cls = ImmutableClassDef(type, 1, "Ljava/lang/Object;", emptyList(), null, emptySet(), emptyList(), methods)
        val store = MemoryDataStore()
        DexPool.writeTo(store, ImmutableDexFile(Opcodes.getDefault(), listOf(cls)))
        return store.data.copyOf(store.size)
    }
    private fun inputs(): Map<String, ByteArray> {
        val availability = "Lcom/google/android/gms/common/GooglePlayServicesUtilLight;"
        val verifier = "Lcom/google/android/gms/common/GoogleSignatureVerifier;"
        val broker = "Lcom/google/android/gms/common/internal/BaseGmsClient;"
        return linkedMapOf(
            "classes.dex" to dex(availability, listOf(method(availability, "isGooglePlayServicesAvailable",
                listOf("Landroid/content/Context;", "I"), "I", 3,
                ImmutableInstruction11n(Opcode.CONST_4, 0, 0), ImmutableInstruction11x(Opcode.RETURN, 0)))),
            "classes2.dex" to dex(verifier, listOf(method(verifier, "zzb", listOf("Landroid/content/pm/PackageInfo;", "Z"),
                "Z", 6, ImmutableInstruction11n(Opcode.CONST_4, 0, 0), ImmutableInstruction11x(Opcode.RETURN, 0)))),
            "classes3.dex" to dex(broker, listOf(method(broker, "target", emptyList(), "Ljava/lang/String;", 1,
                ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("com.google.android.gms")),
                ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("com.google")),
                ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)))),
            "classes4.dex" to dex("Lcom/example/Unchanged;", emptyList())
        )
    }
    private fun apk(entries: Map<String, ByteArray>): File = temporary.newFile().apply {
        ZipOutputStream(outputStream()).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
    }

    @Test fun `adapter finds split Google SDK classes across DEX without modifying unrelated DEX`() {
        val source = inputs()
        val output = GmsCoreDexPatcher.rewriteSet(source)
        assertEquals(setOf("classes.dex", "classes2.dex", "classes3.dex"), output.keys)
        assertThrows(IllegalArgumentException::class.java) { GmsCoreDexPatcher.rewriteSet(source - "classes2.dex") }
        assertThrows(IllegalArgumentException::class.java) { GmsCoreDexPatcher.rewriteSet(output) }
        assertThrows(IllegalArgumentException::class.java) {
            GmsCoreDexPatcher.rewriteSet(source + ("classes5.dex" to source.getValue("classes.dex")))
        }
    }

    @Test fun `lazy input reads each DEX once and retains originals only for changed DEX`() {
        val source = inputs()
        val reads = mutableListOf<String>()
        val originals = linkedMapOf<String, ByteArray>()
        val changed = GmsCoreDexPatcher.rewriteSet(source.keys,
            onChanged = { name, bytes -> originals[name] = bytes }) {
            reads += it
            source.getValue(it)
        }
        assertEquals(source.keys.toList(), reads)
        assertEquals(setOf("classes.dex", "classes2.dex", "classes3.dex"), originals.keys)
        assertEquals(changed.keys, originals.keys)
        originals.forEach { (name, bytes) -> assertSame(source.getValue(name), bytes) }
        val eager = GmsCoreDexPatcher.rewriteSet(source)
        changed.forEach { (name, bytes) -> assertArrayEquals(eager.getValue(name), bytes) }
    }

    @Test fun `profile rejection identifies the failed check while preserving all exact build guards`() {
        val source = inputs()
        val rules = com.moodtools.hub.modules.GmsCorePatchRules("test-v1",
            source.mapValues { GmsCoreDexPatcher.hash(it.value) })
        val missing = assertThrows(GmsCorePatchFailure::class.java) {
            GmsCoreDexPatcher.rewriteSet(source - "classes2.dex", rules)
        }
        assertEquals(GmsCorePatchFailure.Check.DEX_MISSING, missing.check)
        assertEquals("classes2.dex", missing.dexEntry)
        assertNull(missing.actualSha256)
        val wrongHash = assertThrows(GmsCorePatchFailure::class.java) {
            GmsCoreDexPatcher.rewriteSet(source, rules.copy(dexSha256 = rules.dexSha256 + ("classes2.dex" to "0".repeat(64))))
        }
        assertEquals(GmsCorePatchFailure.Check.DEX_HASH_MISMATCH, wrongHash.check)
        assertEquals("0".repeat(64), wrongHash.expectedSha256)
        assertEquals(GmsCoreDexPatcher.hash(source.getValue("classes2.dex")), wrongHash.actualSha256)
        assertEquals("Google account support does not match this game build", wrongHash.message)
        assertEquals(GmsCorePatchFailure.Check.UNPINNED_DEX_CHANGED,
            assertThrows(GmsCorePatchFailure::class.java) {
                GmsCoreDexPatcher.rewriteSet(source, rules.copy(dexSha256 = rules.dexSha256 - "classes3.dex"))
            }.check)
        assertEquals(GmsCorePatchFailure.Check.SDK_SHAPE_UNSUPPORTED,
            assertThrows(GmsCorePatchFailure::class.java) {
                GmsCoreDexPatcher.rewriteSet(source - "classes2.dex")
            }.check)
        assertEquals(GmsCorePatchFailure.Check.ALREADY_ROUTED,
            assertThrows(GmsCorePatchFailure::class.java) {
                GmsCoreDexPatcher.rewriteSet(GmsCoreDexPatcher.rewriteSet(source, rules))
            }.check)
        assertTrue(wrongHash.diagnosticLines().contains("gmsPatchCheck: dex_hash_mismatch"))
        assertTrue(wrongHash.diagnosticLines().all { "Google account support: $it".length <= 320 })
        val privateText = "private-account-token/secret-path"
        val safe = GmsCorePatchFailure(GmsCorePatchFailure.Check.DEX_HASH_MISMATCH,
            privateText, privateText, privateText).diagnosticLines()
        assertEquals(listOf("gmsPatchCheck: dex_hash_mismatch"), safe)
    }

    @Test fun `reapply uses saved originals and opt out restores every changed DEX exactly`() {
        val source = inputs()
        val rules = com.moodtools.hub.modules.GmsCorePatchRules("test-account-v1",
            source.mapValues { GmsCoreDexPatcher.hash(it.value) }, "app.revanced")
        val first = ZipFile(apk(source)).use { DirectGmsArchive.prepare(it, true, null, emptySet(), rules) }
        val marker = JSONObject().put("gmsProfile", "google-sdk-routing-v2")
            .put("originalSignerSha1", "a".repeat(40))
            .put("gmsOriginals", JSONObject(first.originals.mapValues { GmsCoreDexPatcher.hash(it.value) }))
            .put("gmsPatched", JSONObject(first.replacements.mapValues { GmsCoreDexPatcher.hash(it.value) }))
        val installed = source + first.replacements + first.originals.mapKeys { DirectGmsArchive.BACKUP_PREFIX + it.key }
        ZipFile(apk(installed)).use { archive ->
            DirectGmsArchive.verify(archive, marker)
            val reapplied = DirectGmsArchive.prepare(archive, true, marker, emptySet(), rules)
            assertEquals(first.replacements.keys, reapplied.replacements.keys)
            first.replacements.forEach { (name, bytes) -> assertArrayEquals(bytes, reapplied.replacements.getValue(name)) }
            val disabled = DirectGmsArchive.prepare(archive, false, marker, emptySet())
            assertTrue(disabled.originals.isEmpty())
            first.originals.forEach { (name, bytes) -> assertArrayEquals(source.getValue(name), bytes)
                assertArrayEquals(bytes, disabled.replacements.getValue(name)) }
        }
        val tampered = installed + (DirectGmsArchive.BACKUP_PREFIX + "classes.dex" to byteArrayOf(1, 2, 3))
        ZipFile(apk(tampered)).use { archive ->
            assertThrows(IllegalArgumentException::class.java) { DirectGmsArchive.prepare(archive, false, marker, emptySet()) }
        }
    }

    @Test fun `legacy installed profile restores its verified original and rejects corruption`() {
        val root = listOf(File("."), File("..")).first { File(it, "settings.gradle").isFile }
        val fixture = File(root, "../../Fast_analysis/Tests/soul-knight/Update-8.7.0/base.apk")
        org.junit.Assume.assumeTrue("Local original required for legacy migration", fixture.isFile)
        val original = ZipFile(fixture).use { it.getInputStream(it.getEntry(LegacyGmsPatchMigration.ENTRY)).readBytes() }
        val marker = JSONObject().put("gmsProfile", LegacyGmsPatchMigration.PROFILE)
        ZipFile(apk(mapOf("assets/moodtools/gms-original.dex" to original))).use {
            val restored = DirectGmsArchive.prepare(it, false, marker, emptySet())
            assertArrayEquals(original, restored.replacements.getValue(LegacyGmsPatchMigration.ENTRY))
            assertTrue(restored.originals.isEmpty())
        }
        ZipFile(apk(mapOf("assets/moodtools/gms-original.dex" to byteArrayOf(1, 2, 3)))).use {
            assertThrows(IllegalArgumentException::class.java) { DirectGmsArchive.prepare(it, false, marker, emptySet()) }
        }
    }

    @Test fun `disabled feature leaves an unmodified game's DEX entirely untouched`() {
        ZipFile(apk(mapOf("classes.dex" to byteArrayOf(1, 2, 3)))).use { archive ->
            val disabled = DirectGmsArchive.prepare(archive, false, null, emptySet())
            assertTrue(disabled.originals.isEmpty() && disabled.replacements.isEmpty())
        }
    }
}
