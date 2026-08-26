package com.android.support;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Connects the standalone root-launcher payload to the real game Activity. */
public final class RootBootstrap {
    private static final String TAG = "MoodToolsRoot";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int ATTACH_PROBE_COUNT = 50;
    private static final long ATTACH_PROBE_INTERVAL_MS = 200L;

    private RootBootstrap() {
    }

    public static void install(final Application application) {
        if (application == null || !INSTALLED.compareAndSet(false, true)) return;
        MAIN.postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                application.registerActivityLifecycleCallbacks(
                        new Application.ActivityLifecycleCallbacks() {
                            @Override
                            public void onActivityCreated(Activity activity, Bundle state) {
                                attachMenu(activity, true);
                            }

                            @Override
                            public void onActivityStarted(Activity activity) {
                                attachMenu(activity, true);
                            }

                            @Override
                            public void onActivityResumed(Activity activity) {
                                attachMenu(activity, false);
                            }

                            @Override public void onActivityPaused(Activity activity) { }
                            @Override public void onActivityStopped(Activity activity) { }
                            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
                            @Override public void onActivityDestroyed(Activity activity) { }
                        });

                // Injection can finish after the game's first Activity has already reached RESUMED.
                // Android does not replay lifecycle callbacks registered that late, so actively probe
                // the ActivityThread for a currently live Activity during the first few seconds.
                scheduleCurrentActivityProbe(0);
                Log.i(TAG, "Standalone root Activity bootstrap installed for " +
                        application.getPackageName());
            }
        });
    }

    private static void scheduleCurrentActivityProbe(final int attempt) {
        if (attempt >= ATTACH_PROBE_COUNT) {
            Log.w(TAG, "Root Activity attach probe window ended");
            return;
        }
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                Activity activity = findCurrentActivity();
                if (activity != null) {
                    attachMenu(activity, false);
                }
                scheduleCurrentActivityProbe(attempt + 1);
            }
        }, attempt == 0 ? 0L : ATTACH_PROBE_INTERVAL_MS);
    }

    private static Activity findCurrentActivity() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThread.setAccessible(true);
            Object activityThread = currentActivityThread.invoke(null);
            if (activityThread == null) return null;

            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Object activitiesObject = activitiesField.get(activityThread);
            if (!(activitiesObject instanceof Map)) return null;

            Activity fallback = null;
            for (Object record : ((Map<?, ?>) activitiesObject).values()) {
                if (record == null) continue;
                Class<?> recordClass = record.getClass();
                Field activityField = recordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Object value = activityField.get(record);
                if (!(value instanceof Activity)) continue;
                Activity activity = (Activity) value;
                if (!isUsable(activity)) continue;
                fallback = activity;

                try {
                    Field pausedField = recordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    if (!pausedField.getBoolean(record)) return activity;
                } catch (Throwable ignored) {
                    // Some Android releases rename/remove this field. A live Activity is still a
                    // useful fallback and Main.StartRoot performs its own validity checks.
                }
            }
            return fallback;
        } catch (Throwable error) {
            // Lifecycle callbacks remain the primary path. Reflection is only a recovery path for
            // payloads installed after the first resume event, so never let a platform variation
            // crash the host game.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                Log.d(TAG, "Current Activity probe unavailable", error);
            }
            return null;
        }
    }

    private static boolean isUsable(Activity activity) {
        return activity != null && !activity.isFinishing() &&
                (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }

    private static void attachMenu(final Activity activity, boolean frontOfQueue) {
        if (!isUsable(activity)) return;
        Runnable attach = new Runnable() {
            @Override
            public void run() {
                if (!isUsable(activity)) return;
                Main.StartRoot(activity);
            }
        };
        if (frontOfQueue) {
            MAIN.postAtFrontOfQueue(attach);
        } else {
            MAIN.post(attach);
        }
    }
}
