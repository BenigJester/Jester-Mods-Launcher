package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class DiagnosticSupportingResourcesTest {
    @Test fun `short report keeps latest two sampled sites without turning them into attempts`() {
        val samples = "Sampled connection callsites: context only\n\n" + (1..20).joinToString("\n\n") {
            "$it operation=bind call=${it * 256}\n  at caller.Worker.connect:$it\n".trimEnd()
        }
        val latest = latestConnectionSiteSamples(samples)
        assertTrue(latest.contains("19 operation=bind call=4864"))
        assertTrue(latest.contains("20 operation=bind call=5120"))
        assertTrue(latest.contains("sampledCaller: caller.Worker.connect:20"))
        assertFalse(latest.contains("18 operation="))
        assertEquals("", latestConnectionSiteSamples("not_observed"))
        val body = URLDecoder.decode(URI(diagnosticIssueLink("Diagnostics", "System description hint: binder_limit_reported\n" +
            latest + "\n" + "support-resources-123.txt: 99 binderLocal=4000\n".repeat(100)))
            .rawQuery.substringAfter("&body="), "UTF-8")
        assertTrue(body.contains("sampledCaller: caller.Worker.connect:20"))
    }

    private fun sample(pid: Int, time: Int, local: String, proxy: String = "100") =
        "support-resources-$pid.txt: $time binderLocal=$local binderProxy=$proxy " +
            "binderDeath=15 bbConnectionCalls=bind:25/0,unbind:20/0;lastFailure=none processRole=shared_helper"

    @Test fun `latest sample per process is selected before ranking and unknown values stay last`() {
        val newest = sample(3, 300, "4331")
        val lines = listOf(sample(1, 300, "20", "0"), newest, sample(3, 100, "10"),
            sample(2, 200, "100", "2528"), sample(4, 500, "-1", "-1"),
            sample(5, 100, "9999"), sample(5, 300, "30", "0"))
        val sorted = prioritizedSupportingResources(lines)
        assertEquals(newest, sorted.first())
        assertEquals(listOf(3, 2, 5, 1, 4), sorted.map {
            it.substringAfter("support-resources-").substringBefore('.').toInt()
        })
        assertEquals(5, sorted.size)
        assertTrue(sorted.all { it in lines })
        assertTrue(prioritizedSupportingResources(emptyList()).isEmpty())
    }

    @Test fun `malformed missing and overflowing counters do not outrank real samples`() {
        val valid = sample(1, 100, "1")
        val malformed = listOf(sample(2, 100, "99999999999999999999999", "-1"),
            sample(3, 100, "unavailable", "-1"), "support-resources-4.txt: 100 processRole=launcher")
        assertEquals(valid, prioritizedSupportingResources(malformed + valid).first())
    }

    @Test fun `busy helper survives GitHub shortening with complete counters`() {
        val operations = listOf("bind", "unbind", "receiver_add", "receiver_remove", "network_add", "network_remove",
            "audio_add", "audio_remove", "display_add", "display_remove", "cache_add", "cache_remove",
            "toast_add", "toast_remove", "window_add", "window_remove", "alarm_add", "alarm_remove")
        val busy = sample(23996, 300, "4331").replace("bind:25/0,unbind:20/0",
            operations.joinToString(",") { "$it:123/0" })
        val summary = "package: com.appsomniacs.da2\nlauncher: 4.5.1\n" +
            "System description hint: binder_limit_reported\n" +
            "--- HIGHEST SAMPLED HELPER BINDER COUNT (context only, not proof of cause) ---\n" + busy +
            "\n--- LAST RESOURCE SAMPLES ---\n" + sample(23947, 200, "137").repeat(100)
        val link = diagnosticIssueLink("Diagnostics", summary)
        val body = URLDecoder.decode(URI(link).rawQuery.substringAfter("&body="), "UTF-8")
        assertTrue(link.length < 6500)
        assertTrue(body.contains(busy + "\n"))
        assertFalse(body.contains("support-resources-23947")) // The single overlong tail line is omitted, not split.
    }
}
