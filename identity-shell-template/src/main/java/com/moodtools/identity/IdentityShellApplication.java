package com.moodtools.identity;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import dalvik.system.DexClassLoader;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;
import top.niunaijun.blackbox.core.env.BEnvironment;

/** BlackBox host used only by generated exact-package companion shells. */
public final class IdentityShellApplication extends Application {
    private static final String TAG = "IdentityShell";
    private static final long ATTACH_DELAY_MS = 450L;

    private final Map<String, ClassLoader> moduleLoaders = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        BlackBoxCore.get().doAttachBaseContext(base, new ClientConfiguration() {
            @Override
            public String getHostPackageName() {
                return base.getPackageName();
            }

            @Override
            public boolean isEnableDaemonService() {
                return false;
            }

            @Override
            public boolean isHostPackageVirtualizationEnabled() {
                return true;
            }

            @Override
            public boolean requestInstallPackage(File file, int userId) {
                return false;
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerModuleLifecycle();
        BlackBoxCore.get().doCreate();
    }

    private void registerModuleLifecycle() {
        BlackBoxCore.get().addAppLifecycleCallback(new AppLifecycleCallback() {
            @Override
            public void beforeCreateApplication(String packageName, String processName,
                                                Context context, int userId) {
                if (isTarget(packageName, userId)) ensureModuleLoaded(packageName, userId);
            }

            @Override
            public void beforeApplicationOnCreate(String packageName, String processName,
                                                  Application application, int userId) {
                if (!isTarget(packageName, userId) || application == null) return;
                ensureModuleLoaded(packageName, userId);
                application.registerActivityLifecycleCallbacks(
                        new ActivityLifecycleCallbacks() {
                            @Override
                            public void onActivityResumed(Activity activity) {
                                scheduleAttach(activity);
                            }

                            @Override public void onActivityCreated(Activity activity, android.os.Bundle state) { }
                            @Override public void onActivityStarted(Activity activity) { }
                            @Override public void onActivityPaused(Activity activity) { }
                            @Override public void onActivityStopped(Activity activity) { }
                            @Override public void onActivitySaveInstanceState(Activity activity, android.os.Bundle state) { }
                            @Override public void onActivityDestroyed(Activity activity) { }
                        }
                );
            }

            @Override
            public void onActivityResumed(Activity activity) {
                scheduleAttach(activity);
            }
        });
    }

    private boolean isTarget(String packageName, int userId) {
        return userId == 0 && packageName != null && packageName.equals(getPackageName());
    }

    private void scheduleAttach(Activity activity) {
        if (activity == null || activity instanceof IdentityShellActivity) return;
        if (!IdentityLaunchGuard.isFullModuleAuthorized(this)) return;
        mainHandler.postDelayed(() -> startModule(activity), ATTACH_DELAY_MS);
    }

    private void startModule(Activity activity) {
        if (!IdentityLaunchGuard.isFullModuleAuthorized(this)) return;
        if (activity == null || activity.isFinishing()
                || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
        String packageName = activity.getPackageName();
        ClassLoader loader = moduleLoaders.get(packageName);
        if (loader == null && ensureModuleLoaded(packageName, 0)) {
            loader = moduleLoaders.get(packageName);
        }
        if (loader == null) return;
        try {
            Class<?> main = Class.forName(entryPoint(packageName), true, loader);
            try {
                main.getMethod("StartRoot", Activity.class).invoke(null, activity);
            } catch (NoSuchMethodException legacy) {
                main.getMethod("Start", Context.class).invoke(null, activity);
            }
            Log.i(TAG, "Module attached to " + activity.getClass().getName());
        } catch (Throwable error) {
            Log.e(TAG, "Could not attach module", error);
        }
    }

    private synchronized boolean ensureModuleLoaded(String packageName, int userId) {
        if (!isTarget(packageName, userId)) return false;
        if (moduleLoaders.containsKey(packageName)) return true;
        File directory = BEnvironment.getDataFilesDir(packageName, userId);
        File dex = new File(directory, "classes.dex");
        File nativeFile = new File(directory, "libmenu_native.so");
        File config = new File(directory, "config.json");
        if (!dex.isFile() || !nativeFile.isFile() || !config.isFile()) return false;
        try {
            if (dex.canWrite() && !dex.setReadOnly()) {
                throw new IllegalStateException("Could not protect module DEX");
            }
            String cacheKey = dex.length() + "-" + dex.lastModified();
            File optimized = new File(getCodeCacheDir(), "identity-modules/" + cacheKey);
            if (!optimized.mkdirs() && !optimized.isDirectory()) {
                throw new IllegalStateException("Could not create module code cache");
            }
            DexClassLoader loader = new DexClassLoader(
                    dex.getAbsolutePath(), optimized.getAbsolutePath(),
                    directory.getAbsolutePath(), getClassLoader());
            String entryPoint = entryPoint(packageName);
            Class.forName(entryPoint, true, loader);
            int separator = entryPoint.lastIndexOf('.');
            String namespace = separator > 0 ? entryPoint.substring(0, separator) : packageName;
            Class<?> runtime = loader.loadClass(namespace + ".ModuleRuntime");
            if (IdentityLaunchGuard.isFullModuleAuthorized(this)) {
                try {
                    runtime.getMethod("loadNativeForIdentityShell", String.class)
                            .invoke(null, nativeFile.getAbsolutePath());
                } catch (NoSuchMethodException legacyModule) {
                    // Modules built before identity-shell runtime tagging remain launchable.
                    runtime.getMethod("loadNative", String.class)
                            .invoke(null, nativeFile.getAbsolutePath());
                }
            } else {
                try {
                    runtime.getMethod("loadNativeForIdentityShellCompatibility", String.class)
                            .invoke(null, nativeFile.getAbsolutePath());
                } catch (NoSuchMethodException unsupportedModule) {
                    Log.w(TAG, "Module has no guarded identity compatibility entry point");
                    return false;
                }
            }
            moduleLoaders.put(packageName, loader);
            Log.i(TAG, "Module loaded for " + packageName);
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Could not load module", error);
            return false;
        }
    }

    private String entryPoint(String packageName) {
        try {
            File config = new File(BEnvironment.getDataFilesDir(packageName, 0), "config.json");
            String value = new JSONObject(new String(Files.readAllBytes(config.toPath()),
                    StandardCharsets.UTF_8)).optString("entry_point", "").trim();
            if (!value.isEmpty()) return value;
        } catch (Throwable ignored) {
        }
        return packageName + ".Main";
    }
}
