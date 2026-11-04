package com.moodtools.diagnostics;

import org.junit.Test;
import static org.junit.Assert.*;
import top.niunaijun.blackbox.utils.FailureEvidence;

public class ParcelFailureEvidenceTest {
    @Test public void onlyExactSdkClassNamesAreRetained() {
        String name = "com.google.android.gms.games.internal.TestParcelable";
        assertEquals(name, FailureEvidence.missingSdkClass(new RuntimeException("secret", new ClassNotFoundException(name))));
        for (String message : new String[]{null, "account@example.com", "Didn't find class \"" + name + "\" on /private/token",
                name + " secret", "private.account.Token", name + "\nsecret"}) {
            assertEquals("unavailable", FailureEvidence.missingSdkClass(new ClassNotFoundException(message)));
        }
        assertEquals("unavailable", FailureEvidence.missingSdkClass(new RuntimeException(name)));
    }
}
