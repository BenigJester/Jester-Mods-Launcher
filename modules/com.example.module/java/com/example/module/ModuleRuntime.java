package com.example.module;

/** Package-specific loader required by the standalone launcher. */
public final class ModuleRuntime {
    private ModuleRuntime() {
    }

    public static void loadNative(String absolutePath) {
        com.android.support.ModuleRuntime.loadNative(absolutePath);
    }

    public static void loadNativeForIdentityShell(String absolutePath) {
        com.android.support.ModuleRuntime.loadNativeForIdentityShell(absolutePath);
    }

    public static void loadNativeForIdentityShellCompatibility(String absolutePath) {
        com.android.support.ModuleRuntime.loadNativeForIdentityShellCompatibility(absolutePath);
    }
}
