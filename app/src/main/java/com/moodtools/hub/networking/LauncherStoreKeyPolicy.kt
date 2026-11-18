package com.moodtools.hub.networking

internal fun validStoreKeyEntitlement(
    product: String,
    scope: String?,
    addedDays: Int,
    entitlementExpiresAt: Long?,
    issuedAt: Long
): Boolean = when (scope) {
    null -> product == "launcher-only-30d" && addedDays == 30 && entitlementExpiresAt == null
    "free-fire" -> product == "launcher-free-fire-${addedDays}d" && addedDays in setOf(7, 15, 30) &&
        entitlementExpiresAt != null && entitlementExpiresAt > issuedAt
    else -> false
}
