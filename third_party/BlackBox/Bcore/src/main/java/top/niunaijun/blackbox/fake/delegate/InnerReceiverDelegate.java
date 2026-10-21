package top.niunaijun.blackbox.fake.delegate;

import android.content.IIntentReceiver;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import black.android.content.BRIIntentReceiver;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.proxy.record.ProxyBroadcastRecord;


public class InnerReceiverDelegate extends IIntentReceiver.Stub implements IBinder.DeathRecipient {
    public static final String TAG = "InnerReceiverDelegate";

    private static final Map<IBinder, InnerReceiverDelegate> sInnerReceiverDelegate = new HashMap<>();
    private final WeakReference<IIntentReceiver> mIntentReceiver;
    private final IBinder mOriginalBinder;

    private InnerReceiverDelegate(IIntentReceiver iIntentReceiver) {
        this.mIntentReceiver = new WeakReference<>(iIntentReceiver);
        this.mOriginalBinder = iIntentReceiver.asBinder();
    }

    public static synchronized InnerReceiverDelegate getDelegate(IBinder iBinder) {
        if (iBinder instanceof InnerReceiverDelegate) return (InnerReceiverDelegate) iBinder;
        return sInnerReceiverDelegate.get(iBinder);
    }

    public static synchronized IIntentReceiver createProxy(IIntentReceiver base) {
        if (base instanceof InnerReceiverDelegate) {
            return base;
        }
        final IBinder iBinder = base.asBinder();
        InnerReceiverDelegate delegate = sInnerReceiverDelegate.get(iBinder);
        if (delegate == null) {
            delegate = new InnerReceiverDelegate(base);
            sInnerReceiverDelegate.put(iBinder, delegate);
            try {
                iBinder.linkToDeath(delegate, 0);
            } catch (RemoteException e) {
                sInnerReceiverDelegate.remove(iBinder);
            }
        }
        return delegate;
    }

    public void release() {
        synchronized (InnerReceiverDelegate.class) {
            if (sInnerReceiverDelegate.get(mOriginalBinder) != this) return;
            sInnerReceiverDelegate.remove(mOriginalBinder);
            try { mOriginalBinder.unlinkToDeath(this, 0); }
            catch (java.util.NoSuchElementException ignored) { }
        }
    }

    @Override public void binderDied() { release(); }

    @Override
    public void performReceive(Intent intent, int resultCode, String data, Bundle extras, boolean ordered, boolean sticky, int sendingUser) throws RemoteException {
        intent.setExtrasClassLoader(BlackBoxCore.getApplication().getClassLoader());
        ProxyBroadcastRecord proxyBroadcastRecord = ProxyBroadcastRecord.create(intent);
        Intent perIntent;
        if (proxyBroadcastRecord.mIntent != null) {
            proxyBroadcastRecord.mIntent.setExtrasClassLoader(BlackBoxCore.getApplication().getClassLoader());
            perIntent = proxyBroadcastRecord.mIntent;
        } else {
            perIntent = intent;
        }
        IIntentReceiver iIntentReceiver = mIntentReceiver.get();
        if (iIntentReceiver != null) {
            BRIIntentReceiver.get(iIntentReceiver).performReceive(perIntent, resultCode, data, extras, ordered, sticky, sendingUser);
        }
    }
}
