package com.moodtools.gmscore;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.security.MessageDigest;
import com.moodtools.diagnostics.DiagnosticRuntime;

/** Narrow trust/routing adapter for the independently signed ReVanced companion. */
public final class GmsCoreBridge {
    private static final String PACKAGE = "app.revanced.android.gms";
    private static final String SIGNER = "d732e4055c97f8d7a43170a6dc737dc71b6f3e0410940e1b3706c6ade1ff3488";
    private static final java.util.concurrent.atomic.AtomicInteger lastAvailability = new java.util.concurrent.atomic.AtomicInteger(-1);

    @SuppressWarnings("deprecation")
    public static boolean trusted(PackageInfo info) {
        if (info == null || !PACKAGE.equals(info.packageName) || info.signatures == null || info.signatures.length != 1) return false;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(info.signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return SIGNER.equals(hex.toString());
        } catch (Exception error) { return false; }
    }

    @SuppressWarnings("deprecation")
    public static int available(Context context, int minimumVersion) {
        int result;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE, PackageManager.GET_SIGNATURES);
            if (!trusted(info)) result = 9;
            else if (info.versionCode / 1000 < minimumVersion / 1000) result = 2;
            else result = info.applicationInfo != null && info.applicationInfo.enabled ? 0 : 3;
        } catch (PackageManager.NameNotFoundException missing) { result = 1; }
          catch (SecurityException blocked) { result = 9; }
        // Fixed numeric state only. No account details, exception messages or tokens.
        if (lastAvailability.getAndSet(result) != result) DiagnosticRuntime.event("gmscore_availability=" + result);
        return result;
    }
}
