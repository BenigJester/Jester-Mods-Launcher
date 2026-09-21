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

        val parcel = Parcel.obtain()
        val incoming = try {
            outgoing.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Intent.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }

        IntentSanitizer.restoreSanitizedClassExtras(
            incoming,
            ActivityType::class.java.classLoader
        )

        assertEquals(ActivityType.LICENSING, incoming.getSerializableExtra("activity_type"))
    }
}
