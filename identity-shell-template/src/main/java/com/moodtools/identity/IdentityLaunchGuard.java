package com.moodtools.identity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import top.niunaijun.blackbox.core.env.BEnvironment;

/** Verifies Jester-issued launch tickets and shares the resulting mode with guest processes. */
final class IdentityLaunchGuard {
    private static final String TAG = "IdentityLaunchGuard";
    private static final int SCHEMA = 1;
    private static final String EXTRA_SCHEMA = "com.moodtools.identity.guard.SCHEMA";
    private static final String EXTRA_ISSUED_AT = "com.moodtools.identity.guard.ISSUED_AT";
    private static final String EXTRA_NONCE = "com.moodtools.identity.guard.NONCE";
    private static final String EXTRA_SIGNATURE = "com.moodtools.identity.guard.SIGNATURE";
    private static final String MESSAGE_PREFIX = "jester-identity-shell-launch-v1";
    private static final String MODE_FILE = "identity-launch-mode-v1";
    private static final String MODE_FULL = "full";
    private static final long MAX_TICKET_AGE_MILLIS = 30_000L;
    private static final long MAX_CLOCK_SKEW_MILLIS = 5_000L;

    private IdentityLaunchGuard() { }

    static boolean authorize(Context context, Intent intent) {
        if (context == null || intent == null) return false;
        try {
            if (intent.getIntExtra(EXTRA_SCHEMA, 0) != SCHEMA) return false;
            long issuedAt = intent.getLongExtra(EXTRA_ISSUED_AT, 0L);
            long now = System.currentTimeMillis();
            if (issuedAt <= 0L || issuedAt > now + MAX_CLOCK_SKEW_MILLIS
                    || now - issuedAt > MAX_TICKET_AGE_MILLIS) return false;
            String nonce = intent.getStringExtra(EXTRA_NONCE);
            String encodedSignature = intent.getStringExtra(EXTRA_SIGNATURE);
            if (nonce == null || !nonce.matches("[0-9a-f]{32}")
                    || encodedSignature == null || encodedSignature.length() > 1024) return false;
            PublicKey publicKey = ownSigningKey(context);
            if (publicKey == null) return false;
            java.security.Signature verifier = java.security.Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(message(context.getPackageName(), issuedAt, nonce));
            boolean authorized = verifier.verify(Base64.decode(encodedSignature, Base64.NO_WRAP));
            Log.i(TAG, authorized ? "Verified Jester launch ticket" : "Rejected launch ticket");
            return authorized;
        } catch (Throwable error) {
            Log.w(TAG, "Launch ticket verification failed", error);
            return false;
        } finally {
            clearTicket(intent);
        }
    }

    static void persistMode(Context context, boolean fullModule) throws Exception {
        File directory = modeDirectory(context);
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Could not create identity launch-mode directory");
        }
        File target = new File(directory, MODE_FILE);
        File incoming = new File(directory, MODE_FILE + ".incoming");
        if (incoming.exists() && !incoming.delete()) {
            throw new IllegalStateException("Could not replace identity launch mode");
        }
        byte[] value = (fullModule ? MODE_FULL : "compatibility")
                .getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(incoming)) {
            output.write(value);
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Could not replace identity launch mode");
        }
        if (!incoming.renameTo(target)) {
            throw new IllegalStateException("Could not finalize identity launch mode");
        }
    }

    static boolean isFullModuleAuthorized(Context context) {
        try {
            File file = new File(modeDirectory(context), MODE_FILE);
            if (!file.isFile() || file.length() > 32L) return false;
            return MODE_FULL.equals(new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Keep the mode beside the staged module payload. BlackBox redirects ordinary app-private
     * paths after guest binding, while BEnvironment resolves to the same physical directory in
     * both the outer shell and its virtualized game process.
     */
    private static File modeDirectory(Context context) {
        return BEnvironment.getDataFilesDir(context.getPackageName(), 0);
    }

    @SuppressWarnings("deprecation")
    private static PublicKey ownSigningKey(Context context) throws Exception {
        PackageManager manager = context.getPackageManager();
        PackageInfo info;
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info = manager.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        } else {
            info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length != 1) return null;
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(signatures[0].toByteArray()));
        return certificate.getPublicKey();
    }

    private static byte[] message(String packageName, long issuedAt, String nonce) {
        return (MESSAGE_PREFIX + "\n" + packageName + "\n" + issuedAt + "\n" + nonce)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void clearTicket(Intent intent) {
        intent.removeExtra(EXTRA_SCHEMA);
        intent.removeExtra(EXTRA_ISSUED_AT);
        intent.removeExtra(EXTRA_NONCE);
        intent.removeExtra(EXTRA_SIGNATURE);
    }
}
