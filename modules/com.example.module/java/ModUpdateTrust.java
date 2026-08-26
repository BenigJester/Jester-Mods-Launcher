package com.android.support;

import android.util.Base64;

import org.json.JSONObject;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

final class ModUpdateTrust {
    // Generated once for Mood Tools. The matching private key stays outside every project/APK.
    private static final String PUBLIC_KEY_BASE64 =
            "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA9ueWd1XDC7a63XAmkmNPfB52" +
            "dXe/Q6VF/mUQ9639cDm4uLYrQ7+34p+6Vi1m3eU/KV/lY/BlQPWVHM2gwU/xDT32c+zk" +
            "fojTn8Fb9kCxNLOOIvpMBLDMxHoZvv63z1MS7lK2Zd1dNrLbM26UK0K01nDL15yWJj" +
            "Q4fxHvxR1t9sRSXgk9aBft/7R0NMgC2TJDIG1n2Fsk7f82r8gIxIKrI4CnTSKA30zwo" +
            "H46/GhMmoiqaCOEOdqXNoV7ll+bfeN/PNEACxgOGwY750nkkZEgMLvCNXqto4nFflH0b" +
            "YUTazaJ5z3fOuCJ8pR5hh5L1hk44Zh8F8wO0qyjCBMoByypObgpF0JURcZb6G/kmvAEB" +
            "1lZet+yFIeycjCxsksVDVo1SbfXZ10Oum1WZz07X3Bn/i2FjGy6YNzKViRd86khRVa7" +
            "9H9ZIDPx1mb1UYREjpx8tMo/G9pmVY0mooEOBMvzWVm4JxYPQJA8G7kfu/zGS1vWZSU" +
            "cfG1VwDCUULUicu43AgMBAAE=";

    private ModUpdateTrust() {
    }

    static JSONObject verifyAndDecode(JSONObject envelope) throws Exception {
        if (!"SHA256withRSA".equals(envelope.optString("algorithm", ""))) {
            throw new SecurityException("Unsupported update signature algorithm.");
        }
        if (PUBLIC_KEY_BASE64.length() == 0) {
            throw new SecurityException("The updater signing key is not configured.");
        }

        byte[] payload = Base64.decode(envelope.getString("payload"), Base64.DEFAULT);
        byte[] signatureBytes = Base64.decode(envelope.getString("signature"), Base64.DEFAULT);
        byte[] keyBytes = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT);
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(keyBytes));
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(payload);
        if (!verifier.verify(signatureBytes)) {
            throw new SecurityException("The update manifest signature is invalid.");
        }
        return new JSONObject(new String(payload, "UTF-8"));
    }
}
