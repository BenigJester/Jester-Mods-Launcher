package com.moodtools.hub.directpatch

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModuleCompatibilityTest {
    private val VERSION = 972605203L
    private val PACKAGE = "com.mojang.minecraftpe"
    private val PROFILE = "fixture-v1:hash"
    @get:Rule val temp = TemporaryFolder()
    private val main = "lib/arm64-v8a/libminecraftpe.so"
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun apk(name: String, entries: Map<String, ByteArray>): File = temp.newFile(name).also { file ->
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
    }
    private fun entry(name: String, old: String?, new: String) = ModuleCompatibility.Entry(name,
        old?.toByteArray()?.let(::hash), hash(new.toByteArray()), new.toByteArray())
    private val recipe get() = ModuleCompatibility.Recipe(PROFILE, PACKAGE, VERSION, main, hash("gameplay".toByteArray()), listOf(
        entry("AndroidManifest.xml", "manifest", "compatible-manifest"),
        entry("classes.dex", "old", "restored"), entry("classes5.dex", null, "startup"),
        entry("lib/arm64-v8a/libmaesdk.so", "sdk-old", "sdk-restored")))
    private fun bundle(version: Long = VERSION, packageName: String = PACKAGE,
        mutate: (JSONObject) -> Unit = {}): ByteArray {
        val rows = org.json.JSONArray()
        val payloads = linkedMapOf<String, ByteArray>()
        recipe.entries.forEachIndexed { i, e ->
            payloads["payload/$i"] = e.bytes
            rows.put(JSONObject().put("entry",e.name).put("before",e.before ?: JSONObject.NULL)
                .put("after",e.after).put("size",e.bytes.size).put("payload","payload/$i"))
        }
        val json = JSONObject().put("schema",1).put("profile","future-version-$version")
            .put("package",packageName).put("versionCode",version).put("mainEntry",main)
            .put("mainSha256",recipe.mainHash).put("entries",rows).also(mutate)
        payloads["profile.json"] = json.toString().toByteArray()
        return ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip ->
            payloads.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        } }.toByteArray()
    }

    @Test fun `future versions and packages use the same engine without launcher constants`() {
        val first = ModuleCompatibility.load(ByteArrayInputStream(bundle()), PACKAGE)
        val future = ModuleCompatibility.load(ByteArrayInputStream(bundle(VERSION+999, "com.example.future")), "com.example.future")
        assertNotEquals(first.identity, future.identity)
        assertEquals(VERSION+999, future.versionCode)
        assertFalse(ModuleCompatibility.plan(set(false,"future"),future.versionCode,future).alreadyCompatible)
        assertTrue(ModuleCompatibility.plan(set(true,"future-ready"),future.versionCode,future).alreadyCompatible)
        assertThrows(IllegalArgumentException::class.java) {
            ModuleCompatibility.plan(set(false,"future-wrong"), VERSION, future)
        }
    }

    @Test fun `native overlay round trips and corrupt lengths fail closed`() {
        val bytes = bundle()
        val native = temp.newFile("module.so")
        DataOutputStream(native.outputStream()).use {
            it.write(ByteArray(64)); it.write(bytes); it.writeLong(bytes.size.toLong())
            it.write("JESTER-COMPAT-01".toByteArray())
        }
        assertEquals(ModuleCompatibility.load(ByteArrayInputStream(bytes),PACKAGE).identity,
            ModuleCompatibility.fromNative(native,PACKAGE)?.identity)
        assertThrows(IllegalArgumentException::class.java) { ModuleCompatibility.fromNative(native,"com.wrong.package") }
        java.io.RandomAccessFile(native,"rw").use { it.seek(it.length()-24); it.writeLong(Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { ModuleCompatibility.fromNative(native,PACKAGE) }
    }

    @Test fun `recipe rejects wrong package schema hashes paths and unsafe operations`() {
        fun rejects(mutate: (JSONObject) -> Unit) {
            assertThrows(IllegalArgumentException::class.java) {
                ModuleCompatibility.load(ByteArrayInputStream(bundle(mutate=mutate)),PACKAGE)
            }
        }
        rejects { it.put("package","com.wrong.package") }
        rejects { it.put("schema",2) }
        rejects { it.put("mainSha256","invalid") }
        rejects { it.getJSONArray("entries").getJSONObject(1).put("entry","../bad.dex") }
        rejects { it.getJSONArray("entries").getJSONObject(1).put("after","0".repeat(64)) }
        rejects { it.getJSONArray("entries").getJSONObject(1).put("entry",main) }
        rejects { it.getJSONArray("entries").getJSONObject(3).put("before",JSONObject.NULL) }
        rejects { it.getJSONArray("entries").getJSONObject(0).put("entry","classes9.dex") }
        rejects { it.put("entries",org.json.JSONArray()) }
        rejects { it.put("compatibleManifestSha256","not-an-array") }
        rejects { it.put("compatibleManifestSha256",org.json.JSONArray().put("invalid")) }
    }

    @Test fun `approved legacy manifests migrate without trusting missing or old profile markers`() {
        val candidate = set(true,"legacy-candidate")
        val legacyRecipe = recipe.copy(compatibleManifestHashes=setOf(hash("legacy-manifest".toByteArray())))
        fun base(name: String, manifest: String = "legacy-manifest", dex: String = "restored", marker: String? = null): File {
            val entries = mutableMapOf("AndroidManifest.xml" to manifest.toByteArray(),
                "classes.dex" to dex.toByteArray(),"classes5.dex" to "startup".toByteArray())
            marker?.let { entries["assets/moodtools/soul-patch.json"] = it.toByteArray() }
            return apk(name,entries)
        }
        for ((index, marker) in listOf(null, "{}", "{\"compatibilityProfile\":\"old-plain-profile\"}").withIndex()) {
            val files = listOf(base("legacy-$index.apk",marker=marker),candidate[1])
            val result = ModuleCompatibility.plan(files,VERSION,legacyRecipe)
            assertTrue(result.alreadyCompatible)
            assertTrue(result.replacements.isEmpty())
        }
        val unapproved = base("unapproved.apk",marker="{}")
        val failure = assertThrows(IllegalArgumentException::class.java) {
            ModuleCompatibility.plan(listOf(unapproved,candidate[1]),VERSION,recipe)
        }
        assertTrue(failure.message.orEmpty().contains("Actual manifest SHA256: ${hash("legacy-manifest".toByteArray())}"))
        assertTrue(failure.message.orEmpty().contains("Recipe: $PROFILE"))
        assertTrue(failure.message.orEmpty().contains("Approved legacy manifests: none"))
        assertThrows(IllegalArgumentException::class.java) {
            ModuleCompatibility.plan(listOf(base("bad-manifest.apk",manifest="tampered"),candidate[1]),VERSION,legacyRecipe)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModuleCompatibility.plan(listOf(base("bad-dex.apk",dex="tampered"),candidate[1]),VERSION,legacyRecipe)
        }
        val stock = set(false,"legacy-stock")
        assertThrows(IllegalArgumentException::class.java) {
            ModuleCompatibility.plan(listOf(base("bad-native.apk"),stock[1]),VERSION,legacyRecipe)
        }
        val parsed = ModuleCompatibility.load(ByteArrayInputStream(bundle { json ->
            json.put("compatibleManifestSha256",org.json.JSONArray().put(hash("legacy-manifest".toByteArray())))
        }),PACKAGE)
        assertEquals(legacyRecipe.compatibleManifestHashes,parsed.compatibleManifestHashes)
    }

    @Test fun `exact captured legacy base migrates with matching lab split contents`() {
        val path = System.getenv("MINECRAFT_LEGACY_BASE")
        assumeTrue(path != null && File(path).isFile)
        val legacy = File(requireNotNull(path))
        val repo = listOf(File("."),File("..")).map { it.canonicalFile }.first { File(it,"modules").isDirectory }
        val candidate = File(System.getProperty("user.home"),"Downloads/Minecraft-Compatibility-Test")
        assumeTrue(candidate.isDirectory)
        val actual = ModuleCompatibility.load(File(repo,"modules/com.mojang.minecraftpe/compatibility/recipe.zip").inputStream(),PACKAGE)
        val files = listOf(legacy) + listOf("split_config.arm64_v8a.apk","split_config.en.apk",
            "split_config.xxhdpi.apk","split_install_pack.apk").map { File(candidate,it) }
        val before = hash(legacy.readBytes())
        java.util.zip.ZipFile(legacy).use { zip ->
            val marker = JSONObject(String(zip.getInputStream(zip.getEntry("assets/moodtools/soul-patch.json")).readBytes()))
            assertFalse(marker.has("compatibilityProfile"))
            assertFalse(marker.has("compatibilityManifestSha256"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModuleCompatibility.plan(files,VERSION,actual.copy(compatibleManifestHashes=emptySet()))
        }
        val result = ModuleCompatibility.plan(files,VERSION,actual)
        assertTrue(result.alreadyCompatible)
        assertTrue(result.replacements.isEmpty())
        assertEquals(before,hash(legacy.readBytes()))
    }
    private fun set(compatible: Boolean, suffix: String) = listOf(
        apk("base-$suffix.apk", if (compatible) mapOf("AndroidManifest.xml" to "compatible-manifest".toByteArray(),
            "classes.dex" to "restored".toByteArray(), "classes5.dex" to "startup".toByteArray())
        else mapOf("AndroidManifest.xml" to "manifest".toByteArray(), "classes.dex" to "old".toByteArray())),
        apk("native-$suffix.apk", mapOf(main to "gameplay".toByteArray(),
            "lib/arm64-v8a/libmaesdk.so" to (if (compatible) "sdk-restored" else "sdk-old").toByteArray())))

    @Test fun `original is restored and complete candidate is left untouched`() {
        val original = set(false, "stock")
        val plan = ModuleCompatibility.plan(original, VERSION, recipe)
        assertFalse(plan.alreadyCompatible)
        assertEquals(2, plan.replacements.size)
        assertEquals(setOf("AndroidManifest.xml", "classes.dex", "classes5.dex"), plan.replacements.getValue(original[0]).keys)
        assertFalse(plan.replacements.values.any { main in it })
        val candidate = set(true, "candidate")
        val skip = ModuleCompatibility.plan(candidate, VERSION, recipe)
        assertTrue(skip.alreadyCompatible)
        assertTrue(skip.replacements.isEmpty())
        ModuleCompatibility.verify(candidate, recipe)
    }

    @Test fun `partial patch wrong build missing split and marker-only inputs are rejected`() {
        val original = set(false, "stock")
        val candidate = set(true, "candidate")
        fun rejects(block: () -> Unit) { assertThrows(IllegalArgumentException::class.java, block) }
        rejects { ModuleCompatibility.plan(listOf(original[0], candidate[1]), VERSION, recipe) }
        rejects { ModuleCompatibility.plan(candidate, VERSION+1, recipe) }
        rejects { ModuleCompatibility.plan(candidate.take(1), VERSION, recipe) }
        val fake = apk("fake.apk", mapOf("AndroidManifest.xml" to "manifest".toByteArray(),
            "classes.dex" to "unknown".toByteArray(), "assets/moodtools/soul-patch.json" to
                "{\"compatibilityProfile\":\"${PROFILE}\"}".toByteArray()))
        rejects { ModuleCompatibility.plan(listOf(fake, candidate[1]), VERSION, recipe) }
        val collision = apk("collision.apk", mapOf("AndroidManifest.xml" to "manifest".toByteArray(),
            "classes.dex" to "old".toByteArray(), "classes5.dex" to "unrelated-loader".toByteArray()))
        rejects { ModuleCompatibility.plan(listOf(collision, original[1]), VERSION, recipe) }
    }

    @Test fun `GmsCore layered over known restoration uses verified original backup`() {
        val candidate = set(true, "candidate")
        val current = "gms-rewritten".toByteArray()
        val original = "restored".toByteArray()
        val marker = JSONObject().put("gmsPatched", JSONObject().put("classes.dex", hash(current)))
            .put("gmsOriginals", JSONObject().put("classes.dex", hash(original)))
            .put("compatibilityProfile", PROFILE)
            .put("compatibilityManifestSha256", hash("launcher-manifest".toByteArray()))
        val patched = apk("gms-base.apk", mapOf("AndroidManifest.xml" to "launcher-manifest".toByteArray(),
            "classes.dex" to current, "classes5.dex" to "startup".toByteArray(),
            DirectGmsArchive.BACKUP_PREFIX+"classes.dex" to original,
            "assets/moodtools/soul-patch.json" to marker.toString().toByteArray()))
        val result = ModuleCompatibility.plan(listOf(patched,candidate[1]), VERSION, recipe)
        assertTrue(result.alreadyCompatible)
        assertTrue(result.replacements.isEmpty())
        val bad = apk("gms-tampered.apk", mapOf("classes.dex" to "tampered".toByteArray(),
            DirectGmsArchive.BACKUP_PREFIX+"classes.dex" to original,
            "assets/moodtools/soul-patch.json" to marker.toString().toByteArray(), "classes5.dex" to "startup".toByteArray(),
            "AndroidManifest.xml" to "launcher-manifest".toByteArray()))
        assertThrows(IllegalArgumentException::class.java) {
            ModuleCompatibility.plan(listOf(bad,candidate[1]), VERSION, recipe)
        }
    }

    @Test fun `exact local lab artifacts classify stock versus already compatible`() {
        val repo = listOf(File("."),File("..")).map { it.canonicalFile }.first { File(it,"modules").isDirectory }
        val stock = File(requireNotNull(repo.parentFile?.parentFile),"Fast_analysis/Tests/minecraft/originals/apk")
        val candidate = File(System.getProperty("user.home"),"Downloads/Minecraft-Compatibility-Test")
        val asset = File(repo,"modules/com.mojang.minecraftpe/compatibility/recipe.zip")
        assumeTrue(stock.isDirectory && candidate.isDirectory && asset.isFile)
        val actual = ModuleCompatibility.load(asset.inputStream(), PACKAGE)
        val native = File(repo,"module-output/com.mojang.minecraftpe/libmenu_native.so")
        if (native.isFile) {
            assertEquals(actual.identity, ModuleCompatibility.fromNative(native, PACKAGE)?.identity)
        }
        val names = listOf("base.apk","split_config.arm64_v8a.apk","split_config.en.apk","split_config.xxhdpi.apk","split_install_pack.apk")
        val plan = ModuleCompatibility.plan(names.map { File(stock,it) }, VERSION, actual)
        assertFalse(plan.alreadyCompatible)
        assertEquals(2, plan.replacements.size)
        val skip = ModuleCompatibility.plan(names.map { File(candidate,it) }, VERSION, actual)
        assertTrue(skip.alreadyCompatible)
        assertTrue(skip.replacements.isEmpty())
        ModuleCompatibility.verify(names.map { File(candidate,it) },actual)
        val oldHash = actual.mainHash
        assertEquals("5f5baf8e9ded92432e4c2cc9ff58a9313245323e8ca53ce37c169cc720e757c4",oldHash)
    }

    @Test fun `rewriter allocates loader dex after startup dex and rewrites restored native splits aligned`() {
        val path = "src/nonroot/java/com/moodtools/hub/directpatch/DirectPackagePatchManager.kt"
        val source = listOf(File(path), File("app/$path")).first(File::isFile).readText()
        assertTrue(source.contains("entries.map { it.name } + compatibilityEntries.keys"))
        assertTrue(source.contains("ZipEntry.STORED, counting"))
        assertTrue(source.contains("if (name.endsWith(\".so\")) ZipEntry.STORED else ZipEntry.DEFLATED"))
        assertTrue(source.contains("ModuleCompatibility.verify(complete, recipe)"))
        assertTrue(source.contains("compatibility?.replacements?.containsKey(source.file) == true"))
        assertTrue(source.contains("compatibilityEntries.filterKeys { DEX_NAME.matches(it) }"))
        assertTrue(source.contains("Loaded module native SHA256:"))
    }

    @Test fun `compatibility DEX additions are restored even with Google routing disabled`() {
        val file = apk("before.apk", mapOf("classes.dex" to "old".toByteArray()))
        java.util.zip.ZipFile(file).use { zip ->
            val additions = mapOf("classes.dex" to "restored".toByteArray(), "classes5.dex" to "startup".toByteArray())
            val result = DirectGmsArchive.prepare(zip, false, null, setOf("classes6.dex", "classes7.dex"), inputOverrides = additions)
            assertTrue(result.originals.isEmpty())
            assertEquals(additions.keys, result.replacements.keys)
            additions.forEach { (name, bytes) -> assertArrayEquals(bytes, result.replacements[name]) }
            assertThrows(IllegalArgumentException::class.java) {
                DirectGmsArchive.prepare(zip, false, null, setOf("classes5.dex"), inputOverrides = additions)
            }
        }
    }

    @Test fun `missing or mismatched compatibility asset cannot silently fall back to manifest-only stripping`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModuleCompatibility.load(java.io.ByteArrayInputStream("unknown".toByteArray()), PACKAGE)
        }
        val plain = temp.newFile("plain.so").apply { writeBytes("ELF-with-no-overlay".toByteArray()) }
        assertNull(ModuleCompatibility.fromNative(plain, PACKAGE))
    }
}
