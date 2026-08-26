package com.moodtools.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootInjectorArchitectureTest {
    @Test
    fun selectsNativeInjectorForArmAndPcEmulatorKernels() {
        assertEquals(
            "root-runtime/arm64-v8a/moodtools-injector",
            rootInjectorAssetForKernel("aarch64\n")
        )
        assertEquals(
            "root-runtime/x86_64/moodtools-injector",
            rootInjectorAssetForKernel("x86_64\n")
        )
        assertEquals(
            "root-runtime/x86_64/moodtools-injector",
            rootInjectorAssetForKernel("AMD64")
        )
        assertNull(rootInjectorAssetForKernel("i686"))
        assertNull(rootInjectorAssetForKernel(""))
    }
}
