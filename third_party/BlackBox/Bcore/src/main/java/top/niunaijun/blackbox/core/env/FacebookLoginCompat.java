package top.niunaijun.blackbox.core.env;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.regex.Pattern;

/** Compatibility helpers for Facebook login from any application hosted by BlackBox. */
public final class FacebookLoginCompat {
    public static final String FACEBOOK_PACKAGE = "com.facebook.katana";
    public static final String FACEBOOK_PLATFORM_ACTIVITY = "com.facebook.platform.PLATFORM_ACTIVITY";
    public static final String CUSTOM_TAB_MAIN_ACTIVITY = "com.facebook.CustomTabMainActivity";
    public static final String CUSTOM_TAB_REDIRECT_ACTION =
            "CustomTabActivity.action_customTabRedirect";
    public static final String CUSTOM_TAB_REDIRECT_URL_EXTRA = "CustomTabMainActivity.extra_url";

    private static final String CUSTOM_TAB_CALLBACK_SCHEME = "fbconnect";
    private static final String CUSTOM_TAB_CALLBACK_HOST_PREFIX = "cct.";
    private static final Pattern ANDROID_PACKAGE_NAME = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    private static final String FACEBOOK_PLATFORM_WRAPPER_ACTIVITY =
            "com.facebook.platform.common.activity.PlatformWrapperActivity";
    private static final String FACEBOOK_LEGACY_PROXY_AUTH_ACTIVITY =
            "com.facebook.katana.ProxyAuth";
    private static final String FACEBOOK_PROXY_AUTH_ACTIVITY = "com.facebook.gdp.ProxyAuth";
    private static final String FACEBOOK_WEB_VIEW_PROXY_AUTH_ACTIVITY =
            "com.facebook.katana.gdp.WebViewProxyAuth";
    private static final String FACEBOOK_PROXY_AUTH_DIALOG_ACTIVITY =
            "com.facebook.katana.gdp.ProxyAuthDialog";

    private FacebookLoginCompat() {
    }

    public static boolean shouldHideNativeLogin(String callerPackage, Intent intent) {
        if (!isGuestCaller(callerPackage) || intent == null) {
            return false;
        }

        ComponentName component = intent.getComponent();
        if (isNativeLoginComponent(component)) {
            return true;
        }

        String targetPackage = intent.getPackage();
        if (targetPackage == null && component != null) {
            targetPackage = component.getPackageName();
        }
        return FACEBOOK_PLATFORM_ACTIVITY.equals(intent.getAction())
                && (targetPackage == null || FACEBOOK_PACKAGE.equals(targetPackage));
    }

    public static boolean shouldHideNativeLogin(
            String callerPackage, ComponentName componentName) {
        return isGuestCaller(callerPackage) && isNativeLoginComponent(componentName);
    }

    /**
     * Returns the guest package encoded by Facebook SDK's cct.&lt;package&gt; callback host.
     * Invalid, non-canonical, and package-less callback URIs return {@code null}.
     */
    public static String getCustomTabCallbackPackage(Uri uri) {
        if (uri == null
                || !CUSTOM_TAB_CALLBACK_SCHEME.equals(uri.getScheme())
                || uri.getUserInfo() != null
                || uri.getPort() != -1) {
            return null;
        }

        String host = uri.getHost();
        if (host == null || !host.startsWith(CUSTOM_TAB_CALLBACK_HOST_PREFIX)) {
            return null;
        }

        String packageName = host.substring(CUSTOM_TAB_CALLBACK_HOST_PREFIX.length());
        if (!ANDROID_PACKAGE_NAME.matcher(packageName).matches()
                || !host.equals(uri.getEncodedAuthority())) {
            return null;
        }
        return packageName;
    }

    public static boolean isCustomTabCallbackForPackage(Uri uri, String packageName) {
        return packageName != null && packageName.equals(getCustomTabCallbackPackage(uri));
    }

    public static boolean isCustomTabRedirect(Intent intent) {
        if (intent == null || !CUSTOM_TAB_REDIRECT_ACTION.equals(intent.getAction())) {
            return false;
        }
        ComponentName component = intent.getComponent();
        String callbackUrl = intent.getStringExtra(CUSTOM_TAB_REDIRECT_URL_EXTRA);
        return component != null
                && CUSTOM_TAB_MAIN_ACTIVITY.equals(component.getClassName())
                && callbackUrl != null
                && isCustomTabCallbackForPackage(
                Uri.parse(callbackUrl), component.getPackageName());
    }

    public static boolean isNativeLoginComponent(ComponentName componentName) {
        if (componentName == null
                || !FACEBOOK_PACKAGE.equals(componentName.getPackageName())) {
            return false;
        }

        String className = componentName.getClassName();
        if (className == null) {
            return false;
        }
        if (className.startsWith(".")) {
            className = FACEBOOK_PACKAGE + className;
        }
        return FACEBOOK_PLATFORM_WRAPPER_ACTIVITY.equals(className)
                || FACEBOOK_LEGACY_PROXY_AUTH_ACTIVITY.equals(className)
                || FACEBOOK_PROXY_AUTH_ACTIVITY.equals(className)
                || FACEBOOK_WEB_VIEW_PROXY_AUTH_ACTIVITY.equals(className)
                || FACEBOOK_PROXY_AUTH_DIALOG_ACTIVITY.equals(className);
    }

    public static boolean isNativeFacebookActivity(ResolveInfo resolveInfo) {
        if (resolveInfo == null) {
            return false;
        }
        ActivityInfo activityInfo = resolveInfo.activityInfo;
        return activityInfo != null
                && isNativeLoginComponent(new ComponentName(
                activityInfo.packageName, activityInfo.name));
    }

    private static boolean isGuestCaller(String callerPackage) {
        return callerPackage != null
                && !callerPackage.isEmpty()
                && !callerPackage.startsWith("com.facebook.");
    }
}
