package com.moodtools.hub

import java.net.URLEncoder

/** Keep useful evidence in the issue even when the user cannot attach a file. */
internal fun diagnosticIssueLink(report: LaunchDiagnosticReport): String {
    return diagnosticIssueLink("Diagnostics: ${report.packageName.take(120)} / ${report.status.take(80)}", compactDiagnosticExcerpt(report.summary))
}

internal fun compactDiagnosticExcerpt(text: String): String {
    var frames = 0
    return text.lineSequence().filter { line ->
        if (line.startsWith("cause ")) frames = 0
        if (line.trimStart().startsWith("at ")) ++frames <= 6 else true
    }.joinToString("\n")
}

internal fun diagnosticIssueLink(reportTitle: String, reportText: String): String {
    fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    var titleText = reportTitle.take(210)
    while (encode(titleText).length > 600) titleText = titleText.dropLast(1)
    val title = encode(titleText)
    val heading = "## What happened?\nTell us what you saw and what you tapped. Did the game open?\n\n" +
        "## Automatic diagnostics\nKey details are included below for initial investigation. This is a shortened report.\n"
    val attachment = "\n\n## Optional extra details\nIf you saved a Jester-diagnostics .txt file, attach it using GitHub's attachment button or file-drop area. " +
        "If you cannot attach it, you can still submit this issue."
    // Encode before limiting: non-ASCII and punctuation can expand several times in a URL.
    var excerpt = compactDiagnosticExcerpt(reportText.take(24_000))
    fun body() = encode(heading + excerpt.replace("```", "'''") + "\n[End of shortened diagnostics]" + attachment)
    while (body().length > 5500 && excerpt.isNotEmpty()) {
        val lines = excerpt.lines()
        // Remove long stack traces before sacrificing metadata or the startup event trail.
        val frame = lines.indexOfLast { it.trimStart().startsWith("at ") }
        excerpt = if (frame >= 0) lines.filterIndexed { index, _ -> index != frame }.joinToString("\n")
            else {
                // Do not leave a half-written resource/counter line in the submitted report.
                val end = excerpt.lastIndexOf('\n', (excerpt.length - 100).coerceAtLeast(0))
                if (end >= 0) excerpt.take(end) else ""
            }
    }
    return "https://github.com/BenigJester/Jester-Mods-Launcher/issues/new?title=$title&body=${body()}"
}
