package top.niunaijun.blackbox.utils;

/** Counters and sampled code frames only: no arguments, Binder contents or exception messages. */
public final class ConnectionDiagnostics {
    public enum Operation {
        bind, unbind, receiver_add, receiver_remove, network_add, network_remove,
        audio_add, audio_remove, display_add, display_remove, cache_add, cache_remove,
        toast_add, toast_remove, window_add, window_remove, alarm_add, alarm_remove
    }
    private static final long[] calls = new long[Operation.values().length];
    private static final long[] failures = new long[calls.length];
    private static boolean observed;
    private static String lastFailure = "none";
    private static volatile int trackedReceivers = -1;
    private static final java.util.ArrayDeque<String> sites = new java.util.ArrayDeque<>();

    public static Operation operation(String method) {
        return operation(method, "");
    }

    public static Operation operation(String method, String owner) {
        switch (method) {
            case "bindService": case "bindServiceInstance": case "bindIsolatedService": return Operation.bind;
            case "unbindService": return Operation.unbind;
            case "registerReceiver": case "registerReceiverWithFeature": return Operation.receiver_add;
            case "unregisterReceiver": return Operation.receiver_remove;
            case "requestNetwork": case "listenForNetwork": case "registerNetworkCallback":
            case "registerDefaultNetworkCallback": return Operation.network_add;
            case "releaseNetworkRequest": case "unregisterNetworkCallback": return Operation.network_remove;
            case "trackPlayer": case "trackRecorder": case "registerRecordingCallback":
            case "registerPlaybackCallback": return Operation.audio_add;
            case "releasePlayer": case "releaseRecorder": case "unregisterRecordingCallback":
            case "unregisterPlaybackCallback": return Operation.audio_remove;
            case "createVirtualDisplay": return Operation.display_add;
            case "releaseVirtualDisplay": return Operation.display_remove;
            case "enqueueToast": case "enqueueTextToast": return Operation.toast_add;
            case "cancelToast": case "finishToken": return Operation.toast_remove;
            case "addToDisplay": case "addToDisplayAsUser": case "addToDisplayWithoutInputChannel": return Operation.window_add;
            case "remove": return "android.view.IWindowSession".equals(owner) ? Operation.window_remove
                    : "android.app.IAlarmManager".equals(owner) ? Operation.alarm_remove : null;
            case "set": return "android.app.IAlarmManager".equals(owner) ? Operation.alarm_add : null;
            default: return null;
        }
    }

    public static void begin(Operation operation) {
        begin(operation, null);
    }

    public static synchronized void begin(Operation operation, String boundaryClass) {
        if (operation == null) return;
        observed = true;
        long count = ++calls[operation.ordinal()];
        if (sampleCall(count)) try {
            sampleSite(operation, count, Thread.currentThread().getStackTrace(), boundaryClass);
        } catch (Throwable ignored) { /* Recording must not change a service call's behavior. */ }
    }

    static boolean sampleCall(long count) {
        return count == 1 || count == 2 || count == 4 || (count > 0 && count % 256 == 0);
    }

    static synchronized void sampleSite(Operation operation, long count, StackTraceElement[] stack, String boundaryClass) {
        StringBuilder text = new StringBuilder(System.currentTimeMillis() + " operation=" + operation.name()
                + " call=" + count + "\n");
        int captured = 0;
        int afterBoundary = 0;
        // Runtime class identity survives R8 renaming; literal hook package prefixes do not.
        for (int i = 0; i < Math.min(stack.length, 64); i++)
            if (stack[i].getClassName().equals(boundaryClass)) afterBoundary = i + 1;
        for (int i = afterBoundary; i < Math.min(stack.length, 64) && captured < 3; i++) {
            StackTraceElement frame = stack[i];
            String owner = frame.getClassName();
            if (owner.equals(ConnectionDiagnostics.class.getName())
                    || owner.startsWith("top.niunaijun.blackbox.fake.hook.")
                    || owner.startsWith("android.") || owner.startsWith("java.")
                    || owner.startsWith("javax.") || owner.startsWith("dalvik.")
                    || owner.startsWith("libcore.") || owner.startsWith("jdk.")
                    || owner.startsWith("sun.") || owner.matches("(?:.*\\.)?\\$Proxy[0-9]+")) continue;
            // Code identity only, not file names, thread names, runtime arguments or lambda addresses.
            owner = owner.split("/", 2)[0].replaceAll("[^A-Za-z0-9_.$]", "_");
            String method = frame.getMethodName().replaceAll("[^A-Za-z0-9_$]", "_");
            text.append("  at ").append(owner.substring(0, Math.min(owner.length(), 120))).append('.')
                    .append(method.substring(0, Math.min(method.length(), 64))).append(':')
                    .append(frame.getLineNumber()).append('\n');
            captured++;
        }
        if (captured == 0) text.append("caller_frames=unavailable\n");
        sites.addLast(text.toString());
        while (sites.size() > 16) sites.removeFirst();
    }

    public static synchronized String siteSnapshot() {
        if (sites.isEmpty()) return "not_observed";
        return "Sampled connection callsites: process-lifetime, not live connections or Binder allocation ownership.\n\n"
                + String.join("\n", sites);
    }

    public static synchronized void failed(Operation operation, Throwable error) {
        if (operation == null) return;
        failures[operation.ordinal()]++;
        for (int depth = 0; depth < 4 && error instanceof java.lang.reflect.InvocationTargetException; depth++)
            error = error.getCause();
        String kind = error == null ? "rejected" : error instanceof SecurityException ? "permission"
                : error instanceof android.os.RemoteException ? "remote" : "other";
        lastFailure = operation.name() + ":" + kind;
    }

    public static synchronized String snapshot() {
        if (!observed) return "not_observed";
        StringBuilder text = new StringBuilder();
        for (Operation operation : Operation.values()) {
            if (text.length() > 0) text.append(',');
            int index = operation.ordinal();
            text.append(operation.name()).append(':').append(calls[index]).append('/').append(failures[index]);
        }
        return text.append(";lastFailure=").append(lastFailure).toString();
    }

    /** Baselines belong to an observation window, never reset process-wide counters. */
    public static synchronized long[] checkpoint() {
        long[] result = new long[calls.length * 2];
        System.arraycopy(calls, 0, result, 0, calls.length);
        System.arraycopy(failures, 0, result, calls.length, failures.length);
        return result;
    }

    public static synchronized String since(long[] baseline) {
        if (baseline == null || baseline.length != calls.length * 2) return "unavailable";
        StringBuilder text = new StringBuilder();
        for (Operation operation : Operation.values()) {
            int index = operation.ordinal();
            if (calls[index] == baseline[index] && failures[index] == baseline[calls.length + index]) continue;
            if (text.length() > 0) text.append(',');
            text.append(operation.name()).append(':').append(Math.max(0, calls[index] - baseline[index]))
                    .append('/').append(Math.max(0, failures[index] - baseline[calls.length + index]));
        }
        return text.length() == 0 ? "none_observed" : text.toString();
    }

    public static void trackedReceiverCount(int count) { trackedReceivers = Math.max(0, count); }
    public static int trackedReceiverCount() { return trackedReceivers; }

    private ConnectionDiagnostics() { }
}
