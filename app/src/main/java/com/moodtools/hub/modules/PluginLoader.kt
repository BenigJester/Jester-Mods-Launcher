package com.moodtools.hub.modules

import android.content.Context
import dalvik.system.DexClassLoader
import java.lang.reflect.Method
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PluginLoader(private val context: Context) {
    private fun createLoader(module: ModuleConfig): DexClassLoader {
        val directory = File(File(context.filesDir, "menus"), module.packageName)
        val dex = File(directory, module.dexFile)
        require(dex.isFile && dex.length() > 0L) { "Module DEX is missing" }
        check(dex.setReadOnly() || !dex.canWrite()) { "Module DEX could not be made read-only" }

        // A stable optimized-dex directory can keep the previous Menu.class
        // after a module update. Include the payload size and timestamp in the
        // cache key so updated slider/spinner rendering is loaded immediately.
        val cacheKey = "${dex.length()}-${dex.lastModified()}"
        val loaderKey = "${module.packageName}/$cacheKey"
        loaders[loaderKey]?.let { return it }
        val optimized = File(
            context.codeCacheDir,
            "modules/${module.packageName}/$cacheKey"
        ).apply { mkdirs() }
        return DexClassLoader(
            dex.absolutePath,
            optimized.absolutePath,
            directory.absolutePath,
            context.classLoader
        ).also { loaders[loaderKey] = it }
    }

    fun load(module: ModuleConfig): MenuPlugin? {
        val entryPoint = module.entryPoint ?: return null
        val loader = createLoader(module)

        val pluginClass = loader.loadClass(entryPoint)
        val plugin = pluginClass.getDeclaredConstructor().newInstance()
        if (plugin is MenuPlugin) return plugin

        // Legacy standalone menu templates expose Main.Start(Context) instead of MenuPlugin.
        // Keep the module boundary small while allowing those templates to be staged here.
        val start = runCatching {
            pluginClass.getMethod("StartExternal", Context::class.java)
        }.getOrElse {
            pluginClass.getMethod("Start", Context::class.java)
        }
        return ReflectiveMenuPlugin(start)
    }

    /** Loads the module through its own class loader so JNI_OnLoad resolves module classes. */
    fun loadNative(module: ModuleConfig, nativePath: File): Boolean {
        require(nativePath.isFile) { "Module native library is missing" }
        val loader = createLoader(module)
        val namespace = module.entryPoint?.substringBeforeLast('.', "")
            ?.takeIf(String::isNotBlank)
            ?: error("Module entry point is missing")
        val runtime = loader.loadClass("$namespace.ModuleRuntime")
        val load = runtime.getMethod("loadNativeExternal", String::class.java)
        load.invoke(null, cachedNative(module, nativePath).absolutePath)
        return true
    }

    fun loadNativeForExtraction(module: ModuleConfig, nativePath: File): Boolean {
        require(nativePath.isFile) { "Module native library is missing" }
        val namespace = module.entryPoint?.substringBeforeLast('.', "")
            ?.takeIf(String::isNotBlank)
            ?: error("Module entry point is missing")
        val runtime = createLoader(module).loadClass("$namespace.ModuleRuntime")
        runtime.getMethod("loadNativeForExtraction", String::class.java)
            .invoke(null, cachedNative(module, nativePath).absolutePath)
        return true
    }

    fun loadNativeBlackBox(
        module: ModuleConfig,
        nativePath: File,
        featureStatePath: File
    ): Boolean {
        require(nativePath.isFile) { "Module native library is missing" }
        require(featureStatePath.isAbsolute) { "BlackBox feature-state path must be absolute" }
        val namespace = module.entryPoint?.substringBeforeLast('.', "")
            ?.takeIf(String::isNotBlank)
            ?: error("Module entry point is missing")
        val runtime = createLoader(module).loadClass("$namespace.ModuleRuntime")
        val load = runtime.getMethod(
            "loadNativeBlackBox",
            String::class.java,
            String::class.java
        )
        load.invoke(
            null,
            cachedNative(module, nativePath).absolutePath,
            featureStatePath.absolutePath
        )
        return true
    }

    fun loadNativeDirectPatchExternal(module: ModuleConfig, nativePath: File): Boolean {
        require(nativePath.isFile) { "Module native library is missing" }
        val namespace = module.entryPoint?.substringBeforeLast('.', "")
            ?.takeIf(String::isNotBlank)
            ?: error("Module entry point is missing")
        val runtime = createLoader(module).loadClass("$namespace.ModuleRuntime")
        runtime.getMethod("loadNativeDirectPatchExternal", String::class.java)
            .invoke(null, cachedNative(module, nativePath).absolutePath)
        return true
    }

    private fun cachedNative(module: ModuleConfig, source: File): File {
        val cacheKey = "${source.length()}-${source.lastModified()}"
        val directory = File(context.codeCacheDir, "modules/${module.packageName}/$cacheKey")
            .apply { check(isDirectory || mkdirs()) { "Module native cache could not be created" } }
        val target = File(directory, source.name)
        if (target.length() != source.length()) {
            val temporary = File(directory, "${source.name}.next")
            source.copyTo(temporary, overwrite = true)
            check(!target.exists() || target.delete()) { "Stale module native cache could not be replaced" }
            check(temporary.renameTo(target)) { "Module native cache could not be committed" }
        }
        return target
    }

    fun extractEmbeddedLibrary(module: ModuleConfig, output: File): Boolean {
        val namespace = module.entryPoint?.substringBeforeLast('.', "")
            ?.takeIf(String::isNotBlank)
            ?: error("Module entry point is missing")
        val runtime = createLoader(module).loadClass("$namespace.ModuleRuntime")
        val extract = runtime.getMethod("extractEmbeddedIl2Cpp", String::class.java)
        return extract.invoke(null, output.absolutePath) == true
    }

    fun embeddedLibrarySha256(module: ModuleConfig): String {
        val namespace = module.entryPoint?.substringBeforeLast('.', "")
            ?.takeIf(String::isNotBlank)
            ?: error("Module entry point is missing")
        val runtime = createLoader(module).loadClass("$namespace.ModuleRuntime")
        return (runtime.getMethod("embeddedIl2CppSha256").invoke(null) as String).lowercase()
    }

    fun stopExternal(module: ModuleConfig) {
        val entryPoint = module.entryPoint ?: return
        val pluginClass = createLoader(module).loadClass(entryPoint)
        pluginClass.getMethod("StopExternal").invoke(null)
    }

    companion object {
        private val loaders = ConcurrentHashMap<String, DexClassLoader>()
    }
}

private class ReflectiveMenuPlugin(private val start: Method) : MenuPlugin {
    override fun onLaunch(context: PluginContext) {
        start.invoke(null, context.hostContext)
    }
}
