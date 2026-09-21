package com.moodtools.hub

import com.moodtools.hub.networking.LauncherServiceException

internal const val ACCESS_KEY_INVALID = "ACCESS_KEY_INVALID"
internal const val ACCESS_KEY_IN_USE = "ACCESS_KEY_IN_USE"
internal const val ACCESS_KEY_RETRY = "ACCESS_KEY_RETRY"

internal data class AccessKeyErrorFeedback(val code: String, val message: String)

internal fun accessKeyErrorFeedback(error: Throwable): AccessKeyErrorFeedback {
    val serviceCode = (error as? LauncherServiceException)?.code
    val message = error.message.orEmpty()
    return when {
        serviceCode == "ROOT_LAUNCHER_REQUIRED" -> AccessKeyErrorFeedback(
            serviceCode,
            "Open Jester Mods Root Launcher and enter this key there. This key has not been used."
        )
        message == "Enter a valid Jester Mods access key" ||
            message == "This access key is invalid." -> AccessKeyErrorFeedback(
                ACCESS_KEY_INVALID,
                "We couldn't find this key. Check the key from your Ko-fi order and try again."
            )
        message == "This access key is already bound to another device." -> AccessKeyErrorFeedback(
            ACCESS_KEY_IN_USE,
            "This key has already been used on another device."
        )
        else -> AccessKeyErrorFeedback(
            ACCESS_KEY_RETRY,
            "We couldn't redeem your key right now. Try again in a moment. You won't lose any purchased time."
        )
    }
}

internal fun accessKeyErrorTitle(code: String?): String = when (code) {
    "ROOT_LAUNCHER_REQUIRED" -> "Open the Root Launcher"
    ACCESS_KEY_INVALID -> "Check your key"
    ACCESS_KEY_IN_USE -> "Key already used"
    else -> "Please try again"
}
