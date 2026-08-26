package com.android.support;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Verifies that a direct-patched game was opened by Jester Moods. */
final class DirectLaunchGuard {
    static final int SCHEMA = 1;
    static final String EXTRA_SCHEMA = "com.moodtools.directpatch.guard.SCHEMA";
    static final String EXTRA_ISSUED_AT = "com.moodtools.directpatch.guard.ISSUED_AT";
    static final String EXTRA_NONCE = "com.moodtools.directpatch.guard.NONCE";
    static final String EXTRA_SIGNATURE = "com.moodtools.directpatch.guard.SIGNATURE";

    private static final String TAG = "MoodToolsLaunchGuard";
    private static final String MARKER_ENTRY = "assets/moodtools/soul-patch.json";
    private static final String MESSAGE_PREFIX = "jester-direct-patch-launch-v1";
    private static final long MAX_TICKET_AGE_MILLIS = 30_000L;
    private static final long MAX_CLOCK_SKEW_MILLIS = 5_000L;

    private DirectLaunchGuard() { }

    static boolean authorize(ApplicationInfo info, Intent intent) {
        if (info == null || intent == null || info.packageName == null) return false;
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
            PublicKey publicKey = readPublicKey(info);
            if (publicKey == null) return false;
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(message(info.packageName, issuedAt, nonce));
            boolean authorized = verifier.verify(Base64.decode(encodedSignature, Base64.NO_WRAP));
            Log.i(TAG, authorized ? "Verified launcher-origin ticket" : "Rejected launch ticket signature");
            return authorized;
        } catch (Throwable error) {
            Log.w(TAG, "Launch ticket verification failed", error);
            return false;
        } finally {
            clearTicket(intent);
        }
    }

    private static PublicKey readPublicKey(ApplicationInfo info) throws Exception {
        ZipFile archive = new ZipFile(info.sourceDir);
        try {
            ZipEntry marker = archive.getEntry(MARKER_ENTRY);
            if (marker == null) return null;
            InputStream input = archive.getInputStream(marker);
            try {
                byte[] bytes = new byte[4096];
                int total = 0;
                int read;
                while ((read = input.read(bytes, total, bytes.length - total)) > 0) {
                    total += read;
                    if (total == bytes.length) break;
                }
                JSONObject json = new JSONObject(new String(bytes, 0, total, "UTF-8"));
                if (json.optInt("launchGuardSchema", 0) != SCHEMA) return null;
                String encoded = json.optString("launchGuardPublicKey", "");
                if (encoded.length() == 0 || encoded.length() > 2048) return null;
                return KeyFactory.getInstance("RSA").generatePublic(
                        new X509EncodedKeySpec(Base64.decode(encoded, Base64.NO_WRAP)));
            } finally {
                input.close();
            }
        } finally {
            archive.close();
        }
    }

    private static byte[] message(String packageName, long issuedAt, String nonce) throws Exception {
        return (MESSAGE_PREFIX + "\n" + packageName + "\n" + issuedAt + "\n" + nonce)
                .getBytes("UTF-8");
    }

    private static void clearTicket(Intent intent) {
        intent.removeExtra(EXTRA_SCHEMA);
        intent.removeExtra(EXTRA_ISSUED_AT);
        intent.removeExtra(EXTRA_NONCE);
        intent.removeExtra(EXTRA_SIGNATURE);
    }
}
