package com.moodtools.hub;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import dalvik.system.DexClassLoader;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;
import top.niunaijun.blackbox.core.env.BEnvironment;

/** Initializes BlackBox and module callbacks inside the single non-root launcher package. */
public final class HubApplication extends Application {
    private static final long MODULE_ATTACH_SETTLE_MS = 250L;
    private static final long MODULE_ATTACH_RETRY_MS = 100L;
    private static final int MODULE_ATTACH_MAX_RETRIES = 20;

    private final Map<String, ClassLoader> moduleLoaders = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<android.app.Activity, Integer> activityResumeGenerations =
            new WeakHashMap<>();
    private final Map<Application, Boolean> directLifecycleApplications =
            new WeakHashMap<>();

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
                // The standalone launcher owns the BlackBox lifetime. A daemon would keep the
                // virtual runtime alive after the user removes the launcher task from Recents.
                return false;
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
        registerModuleLoader();
        BlackBoxCore.get().doCreate();
    }

    private void registerModuleLoader() {
        BlackBoxCore.get().addAppLifecycleCallback(new AppLifecycleCallback() {
            @Override
            public void beforeCreateApplication(String packageName, String processName,
                                                Context context, int userId) {
                android.util.Log.i("NonRootBlackBox", "beforeCreateApplication package=" + packageName
                        + " process=" + processName + " user=" + userId);
                if (userId != 0 || packageName == null || packageName.equals(getPackageName())) {
                    return;
                }
                ensureModuleLoaded(packageName, userId);
            }

            @Override
            public void beforeApplicationOnCreate(String packageName, String processName,
                                                  android.app.Application application, int userId) {
                if (application == null || userId != 0 || packageName == null ||
                        packageName.equals(getPackageName())) {
                    return;
                }
                // RootBootstrap is reserved for the root injector. Installing it here used to
                // race BlackBox's own callbacks and could attach a TYPE_APPLICATION menu while
                // Unity was still recreating its first graphics surface.
                if (ensureModuleLoaded(packageName, userId)) {
                    registerDirectActivityLifecycle(application);
                }
            }

            @Override
            public void onActivityCreated(android.app.Activity activity, android.os.Bundle state) {
                logActivityPhase(activity, "created");
            }

            @Override
            public void onActivityStarted(android.app.Activity activity) {
                logActivityPhase(activity, "started");
            }

            @Override
            public void onActivityResumed(android.app.Activity activity) {
                logActivityPhase(activity, "resumed");
                scheduleModuleAttach(activity);
            }

            @Override
            public void onActivityPaused(android.app.Activity activity) {
                invalidateModuleAttach(activity);
            }

            @Override
            public void onActivityDestroyed(android.app.Activity activity) {
                invalidateModuleAttach(activity);
                synchronized (activityResumeGenerations) {
                    activityResumeGenerations.remove(activity);
                }
            }

            private void logActivityPhase(android.app.Activity activity, String phase) {
                if (!isUsable(activity)) return;
                String activityName = activity.getClass().getName();
                android.util.Log.i("NonRootBlackBox", "activity " + phase + "="
                        + activityName + " package=" + activity.getPackageName());
            }

            private void registerDirectActivityLifecycle(final Application application) {
                synchronized (directLifecycleApplications) {
                    if (directLifecycleApplications.containsKey(application)) return;
                    directLifecycleApplications.put(application, Boolean.TRUE);
                }
                application.registerActivityLifecycleCallbacks(
                        new Application.ActivityLifecycleCallbacks() {
                            @Override
                            public void onActivityCreated(android.app.Activity activity,
                                                          android.os.Bundle state) {
                                logActivityPhase(activity, "direct-created");
                            }

                            @Override
                            public void onActivityStarted(android.app.Activity activity) {
                                logActivityPhase(activity, "direct-started");
                            }

                            @Override
                            public void onActivityResumed(android.app.Activity activity) {
                                logActivityPhase(activity, "direct-resumed");
                                scheduleModuleAttach(activity);
                            }

                            @Override
                            public void onActivityPaused(android.app.Activity activity) {
                                invalidateModuleAttach(activity);
                            }

                            @Override
                            public void onActivityDestroyed(android.app.Activity activity) {
                                invalidateModuleAttach(activity);
                                synchronized (activityResumeGenerations) {
                                    activityResumeGenerations.remove(activity);
                                }
                            }

                            @Override public void onActivityStopped(android.app.Activity activity) { }
                            @Override public void onActivitySaveInstanceState(
                                    android.app.Activity activity, android.os.Bundle state) { }
                        });
                android.util.Log.i("NonRootBlackBox",
                        "Installed direct Activity lifecycle fallback for "
                                + application.getPackageName());
            }

            private void scheduleModuleAttach(final android.app.Activity activity) {
                if (!isUsable(activity)) return;
                final int generation;
                synchronized (activityResumeGenerations) {
                    Integer previous = activityResumeGenerations.get(activity);
                    generation = previous == null ? 1 : previous + 1;
                    activityResumeGenerations.put(activity, generation);
                }

                android.view.Window window = activity.getWindow();
                final android.view.View decor = window == null ? null : window.getDecorView();
                if (decor == null) {
                    scheduleAttachAttempt(activity, generation, 0);
                    return;
                }
                // Wait for two rendered frames before starting the settle interval. This keeps
                // module UI creation out of Unity's first Activity/surface recreation transaction.
                decor.postOnAnimation(new Runnable() {
                    @Override
                    public void run() {
                        if (!isCurrentResume(activity, generation)) return;
                        decor.postOnAnimation(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCurrentResume(activity, generation)) return;
                                mainHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        attachWhenWindowReady(activity, generation, 0);
                                    }
                                }, MODULE_ATTACH_SETTLE_MS);
                            }
                        });
                    }
                });
            }

            private void scheduleAttachAttempt(final android.app.Activity activity,
                                               final int generation, final int retry) {
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        attachWhenWindowReady(activity, generation, retry);
                    }
                }, retry == 0 ? MODULE_ATTACH_SETTLE_MS : MODULE_ATTACH_RETRY_MS);
            }

            private void attachWhenWindowReady(android.app.Activity activity, int generation,
                                               int retry) {
                if (!isCurrentResume(activity, generation)) return;
                android.view.Window window = activity.getWindow();
                android.view.View decor = window == null ? null : window.getDecorView();
                if (decor == null || !decor.isAttachedToWindow()) {
                    if (retry < MODULE_ATTACH_MAX_RETRIES) {
                        scheduleAttachAttempt(activity, generation, retry + 1);
                    } else {
                        android.util.Log.w("NonRootBlackBox",
                                "Module attach window never became ready for " +
                                        activity.getPackageName());
                    }
                    return;
                }
                if (!activity.hasWindowFocus() && retry < MODULE_ATTACH_MAX_RETRIES) {
                    scheduleAttachAttempt(activity, generation, retry + 1);
                    return;
                }
                if (!activity.hasWindowFocus()) {
                    // Some games intentionally keep a child/native window focused. The bounded
                    // wait still removes cold-start contention without suppressing their menu.
                    android.util.Log.i("NonRootBlackBox",
                            "Attaching module after bounded window-focus wait for " +
                                    activity.getPackageName());
                }
                startModule(activity);
            }

            private boolean isCurrentResume(android.app.Activity activity, int generation) {
                if (!isUsable(activity)) return false;
                synchronized (activityResumeGenerations) {
                    Integer current = activityResumeGenerations.get(activity);
                    return current != null && current == generation;
                }
            }

            private void invalidateModuleAttach(android.app.Activity activity) {
                if (activity == null) return;
                synchronized (activityResumeGenerations) {
                    Integer current = activityResumeGenerations.get(activity);
                    activityResumeGenerations.put(activity, current == null ? 1 : current + 1);
                }
            }

            private boolean isUsable(android.app.Activity activity) {
                return activity != null && !activity.isFinishing() &&
                        (android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
            }

            private void startModule(android.app.Activity activity) {
                if (activity == null) return;
                String packageName = activity.getPackageName();
                ClassLoader loader = moduleLoaders.get(packageName);
                if (loader == null) {
                    ensureModuleLoaded(packageName, 0);
                    loader = moduleLoaders.get(packageName);
                }
                if (loader == null) {
                    android.util.Log.w("NonRootBlackBox",
                            "No staged module loader for activity package=" + packageName);
                    return;
                }
                try {
                    Class<?> main = Class.forName(findEntryPoint(packageName), true, loader);
                    try {
                        main.getMethod("StartRoot", android.app.Activity.class).invoke(null, activity);
                        android.util.Log.i("NonRootBlackBox",
                                "Direct Activity module attached for " + packageName);
                    } catch (NoSuchMethodException legacyModule) {
                        main.getMethod("Start", android.content.Context.class).invoke(null, activity);
                        android.util.Log.i("NonRootBlackBox",
                                "Legacy Context module attached for " + packageName);
                    }
                } catch (Throwable error) {
                    android.util.Log.e("NonRootBlackBox", "Could not start module for " + packageName, error);
                }
            }

            @Override
            public void afterMainActivityOnCreate(android.app.Activity activity) {
                logActivityPhase(activity, "main-created");
            }

            private String findEntryPoint(String packageName) {
                try {
                    File directory = BEnvironment.getDataFilesDir(packageName, 0);
                    String value = new JSONObject(readText(new File(directory, "config.json")))
                            .optString("entry_point", "").trim();
                    return value.isEmpty() ? packageName + ".Main" : value;
                } catch (Throwable ignored) {
                    return packageName + ".Main";
                }
            }
        });
    }

    private synchronized boolean ensureModuleLoaded(String packageName, int userId) {
        if (packageName == null || packageName.equals(getPackageName()) || userId != 0) return false;
        if (moduleLoaders.containsKey(packageName)) return true;

        File moduleDirectory = BEnvironment.getDataFilesDir(packageName, userId);
        File nativeFile = new File(moduleDirectory, "libmenu_native.so");
        File dexFile = new File(moduleDirectory, "classes.dex");
        File configFile = new File(moduleDirectory, "config.json");
        if (!nativeFile.isFile() || !dexFile.isFile() || !configFile.isFile()) {
            android.util.Log.w("NonRootBlackBox", "Module payload incomplete for " + packageName
                    + " dir=" + moduleDirectory.getAbsolutePath());
            return false;
        }

        try {
            // Android requires dynamically loaded code to be immutable. This
            // also repairs payloads staged by launcher builds predating the
            // atomic read-only staging path.
            if (dexFile.canWrite() && !dexFile.setReadOnly()) {
                throw new IllegalStateException("Could not protect module DEX for " + packageName);
            }
            JSONObject config = new JSONObject(readText(configFile));
            String entryPoint = config.optString("entry_point", "").trim();
            if (entryPoint.isEmpty()) entryPoint = packageName + ".Main";
            int separator = entryPoint.lastIndexOf('.');
            String namespace = separator > 0 ? entryPoint.substring(0, separator) : packageName;
            String cacheKey = dexFile.length() + "-" + dexFile.lastModified();
            File optimized = new File(getCodeCacheDir(),
                    "blackbox-modules/" + packageName + "/" + cacheKey);
            optimized.mkdirs();
            DexClassLoader loader = new DexClassLoader(
                    dexFile.getAbsolutePath(), optimized.getAbsolutePath(),
                    moduleDirectory.getAbsolutePath(), getClassLoader());
            Class.forName(entryPoint, true, loader);
            Class<?> runtime = loader.loadClass(namespace + ".ModuleRuntime");
            runtime.getMethod("loadNative", String.class).invoke(null, nativeFile.getAbsolutePath());
            moduleLoaders.put(packageName, loader);
            android.util.Log.i("NonRootBlackBox", "Module loaded for " + packageName);
            return true;
        } catch (Throwable error) {
            android.util.Log.e("NonRootBlackBox", "Could not load module for " + packageName, error);
            return false;
        }
    }

    private static String readText(File file) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
