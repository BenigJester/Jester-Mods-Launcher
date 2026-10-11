package com.moodtools.hub

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import top.niunaijun.blackbox.fake.service.IPermissionManagerProxy.CheckUidPermission
import top.niunaijun.blackbox.fake.service.IActivityManagerProxy.checkPermissionForDevice

class PermissionUidMappingTest {
    @Test
    fun `device-aware check maps only the UID never PID or device ID`() {
        for (uid in listOf(10007, 110007)) {
            val args = arrayOf<Any>("android.permission.INTERNET", 10007, uid, 10007)
            checkPermissionForDevice.remapUid(args, 110007, 10007, 10530)
            assertArrayEquals(arrayOf<Any>("android.permission.INTERNET", 10007, 10530, 10007), args)
        }
        for (uid in listOf(10530, 1000, 10999)) {
            val args = arrayOf<Any>("android.permission.CAMERA", 10007, uid, 10007)
            checkPermissionForDevice.remapUid(args, 110007, 10007, 10530)
            assertArrayEquals(arrayOf<Any>("android.permission.CAMERA", 10007, uid, 10007), args)
        }
        checkPermissionForDevice.remapUid(null, 10007, 10007, 10530)
        checkPermissionForDevice.remapUid(emptyArray(), 10007, 10007, 10530)
        val malformed = arrayOf<Any>("android.permission.INTERNET", 7791, "10007", 0)
        checkPermissionForDevice.remapUid(malformed, 10007, 10007, 10530)
        assertArrayEquals(arrayOf<Any>("android.permission.INTERNET", 7791, "10007", 0), malformed)
    }

    @Test
    fun `maps guest UID in modern and legacy signatures without altering permission or device`() {
        val modern = arrayOf<Any>(10007, "android.permission.INTERNET", 10007)
        CheckUidPermission.remapUid(modern, 10007, 10007, 10530)
        assertArrayEquals(arrayOf<Any>(10530, "android.permission.INTERNET", 10007), modern)

        val legacy = arrayOf<Any>("android.permission.CAMERA", 10007)
        CheckUidPermission.remapUid(legacy, 10007, 10007, 10530)
        assertArrayEquals(arrayOf<Any>("android.permission.CAMERA", 10530), legacy)
    }

    @Test
    fun `maps secondary virtual user UID and app ID`() {
        for (uid in listOf(110007, 10007)) {
            val args = arrayOf<Any>(uid, "android.permission.INTERNET", 0)
            CheckUidPermission.remapUid(args, 110007, 10007, 10530)
            assertArrayEquals(arrayOf<Any>(10530, "android.permission.INTERNET", 0), args)
        }
    }

    @Test
    fun `leaves host and unrelated UIDs unchanged even when device ID matches guest`() {
        for (uid in listOf(10530, 1000, 10999)) {
            val args = arrayOf<Any>(uid, "android.permission.INTERNET", 10007)
            CheckUidPermission.remapUid(args, 10007, 10007, 10530)
            assertArrayEquals(arrayOf<Any>(uid, "android.permission.INTERNET", 10007), args)
        }
        CheckUidPermission.remapUid(null, 10007, 10007, 10530)
        CheckUidPermission.remapUid(emptyArray(), 10007, 10007, 10530)
    }
}
