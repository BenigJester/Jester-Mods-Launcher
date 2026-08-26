package com.moodtools.hub.nativebridge

object NativeLinker {
    init {
        System.loadLibrary("menu_native")
    }

    @JvmStatic
    external fun load(nativePath: String, packageName: String): Boolean

    @JvmStatic
    external fun unload()

    @JvmStatic
    external fun inspectRuntime(): Int
}
