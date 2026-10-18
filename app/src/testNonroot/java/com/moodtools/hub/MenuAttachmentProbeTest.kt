package com.moodtools.hub

import org.junit.Assert.*
import org.junit.Test

class MenuAttachmentProbeTest {
    class Menu(private val attached: Boolean) {
        fun isOverlayAttached() = attached
    }
    class Entry { companion object { @JvmField var directMenu: Menu? = null } }
    class Legacy
    class UnknownMenu
    class UnknownEntry { companion object { @JvmField var directMenu = UnknownMenu() } }
    class BrokenMenu { fun isOverlayAttached(): Boolean = error("private account text") }
    class BrokenEntry { companion object { @JvmField var directMenu = BrokenMenu() } }

    @Test fun `existing module attachment is observed without modifying or starting it`() {
        Entry.directMenu = null
        assertEquals("not_attached", MenuAttachmentProbe.inspect(Entry::class.java))
        Entry.directMenu = Menu(false)
        assertEquals("not_attached", MenuAttachmentProbe.inspect(Entry::class.java))
        Entry.directMenu = Menu(true)
        val original = Entry.directMenu
        assertEquals("attached", MenuAttachmentProbe.inspect(Entry::class.java))
        assertSame(original, Entry.directMenu)
        assertEquals("unsupported", MenuAttachmentProbe.inspect(Legacy::class.java))
        assertEquals("unsupported", MenuAttachmentProbe.inspect(UnknownEntry::class.java))
        assertEquals("probe_error", MenuAttachmentProbe.inspect(BrokenEntry::class.java))
    }

    @Test fun `latest state supersedes missing window without overriding crashes or normal exits`() {
        assertEquals("attached", diagnosticMenuState("100 menu_window not_attached\n200 menu_window attached"))
        assertNull(diagnosticMenuState("100 menu_window private-data"))
        assertEquals("Menu window not attached", diagnosticStatusWithMenu("Game activity opened", "not_attached"))
        for (state in listOf(null, "attached", "checking", "unsupported", "probe_error")) {
            assertEquals("Game activity opened", diagnosticStatusWithMenu("Game activity opened", state))
        }
        for (status in listOf("Crash or startup error recorded", "Process exit recorded", "Session stopped", "Startup in progress")) {
            assertEquals(status, diagnosticStatusWithMenu(status, "not_attached"))
        }
        assertEquals(9, LauncherDiagnosticTranslations.entries.getValue("Menu window not attached").size)
    }
}
