package com.android.support;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Stable manifest bridge that installs a verified menu-module DEX before app components load. */
@TargetApi(28)
public final class ModComponentFactory extends AppComponentFactory {
    public static final String ORIGINAL_FACTORY_METADATA =
            "com.android.support.ORIGINAL_APP_COMPONENT_FACTORY";
    private static final String DEFAULT_ORIGINAL_FACTORY =
            "androidx.core.app.CoreComponentFactory";
    private static final String TAG = "MoodToolsLoader";
    private AppComponentFactory originalFactory;
    private ApplicationInfo applicationInfo;
    private boolean launcherAuthorized;

    @Override
    public ClassLoader instantiateClassLoader(ClassLoader classLoader, ApplicationInfo info) {
        applicationInfo = info;
        ClassLoader base = classLoader;
        AppComponentFactory original = originalFactory(classLoader, info);
        if (original != null) {
            try { base = original.instantiateClassLoader(classLoader, info); }
            catch (Throwable ignored) { base = classLoader; }
        } else {
            base = super.instantiateClassLoader(classLoader, info);
        }
        return DexModuleStore.createPatchedClassLoader(info, base);
    }

    @Override
    public Activity instantiateActivity(ClassLoader loader, String name, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (!launcherAuthorized) {
            launcherAuthorized = DirectLaunchGuard.authorize(applicationInfo, intent);
        }
        if (launcherAuthorized) loadAuthorizedPayload();
        AppComponentFactory original = originalFactory;
        Activity activity = original == null ? super.instantiateActivity(loader, name, intent)
                : original.instantiateActivity(loader, name, intent);
        if (launcherAuthorized) attachAuthorizedMenu(activity);
        return activity;
    }

    @Override
    public Application instantiateApplication(ClassLoader loader, String name)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        AppComponentFactory original = originalFactory;
        return original == null ? super.instantiateApplication(loader, name)
                : original.instantiateApplication(loader, name);
    }

    @Override
    public Service instantiateService(ClassLoader loader, String name, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        AppComponentFactory original = originalFactory;
        return original == null ? super.instantiateService(loader, name, intent)
                : original.instantiateService(loader, name, intent);
    }

    @Override
    public BroadcastReceiver instantiateReceiver(ClassLoader loader, String name, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        AppComponentFactory original = originalFactory;
        return original == null ? super.instantiateReceiver(loader, name, intent)
                : original.instantiateReceiver(loader, name, intent);
    }

    private AppComponentFactory originalFactory(ClassLoader loader, ApplicationInfo info) {
        if (originalFactory != null || info == null) return originalFactory;
        String name = info.metaData == null
                ? DEFAULT_ORIGINAL_FACTORY
                : info.metaData.getString(ORIGINAL_FACTORY_METADATA, DEFAULT_ORIGINAL_FACTORY);
        if (name.length() == 0 || ModComponentFactory.class.getName().equals(name)) return null;
        try {
            Object instance = Class.forName(name, true, loader).newInstance();
            if (instance instanceof AppComponentFactory) originalFactory = (AppComponentFactory) instance;
        } catch (Throwable ignored) {
        }
        return originalFactory;
    }

    private void loadAuthorizedPayload() {
        try {
            Log.i(TAG, "Launcher-authorized native pre-load");
            NativePayloadLoader.ensureLoaded(null);
            ModuleRuntime.startDirectPatchRuntime();
        } catch (Throwable error) {
            Log.e(TAG, "Authorized native load failed", error);
        }
    }

    private void attachAuthorizedMenu(final Activity activity) {
        new Handler(Looper.getMainLooper()).postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                try {
                    Application application = activity.getApplication();
                    if (application != null) RootBootstrap.install(application);
                } catch (Throwable error) {
                    Log.e(TAG, "Authorized menu bootstrap failed", error);
                }
            }
        });
    }
}
