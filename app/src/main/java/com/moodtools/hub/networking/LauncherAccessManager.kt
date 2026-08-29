package com.moodtools.hub.networking

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import com.moodtools.hub.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

data class LauncherLease(val issuedAt: Long, val expiresAt: Long)
data class LauncherAccountIdentity(
    val grantPassIdentity: String,
    val deviceId: String,
    val recoveryId: String,
    val installationId: String,
    val proofKeyId: String,
    val flavor: String,
    val accessVersion: Int
)

data class LauncherPrivateCatalog(
    val envelopes: List<JSONObject>,
    val capability: String
)

class LauncherModuleAuthorization internal constructor(
    val manifest: JSONObject,
    val capability: String,
    val expiresAt: Long,
    val privateScope: String?,
    internal val proofKeyId: String,
    private val payloadProofProvider: (method: String, path: String) -> LauncherRequestProof
) {
    internal fun payloadProof(method: String, path: String): LauncherRequestProof =
        payloadProofProvider(method, path)
}

class LauncherAccessManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val proofKeys = LauncherProofKeyManager(appContext)
    private val attestationKeys = LauncherAttestationKeyManager(appContext)
    private val stableAndroidId: String by lazy {
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            .orEmpty()
            .also {
                require(it.isNotEmpty() && it != LEGACY_BROKEN_ANDROID_ID) {
                    "This device did not provide a stable Android identity"
                }
            }
    }
    val installationId: String
        get() = preferences.getString(INSTALLATION_ID, null)?.takeIf { it.matches(ID_PATTERN) }
            ?: randomId().also { preferences.edit().putString(INSTALLATION_ID, it).apply() }
    val deviceId: String by lazy {
        hashedId("$DEVICE_ID_NAMESPACE\u0000$stableAndroidId")
    }
    private val recoveryId: String by lazy {
        hashedId("$RECOVERY_ID_NAMESPACE\u0000$stableAndroidId")
    }

    fun accountIdentity(): LauncherAccountIdentity {
        val proofIdentity = proofKeys.identity()
        return LauncherAccountIdentity(
            grantPassIdentity = "JM$ACCESS_VERSION.$deviceId.$recoveryId",
            deviceId = deviceId,
            recoveryId = recoveryId,
            installationId = installationId,
            proofKeyId = proofIdentity.keyId,
            flavor = BuildConfig.FLAVOR,
            accessVersion = ACCESS_VERSION
        )
    }

    /** Refreshes a server-approved private scope, falling back to its signed offline lease. */
    internal fun currentPrivateLease(scope: String): LauncherPrivateLease? {
        require(scope.matches(PRIVATE_SCOPE_PATTERN)) { "Invalid private module scope" }
        val now = System.currentTimeMillis() / 1_000L
        val digitalKey = preferences.getString(DIGITAL_KEY, null).orEmpty()
        if (digitalKey.isEmpty()) {
            clearPrivateLease(scope)
            return null
        }
        val proofIdentity = proofKeys.identity()
        val cachedText = preferences.getString("$PRIVATE_LEASE_PREFIX$scope", null).orEmpty()
        val cached = runCatching {
            LauncherPrivateLeaseVerifier.verify(
                envelope = JSONObject(cachedText),
                expectedScope = scope,
                expectedDeviceId = deviceId,
                expectedRecoveryId = recoveryId,
                expectedFlavor = BuildConfig.FLAVOR,
                expectedProofKeyId = proofIdentity.keyId
            )
        }.getOrNull()?.takeIf { lease ->
            LauncherOfflineLeaseVerifier.clockStatus(
                issuedAt = lease.issuedAt,
                expiresAt = lease.expiresAt,
                now = now,
                lastSeen = preferences.getLong(privateLastSeenKey(scope), 0L),
                elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                lastElapsedRealtimeMillis = preferences.getLong(privateElapsedKey(scope), 0L)
            ) == LauncherLeaseClockStatus.VALID
        }

        return try {
            val response = postJson(
                "$BASE_URL/api/launcher/private-access",
                JSONObject()
                    .put("digitalKey", digitalKey)
                    .put("installationId", installationId)
                    .put("deviceId", deviceId)
                    .put("recoveryId", recoveryId)
                    .put("flavor", BuildConfig.FLAVOR)
                    .put("accessVersion", ACCESS_VERSION)
                    .put("proofVersion", LauncherProofKeyManager.PROOF_VERSION)
                    .put("proofKeyId", proofIdentity.keyId)
                    .put("scope", scope)
            )
            if (response.has("approved") && !response.optBoolean("approved")) {
                clearPrivateLease(scope)
                null
            } else {
                require(response.optBoolean("ok")) {
                    response.optString("message", "Private device approval is unavailable")
                }
                val envelope = response.getJSONObject("offlineLease")
                val lease = LauncherPrivateLeaseVerifier.verify(
                    envelope = envelope,
                    expectedScope = scope,
                    expectedDeviceId = deviceId,
                    expectedRecoveryId = recoveryId,
                    expectedFlavor = BuildConfig.FLAVOR,
                    expectedProofKeyId = proofIdentity.keyId
                )
                check(preferences.edit()
                    .putString("$PRIVATE_LEASE_PREFIX$scope", envelope.toString())
                    .putLong(privateLastSeenKey(scope), now)
                    .putLong(privateElapsedKey(scope), SystemClock.elapsedRealtime())
                    .commit()) { "Private device approval could not be saved" }
                lease
            }
        } catch (error: Exception) {
            if (cached != null) {
                android.util.Log.w(
                    "JesterMoodsPrivateAccess",
                    "The private access server could not be reached; using the signed offline approval.",
                    error
                )
                rememberPrivateLeaseClock(scope, now)
                cached
            } else {
                throw error
            }
        }
    }

    private fun clearPrivateLease(scope: String) {
        preferences.edit()
            .remove("$PRIVATE_LEASE_PREFIX$scope")
            .remove(privateLastSeenKey(scope))
            .remove(privateElapsedKey(scope))
            .apply()
    }

    private fun rememberPrivateLeaseClock(scope: String, now: Long) {
        preferences.edit()
            .putLong(
                privateLastSeenKey(scope),
                maxOf(now, preferences.getLong(privateLastSeenKey(scope), 0L))
            )
            .putLong(privateElapsedKey(scope), SystemClock.elapsedRealtime())
            .apply()
    }

    private fun privateLastSeenKey(scope: String) = "$PRIVATE_LEASE_PREFIX${scope}_last_seen"

    private fun privateElapsedKey(scope: String) = "$PRIVATE_LEASE_PREFIX${scope}_elapsed"

    fun currentLease(): LauncherLease? {
        val now = System.currentTimeMillis() / 1000L
        val key = preferences.getString(DIGITAL_KEY, null).orEmpty()
        val issuedAt = preferences.getLong(ISSUED_AT, 0L)
        val expiresAt = preferences.getLong(EXPIRES_AT, 0L)
        val lastSeen = preferences.getLong(LAST_SEEN, 0L)
        val offlineLeaseText = preferences.getString(OFFLINE_LEASE, null).orEmpty()
        val accessVersion = activeAccessVersion()
        if (accessVersion == ACCESS_VERSION && offlineLeaseText.isEmpty()) {
            clearLease()
            return recoverLease(ACCESS_VERSION)
        }
        if (accessVersion == ACCESS_VERSION && offlineLeaseText.isNotEmpty()) {
            val proofIdentity = proofKeys.identity()
            val claims = runCatching {
                LauncherOfflineLeaseVerifier.verify(
                    envelope = JSONObject(offlineLeaseText),
                    digitalKey = key,
                    expectedDeviceId = deviceId,
                    expectedFlavor = BuildConfig.FLAVOR,
                    expectedProofKeyId = proofIdentity.keyId
                )
            }.getOrNull()
            if (claims == null || claims.issuedAt != issuedAt || claims.expiresAt != expiresAt) {
                clearLease()
                return recoverLease(ACCESS_VERSION)
            }
            val clockStatus = LauncherOfflineLeaseVerifier.clockStatus(
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                now = now,
                lastSeen = lastSeen,
                elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                lastElapsedRealtimeMillis = preferences.getLong(LAST_ELAPSED_REALTIME, 0L)
            )
            return when (clockStatus) {
                LauncherLeaseClockStatus.VALID -> {
                    try {
                        val response = postJson(
                            "$BASE_URL/api/launcher/access",
                            JSONObject()
                                .put("digitalKey", key)
                                .put("installationId", installationId)
                                .put("deviceId", deviceId)
                                .put("flavor", BuildConfig.FLAVOR)
                                .put("accessVersion", ACCESS_VERSION)
                                .put("managedExpiry", true)
                        )
                        if (!response.optBoolean("ok")) {
                            clearLease()
                            recoverLease(ACCESS_VERSION)
                        } else {
                            acceptProtocol4Lease(response, proofIdentity, requireRecoveryBound = true)
                        }
                    } catch (error: Exception) {
                        android.util.Log.w(
                            "JesterMoodsAccess",
                            "The access server could not be reached; using the verified offline lease.",
                            error
                        )
                        rememberLeaseClock(now)
                        LauncherLease(issuedAt, expiresAt)
                    }
                }
                LauncherLeaseClockStatus.EXPIRED -> {
                    clearLease()
                    recoverLease(ACCESS_VERSION)
                }
                LauncherLeaseClockStatus.ROLLED_BACK -> null
            }
        }
        val valid = key.length in 80..4096 && issuedAt > 0 && expiresAt > issuedAt &&
            now >= issuedAt - CLOCK_SKEW_SECONDS && now < expiresAt &&
            (lastSeen == 0L || now + CLOCK_SKEW_SECONDS >= lastSeen)
        if (!valid) {
            clearLease()
            return recoverLease(DEVICE_LOCK_ACCESS_VERSION)
        }
        val response = postJson(
            "$BASE_URL/api/launcher/access",
            JSONObject()
                .put("digitalKey", key)
                .put("installationId", installationId)
                .put("deviceId", deviceId)
                .put("flavor", BuildConfig.FLAVOR)
                .put("accessVersion", accessVersion)
        )
        if (!response.optBoolean("ok") || response.optLong("issuedAt") != issuedAt ||
            response.optLong("expiresAt") != expiresAt) {
            clearLease()
            return recoverLease(DEVICE_LOCK_ACCESS_VERSION)
        }
        rememberLeaseClock(now)
        return LauncherLease(issuedAt, expiresAt)
    }

    private fun recoverLease(accessVersion: Int = ACCESS_VERSION): LauncherLease? {
        val proofIdentity = proofKeys.identity()
        val response = postJson(
            "$BASE_URL/api/launcher/recover",
            JSONObject()
                .put("installationId", installationId)
                .put("deviceId", deviceId)
                .put("recoveryId", recoveryId)
                .put("flavor", BuildConfig.FLAVOR)
                .put("accessVersion", accessVersion)
                .put("managedExpiry", accessVersion == ACCESS_VERSION)
                .put("proofVersion", LauncherProofKeyManager.PROOF_VERSION)
                .put("proofKeyId", proofIdentity.keyId)
        )
        if (!response.optBoolean("ok")) return null
        val digitalKey = response.optString("digitalKey")
        val issuedAt = response.optLong("issuedAt")
        val expiresAt = response.optLong("expiresAt")
        require(validManagedAccessWindow(issuedAt, expiresAt)) {
            "The recovered digital key is invalid"
        }
        val offlineLease = if (accessVersion == ACCESS_VERSION) {
            response.getJSONObject("offlineLease").also {
                val claims = LauncherOfflineLeaseVerifier.verify(
                    envelope = it,
                    digitalKey = digitalKey,
                    expectedDeviceId = deviceId,
                    expectedFlavor = BuildConfig.FLAVOR,
                    expectedProofKeyId = proofIdentity.keyId
                )
                require(claims.issuedAt == issuedAt && claims.expiresAt == expiresAt)
            }.toString()
        } else null
        if (accessVersion == ACCESS_VERSION) {
            require(response.optBoolean("recoveryBound")) {
                "The recovered lease is not bound to this device recovery identity"
            }
            preferences.edit().putString(RECOVERY_BOUND_KEY, proofIdentity.keyId).apply()
        }
        saveLease(digitalKey, issuedAt, expiresAt, accessVersion, offlineLease)
        return LauncherLease(issuedAt, expiresAt)
    }

    private fun acceptProtocol4Lease(
        response: JSONObject,
        proofIdentity: LauncherProofIdentity,
        requireRecoveryBound: Boolean
    ): LauncherLease {
        require(response.optBoolean("ok")) {
            response.optString("message", "The launcher access check failed")
        }
        if (requireRecoveryBound) {
            require(response.optBoolean("recoveryBound")) {
                "The refreshed lease is not bound to this device recovery identity"
            }
        }
        require(response.optString("proofKeyId") == proofIdentity.keyId) {
            "The refreshed lease belongs to another launcher proof key"
        }
        val digitalKey = response.getString("digitalKey")
        val issuedAt = response.getLong("issuedAt")
        val expiresAt = response.getLong("expiresAt")
        require(digitalKey.length in 80..4096 && validManagedAccessWindow(issuedAt, expiresAt)) {
            "The refreshed digital key is invalid"
        }
        val offlineLease = response.getJSONObject("offlineLease").also {
            val claims = LauncherOfflineLeaseVerifier.verify(
                envelope = it,
                digitalKey = digitalKey,
                expectedDeviceId = deviceId,
                expectedFlavor = BuildConfig.FLAVOR,
                expectedProofKeyId = proofIdentity.keyId
            )
            require(claims.issuedAt == issuedAt && claims.expiresAt == expiresAt)
        }.toString()
        saveLease(digitalKey, issuedAt, expiresAt, ACCESS_VERSION, offlineLease)
        preferences.edit().putString(RECOVERY_BOUND_KEY, proofIdentity.keyId).apply()
        return LauncherLease(issuedAt, expiresAt)
    }

    private fun validManagedAccessWindow(issuedAt: Long, expiresAt: Long): Boolean =
        issuedAt > 0L && expiresAt > issuedAt &&
            expiresAt - issuedAt <= MAX_MANAGED_ACCESS_TTL_SECONDS

    fun createUnlockUrl(): String {
        val challenge = randomId()
        val proofIdentity = proofKeys.identity()
        preferences.edit()
            .putString(PENDING_CHALLENGE, challenge)
            .putLong(PENDING_EXPIRES, System.currentTimeMillis() + FLOW_TTL_MS)
            .apply()
        return Uri.parse("$BASE_URL/launcher/unlock").buildUpon()
            .appendQueryParameter("installation", installationId)
            .appendQueryParameter("device", deviceId)
            .appendQueryParameter("challenge", challenge)
            .appendQueryParameter("flavor", BuildConfig.FLAVOR)
            .appendQueryParameter("accessVersion", ACCESS_VERSION.toString())
            .appendQueryParameter("proofKeyId", proofIdentity.keyId)
            .build().toString()
    }

    fun authorizeModule(packageName: String, abi: String, bootstrap: Int = 1): LauncherModuleAuthorization {
        require(packageName.matches(Regex("[A-Za-z0-9_.]{3,200}")))
        require(abi == "arm64-v8a" || abi == "armeabi-v7a")
        require(bootstrap >= 1)
        require(currentLease() != null) { "Active launcher access is required" }
        val digitalKey = preferences.getString(DIGITAL_KEY, null).orEmpty()
        require(digitalKey.length in 80..4096) { "Active launcher access is required" }
        val accessVersion = activeAccessVersion()
        val identity = ensureProofKeyRegistered(digitalKey)
        ensureProofAttestationRequired(digitalKey, identity)
        val request = accessRequest(digitalKey)
            .put("purpose", "module-authorize")
            .put("proofVersion", LauncherProofKeyManager.PROOF_VERSION)
            .put("keyId", identity.keyId)
            .put("packageName", packageName)
            .put("abi", abi)
            .put("bootstrap", bootstrap)
        val challengeResponse = postJson("$BASE_URL/api/launcher/proof/challenge", request)
        require(challengeResponse.optBoolean("ok")) {
            challengeResponse.optString("message", "The launcher proof challenge failed")
        }
        val proofNonce = challengeResponse.getString("nonce")
        validateProofChallenge(challengeResponse, identity.keyId, proofNonce)
        val proof = proofKeys.sign(
            proofNonce,
            LauncherProofKeyManager.moduleAuthorizationCanonical(
                nonce = proofNonce,
                installationId = installationId,
                deviceId = deviceId,
                flavor = BuildConfig.FLAVOR,
                accessVersion = accessVersion,
                packageName = packageName,
                abi = abi,
                bootstrap = bootstrap,
                keyId = identity.keyId
            )
        )
        val response = postJson(
            "$BASE_URL/api/launcher-module",
            accessRequest(digitalKey)
                .put("packageName", packageName)
                .put("abi", abi)
                .put("bootstrap", bootstrap)
                .put("proof", proof.toJson())
        )
        require(response.optBoolean("ok")) {
            response.optString("message", "The launcher could not authorize this module download")
        }
        val capability = response.getString("capability")
        val expiresAt = response.getLong("expiresAt")
        val now = System.currentTimeMillis() / 1000L
        require(capability.length in 80..4096 && capability.matches(Regex("[A-Za-z0-9_.-]+")) &&
            expiresAt > now && expiresAt <= now + MODULE_CAPABILITY_TTL_SECONDS + CLOCK_SKEW_SECONDS &&
            response.optBoolean("proofRequired") &&
            response.optBoolean("attestationRequired") &&
            response.optInt("proofVersion") == LauncherProofKeyManager.PROOF_VERSION &&
            response.optInt("attestationVersion") == LauncherAttestationKeyManager.ATTESTATION_VERSION &&
            response.optString("proofKeyId") == identity.keyId) {
            "The module download authorization is invalid"
        }
        return LauncherModuleAuthorization(
            manifest = response.getJSONObject("manifest"),
            capability = capability,
            expiresAt = expiresAt,
            privateScope = response.optString("privateScope").takeIf(String::isNotBlank)?.also {
                require(it.matches(PRIVATE_SCOPE_PATTERN)) { "The private module scope is invalid" }
            },
            proofKeyId = identity.keyId,
            payloadProofProvider = { method, path ->
                createPayloadProof(capability, identity.keyId, method, path)
            }
        )
    }

    fun privateCatalog(): LauncherPrivateCatalog {
        require(currentLease() != null) { "Active launcher access is required" }
        val digitalKey = preferences.getString(DIGITAL_KEY, null).orEmpty()
        require(digitalKey.length in 80..4096) { "Active launcher access is required" }
        val accessVersion = activeAccessVersion()
        val identity = ensureProofKeyRegistered(digitalKey)
        ensureProofAttestationRequired(digitalKey, identity)
        val challengeResponse = postJson(
            "$BASE_URL/api/launcher/proof/challenge",
            accessRequest(digitalKey)
                .put("purpose", "private-catalog")
                .put("proofVersion", LauncherProofKeyManager.PROOF_VERSION)
                .put("keyId", identity.keyId)
        )
        require(challengeResponse.optBoolean("ok")) {
            challengeResponse.optString("message", "The private catalog proof challenge failed")
        }
        val nonce = challengeResponse.getString("nonce")
        validateProofChallenge(challengeResponse, identity.keyId, nonce)
        val proof = proofKeys.sign(
            nonce,
            LauncherProofKeyManager.privateCatalogAuthorizationCanonical(
                nonce = nonce,
                installationId = installationId,
                deviceId = deviceId,
                flavor = BuildConfig.FLAVOR,
                accessVersion = accessVersion,
                keyId = identity.keyId
            )
        )
        val response = postJson(
            "$BASE_URL/api/launcher/private-catalog",
            accessRequest(digitalKey).put("proof", proof.toJson())
        )
        require(response.optBoolean("ok")) {
            response.optString("message", "The private module catalog is unavailable")
        }
        val catalogs = response.getJSONArray("catalogs")
        require(catalogs.length() in 0..MAX_PRIVATE_CATALOGS)
        val capability = response.getString("capability")
        val expiresAt = response.getLong("expiresAt")
        val now = System.currentTimeMillis() / 1_000L
        require(capability.length in 80..4096 && capability.matches(Regex("[A-Za-z0-9_.-]+")) &&
            expiresAt > now && expiresAt <= now + MODULE_CAPABILITY_TTL_SECONDS + CLOCK_SKEW_SECONDS)
        val envelopes = buildList {
            for (index in 0 until catalogs.length()) add(catalogs.getJSONObject(index))
        }
        return LauncherPrivateCatalog(envelopes, capability)
    }

    private fun ensureProofKeyRegistered(digitalKey: String): LauncherProofIdentity {
        val identity = proofKeys.identity()
        val accessVersion = activeAccessVersion()
        val request = accessRequest(digitalKey)
            .put("purpose", "register")
            .put("proofVersion", LauncherProofKeyManager.PROOF_VERSION)
            .put("keyId", identity.keyId)
            .put("publicKey", identity.publicKey)
        val challengeResponse = postJson("$BASE_URL/api/launcher/proof/challenge", request)
        require(challengeResponse.optBoolean("ok")) {
            challengeResponse.optString("message", "The launcher proof key could not be registered")
        }
        if (challengeResponse.optBoolean("registered")) {
            require(challengeResponse.optString("keyId") == identity.keyId)
            return identity
        }
        val nonce = challengeResponse.getString("nonce")
        validateProofChallenge(challengeResponse, identity.keyId, nonce)
        val proof = proofKeys.sign(
            nonce,
            LauncherProofKeyManager.registrationCanonical(
                nonce = nonce,
                installationId = installationId,
                deviceId = deviceId,
                flavor = BuildConfig.FLAVOR,
                accessVersion = accessVersion,
                keyId = identity.keyId,
                publicKey = identity.publicKey
            )
        )
        val registration = postJson(
            "$BASE_URL/api/launcher/proof/register",
            accessRequest(digitalKey)
                .put("publicKey", identity.publicKey)
                .put("proof", proof.toJson())
        )
        require(registration.optBoolean("ok") && registration.optBoolean("registered") &&
            registration.optInt("proofVersion") == LauncherProofKeyManager.PROOF_VERSION &&
            registration.optString("keyId") == identity.keyId) {
            registration.optString("message", "The launcher proof key registration was rejected")
        }
        return identity
    }

    private fun ensureRecoveryBindingBestEffort(
        digitalKey: String,
        identity: LauncherProofIdentity
    ) {
        if (activeAccessVersion() != ACCESS_VERSION ||
            preferences.getString(RECOVERY_BOUND_KEY, null) == identity.keyId) return
        try {
            val registeredIdentity = ensureProofKeyRegistered(digitalKey)
            require(registeredIdentity.keyId == identity.keyId)
            val challengeResponse = postJson(
                "$BASE_URL/api/launcher/proof/challenge",
                accessRequest(digitalKey)
                    .put("purpose", "recovery-bind")
                    .put("proofVersion", LauncherProofKeyManager.PROOF_VERSION)
                    .put("keyId", identity.keyId)
                    .put("recoveryId", recoveryId)
            )
            require(challengeResponse.optBoolean("ok")) {
                challengeResponse.optString("message", "Device recovery binding is unavailable")
            }
            val nonce = challengeResponse.getString("nonce")
            validateProofChallenge(challengeResponse, identity.keyId, nonce)
            val proof = proofKeys.sign(
                nonce,
                LauncherProofKeyManager.recoveryBindingCanonical(
                    nonce = nonce,
                    installationId = installationId,
                    deviceId = deviceId,
                    recoveryId = recoveryId,
                    flavor = BuildConfig.FLAVOR,
                    accessVersion = activeAccessVersion(),
                    keyId = identity.keyId
                )
            )
            val response = postJson(
                "$BASE_URL/api/launcher/recovery/bind",
                accessRequest(digitalKey)
                    .put("recoveryId", recoveryId)
                    .put("proof", proof.toJson())
            )
            require(response.optBoolean("ok") && response.optBoolean("recoveryBound") &&
                response.optString("proofKeyId") == identity.keyId) {
                response.optString("message", "Device recovery binding was rejected")
            }
            preferences.edit().putString(RECOVERY_BOUND_KEY, identity.keyId).apply()
        } catch (_: Exception) {
            // Offline access remains usable; retry the server binding on the next valid startup.
        }
    }

    private fun ensureProofAttestationRequired(
        digitalKey: String,
        identity: LauncherProofIdentity
    ) {
        val accessVersion = activeAccessVersion()
        val challengeResponse = postJson(
            "$BASE_URL/api/launcher/proof/attestation/challenge",
            accessRequest(digitalKey)
                .put("proofVersion", LauncherProofKeyManager.PROOF_VERSION)
                .put("keyId", identity.keyId)
        )
        require(challengeResponse.optBoolean("ok") &&
            challengeResponse.optInt("proofVersion") == LauncherProofKeyManager.PROOF_VERSION &&
            challengeResponse.optString("keyId") == identity.keyId) {
            challengeResponse.optString("message", "Official launcher verification is unavailable")
        }
        if (challengeResponse.optBoolean("registered")) {
            require(challengeResponse.optBoolean("accepted")) {
                "This launcher build is not accepted for protected modules"
            }
            return
        }

        val nonce = challengeResponse.getString("nonce")
        validateProofChallenge(challengeResponse, identity.keyId, nonce)
        val challenge = LauncherProofKeyManager.attestationChallenge(
            nonce = nonce,
            installationId = installationId,
            deviceId = deviceId,
            flavor = BuildConfig.FLAVOR,
            accessVersion = accessVersion,
            keyId = identity.keyId
        )
        val evidence = attestationKeys.createEvidence(challenge)
        val proof = proofKeys.sign(
            nonce,
            LauncherProofKeyManager.attestationEvidenceCanonical(
                nonce = nonce,
                installationId = installationId,
                deviceId = deviceId,
                flavor = BuildConfig.FLAVOR,
                accessVersion = accessVersion,
                keyId = identity.keyId,
                chainHash = evidence.chainHash
            )
        )
        val response = postJson(
            "$BASE_URL/api/launcher/proof/attest",
            accessRequest(digitalKey)
                .put("proof", proof.toJson())
                .put(
                    "attestation",
                    JSONObject()
                        .put("version", LauncherAttestationKeyManager.ATTESTATION_VERSION)
                        .put("chainHash", evidence.chainHash)
                        .put("certificateChain", JSONArray(evidence.certificateChain))
                )
        )
        require(response.optBoolean("ok") && response.optBoolean("registered") &&
            response.optBoolean("accepted") &&
            response.optInt("attestationVersion") == LauncherAttestationKeyManager.ATTESTATION_VERSION &&
            response.optString("proofKeyId") == identity.keyId &&
            response.optString("chainHash") == evidence.chainHash) {
            response.optString("message", "Official launcher verification was not accepted")
        }
    }

    private fun createPayloadProof(
        capability: String,
        keyId: String,
        method: String,
        path: String
    ): LauncherRequestProof {
        require(method == "GET" || method == "HEAD")
        require(path.matches(Regex("^/api/launcher-module-payload/[A-Za-z0-9._/-]{1,400}$")))
        val response = postJson(
            "$BASE_URL/api/launcher-module-proof",
            JSONObject()
                .put("method", method)
                .put("path", path)
                .put("keyId", keyId),
            mapOf("Authorization" to "Bearer $capability")
        )
        require(response.optBoolean("ok")) {
            response.optString("message", "The launcher payload proof challenge failed")
        }
        val nonce = response.getString("nonce")
        validateProofChallenge(response, keyId, nonce)
        return proofKeys.sign(
            nonce,
            LauncherProofKeyManager.modulePayloadCanonical(
                nonce = nonce,
                method = method,
                path = path,
                keyId = keyId,
                capabilityHash = hashedId(capability)
            )
        )
    }

    private fun validateProofChallenge(response: JSONObject, keyId: String, nonce: String) {
        val now = System.currentTimeMillis() / 1000L
        val expiresAt = response.getLong("expiresAt")
        require(response.optInt("proofVersion") == LauncherProofKeyManager.PROOF_VERSION &&
            response.optString("keyId") == keyId && nonce.matches(ID_PATTERN) &&
            expiresAt > now && expiresAt <= now + PROOF_NONCE_TTL_SECONDS + CLOCK_SKEW_SECONDS) {
            "The launcher proof challenge is invalid"
        }
    }

    private fun accessRequest(digitalKey: String): JSONObject = JSONObject()
        .put("digitalKey", digitalKey)
        .put("installationId", installationId)
        .put("deviceId", deviceId)
        .put("flavor", BuildConfig.FLAVOR)
        .put("accessVersion", activeAccessVersion())

    fun redeem(uri: Uri): LauncherLease {
        require(uri.scheme.equals("moodtools-launcher", true) && uri.host.equals("unlock", true))
        val token = uri.getQueryParameter("token").orEmpty()
        val challenge = uri.getQueryParameter("challenge").orEmpty()
        val expectedChallenge = preferences.getString(PENDING_CHALLENGE, null)
        val proofIdentity = proofKeys.identity()
        require(token.matches(ID_PATTERN) && challenge.matches(ID_PATTERN))
        require(challenge == expectedChallenge && System.currentTimeMillis() <= preferences.getLong(PENDING_EXPIRES, 0L)) {
            "The launcher unlock return expired"
        }
        val redemptionProof = proofKeys.sign(
            token,
            LauncherProofKeyManager.recoveryBindingCanonical(
                nonce = token,
                installationId = installationId,
                deviceId = deviceId,
                recoveryId = recoveryId,
                flavor = BuildConfig.FLAVOR,
                accessVersion = ACCESS_VERSION,
                keyId = proofIdentity.keyId
            )
        )
        val request = JSONObject()
            .put("token", token)
            .put("installationId", installationId)
            .put("deviceId", deviceId)
            .put("recoveryId", recoveryId)
            .put("challenge", challenge)
            .put("flavor", BuildConfig.FLAVOR)
            .put("accessVersion", ACCESS_VERSION)
            .put("proofVersion", LauncherProofKeyManager.PROOF_VERSION)
            .put("proofKeyId", proofIdentity.keyId)
            .put("publicKey", proofIdentity.publicKey)
            .put("proof", redemptionProof.toJson())
        val response = postJson("$BASE_URL/api/launcher/redeem", request)
        require(response.optBoolean("ok")) { response.optString("message", "Digital key redemption failed") }
        require(response.optBoolean("recoveryBound") &&
            response.optString("proofKeyId") == proofIdentity.keyId) {
            "The digital key was not durably bound to this device"
        }
        val digitalKey = response.getString("digitalKey")
        val issuedAt = response.getLong("issuedAt")
        val expiresAt = response.getLong("expiresAt")
        require(digitalKey.length in 80..4096 && validManagedAccessWindow(issuedAt, expiresAt))
        val offlineLease = response.getJSONObject("offlineLease").also {
            val claims = LauncherOfflineLeaseVerifier.verify(
                envelope = it,
                digitalKey = digitalKey,
                expectedDeviceId = deviceId,
                expectedFlavor = BuildConfig.FLAVOR,
                expectedProofKeyId = proofIdentity.keyId
            )
            require(claims.issuedAt == issuedAt && claims.expiresAt == expiresAt)
        }.toString()
        saveLease(digitalKey, issuedAt, expiresAt, ACCESS_VERSION, offlineLease)
        check(preferences.edit()
            .putString(RECOVERY_BOUND_KEY, proofIdentity.keyId)
            .remove(PENDING_CHALLENGE)
            .remove(PENDING_EXPIRES)
            .commit()) { "The launcher access state could not be saved" }
        return LauncherLease(issuedAt, expiresAt)
    }

    private fun saveLease(
        digitalKey: String,
        issuedAt: Long,
        expiresAt: Long,
        accessVersion: Int,
        offlineLease: String?
    ) {
        check(preferences.edit()
            .putString(DIGITAL_KEY, digitalKey)
            .putLong(ISSUED_AT, issuedAt)
            .putLong(EXPIRES_AT, expiresAt)
            .putInt(LEASE_ACCESS_VERSION, accessVersion)
            .apply {
                if (offlineLease == null) remove(OFFLINE_LEASE) else putString(OFFLINE_LEASE, offlineLease)
            }
            .putLong(LAST_SEEN, System.currentTimeMillis() / 1000L)
            .putLong(LAST_ELAPSED_REALTIME, SystemClock.elapsedRealtime())
            .commit()) { "The launcher lease could not be saved" }
    }

    private fun activeAccessVersion(): Int {
        val stored = preferences.getInt(LEASE_ACCESS_VERSION, 0)
        return if (stored == ACCESS_VERSION || stored == DEVICE_LOCK_ACCESS_VERSION) stored
        else ACCESS_VERSION
    }

    private fun rememberLeaseClock(now: Long) {
        preferences.edit()
            .putLong(LAST_SEEN, maxOf(now, preferences.getLong(LAST_SEEN, 0L)))
            .putLong(LAST_ELAPSED_REALTIME, SystemClock.elapsedRealtime())
            .apply()
    }

    fun clearLease() {
        val editor = preferences.edit()
            .remove(DIGITAL_KEY)
            .remove(ISSUED_AT)
            .remove(EXPIRES_AT)
            .remove(LEASE_ACCESS_VERSION)
            .remove(OFFLINE_LEASE)
            .remove(LAST_SEEN)
            .remove(LAST_ELAPSED_REALTIME)
        preferences.all.keys
            .filter { it.startsWith(PRIVATE_LEASE_PREFIX) }
            .forEach(editor::remove)
        editor.apply()
    }

    private fun postJson(
        address: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap()
    ): JSONObject {
        val url = URL(address)
        require(url.protocol == "https" && url.host == HOST)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            JSONObject(stream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun randomId(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun hashedId(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    companion object {
        private const val BASE_URL = ModuleCatalogClient.BASE_URL
        private const val HOST = "jester.moodtools.workers.dev"
        private const val PREFERENCES = "jester_moods_launcher_access"
        private const val INSTALLATION_ID = "installation_id"
        private const val DIGITAL_KEY = "digital_key"
        private const val ISSUED_AT = "issued_at"
        private const val EXPIRES_AT = "expires_at"
        private const val LEASE_ACCESS_VERSION = "lease_access_version"
        private const val OFFLINE_LEASE = "offline_lease"
        private const val LAST_SEEN = "last_seen"
        private const val LAST_ELAPSED_REALTIME = "last_elapsed_realtime"
        private const val PENDING_CHALLENGE = "pending_challenge"
        private const val PENDING_EXPIRES = "pending_expires"
        private const val RECOVERY_BOUND_KEY = "recovery_bound_key"
        private const val PRIVATE_LEASE_PREFIX = "private_lease_"
        private const val FLOW_TTL_MS = 20L * 60L * 1000L
        private const val ACCESS_VERSION = 4
        private const val DEVICE_LOCK_ACCESS_VERSION = 3
        private const val MAX_MANAGED_ACCESS_TTL_SECONDS = 10L * 365L * 24L * 60L * 60L
        private const val CLOCK_SKEW_SECONDS = 5L * 60L
        private const val MODULE_CAPABILITY_TTL_SECONDS = 10L * 60L
        private const val PROOF_NONCE_TTL_SECONDS = 2L * 60L
        private const val MAX_PRIVATE_CATALOGS = 256
        private const val DEVICE_ID_NAMESPACE = "jester-moods-launcher-device-v1"
        private const val RECOVERY_ID_NAMESPACE = "jester-moods-launcher-recovery-v1"
        private const val LEGACY_BROKEN_ANDROID_ID = "9774d56d682e549c"
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]{43}")
        private val PRIVATE_SCOPE_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,63}")
    }
}

private fun LauncherRequestProof.toJson(): JSONObject = JSONObject()
    .put("version", version)
    .put("keyId", keyId)
    .put("nonce", nonce)
    .put("signature", signature)
