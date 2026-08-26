package com.moodtools.hub.modules

import android.content.Context
import dalvik.system.DexClassLoader
import java.lang.reflect.Method
import java.io.File

class PluginLoader(private val context: Context) {
    private fun createLoader(module: ModuleConfig): DexClassLoader {
        val directory = File(File(context.filesDir, "menus"), module.packageName)
        val dex = File(directory, module.dexFile)
        require(dex.isFile && dex.length() > 0L) { "Module DEX is missing" }

        // A stable optimized-dex directory can keep the previous Menu.class
        // after a module update. Include the payload size and timestamp in the
        // cache key so updated slider/spinner rendering is loaded immediately.
        val cacheKey = "${dex.length()}-${dex.lastModified()}"
        val optimized = File(
            context.codeCacheDir,
            "modules/${module.packageName}/$cacheKey"
        ).apply { mkdirs() }
        return DexClassLoader(
            dex.absolutePath,
            optimized.absolutePath,
            directory.absolutePath,
            context.classLoader
        )
    }

    fun load(module: ModuleConfig): MenuPlugin? {
        val entryPoint = module.entryPoint ?: return null
        val loader = createLoader(module)

        val pluginClass = loader.loadClass(entryPoint)
        val plugin = pluginClass.getDeclaredConstructor().newInstance()
        if (plugin is MenuPlugin) return plugin

        // Legacy standalone menu templates expose Main.Start(Context) instead of MenuPlugin.
        // Keep the module boundary small while allowing those templates to be staged here.
        val start = pluginClass.getMethod("Start", Context::class.java)
        return ReflectiveMenuPlugin(start)
    }

    /** Loads the module through its own class loader so JNI_OnLoad resolves module classes. */
    fun loadNative(module: ModuleConfig, nativePath: File): Boolean {
        require(nativePath.isFile) { "Module native library is missing" }
        val loader = createLoader(module)
        val runtime = loader.loadClass("${module.packageName}.ModuleRuntime")
        val load = runtime.getMethod("loadNative", String::class.java)
        load.invoke(null, nativePath.absolutePath)
        return true
    }
}

private class ReflectiveMenuPlugin(private val start: Method) : MenuPlugin {
    override fun onLaunch(context: PluginContext) {
        start.invoke(null, context.hostContext)
    }
}
