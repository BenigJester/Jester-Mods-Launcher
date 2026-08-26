package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.text.TextUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ContextCompat;

/**
 * Keeps clipboard calls from virtual apps inside the host app's real identity.
 * Modern Android validates the calling package and AttributionSource against
 * the Binder UID, so forwarding the guest identity causes a SecurityException.
 */
public final class IClipboardManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IClipboardManagerProxy";

    public IClipboardManagerProxy() {
        super(BRServiceManager.get().getService(Context.CLIPBOARD_SERVICE));
    }

    @Override
    protected Object getWho() {
        IBinder binder = BRServiceManager.get().getService(Context.CLIPBOARD_SERVICE);
        if (binder == null) {
            return null;
        }
        try {
            Class<?> stub = Class.forName("android.content.IClipboard$Stub");
            Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
            asInterface.setAccessible(true);
            return asInterface.invoke(null, binder);
        } catch (Throwable error) {
            Slog.e(TAG, "Unable to resolve IClipboard", error);
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.CLIPBOARD_SERVICE);
        replaceCachedClipboardService(proxyInvocation);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceAllAppPkg(args);
        rewriteAttributionSources(args);
        return super.invoke(proxy, method, args);
    }

    @Override
    public boolean isBadEnv() {
        IBinder binder = BRServiceManager.get().getService(Context.CLIPBOARD_SERVICE);
        return binder != null && binder != this;
    }

    private static void rewriteAttributionSources(Object[] args) {
        if (args == null) {
            return;
        }
        String hostPackage = BlackBoxCore.getHostPkg();
        int hostUid = BlackBoxCore.getHostUid();
        if (TextUtils.isEmpty(hostPackage) || hostUid <= 0) {
            return;
        }
        for (Object arg : args) {
            if (arg != null && "android.content.AttributionSource".equals(arg.getClass().getName())) {
                ContextCompat.fixAttributionSourceState(arg, hostPackage, hostUid);
            }
        }
    }

    private static void replaceCachedClipboardService(Object proxyInvocation) {
        Context context = BlackBoxCore.getContext();
        if (context == null || proxyInvocation == null) {
            return;
        }
        try {
            Object clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager == null) {
                return;
            }
            Field service = findField(clipboardManager.getClass(), "mService");
            if (service != null) {
                service.setAccessible(true);
                service.set(clipboardManager, proxyInvocation);
            }
        } catch (Throwable error) {
            Slog.w(TAG, "Unable to replace cached ClipboardManager service: " + error);
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
