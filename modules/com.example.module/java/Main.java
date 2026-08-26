package com.android.support;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static final String TAG = "Mod_Menu";

    // Kept for native JNI registration compatibility; Java path below performs the permission flow.
    private static native void CheckOverlayPermission(Context context);
    // Optional game hook. Zombie Tsunami registers this method and uses it to stop gameplay while
    // the menu is expanded. Other modules safely fall through the guarded wrapper below.
    private static native void SetMenuExpandedNative(boolean expanded);

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final AtomicInteger NATIVE_TOAST_GENERATION = new AtomicInteger();
    private static final AtomicBoolean LIFECYCLE_RECOVERY_REGISTERED = new AtomicBoolean();
    private static Menu directMenu;
    private static Toast activeNativeToast;

    public static void Preload(Context context) {
        // Payload download and verification are owned by the standalone launcher.
    }

    public static void SetMenuExpanded(boolean expanded) {
        try {
            SetMenuExpandedNative(expanded);
        } catch (UnsatisfiedLinkError ignored) {
            // Most modules do not need a game-side pause/freeze hook.
        }
    }

    public static void ShowNativeToast(final Context context, final String message, final int length) {
        if (context == null || message == null || message.length() == 0) {
            return;
        }
        final int requestGeneration = NATIVE_TOAST_GENERATION.incrementAndGet();
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                // Ignore native toast requests superseded before the main thread reached them.
                if (requestGeneration != NATIVE_TOAST_GENERATION.get()) return;
                if (activeNativeToast != null) activeNativeToast.cancel();
                activeNativeToast = Toast.makeText(context.getApplicationContext(),
                        OfflineTranslator.tr(message), length);
                activeNativeToast.show();
            }
        });
    }

    public static void StartWithoutPermission(Context context) {
        initializeOptionalRuntimeDumpWriter(context);
        CrashHandler.init(context);
        if (context instanceof Activity) {
            //Check if context is an Activity.
            Menu menu = new Menu(context);
            menu.SetWindowManagerActivity();
            menu.ShowMenu();
        } else {
            Toast.makeText(context, OfflineTranslator.tr("Failed to launch the mod menu"), Toast.LENGTH_LONG).show();
        }
    }

    /** Standalone root-launcher entry point, attached to the real game Activity. */
    public static void StartRoot(Activity activity) {
        if (activity == null || activity.isFinishing() ||
                (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
            return;
        }
        Log.i(TAG, "Main.StartRoot package=" + activity.getPackageName());
        GameThreadBridge.bind(activity);
        Launcher.allowRunning();
        initializeOptionalRuntimeDumpWriter(activity);
        CrashHandler.init(activity);

        if (directMenu != null && directMenu.isOverlayAttached()) {
            return;
        }
        if (directMenu != null) {
            directMenu.onDestroy();
            directMenu = null;
        }
        try {
            directMenu = new Menu(activity);
            directMenu.SetWindowManagerActivity();
            directMenu.ShowMenu();
            Log.i(TAG, "Root menu attached to game Activity");
        } catch (Throwable error) {
            Log.e(TAG, "Root menu attachment failed", error);
            if (directMenu != null) directMenu.onDestroy();
            directMenu = null;
        }
    }

    public static void Start(Context context) {
        Log.i(TAG, "Main.Start package=" + (context == null ? "null" : context.getPackageName()));
        GameThreadBridge.bind(context);
        Launcher.allowRunning();
        initializeOptionalRuntimeDumpWriter(context);
        CrashHandler.init(context);
        registerMenuLifecycleRecovery(context);
        checkOverlayPermission(context);
    }

    public static boolean OpenSaveDataPicker(Context context, boolean export) {
        if (context == null) return false;
        try {
            Class<?> pickerClass = Class.forName("com.android.support.SaveDataActivity");
            try {
                Method launch = pickerClass.getMethod("launch", Context.class, boolean.class);
                return Boolean.TRUE.equals(launch.invoke(null, context, export));
            } catch (NoSuchMethodException legacyHelper) {
                // Older standalone APKs declared this helper as an Activity.
            }
            Intent intent = new Intent();
            intent.setClassName(context, "com.android.support.SaveDataActivity");
            intent.putExtra("export", export);
            intent.putExtra("com.android.support.extra.EXPORT_SAVE_DATA", export);
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return true;
        } catch (Throwable error) {
            ShowNativeToast(context, "File picker is not available in this launcher module.", Toast.LENGTH_LONG);
            return false;
        }
    }

    private static void initializeOptionalRuntimeDumpWriter(Context context) {
        if (context == null) return;
        try {
            Class<?> writer = Class.forName("com.android.support.RuntimeDumpWriter");
            Method initialize = writer.getMethod("initialize", Context.class);
            initialize.invoke(null, context);
        } catch (Throwable ignored) {
        }
    }

    private static void checkOverlayPermission(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Launcher.hasOverlayPermission(context)) {
            Toast.makeText(context,
                    OfflineTranslator.tr("Overlay permission is required in order to show mod menu."),
                    Toast.LENGTH_SHORT).show();
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
            } catch (RuntimeException error) {
                try {
                    context.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                } catch (RuntimeException ignored) {
                }
            }
            return;
        }
        Launcher.ensureRunning(context);
        // BlackBox versions that do not expose module-declared services still
        // provide a valid target Activity. Attach once directly so the menu icon
        // is visible without depending on service virtualization.
        if (context instanceof Activity && directMenu == null) {
            try {
                directMenu = new Menu(context);
                directMenu.SetWindowManagerWindowService();
                directMenu.ShowMenu();
                Log.i(TAG, "Direct menu overlay attached");
            } catch (Throwable error) {
                Log.e(TAG, "Direct menu overlay failed", error);
                if (directMenu != null) directMenu.onDestroy();
                directMenu = null;
            }
        }
    }

    private static void registerMenuLifecycleRecovery(Context context) {
        if (context == null || !LIFECYCLE_RECOVERY_REGISTERED.compareAndSet(false, true)) return;
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof Application)) {
            LIFECYCLE_RECOVERY_REGISTERED.set(false);
            return;
        }
        ((Application) appContext).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityResumed(Activity activity) {
                        scheduleMenuRecovery(activity, 0L);
                    }

                    @Override public void onActivityCreated(Activity activity, Bundle state) { }
                    @Override public void onActivityStarted(Activity activity) { }
                    @Override public void onActivityPaused(Activity activity) {
                        scheduleMenuRecovery(activity, 750L);
                    }
                    @Override public void onActivityStopped(Activity activity) {
                        scheduleMenuRecovery(activity, 1500L);
                    }
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
                    @Override public void onActivityDestroyed(Activity activity) { }
                });
    }

    private static void scheduleMenuRecovery(final Activity activity, long delayMillis) {
        if (activity == null || activity instanceof UpdateActivity) return;
        MAIN_HANDLER.postDelayed(new Runnable() {
            @Override
            public void run() {
                Launcher.ensureRunningForRecovery(activity);
            }
        }, Math.max(0L, delayMillis));
    }
}
