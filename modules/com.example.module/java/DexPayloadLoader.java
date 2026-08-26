package com.android.support;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import dalvik.system.DexClassLoader;

final class DexPayloadLoader {
    static final String PHASE_BEFORE_GAME_LAUNCH = "before_game_launch";
    static final String PHASE_MENU_START = "menu_start";

    private static final String PREFS = "mood_tools_dex_update";
    private static final String KEY_BUILD = "build";
    private static final String KEY_SHA256 = "sha256";
    private static final String KEY_BASE_SHA256 = "base_sha256";
    private static final String KEY_ENTRY_CLASS = "entry_class";
    private static final String KEY_PATH = "path";
    private static final Set<String> INVOKED_PHASES = new HashSet<>();

    private static ClassLoader payloadClassLoader;
    private static String loadedIdentity = "";

    private DexPayloadLoader() {
    }

    static synchronized String invokeCached(Context context,
                                            NativePayloadLoader.BundledPayload bundled,
                                            String phase) {
        CachedPayload cached = readValidCachedPayload(context, bundled);
        if (cached == null) {
            if (hasCachedPayload(context)) clearCachedPayload(context);
            return "no DEX payload";
        }
        try {
            invoke(context, cached.file, cached.build, cached.sha256, cached.entryClass, phase);
            return "DEX payload " + cached.build;
        } catch (Throwable ignored) {
            clearCachedPayload(context);
            return "rejected cached DEX payload";
        }
    }

    static synchronized String installPayload(Context context, File payload, long build,
                                              String sha256, String baseSha256,
                                              String entryClass) throws Exception {
        if (context == null || payload == null || !payload.isFile()) {
            throw new IllegalArgumentException("Downloaded DEX payload is missing.");
        }
        String normalizedSha = normalizeSha(sha256);
        if (!normalizedSha.equals(sha256(payload))) {
            throw new SecurityException("Downloaded DEX payload failed SHA-256 verification.");
        }
        if (!isAllowedEntryClass(entryClass)) {
            throw new SecurityException("Downloaded DEX entry point is not allowed.");
        }
        if (!payload.setReadable(true, true) || !payload.setWritable(false, false)) {
            throw new SecurityException("Downloaded DEX payload could not be made read-only.");
        }
        boolean saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_BUILD, build)
                .putString(KEY_SHA256, normalizedSha)
                .putString(KEY_BASE_SHA256, normalizeSha(baseSha256))
                .putString(KEY_ENTRY_CLASS, entryClass)
                .putString(KEY_PATH, payload.getAbsolutePath())
                .commit();
        if (!saved) {
            throw new SecurityException("Cached DEX payload metadata could not be saved.");
        }
        return "cached DEX payload " + build;
    }

    static CachedPayload readValidCachedPayload(Context context,
                                                NativePayloadLoader.BundledPayload bundled) {
        if (context == null || bundled == null) return null;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long build = preferences.getLong(KEY_BUILD, 0L);
        String sha = normalizeSha(preferences.getString(KEY_SHA256, ""));
        String baseSha = normalizeSha(preferences.getString(KEY_BASE_SHA256, ""));
        String entryClass = preferences.getString(KEY_ENTRY_CLASS, "");
        String path = preferences.getString(KEY_PATH, "");
        if (build <= 0 || sha.length() != 64 || !bundled.sha256.equals(baseSha)
                || !isAllowedEntryClass(entryClass) || path.length() == 0) {
            return null;
        }

        File file = new File(path);
        try {
            if (!isInside(file, updateRoot(context)) || !file.isFile() || !sha.equals(sha256(file))) {
                return null;
            }
            file.setReadable(true, true);
            file.setWritable(false, false);
            return new CachedPayload(build, sha, baseSha, entryClass, file);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static File updateRoot(Context context) {
        return new File(context.getCodeCacheDir(), "mood-tools-dex");
    }

    static void clearCachedPayload(Context context) {
        if (context == null) return;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = preferences.getString(KEY_PATH, "");
        preferences.edit().clear().apply();
        if (path.length() == 0) return;
        try {
            File file = new File(path);
            if (isInside(file, updateRoot(context))) {
                file.setWritable(true, true);
                file.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    static boolean isAllowedEntryClass(String value) {
        return value != null
                && value.matches("^com\\.moodtools\\.payload\\.[A-Za-z_$][A-Za-z0-9_$.]{0,180}$");
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream input = new BufferedInputStream(new FileInputStream(file));
        try {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        } finally {
            input.close();
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static void invoke(Context context, File payload, long build, String sha256,
                               String entryClass, String phase) throws Exception {
        String phaseKey = sha256 + ":" + phase;
        if (INVOKED_PHASES.contains(phaseKey)) return;

        String identity = sha256 + ":" + entryClass;
        if (payloadClassLoader == null || !identity.equals(loadedIdentity)) {
            File optimized = new File(updateRoot(context), "optimized");
            if (!optimized.exists() && !optimized.mkdirs()) {
                throw new Exception("Could not create private DEX optimization storage.");
            }
            payloadClassLoader = new DexClassLoader(
                    payload.getAbsolutePath(), optimized.getAbsolutePath(), null,
                    context.getClassLoader());
            loadedIdentity = identity;
        }

        Class<?> entry = Class.forName(entryClass, true, payloadClassLoader);
        Method method = entry.getMethod("initialize", Context.class, Bundle.class);
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != Void.TYPE) {
            throw new SecurityException("DEX entry point must be public static void initialize(Context, Bundle).");
        }

        Bundle state = new Bundle();
        state.putString("phase", phase);
        state.putLong("build", build);
        state.putString("sha256", sha256);
        state.putString("packageName", context.getPackageName());
        try {
            method.invoke(null, context, state);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new Exception("DEX entry point failed.", cause);
        }
        INVOKED_PHASES.add(phaseKey);
    }

    private static boolean hasCachedPayload(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_BUILD, 0L) > 0L;
    }

    private static String normalizeSha(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static boolean isInside(File child, File parent) throws Exception {
        String childPath = child.getCanonicalPath();
        String parentPath = parent.getCanonicalPath();
        return childPath.startsWith(parentPath + File.separator);
    }

    static final class CachedPayload {
        final long build;
        final String sha256;
        final String baseSha256;
        final String entryClass;
        final File file;

        CachedPayload(long build, String sha256, String baseSha256,
                      String entryClass, File file) {
            this.build = build;
            this.sha256 = sha256;
            this.baseSha256 = baseSha256;
            this.entryClass = entryClass;
            this.file = file;
        }
    }
}
