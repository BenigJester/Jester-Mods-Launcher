package top.niunaijun.blackbox.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class FailureEvidenceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static Throwable cleanup() {
        Throwable error = new IllegalArgumentException("private-account-token");
        error.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("android.net.ConnectivityManager", "unregisterNetworkCallback", "/private/path", 5542),
                new StackTraceElement("android.app.ActivityThread", "performDestroyActivity", "/private/path", 6413)});
        return new RuntimeException("secret-url", error);
    }

    @Test public void cleanupIsLabeledWithoutClaimingTheOriginalCause() {
        assertEquals("activity_teardown", FailureEvidence.phase(cleanup()));
        assertEquals("network_callback_cleanup", FailureEvidence.hint(cleanup()));
        Throwable registration = new IllegalArgumentException();
        registration.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("android.net.ConnectivityManager", "unregisterNetworkCallback", "", 1)});
        assertEquals("unclassified", FailureEvidence.phase(registration));
        assertEquals("unclassified", FailureEvidence.hint(registration));
        Throwable cyclic = new RuntimeException(); cyclic.initCause(new RuntimeException(cyclic));
        assertEquals("unclassified", FailureEvidence.phase(cyclic));
    }

    @Test public void laterCleanupDoesNotOverwriteFirstFailureAndDuplicatesAreBounded() throws Exception {
        File file = new File(temporary.newFolder(), "failure-1.txt");
        Throwable original = new IllegalStateException("secret-original");
        FailureEvidence.record(file, "application_bind", original, LaunchSessionRecorder.exceptionFrames(original));
        String first = read(file);
        Throwable teardown = cleanup();
        FailureEvidence.record(file, "uncaught_java", teardown, LaunchSessionRecorder.exceptionFrames(teardown));
        String both = read(file);
        assertTrue(both.startsWith(first));
        assertTrue(both.contains("Failure capture: later_observed"));
        assertTrue(both.contains("Failure phase: activity_teardown"));
        assertTrue(both.contains("trigger for shutdown is not established"));
        assertFalse(both.contains("private-account-token"));
        assertFalse(both.contains("secret-url"));
        assertFalse(both.contains("/private/path"));
        FailureEvidence.record(file, "uncaught_java", teardown, LaunchSessionRecorder.exceptionFrames(teardown));
        assertEquals(both, read(file));
        FailureEvidence.record(file, "third", original, "third frames");
        String three = read(file);
        FailureEvidence.record(file, "fourth", original, "fourth frames");
        assertEquals(three, read(file));
    }

    @Test public void oldEvidenceAndClearedSessionsArePreserved() throws Exception {
        File parent = temporary.newFolder();
        File file = new File(parent, "failure-1.txt");
        Files.write(file.toPath(), "legacy first failure\n".getBytes(StandardCharsets.UTF_8));
        FailureEvidence.record(file, "uncaught_java", cleanup(), "safe frames");
        assertTrue(read(file).startsWith("legacy first failure\n"));
        FailureEvidence.record(file, "second", cleanup(), "second frames");
        String bounded = read(file);
        FailureEvidence.record(file, "third", cleanup(), "third frames");
        assertEquals(bounded, read(file));
        assertTrue(file.delete());
        assertTrue(parent.delete());
        FailureEvidence.record(file, "uncaught_java", cleanup(), "safe frames");
        assertFalse(parent.exists());
    }

    @Test public void oversizedRecordsRemainBoundedAndSymlinksAreNotFollowed() throws Exception {
        File parent = temporary.newFolder();
        File file = new File(parent, "failure-1.txt");
        FailureEvidence.record(file, "uncaught_java", cleanup(), "\u4e2d".repeat(100000));
        assertTrue(file.length() <= 64 * 1024);
        String first = read(file);
        FailureEvidence.record(file, "later", cleanup(), "\u6587".repeat(100000));
        assertEquals(first, read(file));
        // Windows may not permit symlink creation for this account.
        File link = new File(parent, "failure-2.txt");
        try { Files.createSymbolicLink(link.toPath(), file.toPath()); }
        catch (java.nio.file.FileSystemException | UnsupportedOperationException unavailable) { return; }
        FailureEvidence.record(link, "uncaught_java", cleanup(), "must not append");
        assertEquals(first, read(file));
    }
}
