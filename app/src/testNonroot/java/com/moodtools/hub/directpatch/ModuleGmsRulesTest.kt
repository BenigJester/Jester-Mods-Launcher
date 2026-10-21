package com.moodtools.hub.directpatch

import com.moodtools.hub.modules.GmsCorePatchRules
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.StringReference
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ModuleGmsRulesTest {
    private val root = listOf(File("."), File("..")).first { File(it, "settings.gradle").isFile }
    private fun recipe() = JSONObject().put("gmscore_patch", GmsCorePatchRules(
        "test-account-v1", mapOf("classes.dex" to "a".repeat(64)), "app.revanced").toJson())

    @Test fun `restricted recipes reject unknown operations and malformed or missing pins`() {
        val rule = recipe().getJSONObject("gmscore_patch")
        assertNotNull(GmsCorePatchRules.fromJson(JSONObject().put("rules", rule), "rules"))
        assertNull(GmsCorePatchRules.fromJson(JSONObject(), "rules"))
        for (bad in listOf(
            JSONObject(rule.toString()).put("schema", 2),
            JSONObject(rule.toString()).put("schema", "1"),
            JSONObject(rule.toString()).put("script", "anything"),
            JSONObject(rule.toString()).put("account_type", "com.other.account"),
            JSONObject(rule.toString()).put("revision", "../escape"),
            JSONObject(rule.toString()).put("dex_sha256", JSONObject()),
            JSONObject(rule.toString()).put("dex_sha256", JSONObject().put("../classes.dex", "a".repeat(64))),
            JSONObject(rule.toString()).put("dex_sha256", JSONObject().put("classes.dex", "invalid"))
        )) assertThrows(RuntimeException::class.java) { GmsCorePatchRules.fromJson(JSONObject().put("rules", bad), "rules") }
    }

    @Test fun `changing rule content invalidates fingerprint even without changing the revision`() {
        val first = GmsCorePatchRules("test-v1", linkedMapOf("classes.dex" to "a".repeat(64), "classes2.dex" to "b".repeat(64)))
        assertEquals(first.fingerprint(), first.copy(dexSha256 = first.dexSha256.entries.reversed().associate { it.toPair() }).fingerprint())
        assertNotEquals(first.fingerprint(), first.copy(accountType = "app.revanced").fingerprint())
        assertNotEquals(first.fingerprint(), first.copy(revision = "test-v2").fingerprint())
    }

    @Test fun `Among Us recipe rewrites the account type using exact local game originals`() {
        val original = File(root, "../../Fast_analysis/Tests/among-us/Update-2026.9.29/original/base.apk")
        assumeTrue("Local proprietary fixture required", original.isFile)
        val module = JSONObject(File(root, "modules/com.innersloth.spacemafia/config.json").readText())
        val rules = GmsCorePatchRules.fromJson(module, "gmscore_patch")!!
        val inputs = ZipFile(original).use { zip -> zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes[0-9]*\\.dex")) }
            .associate { it.name to zip.getInputStream(it).use { input -> input.readBytes() } } }
        rules.dexSha256.forEach { (name, expected) -> assertEquals(name, expected, GmsCoreDexPatcher.hash(inputs.getValue(name))) }
        val normal = GmsCoreDexPatcher.rewriteSet(inputs)
        val custom = GmsCoreDexPatcher.rewriteSet(inputs, rules)
        val before = accountConstants(normal)
        val after = accountConstants(custom)
        assertTrue("No matching account-type sites in baseline", before.isNotEmpty())
        assertTrue("Google account literals remain at $after", after.isEmpty())
        assertTrue(custom.keys.all { it in rules.dexSha256 })
        assertThrows(IllegalArgumentException::class.java) { GmsCoreDexPatcher.rewriteSet(inputs,
            rules.copy(dexSha256 = rules.dexSha256 + ("classes.dex" to "0".repeat(64)))) }
        assertThrows(IllegalArgumentException::class.java) { GmsCoreDexPatcher.rewriteSet(inputs,
            rules.copy(dexSha256 = rules.dexSha256 - "classes.dex")) }
    }

    @Test fun `renamed SDK symbol validation rejects executable rules duplicates and missing selectors`() {
        val symbols = com.moodtools.hub.modules.GmsCoreSdkSymbols("LU0/f;", "c", false, "LU0/j;", "f",
            setOf("LU0/f;", "LU0/j;"))
        val rules = GmsCorePatchRules("symbols-v1", mapOf("classes.dex" to "a".repeat(64)), "app.revanced", symbols)
        assertEquals(rules, GmsCorePatchRules.fromJson(JSONObject().put("rule", rules.toJson()), "rule"))
        for (bad in listOf(
            symbols.toJson().put("script", "code"),
            symbols.toJson().put("availability_static", "false"),
            symbols.toJson().put("availability_method", "../c"),
            symbols.toJson().put("routing_classes", org.json.JSONArray().put("LU0/f;").put("LU0/f;")),
            symbols.toJson().put("signature_class", "Lother/Class;"),
            symbols.toJson().apply { remove("signature_method") }
        )) assertThrows(RuntimeException::class.java) { com.moodtools.hub.modules.GmsCoreSdkSymbols.fromJson(bad) }
        assertEquals(rules.fingerprint(), rules.copy(sdkSymbols = symbols.copy(routingClasses =
            symbols.routingClasses.reversed().toSet())).fingerprint())
        assertNotEquals(rules.fingerprint(), rules.copy(sdkSymbols = symbols.copy(availabilityStatic = true)).fingerprint())
    }

    @Test fun `Wild Darkness exact module profile supports renamed Google SDK methods without touching other classes`() {
        val original = File(root, "temp/wild-darkness-1431-original-base.apk")
        assumeTrue("Local proprietary fixture required", original.isFile)
        val config = JSONObject(File(root, "modules/com.ctugames.km2/config.json").readText().removePrefix("\uFEFF"))
        val rules = requireNotNull(GmsCorePatchRules.fromJson(config, "gmscore_patch"))
        val symbols = requireNotNull(rules.sdkSymbols)
        assertEquals(rules, GmsCorePatchRules.fromJson(JSONObject().put("rules", rules.toJson()), "rules"))
        val inputs = ZipFile(original).use { zip -> zip.entries().asSequence()
            .filter { it.name.matches(Regex("classes[0-9]*\\.dex")) }
            .associate { it.name to zip.getInputStream(it).use { input -> input.readBytes() } } }
        assertThrows(IllegalArgumentException::class.java) { GmsCoreDexPatcher.rewriteSet(inputs) }
        val output = GmsCoreDexPatcher.rewriteSet(inputs, rules)
        assertEquals(setOf("classes.dex"), output.keys)
        assertTrue(accountConstants(output, symbols.routingClasses).isEmpty())
        val dex = DexBackedDexFile(Opcodes.getDefault(), output.getValue("classes.dex"))
        val availability = dex.classes.single { it.type == symbols.availabilityClass }.methods
            .single { it.name == symbols.availabilityMethod && it.parameterTypes.size == 2 }
        assertEquals(0, availability.accessFlags and 8) // Preserve the instance-method ABI.
        assertTrue(availability.implementation!!.instructions.any {
            ((it as? ReferenceInstruction)?.reference as? org.jf.dexlib2.iface.reference.MethodReference)?.definingClass ==
                "Lcom/moodtools/gmscore/GmsCoreBridge;"
        })
        fun classes(bytes: ByteArray) = DexBackedDexFile(Opcodes.getDefault(), bytes).classes.associate { cls ->
            val store = org.jf.dexlib2.writer.io.MemoryDataStore()
            org.jf.dexlib2.writer.pool.DexPool.writeTo(store, org.jf.dexlib2.immutable.ImmutableDexFile(
                Opcodes.getDefault(), listOf(org.jf.dexlib2.immutable.ImmutableClassDef.of(cls))))
            cls.type to GmsCoreDexPatcher.hash(store.data.copyOf(store.size))
        }
        val before = classes(inputs.getValue("classes.dex"))
        val after = classes(output.getValue("classes.dex"))
        assertEquals(before.keys, after.keys)
        before.keys.filter { before[it] != after[it] }.forEach { type ->
            assertTrue("Unexpected changed class: $type", type in symbols.routingClasses ||
                listOf("common/", "auth/", "signin/", "games/").any { type.startsWith("Lcom/google/android/gms/$it") })
        }
        assertThrows(IllegalArgumentException::class.java) { GmsCoreDexPatcher.rewriteSet(inputs,
            rules.copy(dexSha256 = mapOf("classes.dex" to "0".repeat(64)))) }
        assertThrows(IllegalArgumentException::class.java) { GmsCoreDexPatcher.rewriteSet(output, rules) }
        assertThrows(IllegalArgumentException::class.java) { GmsCoreDexPatcher.rewriteSet(inputs,
            rules.copy(sdkSymbols = symbols.copy(availabilityStatic = true))) }
        assertNotEquals(rules.fingerprint(), rules.copy(sdkSymbols = symbols.copy(availabilityMethod = "wrong")).fingerprint())
        val duplicate = symbols.toJson().put("routing_classes", org.json.JSONArray().put("LU0/f;").put("LU0/f;"))
        assertThrows(IllegalArgumentException::class.java) { com.moodtools.hub.modules.GmsCoreSdkSymbols.fromJson(duplicate) }
    }

    private fun accountConstants(dex: Map<String, ByteArray>, aliases: Set<String> = emptySet()): List<String> = dex.values.flatMap { bytes ->
        DexBackedDexFile(Opcodes.getDefault(), bytes).classes.filter { cls ->
            cls.type in aliases || listOf("common/", "auth/", "signin/", "games/").any { cls.type.startsWith("Lcom/google/android/gms/$it") }
        }.flatMap { cls -> cls.methods.filter { method -> method.implementation?.instructions?.any { instruction ->
            instruction.opcode in listOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
                ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string == "com.google"
        } == true }.map { cls.type + "->" + it.name } }
    }
}
