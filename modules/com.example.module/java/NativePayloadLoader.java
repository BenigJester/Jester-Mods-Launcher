package com.android.support;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

final class NativePayloadLoader {
    static final String LIBRARY_NAME = "YourSaviour";
    static final String PAYLOAD_ASSET = "moodtools/native-payload.json";
    private static final String TAG = "MoodToolsLoader";

    private static final String PREFS = "mood_tools_native_update";
    private static final String KEY_BUILD = "build";
    private static final String KEY_SHA256 = "sha256";
    private static final String KEY_BASE_SHA256 = "base_sha256";
    private static final String KEY_ABI = "abi";
    private static final String KEY_PATH = "path";

    private static boolean loaded;
    private static String loadedDescription = "not loaded";

    private NativePayloadLoader() {
    }

    private static native String nativeProbe();

    static synchronized String ensureLoaded(Context context) {
        if (loaded) {
            Log.i(TAG, "Native payload already loaded: " + loadedDescription);
            logNativeProbe("already-loaded");
            return loadedDescription;
        }

        Log.i(TAG, "ensureLoaded abi=" + currentAbi()
                + " context=" + (context == null ? "null" : context.getClass().getName()));

        if (context != null) {
            BundledPayload bundled = readBundledPayload(context);
            Log.i(TAG, "bundled payload="
                    + (bundled == null ? "null" : bundled.abi + ":" + bundled.sha256));
            CachedPayload cached = readValidCachedPayload(context, bundled, currentAbi());
            if (cached != null) {
                try {
                    Log.i(TAG, "loading cached native payload " + cached.file.getAbsolutePath());
                    loadAbsolute(cached.file);
                    loadedDescription = "updated native payload " + cached.build;
                    Log.i(TAG, "loaded " + loadedDescription);
                    logNativeProbe("cached");
                    return loadedDescription;
                } catch (Throwable ignored) {
                    Log.e(TAG, "cached native payload load failed", ignored);
                }
            }

            File recovered = findSelfVerifiedCachedPayload(context, currentAbi());
            if (recovered != null) {
                Log.i(TAG, "loading recovered native payload " + recovered.getAbsolutePath());
                loadAbsolute(recovered);
                loadedDescription = "recovered cached native payload";
                Log.i(TAG, "loaded " + loadedDescription);
                logNativeProbe("recovered");
                return loadedDescription;
            }
        }

        try {
            Log.i(TAG, "loading bundled library " + LIBRARY_NAME);
            System.loadLibrary(LIBRARY_NAME);
            loaded = true;
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "bundled native payload load failed", error);
            if (!isAlreadyOpenedByAnotherClassLoader(error)) throw error;
            loaded = true;
        }
        loadedDescription = isRunningFromModuleClassLoader()
                ? "bundled native payload fallback for updated menu module"
                : "bundled native payload";
        Log.i(TAG, "loaded " + loadedDescription);
        logNativeProbe("bundled");
        return loadedDescription;
    }

    private static void logNativeProbe(String stage) {
        try {
            Log.i(TAG, "native probe " + stage + ": " + nativeProbe());
        } catch (Throwable error) {
            Log.e(TAG, "native probe failed at " + stage, error);
        }
    }

    static synchronized String installPayload(Context context, File payload, long build,
                                              String sha256, String baseSha256, String abi)
            throws Exception {
        if (context == null || payload == null || !payload.isFile()) {
            throw new IllegalArgumentException("Downloaded native payload is missing.");
        }
        if (!normalizeSha(sha256).equals(sha256(payload))) {
            throw new SecurityException("Downloaded native payload failed SHA-256 verification.");
        }
        if (!payload.setReadable(true, true) || !payload.setWritable(false, false)) {
            throw new SecurityException("Downloaded native payload could not be made read-only.");
        }
        boolean saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_BUILD, build)
                .putString(KEY_SHA256, normalizeSha(sha256))
                .putString(KEY_BASE_SHA256, normalizeSha(baseSha256))
                .putString(KEY_ABI, abi)
                .putString(KEY_PATH, payload.getAbsolutePath())
                .commit();
        if (!saved) {
            throw new SecurityException("Cached native payload metadata could not be saved.");
        }
        return "cached native payload " + build;
    }

    static BundledPayload readBundledPayload(Context context) {
        if (context == null) return null;
        InputStream input = null;
        try {
            input = context.getAssets().open(PAYLOAD_ASSET);
            StringBuilder text = new StringBuilder();
            byte[] buffer = new byte[2048];
            int read;
            while ((read = input.read(buffer)) != -1) {
                text.append(new String(buffer, 0, read, "UTF-8"));
            }
            JSONObject json = new JSONObject(text.toString());
            String sha = normalizeSha(json.optString("sha256", ""));
            String abi = json.optString("abi", "");
            if (sha.length() != 64 || abi.length() == 0) return null;
            return new BundledPayload(sha, abi);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    static CachedPayload readValidCachedPayload(Context context, BundledPayload bundled, String abi) {
        if (context == null || bundled == null || abi == null) return null;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long build = preferences.getLong(KEY_BUILD, 0L);
        String sha = normalizeSha(preferences.getString(KEY_SHA256, ""));
        String baseSha = normalizeSha(preferences.getString(KEY_BASE_SHA256, ""));
        String cachedAbi = preferences.getString(KEY_ABI, "");
        String path = preferences.getString(KEY_PATH, "");
        if (build <= 0 || sha.length() != 64 || !bundled.sha256.equals(baseSha)
                || !abi.equals(cachedAbi) || path.length() == 0) {
            return null;
        }

        File file = new File(path);
        try {
            if (!isInside(file, updateRoot(context)) || !file.isFile() || !sha.equals(sha256(file))) {
                return null;
            }
            file.setReadable(true, true);
            file.setWritable(false, false);
            return new CachedPayload(build, sha, baseSha, abi, file);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static File updateRoot(Context context) {
        return new File(context.getCodeCacheDir(), "mood-tools-native");
    }

    private static File findSelfVerifiedCachedPayload(Context context, String abi) {
        if (context == null || abi == null || abi.length() == 0) return null;
        File root = new File(updateRoot(context), abi);
        File[] files = root.listFiles();
        if (files == null) return null;
        for (File file : files) {
            try {
                String name = file.getName();
                String prefix = "libYourSaviour-";
                String suffix = ".so";
                if (!name.startsWith(prefix) || !name.endsWith(suffix)) continue;
                String sha = normalizeSha(name.substring(prefix.length(),
                        name.length() - suffix.length()));
                if (sha.length() != 64 || !file.isFile() || !isInside(file, root)) continue;
                if (!sha.equals(sha256(file))) continue;
                file.setReadable(true, true);
                file.setWritable(false, false);
                return file;
            } catch (Throwable ignored) {
            }
        }
        return null;
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

    static String currentAbi() {
        if (Build.VERSION.SDK_INT >= 21 && Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if ("arm64-v8a".equals(abi) || "armeabi-v7a".equals(abi)) return abi;
            }
        }
        return Build.CPU_ABI;
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
        for (byte value : digest.digest()) result.append(String.format(Locale.US, "%02x", value & 0xff));
        return result.toString();
    }

    private static void loadAbsolute(File file) {
        try {
            System.load(file.getAbsolutePath());
            loaded = true;
        } catch (UnsatisfiedLinkError error) {
            if (!isAlreadyOpenedByAnotherClassLoader(error)) throw error;
            loaded = true;
        }
    }

    private static boolean isAlreadyOpenedByAnotherClassLoader(Throwable error) {
        String message = error == null ? "" : String.valueOf(error.getMessage());
        return message.contains("already opened by ClassLoader");
    }

    private static boolean isRunningFromModuleClassLoader() {
        try {
            String text = String.valueOf(NativePayloadLoader.class.getClassLoader());
            return text.contains("mood-tools-module") || text.contains("DelegateLastClassLoader");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String normalizeSha(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static boolean isInside(File child, File parent) throws Exception {
        String childPath = child.getCanonicalPath();
        String parentPath = parent.getCanonicalPath();
        return childPath.startsWith(parentPath + File.separator);
    }

    static final class BundledPayload {
        final String sha256;
        final String abi;

        BundledPayload(String sha256, String abi) {
            this.sha256 = sha256;
            this.abi = abi;
        }
    }

    static final class CachedPayload {
        final long build;
        final String sha256;
        final String baseSha256;
        final String abi;
        final File file;

        CachedPayload(long build, String sha256, String baseSha256, String abi, File file) {
            this.build = build;
            this.sha256 = sha256;
            this.baseSha256 = baseSha256;
            this.abi = abi;
            this.file = file;
        }
    }
}
