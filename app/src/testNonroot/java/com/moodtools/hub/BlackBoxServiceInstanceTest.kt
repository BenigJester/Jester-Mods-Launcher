package com.moodtools.hub

import java.io.File
import java.net.URLClassLoader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Executes the production argument-routing block, not Android's Binder or WebView runtime. */
class BlackBoxServiceInstanceTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun source() = listOf(File("."), File("..")).first {
        File(it, "settings.gradle").isFile
    }.resolve("third_party/BlackBox/Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IActivityManagerProxy.java").readText()

    @Test fun `only virtual proxy services lose isolated instance and external flags`() {
        val text = source()
        // Include the real routing predicate too, so removing its guard breaks this test.
        val block = text.substringAfter("if (proxyIntent != null && proxyIntent.getComponent() != null &&")
            .substringBefore("args[callingPackageIndex]")
        val flags = text.substringAfter("private static int getFlagsIndex(Object[] args) {")
            .substringBefore("@ProxyMethod(\"bindService\")")
        assertTrue(block.contains("args[6] = null"))
        val dir = temporary.newFolder()
        val java = File(dir, "Fixture.java").apply {
            writeText("""
                import java.lang.reflect.Method;
                public class Fixture {
                    static class Context { static final int BIND_EXTERNAL_SERVICE = 0x80000000; }
                    static class BlackBoxCore { static String getHostPkg() { return "launcher"; } }
                    static class MethodParameterUtils { static int toInt(Object o) { return ((Number)o).intValue(); } }
                    static class Component { String pkg; Component(String p) { pkg=p; } String getPackageName() { return pkg; } }
                    static class Intent { Component component; Intent(String p) { component=p==null?null:new Component(p); } Component getComponent() { return component; } }
                    public static void bindService() {}
                    public static void bindServiceInstance() {}
                    public static void bindIsolatedService() {}
                    static void route(Intent proxyIntent, Method method, Object[] args) {
                        if (proxyIntent != null && proxyIntent.getComponent() != null && $block
                    }
                    private static int getFlagsIndex(Object[] args) { $flags
                    static void check(boolean b) { if(!b) throw new AssertionError(); }
                    public static void run() throws Exception {
                        for(String name : new String[]{"bindServiceInstance", "bindIsolatedService"}) {
                            Method m=Fixture.class.getMethod(name);
                            for(Intent intent : new Intent[]{null,new Intent(null),new Intent("com.google.android.webview"),new Intent("com.android.webview"),new Intent("launcher")}) {
                                boolean virtual=intent!=null && intent.component!=null && intent.component.pkg.equals("launcher");
                                for(Number flag : new Number[]{Integer.valueOf(0x80000001),Long.valueOf(0x80000001L)}) {
                                    Object[] args={null,null,null,null,null,flag,"renderer-42","caller"};
                                    route(intent,m,args);
                                    check(virtual ? args[6]==null : "renderer-42".equals(args[6]));
                                    check(virtual ? ((Number)args[5]).intValue()==1 : args[5]==flag);
                                    check("caller".equals(args[7]));
                                }
                            }
                        }
                        Object[] normal={null,null,null,null,null,1,"caller"};
                        route(new Intent("launcher"),Fixture.class.getMethod("bindService"),normal);
                        check("caller".equals(normal[6]));
                    }
                }
            """.trimIndent())
        }
        val compiler = File(System.getProperty("java.home"), "bin/javac" +
            if (System.getProperty("os.name").orEmpty().startsWith("Windows")) ".exe" else "")
        val process = ProcessBuilder(compiler.path, "-d", dir.path, java.path).redirectErrorStream(true).start()
        val errors = process.inputStream.bufferedReader().readText()
        assertEquals(errors, 0, process.waitFor())
        URLClassLoader(arrayOf(dir.toURI().toURL()), null).use {
            it.loadClass("Fixture").getMethod("run").invoke(null)
        }
    }

    @Test fun `isolated hooks leave instance adjustment to the shared routing decision`() {
        val text = source()
        for (name in listOf("bindServiceInstance", "BindIsolatedService")) {
            val hook = text.substringAfter("public static class $name ").substringBefore("@ProxyMethod")
            assertTrue(hook.contains("return BindServiceCommon(who,method,args,7);"))
            assertFalse(hook.contains("args[6] = null"))
        }
    }
}
