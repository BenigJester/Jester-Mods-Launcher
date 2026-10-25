package top.niunaijun.blackbox.utils;

/** At most one main-thread snapshot per observer window. Delay is not proof of a freeze. */
public final class HelperThreadSnapshot {
    private boolean captured;

    public boolean shouldCapture(long pendingMs) {
        if (captured || pendingMs < 30_000) return false;
        captured = true;
        return true;
    }

    public static String format(long epoch, long pendingMs, Thread.State state, StackTraceElement[] frames) {
        StringBuilder text = new StringBuilder("capturedAt=" + epoch + " mainPendingMs=" + pendingMs
                + " threadRole=main state=" + state.name() + "\n"
                + "Shared-process observation, not proof of a freeze or the game's cause.\n");
        for (int i = 0; i < Math.min(frames.length, 32); i++) {
            String owner = frames[i].getClassName().split("/", 2)[0].replaceAll("[^A-Za-z0-9_.$]", "_");
            String method = frames[i].getMethodName().replaceAll("[^A-Za-z0-9_$]", "_");
            text.append("  at ").append(owner.substring(0, Math.min(120, owner.length()))).append('.')
                    .append(method.substring(0, Math.min(64, method.length()))).append(':')
                    .append(frames[i].getLineNumber()).append('\n');
        }
        return text.toString();
    }

    public HelperThreadSnapshot() { }
}
