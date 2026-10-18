package com.moodtools.hub

import android.app.Service
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ExternalControllerStartTest {
    @Test fun `controller accepts explicit launches but rejects Android recovery and empty requests`() {
        assertTrue(acceptsExternalControllerStart("com.dts.freefireth", 0))
        for (flags in listOf(Service.START_FLAG_REDELIVERY, Service.START_FLAG_RETRY,
            Service.START_FLAG_REDELIVERY or Service.START_FLAG_RETRY)) {
            assertFalse(acceptsExternalControllerStart("com.dts.freefireth", flags))
        }
        for (pkg in listOf(null, "", "   ")) assertFalse(acceptsExternalControllerStart(pkg, 0))
    }

    @Test fun `service rejects recovery before loading modules and never requests automatic restart`() {
        val path = "src/externalController/java/com/moodtools/hub/ExternalControllerService.kt"
        val source = listOf(File(path), File("app/$path")).first { it.isFile }.readText()
        val start = source.substringAfter("override fun onStartCommand(").substringBefore("override fun onDestroy()")
        assertTrue(start.indexOf("acceptsExternalControllerStart(packageName, flags)") < start.indexOf("pluginLoader."))
        assertTrue(start.substringBefore("val featureStatePath").contains("stopSelf(startId)"))
        assertFalse(source.contains("override fun onCreate()"))
        assertEquals(1, Regex("showControllerNotification\\(\\)").findAll(start).count())
        assertTrue(start.indexOf("showControllerNotification()") > start.indexOf("return START_NOT_STICKY"))
        assertTrue(start.indexOf("showControllerNotification()") < start.indexOf("pluginLoader."))
        assertFalse(source.contains("START_REDELIVER_INTENT"))
        assertEquals(3, Regex("return START_NOT_STICKY").findAll(start).count())
        assertFalse(source.contains("override fun onTaskRemoved"))
        for (entry in listOf("fun start(", "fun startBlackBox(", "fun startDirectPatch(")) assertTrue(source.contains(entry))
    }
}
