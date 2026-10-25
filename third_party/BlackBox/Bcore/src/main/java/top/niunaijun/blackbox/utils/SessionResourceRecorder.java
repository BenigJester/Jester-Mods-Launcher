package top.niunaijun.blackbox.utils;

import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Per-process rolling evidence, not a freeze detector. No text, network or account data. */
public final class SessionResourceRecorder {
    public static final int MAX_SAMPLES = 24;
    private final ArrayDeque<String> samples = new ArrayDeque<>();
    private volatile boolean foreground;
    private String processRole = "game";
    private final long[] connectionBaseline = ConnectionDiagnostics.checkpoint();

    public void foreground(boolean value) { foreground = value; }

    public synchronized String record(long epoch, long elapsed, long cpu, long pssKb,
                                      long javaKb, long nativeKb, long mainPendingMs) {
        return record(epoch, elapsed, cpu, pssKb, javaKb, nativeKb, mainPendingMs, -1, -1, -1, -1, -1);
    }

    public synchronized String record(long epoch, long elapsed, long cpu, long pssKb,
                                      long javaKb, long nativeKb, long mainPendingMs,
                                      int binderLocal, int binderProxy, int binderDeath, int binderSent, int binderReceived) {
        samples.addLast(epoch + " elapsedMs=" + elapsed + " cpuTotalMs=" + cpu
                + " pssKb=" + pssKb + " javaHeapKb=" + javaKb + " nativeHeapKb=" + nativeKb
                + " activityResumed=" + ("game".equals(processRole) ? foreground : "unavailable") + " mainPendingMs=" + mainPendingMs
                + " binderLocal=" + binderLocal + " binderProxy=" + binderProxy + " binderDeath=" + binderDeath
                + " binderSentTotal=" + binderSent + " binderReceivedTotal=" + binderReceived
                + " bbConnectionCalls=" + ConnectionDiagnostics.snapshot()
                + " bbWindowConnectionCalls=" + ConnectionDiagnostics.since(connectionBaseline)
                + " bbTrackedReceivers=" + ConnectionDiagnostics.trackedReceiverCount() + " processRole=" + processRole);
        while (samples.size() > MAX_SAMPLES) samples.removeFirst();
        return String.join("\n", samples) + "\n";
    }

    public void start(File session) {
        startWorker(session, false, () -> true);
    }

    public void startSupporting(File session, String role, java.util.function.BooleanSupplier active) {
        if (!java.util.Arrays.asList("launcher", "compatibility_service", "shared_helper").contains(role))
            throw new IllegalArgumentException("Invalid supporting process role");
        processRole = role;
        startWorker(session, true, active);
    }

    private void startWorker(File session, boolean supporting, java.util.function.BooleanSupplier active) {
        Thread worker = new Thread(() -> {
            AtomicBoolean pending = new AtomicBoolean(false);
            AtomicLong postedAt = new AtomicLong();
            Handler main = null;
            HelperThreadSnapshot snapshot = new HelperThreadSnapshot();
            while (active.getAsBoolean() && session.isDirectory() && !new File(session, "stopped").exists()) {
                try {
                    if (main == null && Looper.getMainLooper() != null) main = new Handler(Looper.getMainLooper());
                    long now = SystemClock.uptimeMillis();
                    long pendingMs = pending.get() ? Math.max(0, now - postedAt.get()) : 0;
                    if (main != null && pending.compareAndSet(false, true)) {
                        postedAt.set(now);
                        if (!main.post(() -> pending.set(false))) pending.set(false);
                    }
                    Debug.MemoryInfo memory = new Debug.MemoryInfo();
                    Debug.getMemoryInfo(memory);
                    Runtime runtime = Runtime.getRuntime();
                    int[] binders = {-1, -1, -1, -1, -1};
                    try {
                        binders[0] = Debug.getBinderLocalObjectCount();
                        binders[1] = Debug.getBinderProxyObjectCount();
                        binders[2] = Debug.getBinderDeathObjectCount();
                        binders[3] = Debug.getBinderSentTransactions();
                        binders[4] = Debug.getBinderReceivedTransactions();
                    } catch (Throwable ignored) { /* Unsupported counters stay unavailable, never drop the sample. */ }
                    String text = record(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                            Process.getElapsedCpuTime(), memory.getTotalPss(),
                            (runtime.totalMemory() - runtime.freeMemory()) / 1024,
                            Debug.getNativeHeapAllocatedSize() / 1024, main == null ? -1 : pendingMs,
                            binders[0], binders[1], binders[2], binders[3], binders[4]);
                    if (active.getAsBoolean()) {
                        persist(session, Process.myPid(), text, supporting);
                        if (supporting && main != null && snapshot.shouldCapture(pendingMs) && active.getAsBoolean()) {
                            Thread mainThread = main.getLooper().getThread();
                            String frames = HelperThreadSnapshot.format(System.currentTimeMillis(), pendingMs,
                                    mainThread.getState(), mainThread.getStackTrace());
                            if (active.getAsBoolean()) persistHelperSnapshot(session, Process.myPid(), frames);
                        }
                        String sites = ConnectionDiagnostics.siteSnapshot();
                        if (!sites.equals("not_observed") && active.getAsBoolean())
                            persistConnectionSites(session, Process.myPid(), sites, supporting);
                    }
                } catch (Throwable ignored) { /* Diagnostics must never terminate a game. */ }
                try { Thread.sleep(15_000L); }
                catch (InterruptedException stopped) { return; }
            }
        }, "BlackBoxSessionResources");
        worker.setDaemon(true);
        worker.start();
    }

    public static void persist(File session, int pid, String text) throws Exception {
        persist(session, pid, text, false);
    }

    public static void persist(File session, int pid, String text, boolean supporting) throws Exception {
        String prefix = supporting ? "support-resources-" : "resources-";
        persistText(session, prefix + pid, text);
    }

    public static void persistConnectionSites(File session, int pid, String text, boolean supporting) throws Exception {
        if (text.getBytes(StandardCharsets.UTF_8).length > 16 * 1024)
            throw new java.io.IOException("Connection sample limit");
        persistText(session, (supporting ? "support-connections-" : "connections-") + pid, text);
    }

    public static void persistHelperSnapshot(File session, int pid, String text) throws Exception {
        if (text.getBytes(StandardCharsets.UTF_8).length > 16 * 1024)
            throw new java.io.IOException("Helper snapshot limit");
        persistText(session, "support-stacks-" + pid, text);
    }

    private static void persistText(File session, String name, String text) throws Exception {
        // Unlike AtomicFile.startWrite, this never creates parents: Clear cannot be undone by a guest.
        File temporary = new File(session, name + ".pending");
        try {
            try (FileOutputStream stream = new FileOutputStream(temporary)) {
                stream.write(text.getBytes(StandardCharsets.UTF_8));
                stream.getFD().sync();
            }
            Files.move(temporary.toPath(), new File(session, name + ".txt").toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { temporary.delete(); }
    }
}
