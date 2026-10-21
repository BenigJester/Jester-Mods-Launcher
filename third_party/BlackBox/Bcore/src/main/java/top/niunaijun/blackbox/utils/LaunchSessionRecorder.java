package top.niunaijun.blackbox.utils;

import android.os.Process;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import top.niunaijun.blackbox.core.env.BEnvironment;

/** Structured diagnostics only: never collect guest text, URLs, account data or raw logcat. */
public final class LaunchSessionRecorder {
    public static final int MAX_EVENT_BYTES = 64 * 1024;
    private static File guestSession;
    private static String guestPackage;
    private static volatile boolean activityResumed;
    private static SessionResourceRecorder resources;

    public static File root() { return new File(BEnvironment.getSystemDir(), "launch-diagnostics"); }

    public static File packageDirectory(String pkg) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) throw new IllegalArgumentException("Invalid package");
        return new File(root(), pkg);
    }

    public static synchronized File begin(String pkg, String metadata) throws Exception {
        File parent = packageDirectory(pkg);
        File session = new File(parent, System.currentTimeMillis() + "-" + UUID.randomUUID());
        if (!session.mkdirs()) throw new java.io.IOException("Cannot create diagnostic session");
        Files.write(new File(session, "metadata.txt").toPath(), metadata.getBytes(StandardCharsets.UTF_8));
        AtomicFile pointer = new AtomicFile(new File(parent, "current"));
        FileOutputStream stream = pointer.startWrite();
        try {
            stream.write(session.getName().getBytes(StandardCharsets.UTF_8));
            pointer.finishWrite(stream);
        } catch (Throwable error) { pointer.failWrite(stream); throw error; }
        hostEvent(session, "launch_requested");
        SupportProcessRecorder.observeSession(session, SupportProcessRecorder.Role.launcher);
        return session;
    }

    /** A resume is not a new game session. Restore its pointer without changing guest pins. */
    public static synchronized void resume(File previous, File attempt) throws Exception {
        if (previous == null || attempt == null || !previous.isDirectory() || !attempt.isDirectory()
                || !previous.getCanonicalFile().getParentFile().equals(attempt.getCanonicalFile().getParentFile())) return;
        AtomicFile pointer = new AtomicFile(new File(previous.getParentFile(), "current"));
        FileOutputStream stream = pointer.startWrite();
        try {
            stream.write(previous.getName().getBytes(StandardCharsets.UTF_8));
            pointer.finishWrite(stream);
        } catch (Throwable error) { pointer.failWrite(stream); throw error; }
        Files.write(new File(attempt, "superseded").toPath(), new byte[0]);
        hostEvent(previous, "resuming_existing_session");
        SupportProcessRecorder.observeSession(previous, SupportProcessRecorder.Role.launcher);
    }

    public static File current(String pkg) {
        try {
            File parent = packageDirectory(pkg);
            String name = new String(new AtomicFile(new File(parent, "current")).readFully(), StandardCharsets.UTF_8);
            if (!name.matches("[0-9]+-[a-f0-9-]+")) return null;
            File session = new File(parent, name);
            return session.isDirectory() ? session : null;
        } catch (Throwable ignored) { return null; }
    }

    public static synchronized void attach(String pkg) {
        if (guestSession != null && pkg.equals(guestPackage)) return;
        guestPackage = pkg;
        guestSession = current(pkg); // Pin this process to its session; never contaminate a later launch.
        event("guest_attached main=" + pkg.equals(top.niunaijun.blackbox.app.BActivityThread.getAppProcessName()));
        final File session = guestSession;
        if (session != null && resources == null) {
            try {
                resources = new SessionResourceRecorder();
                resources.start(session);
            } catch (Throwable ignored) { }
        }
        if (session != null && pkg.equals(top.niunaijun.blackbox.app.BActivityThread.getAppProcessName())) {
            try {
                Thread watchdog = new Thread(() -> {
                    try {
                        Thread.sleep(60_000L);
                        if (activityResumed || !session.isDirectory() || new File(session, "stopped").exists()) return;
                        append(session, "process-" + Process.myPid() + ".txt", "startup_thread_snapshot_requested");
                        StringBuilder stacks = new StringBuilder();
                        for (java.util.Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                            if (stacks.length() >= MAX_EVENT_BYTES - 4096) break;
                            stacks.append("Thread ID ").append(entry.getKey().getId()).append(" state=")
                                    .append(entry.getKey().getState()).append('\n');
                            StackTraceElement[] frames = entry.getValue();
                            for (int i = 0; i < Math.min(48, frames.length); i++) {
                                stacks.append("  at ").append(frames[i].getClassName()).append('.')
                                        .append(frames[i].getMethodName()).append(':').append(frames[i].getLineNumber()).append('\n');
                            }
                        }
                        if (session.isDirectory()) Files.write(new File(session, "stacks-" + Process.myPid() + ".txt").toPath(),
                                stacks.substring(0, Math.min(stacks.length(), MAX_EVENT_BYTES)).getBytes(StandardCharsets.UTF_8));
                    } catch (Throwable ignored) { }
                }, "BlackBoxStartupDiagnostics");
                watchdog.setDaemon(true);
                watchdog.start();
            } catch (Throwable ignored) { }
        }
    }

    public static synchronized void event(String event) {
        try {
            if (event.startsWith("activity_resumed ") || event.startsWith("activity_direct-resumed ")) activityResumed = true;
            if (resources != null) {
                if (event.startsWith("activity_resumed ") || event.startsWith("activity_direct-resumed ")) resources.foreground(true);
                if (event.startsWith("activity_paused ") || event.startsWith("activity_direct-paused ")
                        || event.startsWith("activity_stopped ") || event.startsWith("activity_direct-stopped ")) resources.foreground(false);
            }
            append(guestSession, "process-" + Process.myPid() + ".txt", event);
        } catch (Throwable ignored) { }
    }

    public static synchronized void event(String pkg, String event) {
        if (pkg != null && pkg.equals(guestPackage)) event(event);
    }

    public static void hostEvent(File session, String event) { append(session, "host.txt", event); }

    public static synchronized void menuAttachment(String state) {
        if (!java.util.Arrays.asList("checking", "attached", "not_attached", "unsupported", "probe_error").contains(state)) return;
        event("menu_window " + state);
        if (guestSession == null || !guestSession.isDirectory()) return;
        try {
            // No parent creation: clearing a running session must stay cleared.
            Files.write(new File(guestSession, "menu-" + Process.myPid() + ".txt").toPath(),
                    (System.currentTimeMillis() + " menu_window " + state + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) { }
    }

    public static void processCreated(String pkg, int pid) {
        if (pid > 0) append(current(pkg), "process-" + pid + ".txt", "guest_process_created");
    }

    private static synchronized void append(File session, String name, String event) {
        if (session == null || !session.isDirectory()) return;
        try {
            File file = new File(session, name);
            byte[] bytes = (System.currentTimeMillis() + " " + event.replace('\n', ' ').replace('\r', ' ') + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            if (file.length() + bytes.length > MAX_EVENT_BYTES) return;
            try (FileOutputStream stream = new FileOutputStream(file, true)) { stream.write(bytes); }
        } catch (Throwable ignored) { /* Recording must never block a game from launching. */ }
    }

    /** Save types and frames, not exception messages (which can contain credentials or URLs). */
    public static String exceptionFrames(Throwable error) {
        java.util.ArrayList<Throwable> causes = new java.util.ArrayList<>();
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (error != null && causes.size() < 32 && seen.add(error)) {
            causes.add(error);
            error = error.getCause();
        }
        StringBuilder text = new StringBuilder();
        // Deepest first so the actual failure survives shortened UI/GitHub excerpts.
        if (error != null) text.append("Exception chain truncated or cyclic; deepest captured cause follows.\n");
        text.append("Exception causes (deepest captured first):\n");
        final int maxChars = MAX_EVENT_BYTES / 4; // At most 64 KiB even with four-byte UTF-8 characters.
        for (int cause = causes.size() - 1; cause >= 0 && text.length() < maxChars; cause--) {
            Throwable captured = causes.get(cause);
            text.append("cause ").append(captured.getClass().getName()).append('\n');
            String category = dynamicCodeRejection(captured);
            if (category != null) text.append("dynamic_code_rejection: ").append(category).append('\n');
            StackTraceElement[] frames = captured.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 48) && text.length() < maxChars; i++) {
                text.append("  at ").append(frames[i].getClassName()).append('.').append(frames[i].getMethodName())
                        .append(':').append(frames[i].getLineNumber()).append('\n');
            }
        }
        if (text.length() >= maxChars) {
            text.setLength(maxChars - 32);
            text.append("\nException frames truncated.\n");
        }
        return text.toString();
    }

    /** Fixed classifications only: never persist a DEX path or arbitrary exception message. */
    public static String dynamicCodeRejection(Throwable error) {
        if (!(error instanceof SecurityException)) return null;
        boolean dexOpen = false;
        for (StackTraceElement frame : error.getStackTrace()) {
            if ("dalvik.system.DexFile".equals(frame.getClassName())
                    && "openDexFileNative".equals(frame.getMethodName())) {
                dexOpen = true;
                break;
            }
        }
        if (!dexOpen) return null;
        String message = error.getMessage();
        if (message != null && message.startsWith("Writable dex file '")
                && message.endsWith("' is not allowed.")) return "writable_file_blocked";
        return "unclassified_security_rejection";
    }

    public static synchronized void failure(String stage, Throwable error) {
        if (guestSession == null) return;
        try {
            event("failure " + stage + " " + error.getClass().getName() + " tid=" + Process.myTid());
            File file = new File(guestSession, "failure-" + Process.myPid() + ".txt");
            if (!file.exists()) Files.write(file.toPath(), exceptionFrames(error).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) { }
    }
}
