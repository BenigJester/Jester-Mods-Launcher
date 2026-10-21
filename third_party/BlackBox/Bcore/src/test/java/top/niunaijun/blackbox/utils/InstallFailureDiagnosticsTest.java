package top.niunaijun.blackbox.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class InstallFailureDiagnosticsTest {
    @Test public void failuresUseFixedCategoriesWithoutPrivateMessages() {
        assertEquals("io", InstallFailureDiagnostics.exceptionKind(new java.io.IOException("/private/account")));
        assertEquals("permission", InstallFailureDiagnostics.exceptionKind(new java.io.IOException(new SecurityException("secret"))));
        assertEquals("archive", InstallFailureDiagnostics.exceptionKind(new java.util.zip.ZipException("/private/file")));
        assertEquals("memory", InstallFailureDiagnostics.exceptionKind(new OutOfMemoryError("secret")));
        assertEquals("other", InstallFailureDiagnostics.exceptionKind(new RuntimeException("secret")));
        assertEquals("install_exception:bundle:io", InstallFailureDiagnostics.resultCategory("install_exception:bundle:io"));
        assertEquals("unclassified", InstallFailureDiagnostics.resultCategory("install_exception:bundle:io\nprivate-token"));
        assertEquals("unclassified", InstallFailureDiagnostics.resultCategory(null));
        assertEquals("unclassified", InstallFailureDiagnostics.resultCategory("/private/account"));
        assertEquals("service_unavailable", InstallFailureDiagnostics.resultCategory("Remote exception during install"));
        assertEquals("abi_rejected", InstallFailureDiagnostics.resultCategory("Game[private.package] The box does not support 32-bit Application"));
        assertEquals("package_copy_failed", InstallFailureDiagnostics.resultCategory("install apk error."));
    }
}
