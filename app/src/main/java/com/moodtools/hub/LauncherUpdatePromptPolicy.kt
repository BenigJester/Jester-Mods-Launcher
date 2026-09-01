package com.moodtools.hub

/** Keeps "Later" scoped to this launcher process while allowing newer releases through. */
internal object LauncherUpdatePromptPolicy {
    fun shouldOpen(
        releaseBuild: Long,
        dismissedBuild: Long?,
        currentBuild: Long,
        currentlyOpen: Boolean
    ): Boolean {
        if (currentlyOpen && currentBuild == releaseBuild) return true
        return dismissedBuild != releaseBuild
    }
}

/** Applies the same session-only "Later" behavior to installed add-on update sets. */
internal object InstalledModuleUpdatePromptPolicy {
    fun shouldOpen(
        updateKeys: Set<String>,
        dismissedKeys: Set<String>,
        currentlyOpen: Boolean
    ): Boolean = currentlyOpen || updateKeys.any { it !in dismissedKeys }
}
