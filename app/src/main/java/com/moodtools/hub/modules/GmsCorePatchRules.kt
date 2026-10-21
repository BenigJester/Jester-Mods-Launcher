package com.moodtools.hub.modules

import org.json.JSONObject
import java.security.MessageDigest

internal const val LAUNCHER_MODULE_BOOTSTRAP = 3

/** Exact-build aliases for the same fixed Google SDK operations, never arbitrary method bodies. */
data class GmsCoreSdkSymbols(
    val availabilityClass: String, val availabilityMethod: String, val availabilityStatic: Boolean,
    val signatureClass: String, val signatureMethod: String, val routingClasses: Set<String>
) {
    init {
        val descriptor = Regex("L[A-Za-z_$][A-Za-z0-9_$]*(?:/[A-Za-z_$][A-Za-z0-9_$]*)+;")
        val method = Regex("[A-Za-z_$][A-Za-z0-9_$]{0,63}")
        require(routingClasses.size in 2..64 && routingClasses.all { it.length <= 242 && descriptor.matches(it) })
        require(availabilityClass != signatureClass && availabilityClass in routingClasses && signatureClass in routingClasses)
        require(method.matches(availabilityMethod) && method.matches(signatureMethod))
    }
    fun toJson() = JSONObject().put("availability_class", availabilityClass).put("availability_method", availabilityMethod)
        .put("availability_static", availabilityStatic).put("signature_class", signatureClass)
        .put("signature_method", signatureMethod).put("routing_classes", org.json.JSONArray(routingClasses.sorted()))

    companion object {
        fun fromJson(value: Any): GmsCoreSdkSymbols {
            val json = value as? JSONObject ?: error("Invalid GmsCore SDK symbols")
            require(json.keys().asSequence().toSet() == setOf("availability_class", "availability_method",
                "availability_static", "signature_class", "signature_method", "routing_classes"))
            fun string(key: String) = json.get(key) as? String ?: error("Invalid GmsCore SDK symbol")
            val classes = json.getJSONArray("routing_classes")
            require(classes.length() in 2..64)
            val names = List(classes.length()) { classes.get(it) as? String ?: error("Invalid routing class") }
            require(names.distinct().size == names.size)
            return GmsCoreSdkSymbols(string("availability_class"), string("availability_method"),
                json.get("availability_static") as? Boolean ?: error("Invalid availability kind"),
                string("signature_class"), string("signature_method"), names.toSet())
        }
    }
}

/** Data-only, signed game profile. No byte offsets, scripts, classes to load or arbitrary method bodies. */
data class GmsCorePatchRules(
    val revision: String,
    val dexSha256: Map<String, String>,
    val accountType: String? = null,
    val sdkSymbols: GmsCoreSdkSymbols? = null
) {
    init {
        require(revision.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}")))
        require(dexSha256.size in 1..32 && dexSha256.all { (name, hash) ->
            name.matches(Regex("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) && hash.matches(Regex("[0-9a-f]{64}"))
        })
        require(accountType == null || accountType == "app.revanced")
    }

    fun toJson(): JSONObject = JSONObject().put("schema", 1).put("revision", revision)
        .put("dex_sha256", JSONObject(dexSha256.toSortedMap()))
        .also { accountType?.let { type -> it.put("account_type", type) } }
        .also { sdkSymbols?.let { symbols -> it.put("sdk_symbols", symbols.toJson()) } }

    /** Include the full rule content, not only its human revision, in cache invalidation. */
    fun fingerprint(): String = MessageDigest.getInstance("SHA-256").digest(
        ("schema=1\nrevision=$revision\naccount_type=${accountType.orEmpty()}\n" +
            dexSha256.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" } +
            (sdkSymbols?.let { "\nsdk_symbols=" + listOf(it.availabilityClass, it.availabilityMethod,
                it.availabilityStatic.toString(), it.signatureClass, it.signatureMethod,
                it.routingClasses.sorted().joinToString(",")).joinToString("|") } ?: "")).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    companion object {
        fun fromJson(parent: JSONObject, key: String): GmsCorePatchRules? {
            if (!parent.has(key)) return null
            val json = parent.get(key) as? JSONObject ?: error("$key must be an object")
            require(json.keys().asSequence().all { it in setOf("schema", "revision", "dex_sha256", "account_type", "sdk_symbols") })
            require(json.get("schema") is Number && (json.get("schema") as Number).toDouble() == 1.0) { "Unsupported GmsCore patch schema" }
            val hashes = json.getJSONObject("dex_sha256")
            return GmsCorePatchRules(json.get("revision") as? String ?: error("Invalid GmsCore revision"),
                hashes.keys().asSequence().associateWith { hashes.get(it) as? String ?: error("Invalid GmsCore DEX hash") },
                if (json.has("account_type")) json.get("account_type") as? String ?: error("Invalid account type") else null,
                if (json.has("sdk_symbols")) GmsCoreSdkSymbols.fromJson(json.get("sdk_symbols")) else null)
        }
    }
}
