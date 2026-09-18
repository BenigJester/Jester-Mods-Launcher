package com.moodtools.hub.blackbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import top.niunaijun.blackbox.proxy.record.ProxyServiceRecord

@RunWith(AndroidJUnit4::class)
class ProxyServiceRecordTest {
    @Test
    fun nullFrameworkRestartCreatesEmptyRecord() {
        val record = ProxyServiceRecord.create(null)

        assertNull(record.mServiceIntent)
        assertNull(record.mServiceInfo)
        assertNull(record.mToken)
        assertEquals(0, record.mUserId)
        assertEquals(0, record.mStartId)
    }
}
