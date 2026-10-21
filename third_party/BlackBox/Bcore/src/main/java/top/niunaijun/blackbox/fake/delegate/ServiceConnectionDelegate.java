package top.niunaijun.blackbox.fake.delegate;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import black.android.app.BRIServiceConnectionO;
import top.niunaijun.blackbox.utils.compat.BuildCompat;


public class ServiceConnectionDelegate extends IServiceConnection.Stub implements IBinder.DeathRecipient {
    private static final Map<IBinder, ServiceConnectionDelegate> sServiceConnectDelegate = new HashMap<>();
    private final IServiceConnection mConn;
    private final ComponentName mComponentName;

    private ServiceConnectionDelegate(IServiceConnection mConn, ComponentName targetComponent) {
        this.mConn = mConn;
        this.mComponentName = targetComponent;
    }

    public static synchronized ServiceConnectionDelegate getDelegate(IBinder iBinder) {
        if (iBinder instanceof ServiceConnectionDelegate) return (ServiceConnectionDelegate) iBinder;
        return sServiceConnectDelegate.get(iBinder);
    }

    public static synchronized IServiceConnection createProxy(IServiceConnection base, Intent intent) {
        if (base instanceof ServiceConnectionDelegate) return base;
        final IBinder iBinder = base.asBinder();
        ServiceConnectionDelegate delegate = sServiceConnectDelegate.get(iBinder);
        if (delegate == null) {
            delegate = new ServiceConnectionDelegate(base, intent.getComponent());
            sServiceConnectDelegate.put(iBinder, delegate);
            try {
                iBinder.linkToDeath(delegate, 0);
            } catch (RemoteException e) {
                sServiceConnectDelegate.remove(iBinder);
            }
        }
        return delegate;
    }

    /** Successful unbind must release local binders too: they never deliver binderDied. */
    public void release() {
        synchronized (ServiceConnectionDelegate.class) {
            IBinder binder = mConn.asBinder();
            if (sServiceConnectDelegate.get(binder) != this) return;
            sServiceConnectDelegate.remove(binder);
            try { binder.unlinkToDeath(this, 0); }
            catch (java.util.NoSuchElementException ignored) { }
        }
    }

    @Override public void binderDied() { release(); }

    public IBinder getOriginalBinder() { return mConn.asBinder(); }

    /**
     * Delivers a synthetic service connection through the framework signature used by the
     * current Android release. Android 16 replaced the legacy two/three-argument callback with
     * the IBinderSession overload, so callers must not invoke IServiceConnection directly.
     */
    public static void dispatchConnected(IServiceConnection connection, ComponentName component,
                                         IBinder service) throws RemoteException {
        new ServiceConnectionDelegate(connection, component)
                .connected(component, service, (android.app.IBinderSession) null, false);
    }

    @Override
    public void connected(ComponentName name, IBinder service) throws RemoteException {
        connected(name, service, false);
    }

    public void connected(ComponentName name, IBinder service, boolean dead) throws RemoteException {
        if (BuildCompat.isOreo()) {
            BRIServiceConnectionO.get(mConn).connected(mComponentName, service, dead);
        } else {
            mConn.connected(name, service);
        }
    }

    // Android 16+ signature (added to framework after this project was originally compiled).
    // Intentionally NOT annotated with @Override to keep compatibility with older compile SDKs.
    public void connected(ComponentName name, IBinder service, android.app.IBinderSession session, boolean dead)
            throws RemoteException {
        try {
            Method method = IServiceConnection.class.getMethod(
                    "connected",
                    ComponentName.class,
                    IBinder.class,
                    android.app.IBinderSession.class,
                    boolean.class
            );
            method.invoke(mConn, mComponentName != null ? mComponentName : name, service, session, dead);
            return;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RemoteException) {
                throw (RemoteException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
        } catch (Throwable ignored) {
            // fall through
        }

        connected(name, service, dead);
    }
}
