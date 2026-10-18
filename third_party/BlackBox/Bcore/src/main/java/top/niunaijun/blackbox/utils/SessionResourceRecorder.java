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

    public void foreground(boolean value) { foreground = value; }

    public synchronized String record(long epoch, long elapsed, long cpu, long pssKb,
                                      long javaKb, long nativeKb, long mainPendingMs) {
        samples.addLast(epoch + " elapsedMs=" + elapsed + " cpuTotalMs=" + cpu
                + " pssKb=" + pssKb + " javaHeapKb=" + javaKb + " nativeHeapKb=" + nativeKb
                + " activityResumed=" + foreground + " mainPendingMs=" + mainPendingMs);
        while (samples.size() > MAX_SAMPLES) samples.removeFirst();
        return String.join("\n", samples) + "\n";
    }

    public void start(File session) {
        Thread worker = new Thread(() -> {
            AtomicBoolean pending = new AtomicBoolean(false);
            AtomicLong postedAt = new AtomicLong();
            Handler main = null;
            while (session.isDirectory() && !new File(session, "stopped").exists()) {
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
                    String text = record(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                            Process.getElapsedCpuTime(), memory.getTotalPss(),
                            (runtime.totalMemory() - runtime.freeMemory()) / 1024,
                            Debug.getNativeHeapAllocatedSize() / 1024, main == null ? -1 : pendingMs);
                    persist(session, Process.myPid(), text);
                } catch (Throwable ignored) { /* Diagnostics must never terminate a game. */ }
                try { Thread.sleep(15_000L); }
                catch (InterruptedException stopped) { return; }
            }
        }, "BlackBoxSessionResources");
        worker.setDaemon(true);
        worker.start();
    }

    public static void persist(File session, int pid, String text) throws Exception {
        // Unlike AtomicFile.startWrite, this never creates parents: Clear cannot be undone by a guest.
        File temporary = new File(session, "resources-" + pid + ".pending");
        try {
            try (FileOutputStream stream = new FileOutputStream(temporary)) {
                stream.write(text.getBytes(StandardCharsets.UTF_8));
                stream.getFD().sync();
            }
            Files.move(temporary.toPath(), new File(session, "resources-" + pid + ".txt").toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { temporary.delete(); }
    }
}
