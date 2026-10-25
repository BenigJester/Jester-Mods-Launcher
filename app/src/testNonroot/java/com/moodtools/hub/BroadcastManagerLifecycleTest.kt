package com.moodtools.hub

import java.io.File
import java.net.URLClassLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Executes the production manager against isolated registration/ownership fakes, not Android Binder. */
class BroadcastManagerLifecycleTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `package refreshes release receivers and retain failed cleanup for retry`() {
        val root = listOf(File("."), File("..")).first { File(it, "settings.gradle").isFile }
        val directory = temporary.newFolder()
        val base = "top.niunaijun.blackbox"
        val sources = linkedMapOf(
            "android.content.BroadcastReceiver" to "public class BroadcastReceiver {}",
            "android.content.Context" to """public class Context {
                public static final int RECEIVER_EXPORTED = 2;
                public final java.util.Set<BroadcastReceiver> active = new java.util.HashSet<>();
                public int calls, failRegisterAt = -1;
                public boolean failUnregister;
                public void registerReceiver(BroadcastReceiver r, Object f) { registerReceiver(r, f, 2); }
                public void registerReceiver(BroadcastReceiver r, Object f, int flags) {
                    if (++calls == failRegisterAt) throw new SecurityException("fixture registration failure");
                    if (!active.add(r)) throw new AssertionError("duplicate receiver");
                }
                public void unregisterReceiver(BroadcastReceiver r) {
                    if (failUnregister) { failUnregister = false; throw new SecurityException("fixture cleanup failure"); }
                    if (!active.remove(r)) throw new IllegalArgumentException("already gone");
                }
            }""",
            "android.os.Build" to "public class Build { public static class VERSION { public static int SDK_INT = 36; } public static class VERSION_CODES { public static final int O = 26; } }",
            "android.os.Looper" to "public class Looper { public static Looper getMainLooper() { return new Looper(); } }",
            "android.os.Message" to "public class Message { public int what; public Object obj; public static Message obtain(Handler h, int w, Object o) { Message m = new Message(); m.what=w; m.obj=o; return m; } }",
            "android.os.Handler" to "public class Handler { public Handler(Looper l) {} public void handleMessage(Message m) {} public void sendMessageDelayed(Message m, int t) {} public void removeMessages(int w, Object o) {} }",
            "$base.BlackBoxCore" to "public class BlackBoxCore { private static final android.content.Context context = new android.content.Context(); public static android.content.Context getContext() { return context; } }",
            "$base.utils.Slog" to "public class Slog { public static void d(String tag, String message) {} }",
            "$base.proxy.ProxyBroadcastReceiver" to "public class ProxyBroadcastReceiver extends android.content.BroadcastReceiver {}",
            "$base.entity.am.PendingResultData" to "public class PendingResultData { public String mBToken; public PendingResultData build() { return this; } public void finish() {} }",
            "$base.core.system.am.BActivityManagerService" to "public class BActivityManagerService {}",
            "$base.core.system.pm.BPackage" to """public class BPackage {
                public String packageName;
                public java.util.List<Activity> receivers = new java.util.ArrayList<>();
                public static class Activity { public java.util.List<ActivityIntentInfo> intents = new java.util.ArrayList<>(); }
                public static class ActivityIntentInfo { public Object intentFilter = new Object(); }
            }""",
            "$base.core.system.pm.BPackageSettings" to "public class BPackageSettings { public BPackage pkg; }",
            "$base.core.system.pm.BPackageManagerService" to """public class BPackageManagerService {
                public final java.util.Map<String,BPackageSettings> packages = new java.util.HashMap<>();
                public void addPackageMonitor(PackageMonitor monitor) {}
                public java.util.List<BPackageSettings> getBPackageSettings() { return new java.util.ArrayList<>(packages.values()); }
                public BPackageSettings getBPackageSetting(String name) { return packages.get(name); }
            }""",
            "$base.utils.ConnectionDiagnostics" to """public class ConnectionDiagnostics {
                public static int tracked = -1;
                public static void trackedReceiverCount(int count) { tracked = count; }
            }""",
            "fixture.BroadcastLifecycleFixture" to """import top.niunaijun.blackbox.BlackBoxCore;
                import top.niunaijun.blackbox.core.system.am.*;
                import top.niunaijun.blackbox.core.system.pm.*;
                public class BroadcastLifecycleFixture {
                    static void count(int n) {
                        if (BlackBoxCore.getContext().active.size() != n) throw new AssertionError("active=" + BlackBoxCore.getContext().active.size() + "; expected=" + n);
                        if (top.niunaijun.blackbox.utils.ConnectionDiagnostics.tracked != n) throw new AssertionError("diagnostic receiver count mismatch");
                    }
                    static void put(BPackageManagerService pm, String name, int filters) {
                        BPackageSettings s = new BPackageSettings(); s.pkg = new BPackage(); s.pkg.packageName = name;
                        BPackage.Activity r = new BPackage.Activity();
                        for (int i=0; i<filters; i++) r.intents.add(new BPackage.ActivityIntentInfo());
                        s.pkg.receivers.add(r); pm.packages.put(name, s);
                    }
                    public static void run() {
                        BPackageManagerService pm = new BPackageManagerService();
                        BroadcastManager manager = new BroadcastManager(new BActivityManagerService(), pm);
                        android.content.Context context = BlackBoxCore.getContext();
                        put(pm, "game", 3); put(pm, "other", 2); manager.startup(); count(5);
                        for (int i=0; i<1000; i++) { manager.onPackageInstalled("game", i % 2); count(5); }
                        context.failUnregister = true;
                        try { manager.onPackageInstalled("game", 0); throw new AssertionError("must retain failed cleanup"); } catch (SecurityException expected) {}
                        count(5); manager.onPackageInstalled("game", 0); count(5);
                        context.failRegisterAt = context.calls + 2;
                        try { manager.onPackageInstalled("game", 0); throw new AssertionError("expected partial registration"); } catch (SecurityException expected) {}
                        count(3); manager.onPackageInstalled("game", 0); count(5);
                        put(pm, "game", 1); manager.onPackageInstalled("game", 0); count(3);
                        manager.onPackageUninstalled("game", false, 0); count(3);
                        manager.onPackageUninstalled("game", true, 0); count(2);
                        manager.onPackageUninstalled("game", true, 0); count(2);
                        context.active.clear(); manager.onPackageUninstalled("other", true, 0); count(0);
                        put(pm, "game", 2); manager.onPackageInstalled("game", 0); count(2);
                        pm.packages.remove("game"); manager.onPackageInstalled("game", 0); count(0);
                        android.os.Build.VERSION.SDK_INT = 25;
                        put(pm, "game", 2); manager.onPackageInstalled("game", 0); count(2);
                        manager.onPackageInstalled("game", 0); count(2);
                        manager.onPackageUninstalled("game", true, 0); count(0);
                    }
                }"""
        )
        val files = sources.map { (name, source) ->
            File(directory, name.replace('.', '/') + ".java").apply {
                parentFile.mkdirs()
                writeText("package ${name.substringBeforeLast('.')};\n$source")
            }
        }.toMutableList()
        for (name in listOf("BroadcastManager", "../pm/PackageMonitor")) {
            val source = File(root, "third_party/BlackBox/Bcore/src/main/java/top/niunaijun/blackbox/core/system/am/$name.java")
            files += source
        }
        val classes = File(directory, "classes").apply { mkdirs() }
        val compiler = File(System.getProperty("java.home"), "bin/javac" +
            if (System.getProperty("os.name").startsWith("Windows")) ".exe" else "")
        val process = ProcessBuilder(listOf(compiler.path, "-d", classes.path) + files.map(File::getPath))
            .redirectErrorStream(true).start()
        val errors = process.inputStream.bufferedReader().readText()
        assertEquals(errors, 0, process.waitFor())
        URLClassLoader(arrayOf(classes.toURI().toURL()), null).use { loader ->
            loader.loadClass("fixture.BroadcastLifecycleFixture").getMethod("run").invoke(null)
        }
    }
}
