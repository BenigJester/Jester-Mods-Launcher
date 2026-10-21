package com.moodtools.hub

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Android Binder dispatch requires device testing; these guard the paired cleanup contracts. */
class BinderDelegateCleanupTest {
    private fun source(path: String): String = listOf(File(path), File("../$path")).first { it.isFile }.readText()
    private val base = "third_party/BlackBox/Bcore/src/main/java/top/niunaijun/blackbox/fake/"

    @Test fun `both delegate caches release death links and do not wrap their own wrappers`() {
        for (name in listOf("ServiceConnectionDelegate", "InnerReceiverDelegate")) {
            val text = source("${base}delegate/$name.java")
            assertTrue(text.contains("implements IBinder.DeathRecipient"))
            assertTrue(text.contains("public static synchronized"))
            assertTrue(text.contains("if (base instanceof $name)"))
            assertTrue(text.contains("if (iBinder instanceof $name)"))
            assertTrue(text.contains("linkToDeath(delegate, 0)"))
            val release = text.substringAfter("public void release()").substringBefore("@Override public void binderDied()")
            assertTrue(release.contains("synchronized ($name.class)"))
            assertTrue(release.contains("!= this) return"))
            assertTrue(release.indexOf(".remove(") < release.indexOf(".unlinkToDeath(this, 0)"))
            assertTrue(text.contains("binderDied() { release(); }"))
        }
    }

    @Test fun `unbind and unregister forward the registered wrapper then release after success`() {
        val text = source("${base}service/IActivityManagerProxy.java")
        val service = text.substringAfter("public static class UnbindService").substringBefore("@ProxyMethod")
        val receiver = text.substringAfter("public static class unregisterReceiver").substringBefore("@ProxyMethod")
        for (hook in listOf(service, receiver)) {
            assertTrue(hook.contains("getDelegate("))
            assertTrue(hook.indexOf("args[0] = delegate") < hook.indexOf("Object result = method.invoke(who, args)"))
            assertTrue(hook.indexOf("Object result = method.invoke(who, args)") < hook.indexOf("delegate.release()"))
            assertFalse(hook.contains("finally")) // Failed system calls must preserve the mapping for retry.
        }
        assertTrue(service.contains("Boolean.TRUE.equals(result)"))
        assertTrue(service.contains("delegate.getOriginalBinder()"))
        val bind = text.substringAfter("public static Object BindServiceCommon").substringBefore("private static boolean isGoogleServiceIntent")
        assertTrue(bind.contains("((ServiceConnectionDelegate) connection).getOriginalBinder()"))
    }

    @Test fun `service manager releases stored death notification on unbind and death`() {
        val text = source("third_party/BlackBox/Bcore/src/main/java/top/niunaijun/blackbox/core/system/am/ActiveServices.java")
        assertTrue(text.contains("binder.linkToDeath(connectedService.mDeathRecipient, 0)"))
        assertTrue(text.contains("binderDied() { unbindService(binder, userId); }"))
        val unbind = text.substringAfter("public void unbindService(").substringBefore("public void stopServiceToken(")
        assertTrue(unbind.contains("synchronized (mRunningServiceRecords)"))
        assertTrue(unbind.contains("mConnectedServices.remove(binder)"))
        assertTrue(unbind.contains("if (connectedService == null) return"))
        assertTrue(unbind.contains("binder.unlinkToDeath(connectedService.mDeathRecipient, 0)"))
        assertFalse(unbind.contains("getOrCreateRunningServiceRecord"))
    }
}
