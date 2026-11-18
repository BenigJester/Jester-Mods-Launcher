package com.moodtools.hub.networking

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class GameObbStoragePermissionTest {
    @Test fun `preservation access denial explains the block without suggesting a space problem`() {
        val denial = java.nio.file.AccessDeniedException("/Android/obb/com.example.game")
        for (error in listOf(denial, IllegalStateException("preserve failed", denial), SecurityException())) {
            val message = gameObbPreservationFailureMessage(error)
            assertTrue(message.contains("Android blocked access"))
            assertTrue(message.contains("Nothing was uninstalled"))
            assertTrue(message.contains("do not clear its data"))
            assertFalse(message.contains("free space"))
        }
        assertTrue(gameObbPreservationFailureMessage(java.io.IOException("disk full")).contains("free space"))
    }

    @Test fun `Android 12 and 12L request storage before reading OBB files`() {
        for (sdk in listOf(27, 29, 30, 31, 32)) {
            assertTrue(needsLegacyObbStoragePermission(sdk, false))
            assertFalse(needsLegacyObbStoragePermission(sdk, true))
        }
        for (sdk in listOf(33, 34, 35, 36)) {
            assertFalse(needsLegacyObbStoragePermission(sdk, false))
        }
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest)
        val permissions = document.getElementsByTagName("uses-permission")
        for (name in listOf("READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE")) {
            val entry = (0 until permissions.length).map { permissions.item(it) as org.w3c.dom.Element }
                .single { it.getAttributeNS("http://schemas.android.com/apk/res/android", "name") == "android.permission.$name" }
            assertEquals(LAST_LEGACY_OBB_STORAGE_API.toString(),
                entry.getAttributeNS("http://schemas.android.com/apk/res/android", "maxSdkVersion"))
        }
    }
}
