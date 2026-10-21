package com.moodtools.hub.directpatch

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Bounded data-only recipes carried by the authenticated module native payload. */
internal object ModuleCompatibility {
    private val FOOTER = "JESTER-COMPAT-01".toByteArray(Charsets.US_ASCII)
    private const val MARKER = "assets/moodtools/soul-patch.json"
    private const val MAX_BYTES = 48 * 1024 * 1024

    data class Entry(val name: String, val before: String?, val after: String, val bytes: ByteArray)
    data class Recipe(val identity: String, val packageName: String, val versionCode: Long,
        val mainEntry: String, val mainHash: String, val entries: List<Entry>,
        val compatibleManifestHashes: Set<String> = emptySet())
    data class Plan(val recipe: Recipe, val replacements: Map<File, Map<String, ByteArray>>, val alreadyCompatible: Boolean)

    // ELF loaders ignore this unmapped overlay. The full file (including this trailer) is
    // already hashed by the module manifest; never call this before module verification.
    fun fromNative(file: File, packageName: String): Recipe? = RandomAccessFile(file, "r").use { input ->
        if (input.length() < FOOTER.size + 8) return@use null
        input.seek(input.length() - FOOTER.size)
        val magic = ByteArray(FOOTER.size).also(input::readFully)
        if (!magic.contentEquals(FOOTER)) return@use null
        input.seek(input.length() - FOOTER.size - 8)
        val length = input.readLong()
        require(length in 1..MAX_BYTES.toLong() && length <= input.length() - FOOTER.size - 8) {
            "Invalid module compatibility trailer"
        }
        input.seek(input.length() - FOOTER.size - 8 - length)
        load(ByteArrayInputStream(ByteArray(length.toInt()).also(input::readFully)), packageName)
    }

    fun load(input: InputStream, packageName: String): Recipe {
        val bundle = input.use { it.readBytesBounded(MAX_BYTES) }
        val files = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bundle)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name !in files && files.size < 257)
                val bytes = zip.readBytesBounded(MAX_BYTES - total)
                total += bytes.size
                files[entry.name] = bytes
            }
        }
        val profileBytes = requireNotNull(files.remove("profile.json"))
        require(profileBytes.size in 1..(256 * 1024))
        val json = JSONObject(String(profileBytes, Charsets.UTF_8))
        require(json.getInt("schema") == 1 && json.getString("package") == packageName)
        val profile = json.getString("profile")
        require(profile.matches(Regex("[A-Za-z0-9._-]{1,120}")))
        val version = json.getLong("versionCode").also { require(it > 0) }
        val main = json.getString("mainEntry").also { require(NATIVE.matches(it)) }
        val mainHash = json.getString("mainSha256").also { require(SHA.matches(it)) }
        val values = json.getJSONArray("entries")
        require(values.length() in 2..256)
        val entries = List(values.length()) { index ->
            val row = values.getJSONObject(index)
            val name = row.getString("entry")
            require(name == "AndroidManifest.xml" || DEX.matches(name) || NATIVE.matches(name))
            require(name != main) { "Compatibility must not replace the gameplay address anchor" }
            val bytes = requireNotNull(files.remove(row.getString("payload")))
            require(bytes.isNotEmpty() && bytes.size == row.getInt("size") && hash(bytes) == row.getString("after"))
            val before = if (row.isNull("before")) null else row.getString("before").also { require(SHA.matches(it)) }
            val after = row.getString("after").also { require(SHA.matches(it)) }
            require(before != null || DEX.matches(name)) { "Only a base DEX addition is supported" }
            Entry(name, before, after, bytes)
        }
        require(entries.size in 2..256 && entries.map { it.name }.distinct().size == entries.size && files.isEmpty())
        require(entries.count { it.name == "AndroidManifest.xml" } == 1)
        require(entries.any { it.name != "AndroidManifest.xml" && it.before != it.after })
        val manifests = json.optJSONArray("compatibleManifestSha256")
        require(!json.has("compatibleManifestSha256") || manifests != null)
        require((manifests?.length() ?: 0) <= 32)
        val compatibleManifests = manifests?.let { values ->
            List(values.length()) { values.getString(it).also { digest -> require(SHA.matches(digest)) } }.toSet()
        }.orEmpty()
        return Recipe("$profile:${hash(bundle)}", packageName, version, main, mainHash, entries, compatibleManifests)
    }

    /** Content detection precedes mutation; mixed, renamed or independently modified inputs fail closed. */
    fun plan(apks: List<File>, versionCode: Long, recipe: Recipe): Plan {
        require(apks.isNotEmpty() && versionCode == recipe.versionCode) { "Module compatibility recipe does not support this game build" }
        val before = mutableListOf<Boolean>()
        val after = mutableListOf<Boolean>()
        val locations = linkedMapOf<Entry, File>()
        var mainCount = 0
        for (apk in apks) ZipFile(apk).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            require(names.size == names.distinct().size) { "Duplicate APK entries" }
            zip.getEntry(recipe.mainEntry)?.let { entry ->
                require(zip.getInputStream(entry).use { hash(it) } == recipe.mainHash) {
                    "Gameplay image differs from the module's verified address anchor"
                }
                mainCount++
            }
            for (entry in recipe.entries) {
                val isBase = entry.name == "AndroidManifest.xml" || entry.name.startsWith("classes")
                if (isBase && apk != apks.first()) continue
                if (!isBase && zip.getEntry(entry.name) == null) continue
                require(locations.put(entry, apk) == null) { "Compatibility native split is duplicated" }
                // Launcher GmsCore keeps its exact pre-transform DEX. Validate the actual current
                // DEX too; an untrusted/stale marker alone must not trigger the already-patched path.
                val data = logicalBytes(zip, entry.name)
                require(data != null || entry.before == null) { "APK is missing ${entry.name}" }
                val digest = data?.let(::hash)
                if (entry.name != "AndroidManifest.xml") {
                    before += digest == entry.before
                    after += digest == entry.after
                }
            }
        }
        require(mainCount == 1 && locations.size == recipe.entries.size) { "Compatibility requires the complete matching APK split set" }
        val stock = before.all { it }
        val compatible = after.all { it }
        require(stock || compatible) {
            "Game is partially or differently patched. Use the matching original or complete compatibility APK set; do not uninstall or clear game data."
        }
        if (compatible) {
            val manifest = recipe.entries.single { it.name == "AndroidManifest.xml" }
            ZipFile(apks.first()).use { zip ->
                val digest = hash(requireNotNull(logicalBytes(zip, manifest.name)))
                // Legacy launcher builds predate compatibility marker fields. Accept only
                // exact manifest outputs approved by this authenticated module, AFTER all
                // startup DEX/native hashes and the unchanged gameplay anchor matched.
                if (digest != manifest.after && digest !in recipe.compatibleManifestHashes) {
                    val marker = requireNotNull(zip.getEntry(MARKER)) { "Restored DEX has a mismatched manifest" }
                    require(marker.size in 1..65536)
                    val json = JSONObject(zip.getInputStream(marker).bufferedReader().use { it.readText() })
                    require(json.optString("compatibilityProfile") == recipe.identity &&
                        json.optString("compatibilityManifestSha256") == digest) {
                        "Restored manifest does not match this module recipe\n" +
                            "Actual manifest SHA256: $digest\n" +
                            "Recipe: ${recipe.identity}\n" +
                            "Approved legacy manifests: ${recipe.compatibleManifestHashes.sorted().joinToString().ifEmpty { "none" }}"
                    }
                }
            }
            return Plan(recipe, emptyMap(), true)
        }
        // A fresh restoration additionally requires the exact packaged manifest; never overwrite
        // a manifest from an unrelated repacker with a known one just because DEX happened to match.
        val manifest = recipe.entries.single { it.name == "AndroidManifest.xml" }
        ZipFile(apks.first()).use { zip ->
            require(hash(requireNotNull(logicalBytes(zip, manifest.name))) == manifest.before) {
                "Base manifest differs from the supported original"
            }
        }
        return Plan(recipe, locations.entries.groupBy({ it.value }, { it.key })
            .mapValues { (_, entries) -> entries.associate { it.name to it.bytes } }, false)
    }

    fun verify(apks: List<File>, recipe: Recipe) {
        require(plan(apks, recipe.versionCode, recipe).alreadyCompatible) { "Module compatibility restoration is incomplete" }
    }

    private fun logicalBytes(zip: ZipFile, name: String): ByteArray? {
        val current = zip.getEntry(name) ?: return null
        require(current.size in 1..MAX_BYTES.toLong())
        val data = zip.getInputStream(current).use { it.readBytesBounded(MAX_BYTES) }
        if (!name.endsWith(".dex")) return data
        val backup = zip.getEntry(DirectGmsArchive.BACKUP_PREFIX + name) ?: return data
        val markerEntry = requireNotNull(zip.getEntry(MARKER)) { "GmsCore backup has no matching patch marker" }
        require(markerEntry.size in 1..65536)
        val marker = JSONObject(zip.getInputStream(markerEntry).bufferedReader().use { it.readText() })
        require(hash(data) == marker.getJSONObject("gmsPatched").getString(name))
        require(backup.size in 1..MAX_BYTES.toLong())
        val original = zip.getInputStream(backup).use { it.readBytesBounded(MAX_BYTES) }
        require(hash(original) == marker.getJSONObject("gmsOriginals").getString(name))
        return original
    }

    private fun InputStream.readBytesBounded(limit: Int): ByteArray {
        require(limit > 0)
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32768)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= limit) { "Module compatibility data is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun hash(bytes: ByteArray): String = hash(ByteArrayInputStream(bytes))
    private val SHA = Regex("[0-9a-f]{64}")
    private val DEX = Regex("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")
    private val NATIVE = Regex("lib/(?:arm64-v8a|armeabi-v7a)/[A-Za-z0-9_.-]+\\.so")
    private fun hash(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
