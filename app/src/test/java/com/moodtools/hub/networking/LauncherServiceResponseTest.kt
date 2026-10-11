package com.moodtools.hub.networking

import org.junit.Assert.*
import org.junit.Test

class LauncherServiceResponseTest {
    @Test fun acceptsJsonAndPreservesStructuredServiceDenials() {
        assertTrue(parseLauncherServiceResponse(200, "application/json", "\uFEFF {\"ok\":true}").getBoolean("ok"))
        val denied = parseLauncherServiceResponse(403, "application/json", "{\"ok\":false,\"code\":\"ACCESS_REQUIRED\"}")
        assertEquals("ACCESS_REQUIRED", denied.getString("code"))
    }

    @Test fun rejectsHtmlEmptyMalformedAndOversizedResponsesWithoutLeakingTheirBody() {
        for (body in listOf("<!doctype html>private-token", "", "[]", "{\"secret\":\"private-token\",", "x".repeat(MAX_LAUNCHER_RESPONSE_CHARS + 1))) {
            val error = assertThrows(LauncherServiceException::class.java) {
                parseLauncherServiceResponse(502, null, body)
            }
            assertEquals("UNEXPECTED_SERVICE_RESPONSE", error.code)
            assertTrue(error.message!!.contains("HTTP 502"))
            assertFalse(error.message!!.contains("private-token"))
            assertNull(error.cause)
        }
        assertThrows(LauncherServiceException::class.java) {
            parseLauncherServiceResponse(200, "text/html; charset=utf-8", "{\"ok\":true}")
        }
    }

    @Test fun failedHttpCannotClaimSuccess() {
        for (body in listOf("{\"ok\":true}", "{}")) {
            val error = assertThrows(LauncherServiceException::class.java) {
                parseLauncherServiceResponse(503, "application/json", body)
            }
            assertEquals("SERVICE_HTTP_ERROR", error.code)
        }
    }
}
