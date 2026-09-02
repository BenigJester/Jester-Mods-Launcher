package com.moodtools.hub

import com.moodtools.hub.modules.NonRootMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityShellRemovalPolicyTest {
    @Test
    fun installedExactPackageShellRequiresAndroidUninstallInNonRootMode() {
        assertTrue(
            shouldUninstallIdentityShellBeforeRemoving(
                isRootMode = false,
                method = NonRootMethod.IDENTITY_SHELL,
                installedIdentityShell = true
            )
        )
    }

    @Test
    fun originalAndOtherInstallationMethodsAreNeverUninstalledByLibraryRemoval() {
        assertFalse(
            shouldUninstallIdentityShellBeforeRemoving(
                isRootMode = false,
                method = NonRootMethod.IDENTITY_SHELL,
                installedIdentityShell = false
            )
        )
        assertFalse(
            shouldUninstallIdentityShellBeforeRemoving(
                isRootMode = false,
                method = NonRootMethod.DIRECT_PATCH,
                installedIdentityShell = true
            )
        )
        assertFalse(
            shouldUninstallIdentityShellBeforeRemoving(
                isRootMode = false,
                method = NonRootMethod.INJECTION,
                installedIdentityShell = true
            )
        )
        assertFalse(
            shouldUninstallIdentityShellBeforeRemoving(
                isRootMode = true,
                method = NonRootMethod.IDENTITY_SHELL,
                installedIdentityShell = true
            )
        )
    }
}
