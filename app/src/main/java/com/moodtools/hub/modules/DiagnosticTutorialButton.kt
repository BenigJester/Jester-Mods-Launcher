package com.moodtools.hub.modules

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moodtools.hub.LauncherLocalization
import com.moodtools.hub.LocalizedText as Text

@Composable
internal fun DiagnosticTutorialButton(palette: LauncherPalette, modifier: Modifier = Modifier, accent: Color = palette.accentSecondary) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com/shorts/Isl_ffWYpa4")))
            }.onFailure {
                Toast.makeText(context, LauncherLocalization.translate("Could not open tutorial."), Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, accent.copy(alpha = .45f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
    ) {
        Text("Diagnostic Tutorial")
    }
}
