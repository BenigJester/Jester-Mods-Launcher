package com.moodtools.hub

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.niunaijun.blackbox.core.env.FacebookLoginCompat

class FacebookLoginCompatTest {
    @Test
    fun `only numeric Facebook app ID authorize callbacks trigger fallback`() {
        assertTrue(FacebookLoginCompat.isAppIdCustomTabCallback("fb314571929296102", "authorize"))
        assertTrue(FacebookLoginCompat.isAppIdCustomTabCallback("fb123", "authorize"))
        for (scheme in listOf(null, "fb", "fbconnect", "https", "doodlearmy2", "fb123evil", "FB123")) {
            assertFalse(FacebookLoginCompat.isAppIdCustomTabCallback(scheme, "authorize"))
        }
        for (authority in listOf(null, "", "evil", "authorize:80", "user@authorize", "%61uthorize")) {
            assertFalse(FacebookLoginCompat.isAppIdCustomTabCallback("fb123", authority))
        }
    }

    @Test
    fun `fallback requires both guest SDK activities and cannot leak to host query`() {
        val source = File("../third_party/BlackBox/Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IPackageManagerProxy.java").readText()
        val helper = source.substringAfter("private static boolean shouldPreferFacebookPackageCallback(Intent intent)")
            .substringBefore("private static boolean shouldHideNativeFacebookForGuest(ComponentName")
        assertTrue(helper.contains("intent.getComponent() != null"))
        assertTrue(helper.contains("intent.getSelector() != null"))
        assertTrue(helper.contains("hasGuestFacebookCallback(appIdProbe, caller, userId)"))
        assertTrue(helper.contains("hasGuestFacebookCallback(packageProbe, caller, userId)"))
        assertTrue(helper.contains("queryIntentActivities(probe, PackageManager.GET_RESOLVED_FILTER, null, userId)"))
        assertTrue(helper.contains("if (!FacebookLoginCompat.isGuestCustomTabActivity(activity, caller)) return false;"))
        assertTrue(helper.contains("!caller.equals(intent.getPackage())"))
        val query = source.substringAfter("public static class QueryIntentActivities")
            .substringBefore("@ProxyMethod(\"queryIntentReceivers\")")
        assertTrue(query.indexOf("shouldPreferFacebookPackageCallback(intent)") < query.indexOf("method.invoke(who, args)"))
        assertTrue(query.contains("return BuildCompat.isN() ? ParceledListSliceCompat.create(empty) : empty;"))
        assertTrue(source.contains("shouldHideNativeFacebookForGuest(intent) || shouldPreferFacebookPackageCallback(intent)"))
        val manifest = File("src/nonroot/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:scheme=\"fbconnect\" android:host=\"*\""))
    }
}
