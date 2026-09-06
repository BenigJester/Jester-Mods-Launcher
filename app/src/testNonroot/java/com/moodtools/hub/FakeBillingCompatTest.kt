package com.moodtools.hub

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.niunaijun.blackbox.utils.FakeBillingCatalog

class FakeBillingCompatTest {
    @Test
    fun `billing compatibility is not gated by module configuration`() {
        val config = JSONObject(
            File("../modules/com.ChillyRoom.DungeonShooter/config.json")
                .readText(Charsets.UTF_8)
        )

        assertEquals("com.ChillyRoom.DungeonShooter", config.getString("package_name"))
        assertFalse(config.has("fake_iap_compatibility"))
    }

    @Test
    fun `billing fallback does not depend on virtual Play Store resolution`() {
        val source = File(
            "../third_party/BlackBox/Bcore/src/main/java/" +
                "top/niunaijun/blackbox/utils/FakeBillingCompat.java"
        ).readText(Charsets.UTF_8)

        assertTrue(source.contains("shouldUseFallback(Intent intent, String packageName)"))
        assertFalse(source.contains("virtualBillingResolved"))
    }

    @Test
    fun `billing callback uses Android version compatible dispatcher`() {
        val source = File(
            "../third_party/BlackBox/Bcore/src/main/java/" +
                "top/niunaijun/blackbox/utils/FakeBillingCompat.java"
        ).readText(Charsets.UTF_8)

        assertTrue(source.contains("ServiceConnectionDelegate.dispatchConnected("))
        assertFalse(source.contains("connection.connected("))
        assertTrue(source.contains("catch (Throwable error)"))

        val dispatcherSource = File(
            "../third_party/BlackBox/Bcore/src/main/java/" +
                "top/niunaijun/blackbox/fake/delegate/ServiceConnectionDelegate.java"
        ).readText(Charsets.UTF_8)
        assertTrue(dispatcherSource.contains("IServiceConnection.class.getMethod("))
        assertTrue(dispatcherSource.contains("(android.app.IBinderSession) null"))
    }

    @Test
    fun `modern fallback preserves identity without inventing storefront data`() {
        val json = JSONObject(
            FakeBillingCatalog.productDetailsJson("com.example.game", "coins_100", "inapp")
        )

        assertEquals("coins_100", json.getString("productId"))
        assertEquals("inapp", json.getString("type"))
        assertEquals("", json
            .getJSONObject("oneTimePurchaseOfferDetails")
            .getString("formattedPrice"))
        assertFalse(json.has("title"))
        assertFalse(json.has("description"))
        assertFalse(json.has("purchaseToken"))
        assertFalse(json.has("orderId"))
    }

    @Test
    fun `legacy fallback preserves identity without inventing storefront data`() {
        val json = JSONObject(
            FakeBillingCatalog.legacyProductJson("com.example.game", "monthly", "subs")
        )

        assertEquals("monthly", json.getString("productId"))
        assertEquals("subs", json.getString("type"))
        assertEquals("", json.getString("price"))
        assertEquals("", json.getString("price_currency_code"))
        assertEquals("P1M", json.getString("subscriptionPeriod"))
        assertFalse(json.has("purchaseToken"))
        assertFalse(json.has("purchaseState"))
    }

}
