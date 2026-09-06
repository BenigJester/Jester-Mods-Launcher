package top.niunaijun.blackbox.utils;

import org.json.JSONArray;
import org.json.JSONObject;

/** Builds identity-only product rows without invented prices, purchases, or entitlements. */
public final class FakeBillingCatalog {
    private FakeBillingCatalog() {
    }

    public static String legacyProductJson(
            String packageName, String productId, String productType) {
        try {
            JSONObject json = baseProductJson(packageName, productId, productType);
            // Some games own their visible price labels and only need billing to confirm that the
            // requested product exists. Keep the schema valid without replacing those labels with
            // a fabricated storefront price.
            json.put("price", "");
            json.put("price_amount_micros", 0L);
            json.put("price_currency_code", "");
            if ("subs".equals(productType)) json.put("subscriptionPeriod", "P1M");
            return json.toString();
        } catch (Throwable error) {
            throw new IllegalStateException(error);
        }
    }

    public static String productDetailsJson(
            String packageName, String productId, String productType) {
        try {
            JSONObject json = baseProductJson(packageName, productId, productType);
            json.put("skuDetailsToken", "mock-" + productId);
            if ("subs".equals(productType)) {
                JSONObject pricingPhase = new JSONObject()
                        .put("billingPeriod", "P1M")
                        .put("priceCurrencyCode", "")
                        .put("formattedPrice", "")
                        .put("priceAmountMicros", 0L)
                        .put("recurrenceMode", 1)
                        .put("billingCycleCount", 0);
                JSONObject offer = new JSONObject()
                        .put("basePlanId", "mock-base-plan")
                        .put("offerIdToken", "mock-" + productId)
                        .put("pricingPhases", new JSONArray().put(pricingPhase))
                        .put("offerTags", new JSONArray());
                json.put("subscriptionOfferDetails", new JSONArray().put(offer));
            } else {
                json.put("oneTimePurchaseOfferDetails", new JSONObject()
                        .put("formattedPrice", "")
                        .put("priceAmountMicros", 0L)
                        .put("priceCurrencyCode", "")
                        .put("offerTags", new JSONArray()));
            }
            return json.toString();
        } catch (Throwable error) {
            throw new IllegalStateException(error);
        }
    }

    private static JSONObject baseProductJson(
            String packageName, String productId, String productType) throws Exception {
        String safeType = "subs".equals(productType) ? "subs" : "inapp";
        return new JSONObject()
                .put("productId", productId)
                .put("type", safeType)
                .put("packageName", packageName);
    }
}
