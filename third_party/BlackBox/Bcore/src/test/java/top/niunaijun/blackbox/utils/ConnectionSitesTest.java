package top.niunaijun.blackbox.utils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Deque;
import static org.junit.Assert.*;

public class ConnectionSitesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Before public void clearSites() throws Exception {
        Field sites = ConnectionDiagnostics.class.getDeclaredField("sites");
        sites.setAccessible(true); ((Deque<?>) sites.get(null)).clear();
    }

    @Test public void onlySparseCallsRequestStackCollection() {
        for (long count : new long[]{1, 2, 4, 256, 512, 4096}) assertTrue(ConnectionDiagnostics.sampleCall(count));
        for (long count : new long[]{0, -256, 3, 5, 255, 257, 4095}) assertFalse(ConnectionDiagnostics.sampleCall(count));
    }

    @Test public void latestSitesRetainOnlyBoundedCodeIdentity() {
        assertEquals("not_observed", ConnectionDiagnostics.siteSnapshot());
        StackTraceElement[] frames = {
                new StackTraceElement("java.lang.Thread", "getStackTrace", "secret-file", 1),
                new StackTraceElement(ConnectionDiagnostics.class.getName(), "begin", "secret-file", 2),
                new StackTraceElement("top.niunaijun.blackbox.fake.hook.ClassInvocationStub", "invokeTracked", "secret-file", 3),
                new StackTraceElement("$Proxy123", "bindService", "secret-file", 4),
                new StackTraceElement("android.app.ContextImpl", "bindService", "secret-file", 5),
                new StackTraceElement("com.sdk.Worker", "connect", "secret-file", 42),
                new StackTraceElement("com.sdk.Worker$$Lambda/0x12345", "run", "secret-file", -1),
                new StackTraceElement("com.sdk.Client", "start", "secret-file", 45),
                new StackTraceElement("com.sdk.Extra", "omitted", "secret-file", 46)};
        for (int i = 1; i <= 20; i++) ConnectionDiagnostics.sampleSite(ConnectionDiagnostics.Operation.bind, i * 256, frames, null);
        String text = ConnectionDiagnostics.siteSnapshot();
        assertEquals(16, text.split("operation=bind", -1).length - 1);
        assertFalse(text.contains("call=256\n"));
        assertTrue(text.contains("call=5120\n"));
        assertTrue(text.contains("com.sdk.Worker.connect:42"));
        assertFalse(text.contains("secret-file"));
        assertFalse(text.contains("0x12345"));
        assertFalse(text.contains("ContextImpl"));
        assertFalse(text.contains("com.sdk.Extra"));
        assertTrue(text.getBytes(StandardCharsets.UTF_8).length < 16 * 1024);
    }

    @Test public void missingCallersAndLongIdentitiesStayBounded() {
        ConnectionDiagnostics.sampleSite(ConnectionDiagnostics.Operation.bind, 1, new StackTraceElement[]{
                new StackTraceElement("android.app.ContextImpl", "bindService", "secret", 1)}, null);
        assertTrue(ConnectionDiagnostics.siteSnapshot().contains("caller_frames=unavailable"));
        for (int i = 0; i < 100; i++) ConnectionDiagnostics.sampleSite(ConnectionDiagnostics.Operation.receiver_add, i,
                new StackTraceElement[]{new StackTraceElement("x".repeat(5000), "m".repeat(5000), "secret", Integer.MAX_VALUE)}, null);
        assertTrue(ConnectionDiagnostics.siteSnapshot().getBytes(StandardCharsets.UTF_8).length < 16 * 1024);
    }

    @Test public void supportingSitesNeverCreateGameAttemptsOrResurrectClearedSessions() throws Exception {
        File session = temporary.newFolder();
        SessionResourceRecorder.persistConnectionSites(session, 123, "bounded sites", true);
        File target = new File(session, "support-connections-123.txt");
        assertTrue(target.isFile());
        assertFalse(new File(session, "process-123.txt").exists());
        assertFalse(new File(session, "connections-123.txt").exists());
        assertThrows(java.io.IOException.class, () -> SessionResourceRecorder.persistConnectionSites(session, 123,
                "x".repeat(16 * 1024 + 1), true));
        assertTrue(target.delete()); assertTrue(session.delete());
        assertThrows(java.io.IOException.class, () -> SessionResourceRecorder.persistConnectionSites(session, 123, "sites", true));
        assertFalse(session.exists());
    }

    @Test public void renamedHookBoundaryDoesNotDisplaceActualCallers() {
        ConnectionDiagnostics.sampleSite(ConnectionDiagnostics.Operation.bind, 1, new StackTraceElement[]{
                new StackTraceElement("renamed.a", "record", "", 1),
                new StackTraceElement("renamed.b", "invokeTracked", "", 2),
                new StackTraceElement("renamed.b", "a", "", 3),
                new StackTraceElement("java.lang.reflect.Proxy", "invoke", "", 4),
                new StackTraceElement("android.app.ContextImpl", "bindService", "", 5),
                new StackTraceElement("sdk.Client", "connect", "", 42)}, "renamed.b");
        String text = ConnectionDiagnostics.siteSnapshot();
        assertTrue(text.contains("sdk.Client.connect:42"));
        assertFalse(text.contains("renamed.a"));
        assertFalse(text.contains("renamed.b"));
    }
}
