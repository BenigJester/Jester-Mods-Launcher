package com.moodtools.hub.modules

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Text as RawText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.moodtools.hub.LaunchDiagnosticReport
import com.moodtools.hub.LaunchDiagnostics
import com.moodtools.hub.LauncherLocalization
import com.moodtools.hub.diagnosticIssueLink
import com.moodtools.hub.LocalizedText as Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Uses the same palette, soft accent cards and rounded surfaces as changelog history. */
@Composable
internal fun LaunchDiagnosticsPortal(palette: LauncherPalette, packageName: String? = null, prompts: Boolean = false) {
    val backend = LaunchDiagnostics.backend ?: return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf(emptyList<LaunchDiagnosticReport>()) }
    var selected by remember { mutableStateOf<LaunchDiagnosticReport?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf<LaunchDiagnosticReport?>(null) }
    val reviewed = remember { mutableStateListOf<String>() }
    var error by remember { mutableStateOf(false) }
    var clearPackage by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var clearGeneration by remember { mutableIntStateOf(0) }
    var clearError by remember { mutableStateOf(false) }
    var githubReport by remember { mutableStateOf<LaunchDiagnosticReport?>(null) }
    var exportReportId by rememberSaveable { mutableStateOf<String?>(null) }
    val saveReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val id = exportReportId
        exportReportId = null
        if (uri != null) scope.launch {
            error = runCatching {
                val report = withContext(Dispatchers.IO) {
                    val report = checkNotNull(reports.firstOrNull { it.id == id }
                        ?: backend.reports(context).firstOrNull { it.id == id })
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use {
                        it.write(report.text)
                    }
                    report
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(diagnosticIssueLink(report))))
            }.isFailure
        }
    }
    LaunchedEffect(backend, lifecycle, packageName) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val generation = clearGeneration
                val refreshed = withContext(Dispatchers.IO) { runCatching { backend.reports(context) }.getOrDefault(emptyList()) }
                if (clearing || generation != clearGeneration) {
                    delay(4000)
                    continue
                }
                reports = refreshed
                selected?.let { current -> reports.firstOrNull { it.id == current.id }?.let { selected = it } }
                if (prompts && prompt == null && selected == null && !showHistory) {
                    prompt = reports.firstOrNull { it.important && !it.acknowledged && it.id !in reviewed }
                }
                delay(4000)
            }
        }
    }
    fun acknowledge(report: LaunchDiagnosticReport) {
        reviewed += report.id
        scope.launch(Dispatchers.IO) { backend.acknowledge(report) }
    }
    val gameReports = reports.filter { it.packageName == packageName }
    if (packageName != null) {
        Column(Modifier.fillMaxWidth().background(
            Brush.horizontalGradient(listOf(palette.accentSecondary.copy(alpha = .12f), palette.surfaceDark)),
            RoundedCornerShape(24.dp)
        ).border(1.dp, palette.accentSecondary.copy(alpha = .25f), RoundedCornerShape(24.dp))
            .clickable { showHistory = true }.padding(20.dp)) {
            Text("Diagnostics", color = palette.accentSecondary, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(5.dp))
            Text(if (backend.crashOnly) "View or share a game crash report." else "View the last game session or share a startup report.", color = palette.muted,
                style = MaterialTheme.typography.bodySmall)
        }
    }
    prompt?.let { report ->
        AlertDialog(
            onDismissRequest = { acknowledge(report); prompt = null },
            shape = RoundedCornerShape(28.dp), containerColor = palette.surfaceRaised,
            title = { Text(if (backend.crashOnly) "Game crash recorded" else "A game launch needs review", color = Color.White) },
            text = {
                Column {
                    RawText(report.title, color = palette.accentSecondary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(report.status, color = palette.muted)
                    Spacer(Modifier.height(8.dp))
                    Text(if (backend.crashOnly) "View or share a game crash report." else "A report was saved. A slow launch is not always a freeze.", color = palette.muted)
                }
            },
            confirmButton = {
                TextButton(onClick = { acknowledge(report); selected = report; prompt = null }) {
                    Text("View report", color = palette.accentSecondary)
                }
            },
            dismissButton = {
                TextButton(onClick = { acknowledge(report); prompt = null }) { Text("Dismiss", color = palette.muted) }
            }
        )
    }
    if (showHistory || selected != null) {
        Dialog(onDismissRequest = { showHistory = false; selected = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(16.dp),
                shape = RoundedCornerShape(30.dp), color = palette.surfaceDark,
                border = BorderStroke(1.dp, palette.accentSecondary.copy(alpha = .35f))) {
                Column(Modifier.padding(22.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                    Text("Diagnostics", style = MaterialTheme.typography.headlineSmall,
                        color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Reports stay on your device until you choose to share them.", color = palette.muted,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(18.dp))
                    val report = selected
                    if (report == null) {
                        if (gameReports.isEmpty()) {
                            Text("No reports yet", color = palette.accentSecondary, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(if (backend.crashOnly)
                                "Root mode records game crashes when Android provides crash details. No loading checks are recorded."
                            else "Launch a game to record its latest BlackBox session, startup checkpoints and available crash details.",
                                color = palette.muted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                        }
                        gameReports.forEach { entry ->
                            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)
                                .background(palette.surfaceRaised, RoundedCornerShape(18.dp))
                                .clickable { acknowledge(entry); selected = entry }.padding(16.dp)) {
                                RawText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(entry.startedAt)), color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(entry.status, color = palette.accentSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        RawText(report.title, color = palette.accentSecondary, fontWeight = FontWeight.Bold)
                        Text(report.status, color = palette.muted)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Jester diagnostics", report.summary))
                            android.widget.Toast.makeText(context, LauncherLocalization.translate("Diagnostics copied"),
                                android.widget.Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accentSecondary, contentColor = palette.ink)) {
                            Text("Copy summary", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = {
                            error = false
                            githubReport = report
                        }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, palette.hairline)) {
                            Text("Share full report", color = palette.accentSecondary)
                        }
                        Text("Save the report, then attach it to your GitHub issue.", color = palette.muted,
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            scope.launch {
                                error = runCatching { withContext(Dispatchers.IO) { backend.share(context, report) } }.isFailure
                            }
                        }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, palette.hairline)) {
                            Text("Share with another app", color = palette.accentSecondary)
                        }
                        if (error) Text("Could not share the report. Try copying the summary.", color = palette.muted)
                        Spacer(Modifier.height(12.dp))
                        SelectionContainer {
                            RawText(report.text.take(24_000), color = palette.muted, fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
                        }
                        if (report.text.length > 24_000) Text("Preview shortened. Share the full report for all details.",
                            color = palette.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    val targetPackage = selected?.packageName ?: packageName
                    if (targetPackage != null) {
                        OutlinedButton(onClick = { clearError = false; clearPackage = targetPackage },
                            enabled = !clearing && reports.any { it.packageName == targetPackage },
                            modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, palette.hairline)) {
                            Text("Clear diagnostics", color = palette.accentSecondary)
                        }
                        if (clearError) Text("Could not clear diagnostics. Try again.", color = palette.muted)
                    }
                    TextButton(onClick = { selected = null; showHistory = false }) { Text("Close", color = palette.accentSecondary) }
                }
            }
        }
    }
    clearPackage?.let { target ->
        AlertDialog(onDismissRequest = { if (!clearing) clearPackage = null },
            shape = RoundedCornerShape(28.dp), containerColor = palette.surfaceRaised,
            title = { Text("Clear diagnostics", color = Color.White) },
            text = { Text("Delete this game's diagnostic history? This cannot be undone. Game data and files you saved or shared are not deleted. Recording resumes on the next fresh game launch.", color = palette.muted) },
            confirmButton = {
                TextButton(enabled = !clearing, onClick = {
                    clearing = true
                    clearGeneration++
                    scope.launch {
                        clearError = runCatching { withContext(Dispatchers.IO) { backend.clear(context, target) } }.isFailure
                        if (!clearError) {
                            reports = reports.filterNot { it.packageName == target }
                            selected = null
                            githubReport = null
                            prompt?.takeIf { it.packageName == target }?.let { prompt = null }
                            showHistory = true
                        }
                        clearing = false
                        clearPackage = null
                    }
                }) { Text("Clear diagnostics", color = palette.accentSecondary) }
            },
            dismissButton = { TextButton(enabled = !clearing, onClick = { clearPackage = null }) { Text("Dismiss", color = palette.muted) } })
    }
    githubReport?.let { report ->
        AlertDialog(onDismissRequest = { githubReport = null },
            shape = RoundedCornerShape(28.dp), containerColor = palette.surfaceRaised,
            title = { Text("Share full report", color = Color.White) },
            text = { Text("GitHub issues are public. Review your report before sharing. Save the file, then attach it on GitHub and submit the issue. A GitHub account is required.", color = palette.muted) },
            confirmButton = {
                TextButton(onClick = {
                    githubReport = null
                    exportReportId = report.id
                    error = runCatching { saveReport.launch("Jester-diagnostics-${report.id}.txt") }.isFailure
                }) { Text("Save and open GitHub", color = palette.accentSecondary) }
            },
            dismissButton = { TextButton(onClick = { githubReport = null }) { Text("Dismiss", color = palette.muted) } })
    }
}
