package com.moodtools.hub

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class DiagnosticIssueLinkTest {
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
        assertTrue(query.getValue("body").contains("Attach the saved"))
        assertFalse(query.getValue("body").contains("Full report not put in URL"))
    }

    @Test fun `diagnostic text cannot close markdown fences`() {
        val body = URLDecoder.decode(diagnosticIssueLink(report("```\nPC: 123\n```")), "UTF-8")
        assertFalse(body.contains("```"))
        assertTrue(body.contains("PC: 123"))
    }
}
