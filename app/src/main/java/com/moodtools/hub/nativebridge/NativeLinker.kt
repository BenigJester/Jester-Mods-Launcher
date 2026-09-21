package com.moodtools.hub.nativebridge

object NativeLinker {
    init {
        System.loadLibrary("menu_native")
    }

    @JvmStatic
    external fun inspectRuntime(): Int
}
