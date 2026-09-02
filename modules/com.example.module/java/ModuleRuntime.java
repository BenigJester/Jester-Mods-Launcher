package com.android.support;

/** Entry point used by the launcher to load this module with the module class loader. */
public final class ModuleRuntime {
    private static final int METHOD_INJECTION = 1;
    private static final int METHOD_DIRECT_PATCH = 2;
    private static final int METHOD_IDENTITY_SHELL = 3;
    private static final int METHOD_IDENTITY_SHELL_COMPATIBILITY = 4;
    private static boolean nativeLoaded;
    private static int startedMethod;

    private ModuleRuntime() {
    }

    public static synchronized void loadNative(String absolutePath) {
        ensureNativeLoaded(absolutePath);
        startRuntime(METHOD_INJECTION);
    }

    /** Loads the complete module after the exact-package shell verifies its launch ticket. */
    public static synchronized void loadNativeForIdentityShell(String absolutePath) {
        ensureNativeLoaded(absolutePath);
        startRuntime(METHOD_IDENTITY_SHELL);
    }

    /** Enables package compatibility for an external shell launch without menu or feature hooks. */
    public static synchronized void loadNativeForIdentityShellCompatibility(String absolutePath) {
        ensureNativeLoaded(absolutePath);
        startRuntime(METHOD_IDENTITY_SHELL_COMPATIBILITY);
    }

    private static void ensureNativeLoaded(String absolutePath) {
        if (!nativeLoaded) {
            System.load(absolutePath);
            nativeLoaded = true;
        }
    }

    static synchronized void startDirectPatchRuntime() {
        // NativePayloadLoader performs the actual load for a self-contained patched APK.
        nativeLoaded = true;
        startRuntime(METHOD_DIRECT_PATCH);
    }

    private static void startRuntime(int method) {
        if (startedMethod == method) return;
        if (startedMethod != 0 || !nativeStartRuntime(method)) {
            throw new IllegalStateException("Native payload runtime method conflict.");
        }
        startedMethod = method;
    }

    private static native boolean nativeStartRuntime(int method);
}
