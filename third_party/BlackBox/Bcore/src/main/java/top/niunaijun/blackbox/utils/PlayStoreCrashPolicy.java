package top.niunaijun.blackbox.utils;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Pins the virtual Play Store helper to BlackBox's known UID-mismatch state for game guests on
 * every Android version supported by the non-root launcher.
 *
 * <p>This deliberately applies only to provider queries made from the virtual Play Store. Guest
 * games and every other virtual package keep their normal provider behavior.</p>
 */
public final class PlayStoreCrashPolicy {
    public static final int MIN_NON_ROOT_API = 26;
    public static final String STATE_ID = "BB-PLAY-UID-001";
    public static final String PLAY_STORE_PACKAGE = "com.android.vending";
    public static final String PLAY_SERVICES_PACKAGE = "com.google.android.gms";
    public static final String GOOGLE_SERVICES_FRAMEWORK_PACKAGE = "com.google.android.gsf";
    public static final String SOURCE_GUEST_EXTRA =
            "_B_|_play_store_crash_source_guest_";
    public static final String SOURCE_SESSION_EXTRA =
            "_B_|_play_store_crash_source_session_";

    private static volatile boolean sUidMismatchArmed;
    private static final String sSourceSessionToken = UUID.randomUUID().toString();
    private static final String CRASH_CLAIM_DIRECTORY = "blackbox_play_store_crash_claims";

    private PlayStoreCrashPolicy() {
    }

    public static boolean shouldArmForServiceBind(
            int sdkInt, String sourceGuestPackage, String servicePackage) {
        return sdkInt >= MIN_NON_ROOT_API
                && isGameGuestPackage(sourceGuestPackage)
                && PLAY_STORE_PACKAGE.equals(servicePackage);
    }

    /**
     * Google infrastructure can bind among its own virtual processes and must not be mistaken for
     * a game launch. Any other non-empty guest package is eligible, which keeps this policy aligned
     * with the launcher's changing module catalog without hard-coding individual games.
     */
    public static boolean isGameGuestPackage(String packageName) {
        return packageName != null
                && !packageName.trim().isEmpty()
                && !"android".equals(packageName)
                && !PLAY_STORE_PACKAGE.equals(packageName)
                && !PLAY_SERVICES_PACKAGE.equals(packageName)
                && !GOOGLE_SERVICES_FRAMEWORK_PACKAGE.equals(packageName)
                && !packageName.startsWith("com.google.android.");
    }

    public static String sourceSessionToken() {
        return sSourceSessionToken;
    }

    /**
     * Persists the first crash claim before Play Store application startup. Android may recreate
     * the proxy service directly after the process dies; recreated processes observe this claim
     * and return a dead binding without entering another crash loop.
     */
    public static boolean claimReferenceCrash(Context context, int userId, String sessionToken) {
        if (context == null || sessionToken == null
                || !sessionToken.matches("[A-Za-z0-9-]{1,80}")) {
            return false;
        }
        File claimDirectory = new File(context.getFilesDir(), CRASH_CLAIM_DIRECTORY);
        if (!claimDirectory.exists() && !claimDirectory.mkdirs()) {
            // If the limiter cannot persist, preserve the requested crash instead of hiding it.
            return true;
        }
        File claim = new File(claimDirectory, "u" + userId + "-" + sessionToken + ".claim");
        try {
            boolean firstClaim = claim.createNewFile();
            if (firstClaim) {
                File[] claims = claimDirectory.listFiles();
                if (claims != null) {
                    String userPrefix = "u" + userId + "-";
                    for (File oldClaim : claims) {
                        if (!claim.equals(oldClaim) && oldClaim.getName().startsWith(userPrefix)) {
                            oldClaim.delete();
                        }
                    }
                }
            }
            return firstClaim;
        } catch (IOException ignored) {
            return true;
        }
    }

    public static boolean hasReferenceCrashClaim(Context context, int userId) {
        if (context == null) {
            return false;
        }
        File claimDirectory = new File(context.getFilesDir(), CRASH_CLAIM_DIRECTORY);
        File[] claims = claimDirectory.listFiles();
        if (claims == null) {
            return false;
        }
        String userPrefix = "u" + userId + "-";
        for (File claim : claims) {
            if (claim.isFile() && claim.getName().startsWith(userPrefix)) {
                return true;
            }
        }
        return false;
    }

    public static void clearReferenceCrashClaims(
            Context context, String packageName, int userId) {
        if (context == null || !isGameGuestPackage(packageName)) {
            return;
        }
        File claimDirectory = new File(context.getFilesDir(), CRASH_CLAIM_DIRECTORY);
        File[] claims = claimDirectory.listFiles();
        if (claims == null) {
            return;
        }
        String userPrefix = "u" + userId + "-";
        for (File claim : claims) {
            if (claim.isFile() && claim.getName().startsWith(userPrefix)) {
                claim.delete();
            }
        }
    }

    /** Arms only the Play Store process which receives an eligible game guest's service bind. */
    public static boolean armForServiceBind(
            int sdkInt, String sourceGuestPackage, String servicePackage) {
        boolean shouldArm = shouldArmForServiceBind(
                sdkInt, sourceGuestPackage, servicePackage);
        if (shouldArm) {
            sUidMismatchArmed = true;
        }
        return shouldArm;
    }

    public static boolean shouldForceUidMismatch(
            int sdkInt, boolean armed, String virtualPackage, String providerMethod) {
        return armed
                && sdkInt >= MIN_NON_ROOT_API
                && PLAY_STORE_PACKAGE.equals(virtualPackage)
                && "query".equals(providerMethod);
    }

    public static boolean isUidMismatchArmed() {
        return sUidMismatchArmed;
    }

    public static String uidMismatchMessage(int hostUid) {
        return "Package " + PLAY_STORE_PACKAGE + " does not belong to " + hostUid;
    }

    public static SecurityException uidMismatchException(int hostUid) {
        return new SecurityException(uidMismatchMessage(hostUid));
    }
}
