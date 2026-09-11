package com.moodtools.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore

internal fun isDiagnosticExportFileName(value: String): Boolean =
    Regex("^(MoodTools|OtherworldLegends)-diagnostics-[0-9]{8}-[0-9]{6}\\.txt$").matches(value)

class DiagnosticExportProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(Binder.getCallingUid() == android.os.Process.myUid()) { "Caller is not the launcher" }
        require(mode == "w" || mode == "wt") { "Diagnostic exports are write-only" }
        val fileName = uri.pathSegments.getOrNull(1).orEmpty()
        require(uri.pathSegments.getOrNull(0) == "export" && isDiagnosticExportFileName(fileName)) {
            "Invalid diagnostic export path"
        }

        val resolver = requireNotNull(context).contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MoodTools")
        }
        val target = requireNotNull(
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ) { "Downloads rejected the file" }
        return try {
            requireNotNull(resolver.openFileDescriptor(target, "w")) {
                "Downloads could not open the file"
            }
        } catch (error: Throwable) {
            resolver.delete(target, null, null)
            throw error
        }
    }

    override fun getType(uri: Uri): String = "text/plain"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0
}
