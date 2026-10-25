package com.moodtools.diagnostics;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Callable only by the exact launcher package AND signer baked into the generated APK. */
public final class DiagnosticProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private void authorize() {
        try {
            JSONObject config = DiagnosticRuntime.config(getContext().getApplicationInfo());
            String caller = config.getString("launcher");
            String expected = config.getString("signer");
            String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
            if (packages == null || !Arrays.asList(packages).contains(caller)) throw new SecurityException("Wrong diagnostic caller");
            android.content.pm.PackageInfo info = getContext().getPackageManager().getPackageInfo(caller,
                Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
            android.content.pm.Signature[] signers = Build.VERSION.SDK_INT >= 28 ? info.signingInfo.getApkContentsSigners() : info.signatures;
            for (android.content.pm.Signature signer : signers) {
                StringBuilder hash = new StringBuilder();
                for (byte value : MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
                if (expected.equals(hash.toString())) return;
            }
        } catch (SecurityException error) { throw error; }
        catch (Exception error) { throw new SecurityException("Diagnostic authorization failed"); }
        throw new SecurityException("Wrong diagnostic signer");
    }
    private File root() { return DiagnosticRuntime.root(getContext().getApplicationInfo()); }
    private File[] sessions() {
        File[] files = root().listFiles(file -> file.isDirectory() && !Files.isSymbolicLink(file.toPath()) && file.getName().matches("[0-9]+-[a-f0-9-]+"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName).reversed());
        return files;
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        authorize();
        try {
            Bundle result = new Bundle();
            if ("begin".equals(method)) {
                File current = DiagnosticRuntime.current(root());
                boolean live = false;
                ActivityManager manager = getContext().getSystemService(ActivityManager.class);
                if (current != null && !new File(current, "stopped").exists()) {
                    java.util.List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
                    if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes) {
                        if (process.uid == android.os.Process.myUid() && !process.processName.endsWith(DiagnosticRuntime.PROCESS) &&
                            new File(current, "process-" + process.pid + ".txt").isFile()) live = true;
                    }
                }
                if (!live) {
                    String text = extras == null ? "{}" : extras.getString("metadata", "{}");
                    if (text.length() > 16000) throw new IllegalArgumentException("Diagnostic metadata too large");
                    JSONObject meta = new JSONObject(text);
                    meta.put("package", getContext().getPackageName());
                    meta.put("method", DiagnosticRuntime.config(getContext().getApplicationInfo()).getString("method"));
                    meta.put("runtime", DiagnosticRuntime.config(getContext().getApplicationInfo()).getString("method"));
                    if (!meta.has("title")) meta.put("title", getContext().getPackageName());
                    meta.put("started", System.currentTimeMillis());
                    current = new File(root(), System.currentTimeMillis() + "-" + UUID.randomUUID());
                    if (!current.mkdirs()) throw new IllegalStateException("Cannot create diagnostic session");
                    Files.write(new File(current, "metadata.txt").toPath(), meta.toString().getBytes(StandardCharsets.UTF_8));
                    android.util.AtomicFile pointer = new android.util.AtomicFile(new File(root(), "current"));
                    FileOutputStream stream = pointer.startWrite();
                    try { stream.write(current.getName().getBytes(StandardCharsets.UTF_8)); pointer.finishWrite(stream); }
                    catch (Throwable error) { pointer.failWrite(stream); throw error; }
                }
                result.putString("session", current.getName());
            } else if ("list".equals(method)) {
                ArrayList<String> ids = new ArrayList<>();
                for (File file : sessions()) { if (ids.size() >= 16) break; ids.add(file.getName()); }
                result.putStringArrayList("sessions", ids);
            } else if ("clear".equals(method)) {
                long cutoff = extras == null ? 0 : extras.getLong("before", 0);
                if (cutoff <= 0) throw new IllegalArgumentException("Missing clear boundary");
                for (File session : sessions()) {
                    if (Long.parseLong(session.getName().split("-")[0]) > cutoff) continue;
                    File[] files = session.listFiles();
                    if (files != null) for (File file : files) if (file.isFile() && !Files.isSymbolicLink(file.toPath())) file.delete();
                    session.delete();
                }
                if (DiagnosticRuntime.current(root()) == null) new android.util.AtomicFile(new File(root(), "current")).delete();
            } else throw new IllegalArgumentException("Unknown diagnostic operation");
            return result;
        } catch (Exception error) { throw new IllegalStateException("Diagnostic operation failed", error); }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        authorize();
        if (!"r".equals(mode) || uri.getPathSegments().size() != 1 || !uri.getLastPathSegment().matches("[0-9]+-[a-f0-9-]+")) throw new SecurityException("Invalid diagnostic session");
        File session = new File(root(), uri.getLastPathSegment());
        if (!session.isDirectory() || Files.isSymbolicLink(session.toPath())) throw new java.io.FileNotFoundException();
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread worker = new Thread(() -> {
                try (ZipOutputStream zip = new ZipOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]))) {
                    File[] files = session.listFiles();
                    int count = 0;
                    if (files != null) Arrays.sort(files, Comparator.<File, Boolean>comparing(file -> !file.getName().equals("metadata.txt"))
                        .thenComparing(Comparator.comparingLong(File::lastModified).reversed()));
                    if (files != null) for (File file : files) {
                        if (++count > 48) break;
                        if (!file.isFile() || Files.isSymbolicLink(file.toPath()) || file.length() > DiagnosticRuntime.LIMIT ||
                            !file.getName().matches("(?:metadata|host|process-[0-9]+|failure-[0-9]+|resources-[0-9]+|connections-[0-9]+)\\.txt")) continue;
                        try (InputStream input = Files.newInputStream(file.toPath())) { put(zip, file.getName(), DiagnosticRuntime.readBounded(input, DiagnosticRuntime.LIMIT)); }
                    }
                    if (Build.VERSION.SDK_INT >= 30) {
                        ActivityManager manager = getContext().getSystemService(ActivityManager.class);
                        long nextStart = Long.MAX_VALUE;
                        long started = Long.parseLong(session.getName().split("-")[0]);
                        for (File other : sessions()) {
                            long value = Long.parseLong(other.getName().split("-")[0]);
                            if (value > started) nextStart = Math.min(nextStart, value);
                        }
                        int exits = 0;
                        boolean traceWritten = false;
                        for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(null, 0, 32)) {
                            if (!new File(session, "process-" + exit.getPid() + ".txt").isFile() || exit.getTimestamp() < started || exit.getTimestamp() >= nextStart) continue;
                            if (++exits > 8) break;
                            String name = "exit-" + exit.getPid() + "-" + exit.getTimestamp();
                            String text = "Exit reason: " + exit.getReason() + "; status: " + exit.getStatus() + "; PID: " + exit.getPid() + "; timestamp: " + exit.getTimestamp() + "\nExit evidence schema: 2\nSystem last-sampled memory: pssKb=" + exit.getPss() + "; rssKb=" + exit.getRss() + "; importance=" + exit.getImportance() + "\nSystem description hint: " + DiagnosticRuntime.exitHint(exit.getDescription()) + "\n";
                            put(zip, name + ".txt", text.getBytes(StandardCharsets.UTF_8));
                            if (!traceWritten && Build.VERSION.SDK_INT >= 31 && exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                                try (InputStream trace = exit.getTraceInputStream()) { if (trace != null) put(zip, name + ".pb", DiagnosticRuntime.readBounded(trace, 2 * 1024 * 1024)); }
                                catch (Exception ignored) { }
                                traceWritten = true;
                            }
                        }
                    }
                } catch (Exception ignored) { }
            }, "JesterDiagnosticExport");
            worker.setDaemon(true); worker.start();
            return pipe[0];
        } catch (java.io.IOException error) { throw new java.io.FileNotFoundException("Cannot export diagnostics"); }
    }
    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws java.io.IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry(); }
    @Override public String getType(Uri uri) { return "application/zip"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
}
