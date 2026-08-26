package com.example.module;

/** Package-specific loader required by the standalone launcher. */
public final class ModuleRuntime {
    private ModuleRuntime() {
    }

    public static void loadNative(String absolutePath) {
        com.android.support.ModuleRuntime.loadNative(absolutePath);
    }
}
