package com.moodtools.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NonRootBlackBoxRuntimeTest {
    @Test
    fun `rewrites a persisted Kairo external storage fallback`() {
        val xml = """<map><int name="_storage_path" value="0" /></map>"""

        assertEquals(
            """<map><int name="_storage_path" value="1" /></map>""",
            rewriteKairoStoragePreference(xml)
        )
    }

    @Test
    fun `leaves an internal storage preference unchanged`() {
        val xml = """<map><int name="_storage_path" value="1" /></map>"""

        assertNull(rewriteKairoStoragePreference(xml))
    }

    @Test
    fun `recognizes only the visible launcher task as launcher removal`() {
        val host = "com.moodtools.hub.nonroot"

        assertTrue(isLauncherTaskComponent(host, host, "$host.LauncherActivity"))
        assertFalse(
            isLauncherTaskComponent(
                host,
                host,
                "top.niunaijun.blackbox.proxy.ProxyActivity\$P0"
            )
        )
        assertFalse(isLauncherTaskComponent(host, "com.example.game", "$host.LauncherActivity"))
    }

    @Test
    fun `recognizes BlackBox proxy tasks without matching launcher task`() {
        val host = "com.moodtools.hub.nonroot"

        assertTrue(
            isVirtualGuestTaskComponent(
                host,
                host,
                "top.niunaijun.blackbox.proxy.ProxyActivity\$P7"
            )
        )
        assertTrue(
            isVirtualGuestTaskComponent(
                host,
                host,
                "top.niunaijun.blackbox.proxy.TransparentProxyActivity\$P2"
            )
        )
        assertFalse(isVirtualGuestTaskComponent(host, host, "$host.LauncherActivity"))
        assertFalse(
            isVirtualGuestTaskComponent(
                host,
                "com.someone.else",
                "top.niunaijun.blackbox.proxy.ProxyActivity\$P7"
            )
        )
    }
}
