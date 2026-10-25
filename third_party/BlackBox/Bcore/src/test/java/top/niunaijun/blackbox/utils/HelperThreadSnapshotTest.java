package top.niunaijun.blackbox.utils;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class HelperThreadSnapshotTest {
    @Test public void snapshotIsBoundedPrivateAndCapturedOnlyOncePerWindow() throws Exception {
        HelperThreadSnapshot gate = new HelperThreadSnapshot();
        assertFalse(gate.shouldCapture(-1));
        assertFalse(gate.shouldCapture(29999));
        assertTrue(gate.shouldCapture(30000));
        assertFalse(gate.shouldCapture(60000));
        assertTrue(new HelperThreadSnapshot().shouldCapture(30000));
        StackTraceElement[] frames = new StackTraceElement[100];
        java.util.Arrays.fill(frames, new StackTraceElement("SomeClass", "method", "private-account-file", 42));
        String text = HelperThreadSnapshot.format(1234, 30000, Thread.State.WAITING, frames);
        assertTrue(text.contains("capturedAt=1234 mainPendingMs=30000 threadRole=main state=WAITING"));
        assertEquals(32, text.split("  at ").length - 1);
        assertFalse(text.contains("private-account-file"));
        assertTrue(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 16384);
        File session = Files.createTempDirectory("helper-stack-").toFile();
        File target = new File(session, "support-stacks-123.txt");
        SessionResourceRecorder.persistHelperSnapshot(session, 123, text);
        assertEquals(text, new String(Files.readAllBytes(target.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(java.io.IOException.class, () -> SessionResourceRecorder.persistHelperSnapshot(session, 123, "x".repeat(16385)));
        assertTrue(target.delete());
        assertTrue(session.delete());
        assertThrows(java.io.IOException.class, () -> SessionResourceRecorder.persistHelperSnapshot(session, 123, text));
        assertFalse(session.exists());
    }
}
