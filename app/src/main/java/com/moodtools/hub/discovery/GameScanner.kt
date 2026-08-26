package com.moodtools.hub.discovery

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.moodtools.hub.modules.InstalledGame
import com.moodtools.hub.modules.ModuleConfig
import java.io.File
import java.util.zip.ZipFile

class GameScanner(private val context: Context) {
    private val packageManager = context.packageManager

    fun scan(modules: List<ModuleConfig>): List<InstalledGame> {
        return modules.mapNotNull { module ->
            runCatching {
                val info = packageManager.getApplicationInfo(module.packageName, 0)
                val packageInfo = packageManager.getPackageInfo(module.packageName, 0)
                val versionName = packageInfo.versionName ?: "unknown"
                val abi = detectInstalledAbi(info)
                InstalledGame(
                    packageName = module.packageName,
                    versionName = versionName,
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    },
                    label = packageManager.getApplicationLabel(info).toString(),
                    icon = packageManager.getApplicationIcon(info),
                    module = module,
                    versionSupported = module.supportedVersions.contains(versionName),
                    abi = abi,
                    abiSupported = module.supportedAbis.contains(abi)
                )
            }.getOrNull()
        }
    }

    private fun detectInstalledAbi(info: ApplicationInfo): String {
        abiFromNativeLibraryDir(info.nativeLibraryDir)?.let { return it }

        // nativeLibraryDir normally identifies Android's selected process ABI. If a package does
        // not expose a usable directory, inspect its base/split APK native folders and choose the
        // first ABI that the device can actually run. This also handles split-APK installs.
        val packagedAbis = linkedSetOf<String>()
        buildList {
            info.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            info.splitSourceDirs?.filterNotNull()?.filter { it.isNotBlank() }?.let(::addAll)
        }.forEach { apkPath ->
            runCatching {
                ZipFile(apkPath).use { apk ->
                    val entries = apk.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val name = entry.name
                        if (!name.startsWith("lib/") || !name.endsWith(".so")) continue
                        val abi = name.substringAfter("lib/").substringBefore('/')
                        if (abi in KNOWN_ABIS) packagedAbis += abi
                    }
                }
            }
        }

        return Build.SUPPORTED_ABIS.firstOrNull { it in packagedAbis }
            ?: packagedAbis.firstOrNull()
            ?: ABI_UNKNOWN
    }

    private fun abiFromNativeLibraryDir(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return when (File(path).name.lowercase()) {
            "arm64", "arm64-v8a" -> "arm64-v8a"
            "arm", "armeabi-v7a" -> "armeabi-v7a"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> null
        }
    }

    companion object {
        const val ABI_UNKNOWN = "unknown"
        private val KNOWN_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }
}
