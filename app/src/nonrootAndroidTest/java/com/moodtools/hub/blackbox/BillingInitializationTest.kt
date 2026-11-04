package com.moodtools.hub.blackbox

import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import top.niunaijun.blackbox.utils.FakeBillingCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BillingInitializationTest {
    @Test fun billing9InitializationReturnsCallbackAndPreservesNoPurchases() {
        val field = FakeBillingCompat::class.java.getDeclaredField("BILLING_BINDER")
        field.isAccessible = true
        val service = field.get(null) as IBinder
        val done = CountDownLatch(1)
        var response: Bundle? = null
        val callback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertEquals(1, code)
                assertEquals(IBinder.FLAG_ONEWAY, flags)
                data.enforceInterface("com.android.vending.billing.IInAppBillingInitializeCallback")
                assertEquals(1, data.readInt())
                response = Bundle.CREATOR.createFromParcel(data)
                assertEquals(0, data.dataAvail())
                done.countDown()
                return true
            }
        }
        val request = Parcel.obtain()
        try {
            request.writeInterfaceToken("com.android.vending.billing.IInAppBillingService")
            request.writeInt(25)
            request.writeString("com.raongames.growcastle")
            request.writeInt(1)
            Bundle().writeToParcel(request, 0)
            request.writeStrongBinder(callback)
            assertTrue(service.transact(2101, request, null, IBinder.FLAG_ONEWAY))
            assertTrue(done.await(5, TimeUnit.SECONDS))
            val initialized = requireNotNull(response)
            assertEquals(0, initialized.getInt("RESPONSE_CODE"))
            assertEquals(17, initialized.getInt("BILLING_API_VERSION_KEY"))
            assertFalse(initialized.containsKey("purchaseToken"))
            assertTrue(requireNotNull(initialized.getBundle("ENABLED_SUBSCRIPTION_CLIENT_ACTIONS_KEY")).isEmpty)
        } finally {
            request.recycle()
        }
        for ((code, expected) in listOf(3 to 1, 4 to 0, 902 to 8)) {
            val data = Parcel.obtain()
            val result = Parcel.obtain()
            try {
                data.writeInterfaceToken("com.android.vending.billing.IInAppBillingService")
                assertTrue(service.transact(code, data, result, 0))
                result.readException()
                assertEquals(1, result.readInt())
                val bundle = Bundle.CREATOR.createFromParcel(result)
                assertEquals(expected, bundle.getInt("RESPONSE_CODE"))
                if (code == 4) assertTrue(requireNotNull(bundle.getStringArrayList("INAPP_PURCHASE_DATA_LIST")).isEmpty())
            } finally {
                data.recycle(); result.recycle()
            }
        }
    }
}
