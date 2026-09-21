package com.moodtools.hub.modules

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivateModuleExpiryTest {
    @Test
    fun onlyModulesWithAuthoritativelyDeniedScopesAreRemoved() {
        assertEquals(
            setOf("com.example.expired"),
            expiredPrivateModulePackages(
                installed = mapOf(
                    "com.example.expired" to "expired-scope",
                    "com.example.active" to "active-scope"
                ),
                deniedScopes = setOf("expired-scope")
            )
        )
    }
}
