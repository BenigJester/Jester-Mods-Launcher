package com.moodtools.hub

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Persists a verified Shell/Patch handoff until Android confirms installation. */
internal class PackageReplacementRecoveryStore(
    private val filesDirectory: File
) {
    constructor(context: Context) : this(context.filesDir)

    private val stateFile = File(filesDirectory, STATE_FILE)

    fun save(request: PackageReplacementRequest) {
        require(request.packageName.matches(PACKAGE_NAME)) { "Invalid replacement package" }
        require(request.apks.isNotEmpty()) { "A replacement package must contain an APK" }
        val filesRoot = filesDirectory.canonicalFile
        val apkPaths = request.apks.map { apk ->
            val canonical = apk.canonicalFile
            require(canonical.isFile && canonical.length() > 0L &&
                canonical.path.startsWith(filesRoot.path + File.separator)) {
                "Replacement APK is outside launcher storage"
            }
            canonical.path
        }
        val document = JSONObject()
            .put("schema", SCHEMA)
            .put("packageName", request.packageName)
            .put("title", request.title)
            .put("versionCode", request.versionCode)
            .put("requiresUninstall", request.requiresUninstall)
            .put("kind", request.kind.name)
            .put("inheritExistingInstallation", request.inheritExistingInstallation)
            .put("apks", JSONArray(apkPaths))
        val incoming = File(filesDirectory, "$STATE_FILE.incoming")
        incoming.writeText(document.toString(), Charsets.UTF_8)
        check(!stateFile.exists() || stateFile.delete()) { "Could not replace setup recovery state" }
        check(incoming.renameTo(stateFile)) { "Could not save setup recovery state" }
    }

    fun load(): PackageReplacementRequest? {
        if (!stateFile.isFile) return null
        return runCatching {
            val document = JSONObject(stateFile.readText(Charsets.UTF_8))
            require(document.getInt("schema") == SCHEMA)
            val packageName = document.getString("packageName")
            require(packageName.matches(PACKAGE_NAME))
            val versionCode = document.getLong("versionCode")
            require(versionCode > 0L)
            val filesRoot = filesDirectory.canonicalFile
            val paths = document.getJSONArray("apks")
            val apks = (0 until paths.length()).map { index ->
                File(paths.getString(index)).canonicalFile.also { apk ->
                    require(apk.isFile && apk.length() > 0L &&
                        apk.path.startsWith(filesRoot.path + File.separator) &&
                        apk.extension.equals("apk", ignoreCase = true))
                }
            }
            require(apks.isNotEmpty())
            PackageReplacementRequest(
                packageName = packageName,
                title = document.getString("title"),
                versionCode = versionCode,
                apks = apks,
                requiresUninstall = document.getBoolean("requiresUninstall"),
                kind = PackageReplacementKind.valueOf(document.getString("kind")),
                inheritExistingInstallation = document.optBoolean("inheritExistingInstallation", false)
            )
        }.getOrElse {
            clear()
            null
        }
    }

    fun clear() {
        if (stateFile.exists()) stateFile.delete()
        File(filesDirectory, "$STATE_FILE.incoming").let { if (it.exists()) it.delete() }
    }

    companion object {
        private const val STATE_FILE = "package-replacement-recovery.json"
        private const val SCHEMA = 1
        private val PACKAGE_NAME = Regex("[A-Za-z0-9_.]{3,200}")
    }
}
