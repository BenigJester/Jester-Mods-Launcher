package com.samsung.android.content.clipboard;

import android.os.IInterface;

/** JVM fixture for the Android 10 OEM service cache; never packaged in the launcher. */
public final class SemClipboardManager {
    private static IInterface sService;
    public static IInterface nextService;

    private static IInterface getService() {
        if (sService == null) sService = nextService;
        return sService;
    }

    public static IInterface cachedService() { return sService; }
    public static void reset() { sService = null; nextService = null; }
}
