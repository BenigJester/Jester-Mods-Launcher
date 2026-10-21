package top.niunaijun.blackbox.fake.service;

import android.os.IInterface;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;

/** Samsung's EditText reads a separate clipboard service, not android.content.IClipboard. */
public final class ISemClipboardManagerProxy extends ClassInvocationStub {
    private static final String TAG = "ISemClipboardManagerProxy";
    private Field serviceField;

    @Override
    protected Object getWho() {
        try {
            Class<?> manager = Class.forName("com.samsung.android.content.clipboard.SemClipboardManager");
            // Samsung renamed both the accessor and cached field on newer firmware.
            for (String name : new String[]{"getService", "getSemService"}) {
                try {
                    Method accessor = manager.getDeclaredMethod(name);
                    if (Modifier.isStatic(accessor.getModifiers())) {
                        accessor.setAccessible(true);
                        accessor.invoke(null);
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
            for (String name : new String[]{"sService", "mSemService"}) {
                try {
                    Field field = manager.getDeclaredField(name);
                    if (!Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object service = field.get(null);
                    if (service instanceof IInterface) {
                        serviceField = field;
                        return service;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
        } catch (ClassNotFoundException ignored) {
            // Optional OEM service: nothing to install on other devices.
        } catch (ReflectiveOperationException | RuntimeException error) {
            Slog.w(TAG, "Samsung clipboard hook unavailable: " + error.getClass().getSimpleName());
        }
        return null;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        try {
            serviceField.set(null, proxyInvocation);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Unable to replace Samsung clipboard cache", error);
        }
    }

    @Override
    public boolean isBadEnv() {
        if (serviceField == null || getProxyInvocation() == null) return false;
        try {
            return serviceField.get(null) != getProxyInvocation();
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceAllAppPkg(args);
        MethodParameterUtils.replaceFirstUid(args);
        return invokeClipboard(getBase(), method, args);
    }

    static Object invokeClipboard(Object service, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(service, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            // Android 10 Samsung EditText queries this while gaining focus. A permission
            // denial means no OEM clipboard item, not a fatal game error. Never bypass it.
            if (cause instanceof SecurityException && "getClipData".equals(method.getName())
                    && !method.getReturnType().isPrimitive()) {
                return null;
            }
            throw cause;
        }
    }
}
