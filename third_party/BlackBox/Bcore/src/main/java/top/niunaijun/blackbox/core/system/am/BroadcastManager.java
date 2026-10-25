package top.niunaijun.blackbox.core.system.am;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.pm.BPackage;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.core.system.pm.PackageMonitor;
import top.niunaijun.blackbox.entity.am.PendingResultData;
import top.niunaijun.blackbox.proxy.ProxyBroadcastReceiver;
import top.niunaijun.blackbox.utils.Slog;


public class BroadcastManager implements PackageMonitor {
    public static final String TAG = "BroadcastManager";

    public static final int TIMEOUT = 9000;

    public static final int MSG_TIME_OUT = 1;

    private static BroadcastManager sBroadcastManager;

    private final BActivityManagerService mAms;
    private final BPackageManagerService mPms;
    private final Map<String, List<BroadcastReceiver>> mReceivers = new HashMap<>();
    private int trackedReceivers;
    private final Map<String, PendingResultData> mReceiversData = new HashMap<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_TIME_OUT:
                    try {
                        PendingResultData data = (PendingResultData) msg.obj;
                        data.build().finish();
                        Slog.d(TAG, "Timeout Receiver: " + data);
                    } catch (Throwable ignore) {
                    }
                    break;
            }
        }
    };

    public static BroadcastManager startSystem(BActivityManagerService ams, BPackageManagerService pms) {
        if (sBroadcastManager == null) {
            synchronized (BroadcastManager.class) {
                if (sBroadcastManager == null) {
                    sBroadcastManager = new BroadcastManager(ams, pms);
                }
            }
        }
        return sBroadcastManager;
    }

    public BroadcastManager(BActivityManagerService ams, BPackageManagerService pms) {
        mAms = ams;
        mPms = pms;
        top.niunaijun.blackbox.utils.ConnectionDiagnostics.trackedReceiverCount(0);
    }

    public void startup() {
        mPms.addPackageMonitor(this);
        List<BPackageSettings> bPackageSettings = mPms.getBPackageSettings();
        for (BPackageSettings bPackageSetting : bPackageSettings) {
            BPackage bPackage = bPackageSetting.pkg;
            registerPackage(bPackage);
        }
    }

    private void registerPackage(BPackage bPackage) {
        synchronized (mReceivers) {
            // A package refresh replaces registrations, not just our references to them.
            unregisterPackage(bPackage.packageName);
            Slog.d(TAG, "register: " + bPackage.packageName + ", size: " + bPackage.receivers.size());
            for (BPackage.Activity receiver : bPackage.receivers) {
                List<BPackage.ActivityIntentInfo> intents = receiver.intents;
                for (BPackage.ActivityIntentInfo intent : intents) {
                    ProxyBroadcastReceiver proxyBroadcastReceiver = new ProxyBroadcastReceiver();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        BlackBoxCore.getContext().registerReceiver(proxyBroadcastReceiver, intent.intentFilter, Context.RECEIVER_EXPORTED);
                    }else{
                        BlackBoxCore.getContext().registerReceiver(proxyBroadcastReceiver, intent.intentFilter);
                    }
                    addReceiver(bPackage.packageName, proxyBroadcastReceiver);
                }
            }
        }
    }

    /** Caller holds mReceivers. Keep failed cleanup tracked so it can be retried. */
    private void unregisterPackage(String packageName) {
        List<BroadcastReceiver> receivers = mReceivers.get(packageName);
        if (receivers == null) return;
        Iterator<BroadcastReceiver> iterator = receivers.iterator();
        while (iterator.hasNext()) {
            BroadcastReceiver receiver = iterator.next();
            try {
                BlackBoxCore.getContext().unregisterReceiver(receiver);
            } catch (IllegalArgumentException alreadyUnregistered) {
                // Android reports an already-removed receiver this way; there is nothing left to release.
            }
            iterator.remove();
            top.niunaijun.blackbox.utils.ConnectionDiagnostics.trackedReceiverCount(--trackedReceivers);
        }
        mReceivers.remove(packageName);
    }

    private void addReceiver(String packageName, BroadcastReceiver receiver) {
        List<BroadcastReceiver> broadcastReceivers = mReceivers.get(packageName);
        if (broadcastReceivers == null) {
            broadcastReceivers = new ArrayList<>();
            mReceivers.put(packageName, broadcastReceivers);
        }
        broadcastReceivers.add(receiver);
        top.niunaijun.blackbox.utils.ConnectionDiagnostics.trackedReceiverCount(++trackedReceivers);
    }

    public void sendBroadcast(PendingResultData pendingResultData) {
        synchronized (mReceiversData) {
            
            mReceiversData.put(pendingResultData.mBToken, pendingResultData);
            Message obtain = Message.obtain(mHandler, MSG_TIME_OUT, pendingResultData);
            mHandler.sendMessageDelayed(obtain, TIMEOUT);
        }
    }

    public void finishBroadcast(PendingResultData data) {
        synchronized (mReceiversData) {
            
            mHandler.removeMessages(MSG_TIME_OUT, mReceiversData.get(data.mBToken));
        }
    }

    @Override
    public void onPackageUninstalled(String packageName, boolean removeApp, int userId) {
        if (removeApp) {
            synchronized (mReceivers) {
                unregisterPackage(packageName);
            }
        }
    }

    @Override
    public void onPackageInstalled(String packageName, int userId) {
        synchronized (mReceivers) {
            BPackageSettings bPackageSetting = mPms.getBPackageSetting(packageName);
            if (bPackageSetting != null) {
                registerPackage(bPackageSetting.pkg);
            } else {
                unregisterPackage(packageName);
            }
        }
    }
}
