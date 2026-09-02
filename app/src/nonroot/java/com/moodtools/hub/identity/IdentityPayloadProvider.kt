package com.moodtools.hub.identity

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.moodtools.hub.networking.SmartStorageManager
import java.io.File

/** Read-only payload bridge plus a caller-bound acknowledgement for completed game imports. */
class IdentityPayloadProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r") { "Identity payloads are read-only" }
        val targetPackage = uri.pathSegments.getOrNull(1).orEmpty()
        val fileName = uri.pathSegments.getOrNull(2).orEmpty()
        require(uri.pathSegments.getOrNull(0) == "payload" &&
            PACKAGE_PATTERN.matches(targetPackage) && fileName in ALLOWED_FILES) {
            "Invalid identity payload path"
        }
        require(callerOwnsPackage(targetPackage)) { "The caller does not own this identity" }
        val appContext = requireNotNull(context).applicationContext
        val file = if (fileName == GAME_PAYLOAD) {
            File(appContext.filesDir, "identity-shells/$targetPackage/$GAME_PAYLOAD")
        } else {
            File(appContext.filesDir, "menus/$targetPackage/$fileName")
        }
        require(file.isFile && file.length() > 0L) { "Identity payload is unavailable" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun callerOwnsPackage(targetPackage: String): Boolean {
        val callerUid = Binder.getCallingUid()
        if (callerUid == android.os.Process.myUid()) return true
        return requireNotNull(context).packageManager.getPackagesForUid(callerUid)
            .orEmpty()
            .contains(targetPackage)
    }

    override fun getType(uri: Uri): String = "application/octet-stream"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val targetPackage = uri.pathSegments.getOrNull(1).orEmpty()
        val fileName = uri.pathSegments.getOrNull(2).orEmpty()
        require(uri.pathSegments.getOrNull(0) == "payload" &&
            PACKAGE_PATTERN.matches(targetPackage) && fileName in ALLOWED_FILES) {
            "Invalid identity payload path"
        }
        require(callerOwnsPackage(targetPackage)) { "The caller does not own this identity" }
        val appContext = requireNotNull(context).applicationContext
        val file = if (fileName == GAME_PAYLOAD) {
            File(appContext.filesDir, "identity-shells/$targetPackage/$GAME_PAYLOAD")
        } else {
            File(appContext.filesDir, "menus/$targetPackage/$fileName")
        }
        require(file.isFile && file.length() > 0L) { "Identity payload is unavailable" }

        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns, 1).apply {
            val values: Array<Any?> = columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> fileName
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            }.toTypedArray<Any?>()
            addRow(values)
        }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(method == METHOD_GAME_IMPORT_SUCCEEDED) { "Unsupported identity payload operation" }
        val targetPackage = arg.orEmpty()
        require(PACKAGE_PATTERN.matches(targetPackage)) { "Invalid identity package" }
        require(callerOwnsPackage(targetPackage)) { "The caller does not own this identity" }
        val appContext = requireNotNull(context).applicationContext
        val result = SmartStorageManager(appContext.filesDir, appContext.cacheDir)
            .onIdentityGameImportSucceeded(targetPackage)
        return Bundle().apply {
            putInt(RESULT_DELETED_FILES, result.deletedFiles)
            putLong(RESULT_RECLAIMED_BYTES, result.reclaimedBytes)
        }
    }

    companion object {
        private const val METHOD_GAME_IMPORT_SUCCEEDED = "identity_game_import_succeeded"
        private const val RESULT_DELETED_FILES = "deleted_files"
        private const val RESULT_RECLAIMED_BYTES = "reclaimed_bytes"
        private const val GAME_PAYLOAD = "game.apks"
        private val ALLOWED_FILES = setOf(
            GAME_PAYLOAD,
            "classes.dex",
            "libmenu_native.so",
            "config.json"
        )
        private val PACKAGE_PATTERN = Regex("^[A-Za-z0-9_.]{3,200}$")
    }
}
