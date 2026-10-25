package com.moodtools.hub.directpatch

import org.json.JSONObject
import java.util.zip.ZipFile

/** Reversible per-DEX transformation. APK split/native/resources are not rewritten by this layer. */
internal object DirectGmsArchive {
    const val BACKUP_PREFIX = "assets/moodtools/gms-originals/"
    private val DEX = Regex("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")
    data class Patch(val replacements: Map<String, ByteArray>, val originals: Map<String, ByteArray>)

    fun prepare(archive: ZipFile, enabled: Boolean, previous: JSONObject?, excluded: Set<String>,
        rules: com.moodtools.hub.modules.GmsCorePatchRules? = null,
        inputOverrides: Map<String, ByteArray> = emptyMap()): Patch {
        val originals = linkedMapOf<String, ByteArray>()
        var total = 0L
        fun read(name: String): ByteArray {
            val entry = requireNotNull(archive.getEntry(name)) { "The original Google service client is unavailable" }
            require(entry.size in 1..(32L * 1024 * 1024))
            total += entry.size
            if (total > 96L * 1024 * 1024)
                throw GmsCorePatchFailure(GmsCorePatchFailure.Check.ARCHIVE_LIMIT_EXCEEDED)
            return archive.getInputStream(entry).use { it.readBytes() }
        }
        val recorded = previous?.optJSONObject("gmsOriginals")
        if (recorded != null) for (name in recorded.keys()) {
            require(DEX.matches(name) && name !in excluded)
            val bytes = read(BACKUP_PREFIX + name)
            require(GmsCoreDexPatcher.hash(bytes) == recorded.getString(name))
            originals[name] = bytes
        }
        if (previous?.optString("gmsProfile") == LegacyGmsPatchMigration.PROFILE) {
            val bytes = read("assets/moodtools/gms-original.dex")
            require(GmsCoreDexPatcher.hash(bytes) == LegacyGmsPatchMigration.ORIGINAL)
            originals[LegacyGmsPatchMigration.ENTRY] = bytes
        }
        require(previous?.optString("gmsProfile").isNullOrEmpty() || originals.isNotEmpty()) {
            "The original Google service client is unavailable"
        }
        require(inputOverrides.keys.all { DEX.matches(it) && it !in excluded })
        if (!enabled) return Patch(originals + inputOverrides, emptyMap())
        val names = archive.entries().asSequence().map { it.name }
            .filter { DEX.matches(it) && it !in excluded }.toSet() + inputOverrides.keys
        val inputs = names.associateWith { inputOverrides[it] ?: originals[it] ?: read(it) }
        val changed = GmsCoreDexPatcher.rewriteSet(inputs, rules)
        return Patch(changed, changed.keys.associateWith { inputs.getValue(it) })
    }

    fun verify(archive: ZipFile, marker: JSONObject) {
        require(marker.getString("originalSignerSha1").matches(Regex("[0-9a-f]{40}")))
        val originals = marker.getJSONObject("gmsOriginals")
        val patched = marker.getJSONObject("gmsPatched")
        require(originals.length() > 0 && originals.keys().asSequence().toSet() == patched.keys().asSequence().toSet())
        for (name in originals.keys()) {
            require(DEX.matches(name))
            for ((entryName, expected) in listOf(BACKUP_PREFIX + name to originals.getString(name), name to patched.getString(name))) {
                val entry = requireNotNull(archive.getEntry(entryName))
                require(entry.size in 1..(32L * 1024 * 1024))
                val bytes = archive.getInputStream(entry).use { it.readBytes() }
                require(GmsCoreDexPatcher.hash(bytes) == expected)
            }
        }
    }
}
