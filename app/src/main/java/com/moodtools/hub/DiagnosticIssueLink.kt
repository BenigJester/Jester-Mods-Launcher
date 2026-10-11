package com.moodtools.hub

import java.net.URLEncoder

/** Keep browser links small; the complete report travels as a user-selected attachment. */
internal fun diagnosticIssueLink(report: LaunchDiagnosticReport): String {
    fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    val title = encode("Diagnostics: ${report.packageName.take(120)} / ${report.status.take(80)}")
    val heading = "## What happened?\nDescribe the problem and steps to reproduce it here.\n\n" +
        "## Full diagnostic report\nAttach the saved Jester-diagnostics text file here before submitting.\n\n" +
        "## Diagnostic excerpt (not the full report)\n"
    // Encode before limiting: non-ASCII and punctuation can expand several times in a URL.
    var excerpt = report.summary.take(1800)
    fun body() = encode(heading + excerpt.replace("```", "'''"))
    while (body().length > 5500 && excerpt.isNotEmpty()) excerpt = excerpt.dropLast(100.coerceAtMost(excerpt.length))
    return "https://github.com/BenigJester/Jester-Mods-Launcher/issues/new?title=$title&body=${body()}"
}
