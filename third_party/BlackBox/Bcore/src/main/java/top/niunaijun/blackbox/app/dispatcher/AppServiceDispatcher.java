package top.niunaijun.blackbox.app.dispatcher;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import java.util.HashMap;
import java.util.Map;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.ServiceRecord;
import top.niunaijun.blackbox.entity.UnbindRecord;
import top.niunaijun.blackbox.proxy.record.ProxyServiceRecord;
import top.niunaijun.blackbox.utils.PlayStoreCrashPolicy;
import top.niunaijun.blackbox.utils.Slog;

import static android.app.Service.START_NOT_STICKY;



public class AppServiceDispatcher {
    public static final String TAG = "AppServiceDispatcher";

    private static final AppServiceDispatcher sServiceDispatcher = new AppServiceDispatcher();

    private Map<Intent.FilterComparison, ServiceRecord> mService = new HashMap<>();

    public static AppServiceDispatcher get() {
        return sServiceDispatcher;
    }

    private final Handler mHandler = BlackBoxCore.get().getHandler();

    public IBinder onBind(Intent proxyIntent) {
        ProxyServiceRecord serviceRecord = ProxyServiceRecord.create(proxyIntent);
        Intent intent = serviceRecord.mServiceIntent;
        ServiceInfo serviceInfo = serviceRecord.mServiceInfo;

        if (intent == null || serviceInfo == null)
            return null;

        String sourceGuestPackage = intent.getStringExtra(
                PlayStoreCrashPolicy.SOURCE_GUEST_EXTRA);
        String sourceSessionToken = intent.getStringExtra(
                PlayStoreCrashPolicy.SOURCE_SESSION_EXTRA);
        boolean targetGamePlayStoreBind = PlayStoreCrashPolicy.shouldArmForServiceBind(
                Build.VERSION.SDK_INT, sourceGuestPackage, serviceInfo.packageName);
        boolean referenceCrashAlreadyProduced = targetGamePlayStoreBind
                ? !PlayStoreCrashPolicy.claimReferenceCrash(
                        BlackBoxCore.getContext(), serviceRecord.mUserId, sourceSessionToken)
                : PlayStoreCrashPolicy.PLAY_STORE_PACKAGE.equals(serviceInfo.packageName)
                        && PlayStoreCrashPolicy.hasReferenceCrashClaim(
                                BlackBoxCore.getContext(), serviceRecord.mUserId);
        if (referenceCrashAlreadyProduced) {
            intent.removeExtra(PlayStoreCrashPolicy.SOURCE_GUEST_EXTRA);
            intent.removeExtra(PlayStoreCrashPolicy.SOURCE_SESSION_EXTRA);
            Slog.i(TAG, PlayStoreCrashPolicy.STATE_ID
                    + " returned a stable dead binding after the reference crash");
            return null;
        }
        intent.removeExtra(PlayStoreCrashPolicy.SOURCE_GUEST_EXTRA);
        intent.removeExtra(PlayStoreCrashPolicy.SOURCE_SESSION_EXTRA);
        boolean forcePlayStoreUidMismatch = PlayStoreCrashPolicy.armForServiceBind(
                Build.VERSION.SDK_INT, sourceGuestPackage, serviceInfo.packageName);
        if (forcePlayStoreUidMismatch) {
            Slog.w(TAG, PlayStoreCrashPolicy.STATE_ID + " armed in Play Store process: source="
                    + sourceGuestPackage + " service=" + serviceInfo.name);
        }

        Service service;
        try {
            service = getOrCreateService(serviceRecord);
        } catch (RuntimeException failure) {
            if (!forcePlayStoreUidMismatch || containsPinnedUidMismatch(failure)) {
                throw failure;
            }
            Slog.w(TAG, PlayStoreCrashPolicy.STATE_ID
                    + " replaced a different Play Store startup failure", failure);
            throw PlayStoreCrashPolicy.uidMismatchException(BlackBoxCore.getHostUid());
        }

        // Most Play Store builds query a provider during makeApplication(), so ContentProviderStub
        // produces the original observed stack before reaching here. This fallback pins the same
        // process, exception type, and message when a different Play Store build skips that query.
        if (forcePlayStoreUidMismatch) {
            Slog.w(TAG, PlayStoreCrashPolicy.STATE_ID
                    + " using service-bind fallback because no provider query occurred");
            throw PlayStoreCrashPolicy.uidMismatchException(BlackBoxCore.getHostUid());
        }
        if (service == null)
            return null;
        intent.setExtrasClassLoader(service.getClassLoader());

        ServiceRecord record = findRecord(intent);
        record.incrementAndGetBindCount(intent);
        if (record.hasBinder(intent)) {
            if (record.isRebind()) {
                service.onRebind(intent);
                record.setRebind(false);
            }
            return record.getBinder(intent);
        }

        try {
            IBinder iBinder = service.onBind(intent);
            record.addBinder(intent, iBinder);
            return iBinder;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean containsPinnedUidMismatch(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SecurityException
                    && PlayStoreCrashPolicy.uidMismatchMessage(BlackBoxCore.getHostUid())
                            .equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public int onStartCommand(Intent proxyIntent, int flags, int startId) {
        ProxyServiceRecord stubRecord = ProxyServiceRecord.create(proxyIntent);
        if (stubRecord.mServiceIntent == null || stubRecord.mServiceInfo == null) {
            return START_NOT_STICKY;
        }


        Service service = getOrCreateService(stubRecord);
        if (service == null)
            return START_NOT_STICKY;
        stubRecord.mServiceIntent.setExtrasClassLoader(service.getClassLoader());
        ServiceRecord record = findRecord(stubRecord.mServiceIntent);
        record.setStartId(stubRecord.mStartId);
        try {
            int i = service.onStartCommand(stubRecord.mServiceIntent, flags, stubRecord.mStartId);
            BlackBoxCore.getBActivityManager().onStartCommand(proxyIntent, stubRecord.mUserId);
            return i;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return START_NOT_STICKY;
    }

    public void onDestroy() {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onDestroy();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        mService.clear();

    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onConfigurationChanged(newConfig);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public void onLowMemory() {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onLowMemory();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public void onTrimMemory(int level) {
        if (mService.size() > 0) {
            for (ServiceRecord record : mService.values()) {
                try {
                    record.getService().onTrimMemory(level);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        
    }

    public boolean onUnbind(Intent proxyIntent) {
        ProxyServiceRecord stubRecord = ProxyServiceRecord.create(proxyIntent);
        if (stubRecord.mServiceIntent == null || stubRecord.mServiceInfo == null) {
            return false;
        }
        Intent intent = stubRecord.mServiceIntent;

        try {
            UnbindRecord unbindRecord = BlackBoxCore.getBActivityManager().onServiceUnbind(proxyIntent, BlackBoxCore.getUserId());
            if (unbindRecord == null)
                return false;

            Service service = getOrCreateService(stubRecord);
            if (service == null)
                return false;

            stubRecord.mServiceIntent.setExtrasClassLoader(service.getClassLoader());

            ServiceRecord record = findRecord(intent);

            boolean destroy = unbindRecord.getStartId() == 0;
            if (destroy || record.decreaseConnectionCount(intent)) {
                boolean b = service.onUnbind(intent);
                if (destroy) {
                    service.onDestroy();
                    BlackBoxCore.getBActivityManager().onServiceDestroy(proxyIntent, BlackBoxCore.getUserId());
                    mService.remove(new Intent.FilterComparison(intent));
                }
                record.setRebind(true);

            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    public IBinder peekService(Intent intent) {
        ServiceRecord record = findRecord(intent);
        if (record == null) {
            return null;
        }
        return record.getBinder(intent);
    }

    public void stopService(Intent intent) {
        if (intent == null)
            return;
        ServiceRecord record = findRecord(intent);
        if (record == null)
            return;
        if (record.getService() != null) {
            boolean destroy = record.getStartId() > 0;
            try {
                if (destroy) {
                    mHandler.post(() -> record.getService().onDestroy());
                    BlackBoxCore.getBActivityManager().onServiceDestroy(intent, BlackBoxCore.getUserId());
                    mService.remove(new Intent.FilterComparison(intent));
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private ServiceRecord findRecord(Intent intent) {
        return mService.get(new Intent.FilterComparison(intent));
    }

    private Service getOrCreateService(ProxyServiceRecord proxyServiceRecord) {
        Intent intent = proxyServiceRecord.mServiceIntent;
        ServiceInfo serviceInfo = proxyServiceRecord.mServiceInfo;
        IBinder token = proxyServiceRecord.mToken;

        ServiceRecord record = findRecord(intent);
        if (record != null && record.getService() != null) {
            return record.getService();
        }
        Service service = BlackBoxCore.currentActivityThread().createService(serviceInfo, token);
        if (service == null)
            return null;
        record = new ServiceRecord();
        record.setService(service);
        mService.put(new Intent.FilterComparison(intent), record);
        return service;
    }
}
