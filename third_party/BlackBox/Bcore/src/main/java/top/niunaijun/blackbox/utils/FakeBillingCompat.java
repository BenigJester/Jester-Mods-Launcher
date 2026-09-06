package top.niunaijun.blackbox.utils;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.delegate.ServiceConnectionDelegate;

/**
 * Silent billing discovery fallback for virtual game guests.
 *
 * <p>The fallback deliberately cannot create purchases or entitlements. It only lets a game
 * initialize its billing client and inspect placeholder product rows without entering the virtual
 * Play Store, whose billing process may resolve successfully but remain unusable in BlackBox.
 * Every buy request returns {@code USER_CANCELED}.</p>
 */
public final class FakeBillingCompat {
    public static final String BILLING_ACTION =
            "com.android.vending.billing.InAppBillingService.BIND";
    public static final String PLAY_STORE_PACKAGE = "com.android.vending";
    public static final String SERVICE_CLASS =
            "com.google.android.finsky.billing.iab.InAppBillingService";
    private static final String DESCRIPTOR =
            "com.android.vending.billing.IInAppBillingService";
    private static final int RESPONSE_OK = 0;
    private static final int RESPONSE_USER_CANCELED = 1;
    private static final int RESPONSE_ITEM_NOT_OWNED = 8;
    private static final ComponentName COMPONENT =
            new ComponentName(PLAY_STORE_PACKAGE, SERVICE_CLASS);
    private static final IBinder BILLING_BINDER = new NonEntitlingBillingBinder();
    private static final Set<IBinder> MOCK_CONNECTIONS = ConcurrentHashMap.newKeySet();

    private FakeBillingCompat() {
    }

    public static boolean isBillingIntent(Intent intent) {
        if (intent == null) return false;
        if (BILLING_ACTION.equals(intent.getAction())) return true;
        ComponentName component = intent.getComponent();
        return component != null
                && PLAY_STORE_PACKAGE.equals(component.getPackageName())
                && component.getClassName().toLowerCase(java.util.Locale.ROOT).contains("billing");
    }

    public static boolean isEnabledForGuest(String packageName) {
        return PlayStoreCrashPolicy.isGameGuestPackage(packageName);
    }

    public static boolean shouldUseFallback(Intent intent, String packageName) {
        return isBillingIntent(intent)
                && isEnabledForGuest(packageName);
    }

    public static ResolveInfo createResolveInfo() {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = PLAY_STORE_PACKAGE;
        applicationInfo.name = "Google Play Store";
        applicationInfo.enabled = true;

        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = PLAY_STORE_PACKAGE;
        serviceInfo.name = SERVICE_CLASS;
        serviceInfo.applicationInfo = applicationInfo;
        serviceInfo.enabled = true;
        serviceInfo.exported = true;

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = serviceInfo;
        return resolveInfo;
    }

    public static boolean isMockComponent(ComponentName component) {
        return COMPONENT.equals(component);
    }

    public static int bind(IServiceConnection connection, String packageName) {
        if (connection == null) return 0;
        IBinder token = connection.asBinder();
        MOCK_CONNECTIONS.add(token);
        BlackBoxCore.get().getHandler().post(() -> {
            try {
                ServiceConnectionDelegate.dispatchConnected(
                        connection, COMPONENT, BILLING_BINDER);
            } catch (Throwable error) {
                MOCK_CONNECTIONS.remove(token);
                Slog.w("FakeBillingCompat", "Could not connect mock billing for "
                        + packageName, error);
            }
        });
        return 1;
    }

    public static boolean unbind(IServiceConnection connection) {
        return connection != null && MOCK_CONNECTIONS.remove(connection.asBinder());
    }

    private static final class NonEntitlingBillingBinder extends Binder {
        NonEntitlingBillingBinder() {
            attachInterface(null, DESCRIPTOR);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            data.enforceInterface(DESCRIPTOR);
            switch (code) {
                case 1: // isBillingSupported
                    reply.writeNoException();
                    reply.writeInt(RESPONSE_OK);
                    return true;
                case 10: // isBillingSupportedExtraParams
                    data.readInt();
                    data.readString();
                    data.readString();
                    readBundle(data);
                    reply.writeNoException();
                    reply.writeInt(RESPONSE_OK);
                    return true;
                case 2: // getSkuDetails
                case 901: // getProductDetails
                    writeBundle(reply, readProductRequest(code, data));
                    return true;
                case 3: // getBuyIntent
                case 7: // getBuyIntentToReplaceSkus
                case 8: // getBuyIntentExtraParams
                    writeBundle(reply, response(RESPONSE_USER_CANCELED,
                            "Mock purchase only; no payment or entitlement was created."));
                    return true;
                case 4: // getPurchases
                case 9: // getPurchaseHistory
                case 11: // getPurchasesExtraParams
                    writeBundle(reply, emptyPurchases());
                    return true;
                case 5: // consumePurchase
                    reply.writeNoException();
                    reply.writeInt(RESPONSE_ITEM_NOT_OWNED);
                    return true;
                case 12: // consumePurchaseExtraParams
                case 902: // acknowledgePurchase
                    writeBundle(reply, response(RESPONSE_ITEM_NOT_OWNED,
                            "Mock billing never owns purchases."));
                    return true;
                default:
                    Slog.w("FakeBillingCompat", "Unsupported mock billing transaction " + code);
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private Bundle readProductRequest(int code, Parcel data) {
            int apiVersion = data.readInt();
            String packageName = data.readString();
            String productType = data.readString();
            Bundle products = readBundle(data);
            if (code == 901) readBundle(data);

            ArrayList<String> ids = products == null
                    ? null : products.getStringArrayList("ITEM_ID_LIST");
            if (ids == null) ids = new ArrayList<>();
            ArrayList<String> details = new ArrayList<>();
            for (String id : ids) {
                if (id == null || id.trim().isEmpty()) continue;
                details.add(code == 901
                        ? FakeBillingCatalog.productDetailsJson(packageName, id, productType)
                        : FakeBillingCatalog.legacyProductJson(packageName, id, productType));
            }
            Bundle result = response(RESPONSE_OK,
                    "Testing placeholders supplied by Jester Mods; purchases are disabled.");
            result.putStringArrayList("DETAILS_LIST", details);
            return result;
        }
    }

    private static Bundle readBundle(Parcel data) {
        return data.readInt() == 0 ? null : Bundle.CREATOR.createFromParcel(data);
    }

    private static void writeBundle(Parcel reply, Bundle value) {
        reply.writeNoException();
        reply.writeInt(1);
        value.writeToParcel(reply, 0);
    }

    private static Bundle response(int code, String message) {
        Bundle result = new Bundle();
        result.putInt("RESPONSE_CODE", code);
        result.putString("DEBUG_MESSAGE", message);
        return result;
    }

    private static Bundle emptyPurchases() {
        Bundle result = response(RESPONSE_OK, "Mock billing has no owned purchases.");
        result.putStringArrayList("INAPP_PURCHASE_ITEM_LIST", new ArrayList<>());
        result.putStringArrayList("INAPP_PURCHASE_DATA_LIST", new ArrayList<>());
        result.putStringArrayList("INAPP_DATA_SIGNATURE_LIST", new ArrayList<>());
        result.putStringArrayList("INAPP_PURCHASE_SIGNATURE_LIST", new ArrayList<>());
        return result;
    }
}
