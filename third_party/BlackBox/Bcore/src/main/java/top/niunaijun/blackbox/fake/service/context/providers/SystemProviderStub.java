package top.niunaijun.blackbox.fake.service.context.providers;

import android.os.IInterface;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import black.android.content.BRAttributionSource;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.utils.compat.ContextCompat;


public class SystemProviderStub extends ClassInvocationStub implements BContentProvider {
    private IInterface mBase;

    @Override
    public IInterface wrapper(IInterface contentProviderProxy, String appPkg) {
        mBase = contentProviderProxy;
        injectHook();
        return (IInterface) getProxyInvocation();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    protected void onBindMethod() {

    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Never rewrite a caller-owned array or its cached Context attribution in place.
        args = args == null ? null : args.clone();
        if ("asBinder".equals(method.getName())) {
            return invokeProvider(mBase, method, args);
        }
        
        String methodName = method.getName();
        
        
        
        if ("call".equals(methodName)) {
            // This proxy forwards into a real Android system provider. Binder still sees the
            // launcher as the caller, so every explicit caller identity must describe the host
            // package/UID as well. Android 11 passes the calling package as args[0]; newer
            // releases use AttributionSource. Forwarding the virtual guest identity makes strict
            // providers (notably SettingsProvider) reject the request in AppOps.
            if (args != null) {
                String hostPackage = BlackBoxCore.getHostPkg();
                int hostUid = BlackBoxCore.getHostUid();
                if (args.length > 0 && args[0] instanceof String) {
                    args[0] = hostPackage;
                }
                Class<?> attributionSourceClass = BRAttributionSource.getRealClass();
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];

                    if (arg != null && attributionSourceClass != null &&
                            arg.getClass().getName().equals(attributionSourceClass.getName())) {
                        args[i] = copyAttributionForHost(arg, hostPackage, hostUid);
                    }
                }
            }
            return invokeProvider(mBase, method, args);
        }
        
        
        if (args != null && args.length > 0) {
            Object arg = args[0];
            if (arg instanceof String) {
                String authority = (String) arg;
                
                if (!isSystemProviderAuthority(authority)) {
                    args[0] = BlackBoxCore.getHostPkg();
                }
            } else if (arg != null) {
                Class<?> attrSourceClass = BRAttributionSource.getRealClass();
                
                if (attrSourceClass != null && arg.getClass().getName().equals(attrSourceClass.getName())) {
                    args[0] = copyAttributionForHost(
                            arg, BlackBoxCore.getHostPkg(), BlackBoxCore.getHostUid());
                }
            }
        }
        return invokeProvider(mBase, method, args);
    }

    private static Object copyAttributionForHost(Object source, String packageName, int uid)
            throws ReflectiveOperationException {
        Object copy = copyAttributionChain(source);
        ContextCompat.fixAttributionSourceState(copy, packageName, uid);
        return copy;
    }

    static Object copyAttributionChain(Object source) throws ReflectiveOperationException {
        // Builder(AttributionSource) only exists on API 33+. Copy the framework state
        // instead, using the same hidden state contract as ContextCompat's identity rewrite.
        // Preserve token, permissions and version-specific fields; isolate every next state.
        // Do not unparcel AttributionSource outside Binder (Android rejects that operation).
        Field stateField = source.getClass().getDeclaredField("mAttributionSourceState");
        stateField.setAccessible(true);
        Object state = stateField.get(source);
        return source.getClass().getConstructor(state.getClass()).newInstance(copyAttributionState(state));
    }

    private static Object copyAttributionState(Object state) throws ReflectiveOperationException {
        Class<?> stateClass = state.getClass();
        Object copy = stateClass.getConstructor().newInstance();
        for (Field field : stateClass.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(state);
            if (field.getType().isArray() && field.getType().getComponentType() == stateClass
                    && value != null) {
                Object next = Array.newInstance(stateClass, Array.getLength(value));
                for (int i = 0; i < Array.getLength(value); i++) {
                    Object child = Array.get(value, i);
                    Array.set(next, i, child == null ? null : copyAttributionState(child));
                }
                value = next;
            }
            field.set(copy, value);
        }
        return copy;
    }

    static Object invokeProvider(Object provider, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(provider, args);
        } catch (InvocationTargetException failure) {
            // Preserve the provider's real exception rather than an undeclared proxy wrapper.
            throw failure.getCause();
        }
    }

    private boolean isSystemProviderAuthority(String authority) {
        if (authority == null) return false;
        
        return authority.equals("settings") || 
               authority.equals("media") || 
               authority.equals("downloads") || 
               authority.equals("contacts") || 
               authority.equals("call_log") || 
               authority.equals("telephony") || 
               authority.equals("calendar") || 
               authority.equals("browser") || 
               authority.equals("user_dictionary") || 
               authority.equals("applications") ||
               authority.startsWith("com.android.") ||
               authority.startsWith("android.");
    }
}
