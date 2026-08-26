package com.android.support;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.util.Log;

public class Launcher extends Service implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "Mod_Menu";

    private static final String EXTRA_SUPPRESS_NATIVE_SYNC =
            "com.android.support.extra.SUPPRESS_NATIVE_SYNC";
    private static volatile boolean createdMenuOnce;
    private Menu menu;
    private Activity resumedActivity;
    private boolean updaterScreenActive;
    private boolean destroyed;
    private final Handler visibilityHandler = new Handler(Looper.getMainLooper());
    private final Runnable delayedTaskRemovalCheck = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            updateVisibility(true, true);
        }
    };
    private final Runnable visibilityCheck = new Runnable() {
        @Override
        public void run() {
            try {
                updateVisibility(true);
            } finally {
                if (!destroyed) visibilityHandler.postDelayed(this, 1000);
            }
        }
    };

    public static void ensureRunning(Context context) {
        ensureRunning(context, false);
    }

    public static void ensureRunningForRecovery(Context context) {
        ensureRunning(context, true);
    }

    private static void ensureRunning(Context context, boolean suppressNativeSync) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, Launcher.class);
            intent.putExtra(EXTRA_SUPPRESS_NATIVE_SYNC, suppressNativeSync);
            context.startService(intent);
        } catch (RuntimeException ignored) {
            // A foreground Activity resume will try again. Do not crash the host game when an
            // OEM temporarily rejects service creation during a process-state transition.
        }
    }

    public static void allowRunning() {
    }

    public static boolean hasOverlayPermission(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;
        if (context != null && Settings.canDrawOverlays(context)) return true;
        if (context != null) {
            String[] hosts = {"com.moodtools.hub.nonroot", "com.moodtools.hub.root", "com.moodtools"};
            for (String hostPackage : hosts) {
                try {
                    if (Settings.canDrawOverlays(context.createPackageContext(
                            hostPackage, Context.CONTEXT_IGNORE_SECURITY))) return true;
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    //When this Class is called the code in this function will be executed
    @Override
    public void onCreate() {
        super.onCreate();
        destroyed = false;
        getApplication().registerActivityLifecycleCallbacks(this);
        try {
        } catch (Throwable error) {
            // Stay alive; a later health pass/recovery start can try again.
        }
        visibilityHandler.post(visibilityCheck);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Process importance is retained as a fallback for games with unusual Activity lifecycles.
    private boolean isAppForeground() {
        ActivityManager.RunningAppProcessInfo runningAppProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(runningAppProcessInfo);
        return runningAppProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }

    private boolean shouldShowMenu() {
        if (updaterScreenActive) return false;
        Activity activity = resumedActivity;
        if (activity instanceof UpdateActivity) return false;
        return true;
    }

    private boolean canDrawOverlay() {
        return hasOverlayPermission(this);
    }

    private void createMenu() {
        createMenu(false);
    }

    private void createMenu(boolean suppressNativeSync) {
        if (menu != null || !canDrawOverlay()) return;
        boolean effectiveSuppressNativeSync = suppressNativeSync || createdMenuOnce;
        boolean previousSuppressNativeSync = Preferences.suppressNativeSync;
        if (effectiveSuppressNativeSync) Preferences.suppressNativeSync = true;
        try {
            menu = new Menu(this);
            menu.SetWindowManagerWindowService();
            menu.ShowMenu();
            createdMenuOnce = true;
        } catch (Throwable error) {
            Log.e(TAG, "Menu creation failed", error);
            if (menu != null) menu.onDestroy();
            menu = null;
        } finally {
            if (effectiveSuppressNativeSync) Preferences.suppressNativeSync = previousSuppressNativeSync;
        }
    }

    private void rebuildMenu() {
        if (menu != null) menu.onDestroy();
        menu = null;
        createMenu(true);
    }

    private void updateVisibility(boolean repairDetachedOverlay) {
        updateVisibility(repairDetachedOverlay, repairDetachedOverlay);
    }

    private void updateVisibility(boolean repairDetachedOverlay, boolean suppressNativeSync) {
        if (destroyed) return;
        try {
            if (menu == null) createMenu(suppressNativeSync);
            if (menu == null) return;
            boolean visible = shouldShowMenu();
            menu.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            if (visible && repairDetachedOverlay && canDrawOverlay() && !menu.isOverlayAttached()) {
                rebuildMenu();
                if (menu != null) menu.setVisibility(View.VISIBLE);
            }
        } catch (RuntimeException ignored) {
            // Keep the main thread and the native hooks alive; the next health pass retries.
        }
    }

    //Destroy our View
    public void onDestroy() {
        destroyed = true;
        visibilityHandler.removeCallbacks(visibilityCheck);
        visibilityHandler.removeCallbacks(delayedTaskRemovalCheck);
        getApplication().unregisterActivityLifecycleCallbacks(this);
        if (menu != null) {
            menu.onDestroy();
            menu = null;
        }
        super.onDestroy();
    }

    //Same as above so it wont crash in the background and therefore use alot of Battery life
    public void onTaskRemoved(Intent intent) {
        super.onTaskRemoved(intent);
        visibilityHandler.removeCallbacks(delayedTaskRemovalCheck);
        visibilityHandler.postDelayed(delayedTaskRemovalCheck, 3000);
    }

    public int onStartCommand(Intent intent, int i, int i2) {
        boolean suppressNativeSync = intent != null
                && intent.getBooleanExtra(EXTRA_SUPPRESS_NATIVE_SYNC, false);
        updateVisibility(true, suppressNativeSync);
        return Service.START_STICKY;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        resumedActivity = activity;
        updaterScreenActive = activity instanceof UpdateActivity;
        updateVisibility(true, true);
    }

    @Override
    public void onActivityPaused(final Activity activity) {
        visibilityHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (resumedActivity == activity) resumedActivity = null;
                updateVisibility(false, false);
            }
        }, 350);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (resumedActivity == activity) resumedActivity = null;
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (shouldShowMenu()) updateVisibility(true);
    }
}
