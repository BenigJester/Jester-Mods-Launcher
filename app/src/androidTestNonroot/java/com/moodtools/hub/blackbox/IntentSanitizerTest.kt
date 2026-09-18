package com.moodtools.hub.blackbox

import android.content.Intent
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import top.niunaijun.blackbox.utils.IntentSanitizer

@RunWith(AndroidJUnit4::class)
class IntentSanitizerTest {
    private enum class ActivityType {
        LICENSING,
        DIALOG
    }

    @Test
    fun appDefinedEnumSurvivesCrossProcessIntentRoundTrip() {
        val outgoing = Intent().putExtra("activity_type", ActivityType.LICENSING)
        IntentSanitizer.sanitizeClassExtrasForIpc(outgoing)

        val incoming = parcelRoundTrip(outgoing)

        IntentSanitizer.restoreSanitizedClassExtras(
            incoming,
            ActivityType::class.java.classLoader
        )

        assertEquals(ActivityType.LICENSING, incoming.getSerializableExtra("activity_type"))
    }

    @Test
    fun appDefinedEnumSurvivesOpaqueServerRelay() {
        val outgoing = Intent().putExtra("activity_type", ActivityType.DIALOG)
        val serverCopy = parcelRoundTrip(outgoing)

        // The BlackBox server must not inspect the target extras. It only wraps the still-parcelled
        // target Intent in the proxy Intent for the second Binder hop.
        val proxyCopy = parcelRoundTrip(Intent().putExtra("target", serverCopy))
        val incoming = requireNotNull(proxyCopy.getParcelableExtra<Intent>("target"))
        incoming.setExtrasClassLoader(ActivityType::class.java.classLoader)

        assertEquals(ActivityType.DIALOG, incoming.getSerializableExtra("activity_type"))
    }

    private fun parcelRoundTrip(intent: Intent): Intent {
        val parcel = Parcel.obtain()
        return try {
            intent.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Intent.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
