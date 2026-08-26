package com.android.support;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DelegateLastClassLoader;

/** Stable Bootstrap 4 storage and early class-loader bridge for replaceable menu DEX modules. */
public final class DexModuleStore {
    static final String MODE_MODULE = "module";
    private static final String ROOT = "mood-tools-module";
    private static final String DEX_NAME = "menu-module.dex";
    private static final String METADATA_NAME = "module.json";
    private static final long MAX_MODULE_BYTES = 16L * 1024L * 1024L;

    private DexModuleStore() {
    }

    public static synchronized CachedModule install(Context context, File source, long build,
                                                    String sha256, String baseSha256) throws Exception {
        if (context == null || source == null || !source.isFile() || build <= 0) {
            throw new IllegalArgumentException("Downloaded menu module is missing.");
        }
        String normalizedSha = normalizeSha(sha256);
        String normalizedBase = normalizeSha(baseSha256);
        if (source.length() <= 0 || source.length() > MAX_MODULE_BYTES
                || normalizedSha.length() != 64 || normalizedBase.length() != 64
                || !normalizedSha.equals(hash(source))) {
            throw new SecurityException("Downloaded menu module failed verification.");
        }

        File root = root(context.getApplicationInfo());
        if (!root.exists() && !root.mkdirs()) throw new Exception("Could not create menu module storage.");
        File temporaryDex = new File(root, DEX_NAME + ".tmp");
        File destinationDex = new File(root, DEX_NAME);
        copy(source, temporaryDex);
        if (!normalizedSha.equals(hash(temporaryDex))) {
            temporaryDex.delete();
            throw new SecurityException("Staged menu module failed verification.");
        }
        destinationDex.setWritable(true, true);
        if (destinationDex.exists() && !destinationDex.delete()) {
            throw new Exception("Could not replace the previous menu module.");
        }
        if (!temporaryDex.renameTo(destinationDex)) throw new Exception("Could not activate menu module file.");
        destinationDex.setReadable(true, true);
        destinationDex.setWritable(false, false);

        JSONObject metadata = new JSONObject();
        metadata.put("schema", 1);
        metadata.put("build", build);
        metadata.put("sha256", normalizedSha);
        metadata.put("baseSha256", normalizedBase);
        File temporaryMetadata = new File(root, METADATA_NAME + ".tmp");
        File destinationMetadata = new File(root, METADATA_NAME);
        writeText(temporaryMetadata, metadata.toString());
        destinationMetadata.setWritable(true, true);
        if (destinationMetadata.exists() && !destinationMetadata.delete()) {
            throw new Exception("Could not replace menu module metadata.");
        }
        if (!temporaryMetadata.renameTo(destinationMetadata)) {
            throw new Exception("Could not activate menu module metadata.");
        }
        destinationMetadata.setReadable(true, true);
        destinationMetadata.setWritable(false, false);
        return new CachedModule(build, normalizedSha, normalizedBase, destinationDex);
    }

    public static CachedModule readValid(Context context, NativePayloadLoader.BundledPayload bundled) {
        if (context == null || bundled == null) return null;
        return readValid(context.getApplicationInfo(), bundled.sha256);
    }

    static ClassLoader createPatchedClassLoader(ApplicationInfo info, ClassLoader parent) {
        if (android.os.Build.VERSION.SDK_INT < 28 || info == null || parent == null) return parent;
        try {
            String bundledSha = readBundledSha(info);
            CachedModule module = readValid(info, bundledSha);
            if (module == null) return parent;
            return new DelegateLastClassLoader(
                    module.file.getAbsolutePath(), info.nativeLibraryDir, parent);
        } catch (Throwable ignored) {
            return parent;
        }
    }

    public static void clear(Context context) {
        if (context == null) return;
        File root = root(context.getApplicationInfo());
        deleteKnownFile(new File(root, DEX_NAME));
        deleteKnownFile(new File(root, DEX_NAME + ".tmp"));
        deleteKnownFile(new File(root, METADATA_NAME));
        deleteKnownFile(new File(root, METADATA_NAME + ".tmp"));
    }

    private static CachedModule readValid(ApplicationInfo info, String bundledSha) {
        try {
            File root = root(info);
            File metadataFile = new File(root, METADATA_NAME);
            File dexFile = new File(root, DEX_NAME);
            if (!metadataFile.isFile() || !dexFile.isFile() || dexFile.length() <= 0
                    || dexFile.length() > MAX_MODULE_BYTES) return null;
            JSONObject metadata = new JSONObject(readText(metadataFile));
            long build = metadata.getLong("build");
            String sha = normalizeSha(metadata.getString("sha256"));
            String baseSha = normalizeSha(metadata.getString("baseSha256"));
            if (metadata.getInt("schema") != 1 || build <= 0 || sha.length() != 64
                    || !baseSha.equals(normalizeSha(bundledSha)) || !sha.equals(hash(dexFile))) {
                return null;
            }
            return new CachedModule(build, sha, baseSha, dexFile);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static File root(ApplicationInfo info) {
        return new File(new File(info.dataDir, "code_cache"), ROOT);
    }

    private static String readBundledSha(ApplicationInfo info) throws Exception {
        ZipFile apk = new ZipFile(info.sourceDir);
        try {
            ZipEntry entry = apk.getEntry("assets/moodtools/native-payload.json");
            if (entry == null) return "";
            InputStream input = apk.getInputStream(entry);
            try {
                return normalizeSha(new JSONObject(readText(input)).getString("sha256"));
            } finally {
                input.close();
            }
        } finally {
            apk.close();
        }
    }

    private static void copy(File source, File destination) throws Exception {
        InputStream input = new BufferedInputStream(new FileInputStream(source));
        BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination));
        try {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        } finally {
            output.close();
            input.close();
        }
    }

    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream input = new BufferedInputStream(new FileInputStream(file));
        try {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        } finally {
            input.close();
        }
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest()) value.append(String.format(Locale.US, "%02x", item & 0xff));
        return value.toString();
    }

    private static String readText(File file) throws Exception {
        InputStream input = new BufferedInputStream(new FileInputStream(file));
        try { return readText(input); } finally { input.close(); }
    }

    private static String readText(InputStream input) throws Exception {
        StringBuilder value = new StringBuilder();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (value.length() > 64 * 1024) throw new SecurityException("Menu module metadata is too large.");
            value.append(new String(buffer, 0, read, "UTF-8"));
        }
        return value.toString();
    }

    private static void writeText(File file, String value) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        try { output.write(value.getBytes("UTF-8")); } finally { output.close(); }
    }

    private static String normalizeSha(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static void deleteKnownFile(File file) {
        try {
            file.setWritable(true, true);
            file.delete();
        } catch (Throwable ignored) {
        }
    }

    public static final class CachedModule {
        public final long build;
        public final String sha256;
        public final String baseSha256;
        public final File file;

        CachedModule(long build, String sha256, String baseSha256, File file) {
            this.build = build;
            this.sha256 = sha256;
            this.baseSha256 = baseSha256;
            this.file = file;
        }
    }
}
