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

    @Test public void importMetadataAndPlatformFailuresKeepFixedCategories() {
        Throwable[] errors = {new NoSuchFieldError("private-field"), new NoSuchMethodError("private-method"),
                new NoClassDefFoundError("private-class"), new IncompatibleClassChangeError("secret"),
                new NullPointerException("private-account"), new java.lang.reflect.InvocationTargetException(
                        new NoSuchFieldError("private-field"))};
        String[] kinds = {"missing_field", "missing_method", "missing_class", "linkage", "null_reference", "missing_field"};
        for (int i = 0; i < errors.length; i++) {
            assertEquals(kinds[i], InstallFailureDiagnostics.exceptionKind(new RuntimeException(errors[i])));
            for (String stage : new String[]{"parse", "split_metadata", "host_metadata", "package_settings", "stop_process"}) {
                String message = "install_exception:" + stage + ":" + kinds[i];
                assertEquals(message, InstallFailureDiagnostics.resultCategory(message));
                assertEquals("unclassified", InstallFailureDiagnostics.resultCategory(message + "\n/private/token"));
            }
        }
        assertEquals("reflection", InstallFailureDiagnostics.exceptionKind(new NoSuchMethodException("secret")));
        assertEquals("permission", InstallFailureDiagnostics.exceptionKind(new NullPointerException() {
            @Override public synchronized Throwable getCause() { return new SecurityException("secret"); }
        }));
        assertEquals("unclassified", InstallFailureDiagnostics.resultCategory("install_exception:private_stage:missing_field"));
        assertEquals("unclassified", InstallFailureDiagnostics.resultCategory("install_exception:host_metadata:secret"));
    }
}
