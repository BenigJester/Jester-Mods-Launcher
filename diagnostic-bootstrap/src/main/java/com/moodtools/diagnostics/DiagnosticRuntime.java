package com.moodtools.diagnostics;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Process;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;
import top.niunaijun.blackbox.utils.SessionResourceRecorder;

/** Fixed stages and exception frames only. Never persist arbitrary exception messages or intents. */
public final class DiagnosticRuntime {
    public static final String CONFIG = "assets/moodtools/diagnostics.json";
    public static final String PROCESS = ":jester_diagnostics";
    public static final int LIMIT = 64 * 1024;
    private static File root, session;
    private static SessionResourceRecorder resources;
    private static boolean handlerInstalled;
    private static boolean identityRuntime;
    private static final Set<Application> applications = Collections.newSetFromMap(new IdentityHashMap<>());
    public static String processName() {
        if (android.os.Build.VERSION.SDK_INT >= 28) return Application.getProcessName();
        try (java.io.InputStream input = new java.io.FileInputStream("/proc/self/cmdline")) {
            byte[] bytes = new byte[256]; int count = input.read(bytes);
            return new String(bytes, 0, Math.max(0, count), StandardCharsets.UTF_8).split("\u0000", 2)[0];
        } catch (Exception ignored) { return ""; }
    }
    public static boolean isDiagnosticProcess() { return processName().endsWith(PROCESS); }
    public static JSONObject config(ApplicationInfo info) throws Exception {
        try (ZipFile zip = new ZipFile(info.sourceDir)) {
            java.util.zip.ZipEntry entry = zip.getEntry(CONFIG);
            if (entry == null || entry.getSize() > 8192) throw new IllegalStateException("Missing diagnostics configuration");
            try (java.io.InputStream input = zip.getInputStream(entry)) {
                byte[] bytes = readBounded(input, 8192);
                return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            }
        }
    }
    // BlackBox explicitly exempts /blackbox/ paths from guest IO redirection, including exact-package shells.
    public static File root(ApplicationInfo info) { return new File(info.dataDir, "blackbox/jester-diagnostics"); }
    public static File current(File directory) throws Exception {
        File pointer = new File(directory, "current");
        if (!pointer.isFile() || pointer.length() > 100) return null;
        String id = new String(Files.readAllBytes(pointer.toPath()), StandardCharsets.UTF_8);
        if (!id.matches("[0-9]+-[a-f0-9-]+")) return null;
        File result = new File(directory, id);
        return result.isDirectory() && !Files.isSymbolicLink(result.toPath()) ? result : null;
    }
    public static synchronized void start(ApplicationInfo info) {
        if (isDiagnosticProcess()) return;
        try {
            if (root == null) {
                identityRuntime = "identity_shell".equals(config(info).optString("method"));
                root = root(info);
            }
            File next = current(root);
            if (next == null || next.equals(session)) return;
            if (session != null && session.isDirectory()) new File(session, "stopped").createNewFile();
            session = next;
            boolean main = !identityRuntime && info.packageName.equals(processName());
            event("guest_attached main=" + main);
            resources = new SessionResourceRecorder();
            resources.start(session);
            if (!handlerInstalled) {
                Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                    failure("uncaught_java", error);
                    if (previous != null) previous.uncaughtException(thread, error);
                    else { Process.killProcess(Process.myPid()); System.exit(10); }
                });
                handlerInstalled = true;
            }
        } catch (Throwable ignored) { }
    }
    public static synchronized void attach(Application app) {
        if (isDiagnosticProcess() || !applications.add(app)) return;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            private void phase(Activity activity, String phase) {
                start(app.getApplicationInfo());
                event("activity_" + phase + " " + activity.getClass().getName());
            }
            public void onActivityCreated(Activity a, Bundle b) { phase(a, "created"); }
            public void onActivityStarted(Activity a) { phase(a, "started"); }
            public void onActivityResumed(Activity a) { phase(a, "resumed"); if (resources != null) resources.foreground(true); }
            public void onActivityPaused(Activity a) { phase(a, "paused"); if (resources != null) resources.foreground(false); }
            public void onActivityStopped(Activity a) { phase(a, "stopped"); }
            public void onActivityDestroyed(Activity a) { phase(a, "destroyed"); }
            public void onActivitySaveInstanceState(Activity a, Bundle b) { }
        });
    }
    public static synchronized void event(String event) {
        if (session == null || !session.isDirectory()) return;
        try {
            File file = new File(session, "process-" + Process.myPid() + ".txt");
            byte[] bytes = (System.currentTimeMillis() + " " + event.replace('\n', ' ').replace('\r', ' ') + "\n").getBytes(StandardCharsets.UTF_8);
            if (file.length() + bytes.length <= LIMIT) try (FileOutputStream output = new FileOutputStream(file, true)) { output.write(bytes); }
        } catch (Throwable ignored) { }
    }
    public static synchronized void failure(String stage, Throwable error) {
        event("failure " + stage + " " + error.getClass().getName());
        if (session == null || !session.isDirectory()) return;
        try { Files.write(new File(session, "failure-" + Process.myPid() + ".txt").toPath(), frames(error).getBytes(StandardCharsets.UTF_8)); }
        catch (Throwable ignored) { }
    }
    public static String frames(Throwable error) {
        StringBuilder text = new StringBuilder("Exception causes:\n");
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; error != null && depth < 16 && seen.add(error); depth++, error = error.getCause()) {
            text.append("cause ").append(error.getClass().getName()).append('\n');
            StackTraceElement[] frames = error.getStackTrace();
            for (int n = 0; n < Math.min(32, frames.length) && text.length() < 12000; n++)
                text.append("  at ").append(frames[n].getClassName()).append('.').append(frames[n].getMethodName()).append(':').append(frames[n].getLineNumber()).append('\n');
        }
        return text.substring(0, Math.min(text.length(), 16000));
    }
    public static byte[] readBounded(java.io.InputStream input, int limit) throws java.io.IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count; (count = input.read(buffer)) >= 0;) {
            if (output.size() + count > limit) throw new java.io.IOException("Diagnostic size limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
    /** Fixed categories only; never export the system description itself. */
    public static String exitHint(String description) {
        String text = description == null ? "" : description.substring(0, Math.min(description.length(), 4096)).toLowerCase(java.util.Locale.ROOT);
        if (text.contains("too many binders") || text.contains("excessive binder") || text.contains("binder proxy limit")) return "binder_limit_reported";
        if (text.contains("excessive cpu") || text.contains("excessive background cpu")) return "excessive_cpu_reported";
        if (text.startsWith("bg anr:")) return "background_anr_reported";
        if (text.contains("low memory")) return "low_memory_mentioned";
        if (text.contains("stop idle")) return "idle_stop_mentioned";
        return "unclassified_or_unavailable";
    }
    private DiagnosticRuntime() { }
}
