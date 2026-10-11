package com.moodtools.hub.modules

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moodtools.hub.diagnosticIssueLink
import com.moodtools.hub.LocalizedText as Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared by every installer/transfer diagnostic panel; never posts an issue automatically. */
@Composable
internal fun InstallerDiagnosticGithubButton(report: String, title: String, accent: Color, palette: LauncherPalette, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingText by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTitle by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val saveReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pendingText
        val reportTitle = pendingTitle
        pendingText = null
        if (uri != null && text != null) scope.launch {
            exporting = true
            error = runCatching {
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(text) }
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(diagnosticIssueLink(reportTitle, text))))
            }.isFailure
            exporting = false
        }
    }
    OutlinedButton(enabled = !exporting, modifier = modifier,
        border = BorderStroke(1.dp, accent.copy(alpha = .45f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
        onClick = { pendingText = report; pendingTitle = title; error = false; confirm = true }) {
        Text("Share full report")
    }
    if (error) Text("Could not share the report. Try copying the summary.", color = accent)
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false; pendingText = null },
        shape = RoundedCornerShape(28.dp), containerColor = palette.surfaceRaised,
        title = { Text("Share full report", color = Color.White) },
        text = { Text("GitHub issues are public. Review your report before sharing. Save the file, then attach it on GitHub and submit the issue. A GitHub account is required.", color = palette.muted) },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                error = runCatching { saveReport.launch("Jester-diagnostics-${System.currentTimeMillis()}.txt") }.isFailure
                if (error) pendingText = null
            }) { Text("Save and open GitHub", color = accent) }
        },
        dismissButton = { TextButton(onClick = { confirm = false; pendingText = null }) { Text("Dismiss", color = palette.muted) } }
    )
}
