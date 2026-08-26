package com.moodtools.hub.modules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceArchitectureGuardTest {
    @Test
    fun `accepts add-on when Android supports one of its architectures`() {
        assertTrue(
            isDeviceArchitectureSupported(
                addOnAbis = setOf("arm64-v8a"),
                deviceAbis = setOf("arm64-v8a", "armeabi-v7a")
            )
        )
    }

    @Test
    fun `rejects add-on when Android supports none of its architectures`() {
        assertFalse(
            isDeviceArchitectureSupported(
                addOnAbis = setOf("arm64-v8a"),
                deviceAbis = setOf("armeabi-v7a")
            )
        )
    }

    @Test
    fun `rejects empty architecture declarations`() {
        assertFalse(isDeviceArchitectureSupported(emptySet(), setOf("arm64-v8a")))
        assertFalse(isDeviceArchitectureSupported(setOf("arm64-v8a"), emptySet()))
    }
}
