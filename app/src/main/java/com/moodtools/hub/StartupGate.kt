package com.moodtools.hub

data class StartupGateResult(val allowed: Boolean, val message: String? = null)

sealed interface LauncherStartupState {
    data object CheckingRoot : LauncherStartupState
    data object CheckingAccess : LauncherStartupState
    data class RootDenied(val message: String) : LauncherStartupState
    data class SecurityBlocked(val message: String) : LauncherStartupState
    data class ConnectionRequired(val message: String) : LauncherStartupState
    data class Locked(val message: String? = null) : LauncherStartupState
    data class Ready(val expiresAt: Long) : LauncherStartupState
}

internal fun shouldEnterLauncherLibrary(
    state: LauncherStartupState,
    enteredByUser: Boolean
): Boolean = state is LauncherStartupState.Ready && enteredByUser
