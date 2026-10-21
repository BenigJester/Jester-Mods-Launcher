package top.niunaijun.blackbox.utils;

import android.os.SystemClock;
import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded, same-app supporting-process samples. Never create sessions or launch guest processes. */
public final class SupportProcessRecorder {
    public enum Role { launcher, compatibility_service, shared_helper }
    private static final int MAX_SESSIONS = 4;
    private static final long WINDOW_MS = 10 * 60_000L;
    private static final Map<File, Window> windows = new LinkedHashMap<>();
    private static final class Window {
        final AtomicBoolean active = new AtomicBoolean(true);
        final long until = SystemClock.elapsedRealtime() + WINDOW_MS;
    }

    static File resolveSession(File root, String packageName, String sessionId) throws Exception {
        if (packageName == null || !packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
                || sessionId == null || !sessionId.matches("[0-9]+-[a-f0-9-]+")) return null;
        File parent = new File(root, packageName), session = new File(parent, sessionId);
        if (!session.isDirectory() || Files.isSymbolicLink(parent.toPath()) || Files.isSymbolicLink(session.toPath())
                || !parent.getCanonicalFile().getParentFile().equals(root.getCanonicalFile())
                || !session.getCanonicalFile().getParentFile().equals(parent.getCanonicalFile())
                || !new File(session, "metadata.txt").isFile()) return null;
        return session.getCanonicalFile();
    }

    public static void observe(String packageName, String sessionId, Role role) {
        try { observeSession(resolveSession(LaunchSessionRecorder.root(), packageName, sessionId), role); }
        catch (Throwable ignored) { /* Diagnostic failures must not affect gameplay. */ }
    }

    public static synchronized void observeSession(File session, Role role) {
        try {
            if (session == null || role == null) return;
            session = resolveSession(LaunchSessionRecorder.root(), session.getParentFile().getName(), session.getName());
            if (session == null || new File(session, "stopped").exists()) return;
            Window previous = windows.get(session);
            if (previous != null && previous.active.get() && SystemClock.elapsedRealtime() < previous.until) return;
            if (previous != null) previous.active.set(false);
            while (windows.size() >= MAX_SESSIONS) {
                File oldest = windows.keySet().iterator().next();
                windows.remove(oldest).active.set(false);
            }
            Window window = new Window();
            windows.put(session, window);
            new SessionResourceRecorder().startSupporting(session, role.name(),
                    () -> window.active.get() && SystemClock.elapsedRealtime() < window.until);
        } catch (Throwable ignored) { }
    }

    private SupportProcessRecorder() { }
}
