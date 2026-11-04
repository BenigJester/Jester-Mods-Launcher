package com.moodtools.hub.networking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.Environment
import java.io.File
import java.util.zip.ZipFile

/** Retained outside the download cache: patch replacement uninstalls can remove Android/obb. */
internal class GameObbManager(private val context: Context) {
    fun preserveInstalled(packageName: String, versionCode: Long, isCancelled: () -> Boolean = { false }) {
        val retained = directory(packageName, versionCode)
        val source = File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName")
        // Check permission first: Android can hide a denied folder from isDirectory().
        requireLegacyStoragePermission()
        GameExpansionFiles.preserveInstalled(
            source, packageName, retained, isCancelled
        )
    }

    fun stage(
        archive: ZipFile, packageName: String, versionCode: Long,
        isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit
    ) {
        if (GameExpansionFiles.inspect(archive, packageName).isNotEmpty()) requireLegacyStoragePermission()
        GameExpansionFiles.stage(archive, packageName, directory(packageName, versionCode), isCancelled, onProgress)
    }

    fun install(
        packageName: String, versionCode: Long,
        destination: File = File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName"),
        isCancelled: () -> Boolean = { false }, onProgress: (Long, Long) -> Unit = { _, _ -> }
    ) {
        val directory = directory(packageName, versionCode)
        if (File(directory, "inventory.txt").isFile ||
            File(directory.parentFile, "${directory.name}-previous/inventory.txt").isFile) {
            if (destination.absolutePath.startsWith(Environment.getExternalStorageDirectory().absolutePath + "/Android/obb/")) {
                requireLegacyStoragePermission()
            }
        }
        GameExpansionFiles.deliver(directory, packageName, destination, isCancelled, onProgress)
    }

    private fun requireLegacyStoragePermission() {
        if (needsLegacyObbStoragePermission(Build.VERSION.SDK_INT,
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            context.startActivity(Intent(context, GameObbPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            error("Allow storage access in Android's permission prompt, then try again. If denied permanently, enable Storage in the launcher's app settings.")
        }
    }

    private fun directory(packageName: String, versionCode: Long): File {
        GameExpansionFiles.requirePackage(packageName)
        require(versionCode > 0)
        return File(context.filesDir, "game-obb/$packageName/$versionCode")
    }
}

internal const val LAST_LEGACY_OBB_STORAGE_API = 32

internal fun needsLegacyObbStoragePermission(sdk: Int, granted: Boolean): Boolean =
    sdk <= LAST_LEGACY_OBB_STORAGE_API && !granted
