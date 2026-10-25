package com.moodtools.hub.modules

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.moodtools.hub.GmsCoreSupport
import com.moodtools.hub.LauncherLocalization
import com.moodtools.hub.LocalizedText as Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GmsCoreSetupPortal(palette: LauncherPalette, module: ModuleConfig, canChange: Boolean, onChanged: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var open by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<GmsCoreSupport.State?>(null) }
    var error by remember { mutableStateOf(false) }
    var enabled by remember(module.packageName) { mutableStateOf(GmsCoreSupport.enabled(context, module.packageName)) }
    LaunchedEffect(open, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            state = withContext(Dispatchers.IO) { GmsCoreSupport.state(context) }
        }
    }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, palette.accentSecondary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accentSecondary)) {
        Text("Google account support")
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false }, containerColor = palette.surfaceRaised,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Google account support", color = palette.accentSecondary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    error = runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/kHWNryjx3dQ")))
                    }.isFailure
                }, modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, palette.accentSecondary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accentSecondary)) {
                    Text("Google Login Tutorial")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Use GmsCore", color = palette.muted, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, enabled = canChange && GmsCoreSupport.supports(module),
                        modifier = Modifier.semantics { contentDescription = LauncherLocalization.translate("Use GmsCore") },
                        onCheckedChange = { selected ->
                            GmsCoreSupport.select(context, module, selected)
                            enabled = selected
                            onChanged()
                        }, colors = SwitchDefaults.colors(checkedTrackColor = palette.accentSecondary))
                }
                Text("After changing this setting, update the patched game from Library.", color = palette.muted)
                Text("Turning this off does not guarantee the game will work without Google services.", color = palette.muted)
                if (enabled && state != null && state != GmsCoreSupport.State.READY)
                    Text(GmsCoreSupport.OPTIONAL_SETUP_GUIDANCE, color = palette.muted)
                Text(state?.label ?: "Checking...", color = palette.accentSecondary)
                Text("Sign in inside GmsCore. The launcher never reads your Google account.", color = palette.muted)
                OutlinedButton(onClick = {
                    error = runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GmsCoreSupport.RELEASE_URL)))
                    }.isFailure
                }, modifier = Modifier.fillMaxWidth()) { Text("Get GmsCore") }
                OutlinedButton(onClick = {
                    error = runCatching { GmsCoreSupport.openSettings(context) }.isFailure
                }, enabled = state in setOf(GmsCoreSupport.State.READY, GmsCoreSupport.State.DISABLED, GmsCoreSupport.State.OLD), modifier = Modifier.fillMaxWidth()) {
                    Text("Open GmsCore")
                }
                Text("Install GmsCore and sign in, then update the patched game from Library.", color = palette.muted)
                Text("Back up your progress before replacing an original installation.", color = palette.muted)
                if (error) Text("Could not open this page. Please try again.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Done", color = palette.accentSecondary) } }
    )
}
