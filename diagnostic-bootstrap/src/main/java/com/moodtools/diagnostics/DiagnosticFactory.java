package com.moodtools.diagnostics;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;

/** Delegates normal game components unchanged; report reads use a plain Application in a separate process. */
public final class DiagnosticFactory extends AppComponentFactory {
    private AppComponentFactory delegate = new AppComponentFactory();
    private boolean initialized;
    private void initialize(ClassLoader loader, ApplicationInfo info) {
        if (initialized) return;
        try {
            org.json.JSONObject config;
            if (info != null) config = DiagnosticRuntime.config(info);
            else try (java.io.InputStream input = loader.getResourceAsStream(DiagnosticRuntime.CONFIG)) {
                if (input == null) throw new IllegalStateException("Diagnostics configuration unavailable");
                config = new org.json.JSONObject(new String(DiagnosticRuntime.readBounded(input, 8192), java.nio.charset.StandardCharsets.UTF_8));
            }
            String name = config.optString("factory", "");
            if (!name.isEmpty()) delegate = (AppComponentFactory) loader.loadClass(name).getDeclaredConstructor().newInstance();
            initialized = true;
        } catch (Throwable error) { DiagnosticRuntime.failure("factory_initialize", error); throw new IllegalStateException("Could not initialize game factory", error); }
    }
    @Override public ClassLoader instantiateClassLoader(ClassLoader loader, ApplicationInfo info) {
        if (DiagnosticRuntime.isDiagnosticProcess()) return loader;
        DiagnosticRuntime.start(info);
        try {
            initialize(loader, info);
            return delegate.instantiateClassLoader(loader, info);
        } catch (Throwable error) { DiagnosticRuntime.failure("factory_initialize", error); throw new IllegalStateException("Could not initialize game factory", error); }
    }
    @Override public Application instantiateApplication(ClassLoader loader, String name) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (DiagnosticRuntime.isDiagnosticProcess()) return new Application();
        try {
            // Android 9 has AppComponentFactory but does not call instantiateClassLoader.
            initialize(loader, null);
            Application app = delegate.instantiateApplication(loader, name);
            DiagnosticRuntime.attach(app);
            DiagnosticRuntime.event("application_instantiated");
            return app;
        } catch (RuntimeException | ClassNotFoundException | IllegalAccessException | InstantiationException error) { DiagnosticRuntime.failure("application_create", error); throw error; }
    }
    @Override public Activity instantiateActivity(ClassLoader loader, String name, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        try { DiagnosticRuntime.event("activity_instantiate"); return delegate.instantiateActivity(loader, name, intent); }
        catch (RuntimeException | ClassNotFoundException | IllegalAccessException | InstantiationException error) { DiagnosticRuntime.failure("activity_create", error); throw error; }
    }
    @Override public Service instantiateService(ClassLoader l, String n, Intent i) throws ClassNotFoundException, IllegalAccessException, InstantiationException { return delegate.instantiateService(l,n,i); }
    @Override public BroadcastReceiver instantiateReceiver(ClassLoader l, String n, Intent i) throws ClassNotFoundException, IllegalAccessException, InstantiationException { return delegate.instantiateReceiver(l,n,i); }
    @Override public ContentProvider instantiateProvider(ClassLoader l, String n) throws ClassNotFoundException, IllegalAccessException, InstantiationException { return DiagnosticRuntime.isDiagnosticProcess() ? super.instantiateProvider(l,n) : delegate.instantiateProvider(l,n); }
}
