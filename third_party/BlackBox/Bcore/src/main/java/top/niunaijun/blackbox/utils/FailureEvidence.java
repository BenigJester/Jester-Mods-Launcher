package top.niunaijun.blackbox.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Shared failure storage. No raw exception messages or callback arguments. */
public final class FailureEvidence {
    private static final int MAX_BYTES = 64 * 1024;
    private static final String CAPTURE = "Failure capture: ";

    public static String phase(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; error != null && depth < 32 && seen.add(error); depth++, error = error.getCause()) {
            for (StackTraceElement frame : error.getStackTrace()) {
                String owner = frame.getClassName(), method = frame.getMethodName();
                if ((owner.equals("android.app.Activity") && method.equals("performDestroy"))
                        || (owner.equals("android.app.Instrumentation") && method.equals("callActivityOnDestroy"))
                        || (owner.equals("android.app.ActivityThread")
                            && (method.equals("performDestroyActivity") || method.equals("handleDestroyActivity")))) {
                    return "activity_teardown";
                }
            }
        }
        return "unclassified";
    }

    public static String hint(Throwable error) {
        Set<Throwable> parcelSeen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean parcel = false, missing = false;
        for (Throwable cause = error; cause != null && parcelSeen.size() < 32 && parcelSeen.add(cause); cause = cause.getCause()) {
            parcel |= cause.getClass().getName().equals("android.os.BadParcelableException");
            missing |= cause instanceof ClassNotFoundException;
        }
        if (parcel && missing) return "parcel_class_not_found";
        if (!phase(error).equals("activity_teardown")) return "unclassified";
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; error != null && depth < 32 && seen.add(error); depth++, error = error.getCause()) {
            if (!(error instanceof IllegalArgumentException)) continue;
            for (StackTraceElement frame : error.getStackTrace()) {
                if (frame.getClassName().equals("android.net.ConnectivityManager")
                        && frame.getMethodName().equals("unregisterNetworkCallback")) {
                    return "network_callback_cleanup";
                }
            }
        }
        return "unclassified";
    }

    /** Exact SDK class identities only, never arbitrary messages or path-containing loader errors. */
    public static String missingSdkClass(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; error != null && depth < 32 && seen.add(error); depth++, error = error.getCause()) {
            if (!(error instanceof ClassNotFoundException)) continue;
            String name = error.getMessage();
            if (name != null && name.length() <= 240 &&
                    name.matches("(?:com\\.google\\.android\\.gms|org\\.microg|com\\.chillyroomsdk)(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+")) {
                return name;
            }
        }
        return "unavailable";
    }

    /** Preserve the first capture; deduplicate and retain at most two later failures. */
    public static synchronized void record(File file, String stage, Throwable error, String frames)
            throws IOException {
        if (!file.getParentFile().isDirectory() || Files.isSymbolicLink(file.toPath())) return;
        String phase = phase(error);
        String detail = "Failure stage: " + (stage.matches("[a-z_]{1,48}") ? stage : "unclassified")
                + "\nFailure phase: " + phase + "\nFailure hint: " + hint(error) + "\n"
                + (hint(error).equals("parcel_class_not_found")
                   ? "Missing SDK class: " + missingSdkClass(error) + "\n" : "")
                + (phase.equals("activity_teardown")
                   ? "Shutdown was already in progress; the trigger for shutdown is not established.\n" : "")
                + frames.substring(0, Math.min(frames.length(), 12000));
        boolean exists = file.exists();
        if (exists && file.length() > MAX_BYTES) return;
        String previous = exists ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
        if (previous.contains(detail)) return;
        int captures = exists && !previous.startsWith("Failure evidence schema: 1\n") ? 1 : 0;
        for (int offset = 0; (offset = previous.indexOf(CAPTURE, offset)) >= 0; offset += CAPTURE.length()) captures++;
        if (captures >= 3) return;
        String text = (exists ? "\n--- ADDITIONAL FAILURE ---\n" : "Failure evidence schema: 1\n")
                + CAPTURE + (exists ? "later_observed" : "first_observed")
                + "\nCaptured at: " + System.currentTimeMillis() + "\n"
                + (exists ? "" : "First observed is not necessarily the original cause.\n") + detail + "\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (previous.getBytes(StandardCharsets.UTF_8).length + bytes.length > MAX_BYTES) return;
        Files.write(file.toPath(), bytes, exists ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW);
    }

    private FailureEvidence() { }
}
