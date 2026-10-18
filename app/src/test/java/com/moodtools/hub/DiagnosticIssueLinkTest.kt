package com.moodtools.hub

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class DiagnosticIssueLinkTest {
    @Test fun `installer reports prefill details for each diagnostic type`() {
        for (kind in listOf("launcher update", "add-on transfer", "add-on update check", "companion installer", "package setup")) {
            val report = "Jester Mods $kind diagnostics\nPackage: com.test.game\nStage: Failed\nLauncher: 4.3.0\nEvent trail: HTTP 502"
            val link = diagnosticIssueLink("Jester Mods $kind diagnostics", report)
            val query = URI(link).rawQuery.split('&').associate {
                it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }
            assertEquals("Jester Mods $kind diagnostics", query.getValue("title"))
            assertTrue(query.getValue("body").contains(report))
        }
    }

    @Test fun `large installer reports and non-ascii titles stay within browser limit`() {
        val link = diagnosticIssueLink("診断".repeat(300), "Stage & details?\n".repeat(5000))
        assertTrue(link.length < 6500)
        assertEquals("github.com", URI(link).host)
    }

    private fun report(summary: String) = LaunchDiagnosticReport("123-abc", "com.test.game", "Game", 123,
        "Game crash recorded", true, false, "Full report not put in URL", summary)

    @Test fun `issue link targets public repository encodes summary and stays bounded`() {
        val link = diagnosticIssueLink(report("Crash & hook? #地址\n".repeat(1200)))
        val uri = URI(link)
        assertEquals("github.com", uri.host)
        assertEquals("/BenigJester/Jester-Mods-Launcher/issues/new", uri.path)
        assertTrue(link.length < 6500)
        val query = uri.rawQuery.split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        assertTrue(query.getValue("title").contains("com.test.game"))
        assertTrue(query.getValue("body").contains("Crash & hook? #地址"))
        assertTrue(query.getValue("body").contains("Optional extra details"))
        assertTrue(query.getValue("body").contains("you can still submit this issue"))
        assertFalse(query.getValue("body").contains("Full report not put in URL"))
    }

    @Test fun `diagnostic text cannot close markdown fences`() {
        val body = URLDecoder.decode(diagnosticIssueLink(report("```\nPC: 123\n```")), "UTF-8")
        assertFalse(body.contains("```"))
        assertTrue(body.contains("PC: 123"))
    }

    @Test fun `long crash stacks do not bury cause types or startup evidence in issue links`() {
        val summary = "package: com.test.game\nlauncher: 4.3.2\n" +
            "cause java.lang.SecurityException\n" +
            (1..48).joinToString("\n") { "  at com.google.android.gms.common.internal.BaseGmsClient.getRemoteService:$it" } +
            "\ncause java.lang.Error\n--- LAST STARTUP EVENTS ---\n" +
            "process-123.txt: 100 application_onCreate_completed\n" +
            "process-123.txt: 200 activity_direct-resumed game.Screen\n" +
            "process-123.txt: 300 failure uncaught_java java.lang.Error"
        val link = diagnosticIssueLink(report(summary))
        val body = URLDecoder.decode(URI(link).rawQuery.substringAfter("&body="), "UTF-8")
        assertTrue(link.length < 6500)
        for (expected in listOf("SecurityException", "cause java.lang.Error", "application_onCreate_completed",
            "activity_direct-resumed", "failure uncaught_java", "Optional extra details")) assertTrue(expected, body.contains(expected))
        assertFalse(body.contains("getRemoteService:48"))
    }
}
