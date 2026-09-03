package com.moodtools.hub

import com.moodtools.hub.networking.LauncherServiceException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TransferFailurePolicyTest {
    @Test
    fun differentSignerExplainsWhyManualOfficialInstallIsRequired() {
        val result = TransferFailurePolicy.present(
            IllegalArgumentException("The update is not signed by this launcher"),
            TransferFailureOperation.LAUNCHER_UPDATE
        )

        assertEquals("This launcher can't update in place", result.headline)
        assertFalse(result.detail.contains("connection", ignoreCase = true))
        assertFalse(result.detail.contains("storage", ignoreCase = true))
    }

    @Test
    fun storageGuidanceIsReservedForActualNoSpaceEvidenceAcrossDownloads() {
        val result = TransferFailurePolicy.present(
            IOException("write failed: ENOSPC (No space left on device)"),
            TransferFailureOperation.GAME_DOWNLOAD
        )

        assertEquals("Not enough free storage", result.headline)
    }

    @Test
    fun serverFailureDoesNotClaimTheDeviceIsFull() {
        val result = TransferFailurePolicy.present(
            IOException("Temporary HTTP response 503"),
            TransferFailureOperation.ADD_ON_UPDATE
        )

        assertEquals("Couldn't reach the download service", result.headline)
        assertFalse(result.detail.contains("storage", ignoreCase = true))
    }

    @Test
    fun verifiedGamePackageFailureKeepsInstallerRecovery() {
        val result = TransferFailurePolicy.present(
            IllegalStateException("No activity found to handle installer"),
            TransferFailureOperation.GAME_DOWNLOAD,
            verifiedPackageAvailable = true
        )

        assertEquals("Couldn't open the installer", result.headline)
    }

    @Test
    fun moduleAccessFailureKeepsServerRecoveryInstruction() {
        val result = TransferFailurePolicy.present(
            LauncherServiceException("LAUNCHER_UPDATE_REQUIRED", "Update first"),
            TransferFailureOperation.ADD_ON_DOWNLOAD
        )

        assertEquals("Launcher update required", result.headline)
        assertFalse(result.detail.contains("storage", ignoreCase = true))
    }

    @Test
    fun forbiddenDownloadRequestsFreshAuthorizationInsteadOfBlamingStorage() {
        val result = TransferFailurePolicy.present(
            IOException("Download request failed with HTTP 403"),
            TransferFailureOperation.ADD_ON_DOWNLOAD
        )

        assertEquals("Download authorization expired", result.headline)
        assertFalse(result.detail.contains("storage", ignoreCase = true))
    }

    @Test
    fun verificationFailureNeverSuggestsInstallingTheArtifact() {
        val result = TransferFailurePolicy.present(
            IOException("Game download verification failed"),
            TransferFailureOperation.GAME_DOWNLOAD
        )

        assertEquals("Package couldn't be verified", result.headline)
        assertEquals(true, result.detail.contains("not activated"))
    }

    @Test
    fun manifestHttpFailureInsideServiceErrorStillUsesNetworkRecovery() {
        val result = TransferFailurePolicy.present(
            LauncherServiceException("SERVER_ERROR", "Manifest request failed: 503"),
            TransferFailureOperation.ADD_ON_DOWNLOAD
        )

        assertEquals("Couldn't reach the download service", result.headline)
        assertFalse(result.detail.contains("storage", ignoreCase = true))
    }

    @Test
    fun directPatchFreeSpaceRequirementIsRecognizedAsRealStorageEvidence() {
        val result = TransferFailurePolicy.present(
            IOException("Direct patching needs about 3.2 GB of free storage"),
            TransferFailureOperation.PACKAGE_SETUP
        )

        assertEquals("Not enough free storage", result.headline)
    }

    @Test
    fun unknownFailuresUseOperationSpecificHeadlineAndDiagnostics() {
        val result = TransferFailurePolicy.present(
            IOException("unexpected socket behavior"),
            TransferFailureOperation.ADD_ON_REPAIR
        )

        assertEquals("Couldn't repair the add-on", result.headline)
        assertFalse(result.detail.contains("storage", ignoreCase = true))
        assertEquals(true, result.detail.contains("Diagnostics"))
    }

    @Test
    fun setupCanPreserveSpecificNonTransferFailureDetail() {
        val result = TransferFailurePolicy.present(
            IllegalStateException("unexpected preparation state"),
            TransferFailureOperation.PACKAGE_SETUP,
            fallbackDetail = "Android could not confirm the prepared package."
        )

        assertEquals("Android could not confirm the prepared package.", result.detail)
    }
}
