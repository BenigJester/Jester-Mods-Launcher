package com.moodtools.hub

import com.moodtools.hub.networking.LauncherServiceException

internal data class TransferFailurePresentation(
    val headline: String,
    val detail: String
)

internal enum class TransferFailureOperation {
    LAUNCHER_UPDATE,
    ADD_ON_DOWNLOAD,
    ADD_ON_UPDATE,
    ADD_ON_REPAIR,
    GAME_DOWNLOAD,
    UPDATE_CHECK,
    PACKAGE_SETUP
}

/**
 * Gives every user-visible transfer the same evidence-based recovery guidance.
 * Storage is mentioned only when the exception chain actually reports a storage failure.
 */
internal object TransferFailurePolicy {
    fun present(
        error: Throwable,
        operation: TransferFailureOperation,
        verifiedPackageAvailable: Boolean = false,
        fallbackDetail: String? = null
    ): TransferFailurePresentation {
        val causes = generateSequence(error) { it.cause }.toList()
        val evidence = causes.joinToString("\n") { cause ->
            "${cause.javaClass.name}: ${cause.message.orEmpty()}"
        }.lowercase()
        val serviceError = causes.filterIsInstance<LauncherServiceException>().firstOrNull()

        serviceErrorPresentation(serviceError, operation)?.let { return it }

        if (operation == TransferFailureOperation.LAUNCHER_UPDATE && (
                evidence.contains("not signed by this launcher") ||
                    evidence.contains("belongs to a different launcher") ||
                    evidence.contains("different launcher package")
                )) {
            return TransferFailurePresentation(
                headline = "This launcher can't update in place",
                detail = "This copy uses a different app identity or signing certificate. It may be a debug, locally signed, or repackaged build. Install the official Jester Mods release manually; Android may require this copy to be uninstalled first."
            )
        }

        if (hasNoSpaceEvidence(evidence)) {
            return TransferFailurePresentation(
                headline = "Not enough free storage",
                detail = "Free some device storage, then try again. Any safely saved download progress can be reused."
            )
        }

        if (verifiedPackageAvailable) {
            return TransferFailurePresentation(
                headline = "Couldn't open the installer",
                detail = if (evidence.contains("install app updates") ||
                    evidence.contains("request package installs") ||
                    evidence.contains("unknown app")
                ) {
                    "Allow Jester Mods to install apps in Android settings, then try again."
                } else {
                    "The verified package is still saved. Restart the device and try opening Android's installer again."
                }
            )
        }

        if (Regex("http(?: response)? (?:401|403)").containsMatchIn(evidence)) {
            return TransferFailurePresentation(
                headline = "Download authorization expired",
                detail = "Jester Mods could not authorize this download. Refresh the add-on or update details, then try again."
            )
        }

        if (hasMissingArtifactEvidence(evidence)) {
            return TransferFailurePresentation(
                headline = "Download isn't available",
                detail = "The requested file is not available from the service right now. Open Diagnostics for its response, then try again later."
            )
        }

        if (hasBusyServiceEvidence(evidence)) {
            return TransferFailurePresentation(
                headline = "Download service is busy",
                detail = "The service asked Jester Mods to slow down. Your saved progress is safe; wait a moment, then retry."
            )
        }

        if (hasTemporaryNetworkEvidence(evidence)) {
            return TransferFailurePresentation(
                headline = if (operation == TransferFailureOperation.UPDATE_CHECK) {
                    "Couldn't reach the update service"
                } else {
                    "Couldn't reach the download service"
                },
                detail = "The connection or service was temporarily unavailable. Any partial download is safe; try again when the connection is stable."
            )
        }

        if (hasSecureConnectionEvidence(evidence)) {
            return TransferFailurePresentation(
                headline = "Secure connection failed",
                detail = "Jester Mods could not establish a trusted connection, so nothing was accepted. Check the device date and network, then retry."
            )
        }

        if (hasVerificationEvidence(evidence)) {
            return TransferFailurePresentation(
                headline = if (operation == TransferFailureOperation.PACKAGE_SETUP) {
                    "Setup package couldn't be verified"
                } else {
                    "Package couldn't be verified"
                },
                detail = "The downloaded file did not match its trusted release details, so it was not activated. Retry to obtain a clean copy."
            )
        }

        if (hasStorageWriteEvidence(evidence)) {
            return TransferFailurePresentation(
                headline = if (operation == TransferFailureOperation.PACKAGE_SETUP) {
                    "Couldn't prepare setup storage"
                } else {
                    "Couldn't save the download"
                },
                detail = "Jester Mods could not write the file inside its app storage. Restart the device and check that Android storage is available, then try again."
            )
        }

        return TransferFailurePresentation(
            headline = defaultHeadline(operation),
            detail = serviceError?.message?.takeIf { it.isNotBlank() }
                ?: fallbackDetail?.takeIf { it.isNotBlank() }
                ?: "The operation did not finish. Open Diagnostics for the recorded cause, then try again."
        )
    }

    private fun serviceErrorPresentation(
        error: LauncherServiceException?,
        operation: TransferFailureOperation
    ): TransferFailurePresentation? = when (error?.code) {
        "ACCESS_REQUIRED", "ACCESS_EXPIRED" -> TransferFailurePresentation(
            headline = "Launcher access is required",
            detail = "Your launcher access has expired. Unlock the launcher again, then retry."
        )
        "LAUNCHER_UPDATE_REQUIRED" -> TransferFailurePresentation(
            headline = "Launcher update required",
            detail = "Install the latest Jester Mods Launcher update, then retry."
        )
        "PROOF_KEY_REQUIRED", "PROOF_REJECTED" -> TransferFailurePresentation(
            headline = "Launcher session couldn't be verified",
            detail = "Restart Jester Mods or unlock it again, then retry."
        )
        "ATTESTATION_REQUIRED" -> TransferFailurePresentation(
            headline = "Device security check failed",
            detail = "This device could not complete the security check required for this add-on."
        )
        "MODULE_UNAVAILABLE" -> TransferFailurePresentation(
            headline = "Add-on isn't available",
            detail = "This add-on is not available for your device architecture or launcher version."
        )
        else -> null
    }

    private fun hasNoSpaceEvidence(evidence: String): Boolean =
        evidence.contains("enospc") ||
            evidence.contains("no space left") ||
            evidence.contains("not enough space") ||
            evidence.contains("insufficient storage") ||
            evidence.contains("insufficient space") ||
            evidence.contains("needs about") && evidence.contains("free storage") ||
            evidence.contains("requires") && evidence.contains("free storage")

    private fun hasMissingArtifactEvidence(evidence: String): Boolean =
        Regex("http(?: response)? 404").containsMatchIn(evidence) ||
            evidence.contains("download request failed with http 404")

    private fun hasBusyServiceEvidence(evidence: String): Boolean =
        Regex("http(?: response)? 429").containsMatchIn(evidence)

    private fun hasTemporaryNetworkEvidence(evidence: String): Boolean =
        Regex("(?:http(?: response)?|request failed:?)\\s*5[0-9]{2}").containsMatchIn(evidence) ||
            evidence.contains("temporary http response") ||
            evidence.contains("timed out") ||
            evidence.contains("timeout") ||
            evidence.contains("unknownhost") ||
            evidence.contains("unable to resolve host") ||
            evidence.contains("connection reset") ||
            evidence.contains("connection refused") ||
            evidence.contains("failed to connect") ||
            evidence.contains("network is unreachable") ||
            evidence.contains("software caused connection abort")

    private fun hasSecureConnectionEvidence(evidence: String): Boolean =
        evidence.contains("sslhandshake") ||
            evidence.contains("certificateexception") ||
            evidence.contains("certpathvalidatorexception") ||
            evidence.contains("trust anchor")

    private fun hasVerificationEvidence(evidence: String): Boolean =
        evidence.contains("verification failed") ||
            evidence.contains("hash mismatch") ||
            evidence.contains("sha-256") && evidence.contains("mismatch") ||
            evidence.contains("not a valid android app") ||
            evidence.contains("belongs to a different game") ||
            evidence.contains("does not match its signed catalog") ||
            evidence.contains("unexpected signing certificate") ||
            evidence.contains("signature verification failed") ||
            evidence.contains("signature did not verify") ||
            evidence.contains("signing public key is not configured") ||
            evidence.contains("wrong byte range") ||
            evidence.contains("signed size") ||
            evidence.contains("signed release") && evidence.contains("match")

    private fun hasStorageWriteEvidence(evidence: String): Boolean =
        evidence.contains("could not prepare") && evidence.contains("storage") ||
            evidence.contains("could not save") ||
            evidence.contains("could not commit") ||
            evidence.contains("could not replace the previous") ||
            evidence.contains("read-only file system") ||
            evidence.contains("permission denied") && evidence.contains("file")

    private fun defaultHeadline(operation: TransferFailureOperation): String = when (operation) {
        TransferFailureOperation.LAUNCHER_UPDATE -> "Couldn't download the launcher update"
        TransferFailureOperation.ADD_ON_DOWNLOAD -> "Couldn't download the add-on"
        TransferFailureOperation.ADD_ON_UPDATE -> "Couldn't update the add-on"
        TransferFailureOperation.ADD_ON_REPAIR -> "Couldn't repair the add-on"
        TransferFailureOperation.GAME_DOWNLOAD -> "Couldn't download the game"
        TransferFailureOperation.UPDATE_CHECK -> "Couldn't check for updates"
        TransferFailureOperation.PACKAGE_SETUP -> "Couldn't prepare the game setup"
    }
}
