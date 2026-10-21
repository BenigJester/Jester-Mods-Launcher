package top.niunaijun.blackbox.utils;

/** Fixed counters only: no arguments, package names, Binder contents or exception messages. */
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

    public static synchronized void begin(Operation operation) {
        if (operation == null) return;
        observed = true;
        calls[operation.ordinal()]++;
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

    private ConnectionDiagnostics() { }
}
