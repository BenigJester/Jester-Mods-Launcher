package com.moodtools.hub

import java.io.File
import java.net.URLClassLoader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Executes the production registration hook and package-rewrite helper, not Android networking. */
class NetworkCallbackRegistrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `registration rewrites guest ownership and preserves real results and failures`() {
        val root = listOf(File("."), File("..")).first { File(it, "settings.gradle").isFile }
        val base = "third_party/BlackBox/Bcore/src/main/java/top/niunaijun/blackbox/"
        val text = root.resolve(base + "fake/service/IConnectivityManagerProxy.java").readText()
        val hook = "@ProxyMethods(" + text.substringAfter("@ProxyMethods(")
            .substringBefore("@ProxyMethod(\"getActiveNetworkInfoForUid\")")
        for (name in listOf("requestNetwork", "listenForNetwork", "registerNetworkCallback", "registerDefaultNetworkCallback")) {
            assertTrue(hook.contains("\"$name\""))
            assertFalse(text.contains("@ProxyMethod(\"$name\")"))
        }
        assertFalse(text.contains("createMockNetworkRequestResult"))
        val dir = temporary.newFolder()
        val packageName = "top.niunaijun.blackbox"
        val sources = mapOf(
            "$packageName.BlackBoxCore" to """public class BlackBoxCore {
                public static BlackBoxCore get() { return new BlackBoxCore(); }
                public boolean isInstalled(String name,int user) { return name.equals("guest.game"); }
                public boolean isBlackProcess() { return true; }
                public static int getUserId() { return 0; }
                public static int getHostUserId() { return 0; }
                public static String getHostPkg() { return "launcher"; }
                public static int getBUid() { return 10001; }
                public static int getHostUid() { return 10002; }
            }""",
            "$packageName.app.BActivityThread" to "public class BActivityThread {}",
            "$packageName.fake.service.RegistrationFixture" to """
                import java.lang.reflect.*;
                import top.niunaijun.blackbox.fake.hook.*;
                import top.niunaijun.blackbox.utils.MethodParameterUtils;
                public class RegistrationFixture {
                    $hook
                    public static class Service {
                        int calls; Object result=new Object(); RuntimeException failure;
                        public Object register(Object token,String pkg,String tag,int flags) {
                            ++calls;
                            if(!pkg.equals("launcher") && !pkg.equals("external")) throw new SecurityException("wrong owner");
                            if(failure!=null) throw failure;
                            return result;
                        }
                        public Object legacy(Object token,int flags) { ++calls; return result; }
                        public Object noArgs() { ++calls; return result; }
                    }
                    static void check(boolean b) { if(!b) throw new AssertionError(); }
                    public static void run() throws Throwable {
                        RequestNetwork hook=new RequestNetwork(); Service service=new Service();
                        Method register=Service.class.getMethod("register",Object.class,String.class,String.class,int.class);
                        Object token=new Object();
                        for(String owner : new String[]{"guest.game","launcher","external"}) {
                            Object[] args={token,owner,"attribution",42};
                            check(hook.hook(service,register,args)==service.result);
                            check(args[0]==token && args[2].equals("attribution") && args[3].equals(42));
                            check(args[1].equals(owner.equals("guest.game")?"launcher":owner));
                        }
                        check(service.calls==3);
                        service.result=null;
                        check(hook.hook(service,register,new Object[]{token,"guest.game",null,1})==null);
                        for(RuntimeException error : new RuntimeException[]{new SecurityException(),new IllegalArgumentException(),new IllegalStateException()}) {
                            service.failure=error; int calls=service.calls;
                            try { hook.hook(service,register,new Object[]{token,"guest.game",null,1}); throw new AssertionError(); }
                            catch(RuntimeException observed) { check(observed==error && service.calls==calls+1); }
                        }
                        service.failure=null; service.result=new Object();
                        check(hook.hook(service,Service.class.getMethod("legacy",Object.class,int.class),new Object[]{token,12})==service.result);
                        check(hook.hook(service,Service.class.getMethod("noArgs"),null)==service.result);
                    }
                }
            """
        )
        val files = sources.map { (name, body) ->
            File(dir, name.replace('.', '/') + ".java").apply {
                requireNotNull(parentFile).mkdirs()
                writeText("package ${name.substringBeforeLast('.')};\n$body")
            }
        } + listOf("fake/hook/MethodHook.java", "fake/hook/ProxyMethods.java",
            "utils/MethodParameterUtils.java", "utils/BzArrayUtils.java").map { root.resolve(base + it) }
        val compiler = File(System.getProperty("java.home"), "bin/javac" +
            if (System.getProperty("os.name").orEmpty().startsWith("Windows")) ".exe" else "")
        val process = ProcessBuilder(listOf(compiler.path, "-d", dir.path) + files.map(File::getPath))
            .redirectErrorStream(true).start()
        val errors = process.inputStream.bufferedReader().readText()
        assertEquals(errors, 0, process.waitFor())
        URLClassLoader(arrayOf(dir.toURI().toURL()), null).use {
            it.loadClass("$packageName.fake.service.RegistrationFixture").getMethod("run").invoke(null)
        }
    }
}
