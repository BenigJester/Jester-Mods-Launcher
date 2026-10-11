package com.moodtools.hub

import android.content.Context
import java.io.File
import java.nio.file.Files

internal data class LaunchDiagnosticReport(
    val id: String,
    val packageName: String,
    val title: String,
    val startedAt: Long,
    val status: String,
    val important: Boolean,
    val acknowledged: Boolean,
    val text: String,
    val summary: String = text.take(12_000)
)

/** Flavor-specific collection, shared themed report UI. */
internal interface LaunchDiagnosticsBackend {
    val crashOnly: Boolean get() = false
    fun reports(context: Context): List<LaunchDiagnosticReport>
    fun acknowledge(report: LaunchDiagnosticReport)
    fun share(context: Context, report: LaunchDiagnosticReport)
    fun clear(context: Context, packageName: String)
}

internal object LaunchDiagnostics {
    @Volatile var backend: LaunchDiagnosticsBackend? = null
}

/** Only delete an explicit, direct session child; never follow links outside private diagnostics. */
internal fun deleteDiagnosticSession(session: File, parent: File) {
    require(session.name.matches(Regex("[0-9]+-[a-f0-9-]+")))
    require(!Files.isSymbolicLink(session.toPath()) && session.canonicalFile.parentFile == parent.canonicalFile)
    if (!session.exists()) return
    require(session.walkTopDown().none { Files.isSymbolicLink(it.toPath()) })
    check(session.deleteRecursively()) { "Could not clear diagnostic session" }
}
