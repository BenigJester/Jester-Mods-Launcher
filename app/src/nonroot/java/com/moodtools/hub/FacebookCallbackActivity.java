package com.moodtools.hub;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.FacebookLoginCompat;

/** Returns a physical Facebook browser callback to its live transaction inside BlackBox. */
public final class FacebookCallbackActivity extends Activity {
    private static final String TAG = "FacebookLoginRelay";
    private static final int DEFAULT_USER_ID = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        forwardCallback(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        forwardCallback(intent);
        finish();
    }

    private void forwardCallback(Intent source) {
        Uri callbackUri = source == null ? null : source.getData();
        String guestPackage = FacebookLoginCompat.getCustomTabCallbackPackage(callbackUri);
        if (guestPackage == null) {
            Log.w(TAG, "Ignoring an invalid Facebook Custom Tab callback");
            return;
        }

        ComponentName target = new ComponentName(
                guestPackage, FacebookLoginCompat.CUSTOM_TAB_MAIN_ACTIVITY);
        try {
            BlackBoxCore core = BlackBoxCore.get();
            if (!core.isInstalled(guestPackage, DEFAULT_USER_ID)
                    || BlackBoxCore.getBPackageManager().getActivityInfo(
                    target, PackageManager.GET_META_DATA, DEFAULT_USER_ID) == null) {
                Log.w(TAG, "Ignoring a Facebook callback without a matching virtual guest");
                return;
            }

            Intent redirect = new Intent(FacebookLoginCompat.CUSTOM_TAB_REDIRECT_ACTION);
            redirect.setComponent(target);
            redirect.putExtra(
                    FacebookLoginCompat.CUSTOM_TAB_REDIRECT_URL_EXTRA,
                    callbackUri.toString());
            redirect.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            Log.i(TAG, "Forwarding Facebook callback into virtual guest " + guestPackage);
            // Bypass BlackBoxCore.startActivity(), which routes explicit guest components through
            // LauncherActivity. The virtual activity manager can deliver this callback directly
            // to the already-running Facebook CustomTabMainActivity transaction.
            BlackBoxCore.getBActivityManager().startActivity(redirect, DEFAULT_USER_ID);
        } catch (Throwable error) {
            Log.w(TAG, "Could not forward Facebook callback into the virtual guest", error);
        }
    }
}
