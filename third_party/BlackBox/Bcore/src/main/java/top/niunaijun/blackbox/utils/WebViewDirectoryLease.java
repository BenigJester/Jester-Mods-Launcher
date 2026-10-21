package top.niunaijun.blackbox.utils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Process-lifetime ownership, separate from Chromium's own lock (which we never touch). */
public final class WebViewDirectoryLease implements Closeable {
    private static final int DIRECTORY_COUNT = 4;
    public final String suffix;
    public final boolean alternate;
    private final RandomAccessFile file;
    private final FileLock lock;

    private WebViewDirectoryLease(String suffix, boolean alternate, RandomAccessFile file, FileLock lock) {
        this.suffix = suffix;
        this.alternate = alternate;
        this.file = file;
        this.lock = lock;
    }

    public static WebViewDirectoryLease acquire(File ownershipRoot, String primarySuffix) throws IOException {
        if (primarySuffix == null || primarySuffix.isEmpty() || primarySuffix.contains("/")
                || primarySuffix.contains("\\")) throw new IOException("Invalid WebView directory identity");
        if (!ownershipRoot.isDirectory() && !ownershipRoot.mkdirs() && !ownershipRoot.isDirectory())
            throw new IOException("Cannot create WebView ownership directory");
        final String key;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(primarySuffix.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            key = hex.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        // Bounded, stable alternatives: no PID-named profiles or deletion of existing cookies/data.
        for (int index = 0; index < DIRECTORY_COUNT; index++) {
            RandomAccessFile file = new RandomAccessFile(new File(ownershipRoot, key + "-" + index + ".lock"), "rw");
            boolean retained = false;
            try {
                FileLock lock;
                try { lock = file.getChannel().tryLock(); }
                catch (OverlappingFileLockException busy) { lock = null; }
                if (lock != null) {
                    retained = true;
                    // Primary suffixes start with the integer virtual user ID. A nonnumeric
                    // prefix cannot alias another guest process whose name ends in ':bb1'.
                    return new WebViewDirectoryLease(index == 0 ? primarySuffix : "bb-alternate-" + index + ":" + primarySuffix,
                            index != 0, file, lock);
                }
            } finally { if (!retained) file.close(); }
        }
        throw new IOException("All WebView directory owners are busy");
    }

    @Override public void close() throws IOException {
        try { if (lock.isValid()) lock.release(); }
        finally { file.close(); }
    }
}
