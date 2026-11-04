package com.moodtools.hub

internal enum class PackageReplacementPhase {
    IDLE,
    PREPARING,
    WAITING_FOR_UNINSTALL,
    COMMITTING_INSTALL,
    WAITING_FOR_INSTALL_RESULT,
    WAITING_FOR_LATE_INSTALL_RESULT;

    /** A local observation timeout is not Android's terminal installation result. */
    fun afterInstallerSuccess(): PackageReplacementPhase =
        if (this == WAITING_FOR_LATE_INSTALL_RESULT) WAITING_FOR_INSTALL_RESULT else this
}
