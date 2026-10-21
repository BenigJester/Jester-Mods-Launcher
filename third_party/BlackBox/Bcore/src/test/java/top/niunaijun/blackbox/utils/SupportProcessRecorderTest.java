package top.niunaijun.blackbox.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import static org.junit.Assert.*;

public class SupportProcessRecorderTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void onlyExistingValidatedSessionsAreAccepted() throws Exception {
        File root = temporary.newFolder("sessions");
        File session = new File(root, "com.example.game/1234-abcd");
        assertTrue(session.mkdirs());
        assertNull(SupportProcessRecorder.resolveSession(root, "com.example.game", session.getName()));
        assertTrue(new File(session, "metadata.txt").createNewFile());
        assertEquals(session.getCanonicalFile(), SupportProcessRecorder.resolveSession(root, "com.example.game", session.getName()));
        assertNull(SupportProcessRecorder.resolveSession(root, "../com.example.game", session.getName()));
        assertNull(SupportProcessRecorder.resolveSession(root, "com.example.game", "../1234-abcd"));
        assertNull(SupportProcessRecorder.resolveSession(root, "com.example.other", session.getName()));
        assertNull(SupportProcessRecorder.resolveSession(root, null, session.getName()));
    }

    @Test public void supportingSamplesDoNotCreateAttemptsOrUndoClear() throws Exception {
        File session = temporary.newFolder("session");
        SessionResourceRecorder recorder = new SessionResourceRecorder();
        recorder.startSupporting(session, "shared_helper", () -> false);
        String text = recorder.record(1, 2, 3, 4, 5, 6, 0);
        assertTrue(text.contains("processRole=shared_helper"));
        assertTrue(text.contains("activityResumed=unavailable"));
        SessionResourceRecorder.persist(session, 123, text, true);
        assertTrue(new File(session, "support-resources-123.txt").isFile());
        assertFalse(new File(session, "process-123.txt").exists());
        assertFalse(new File(session, "resources-123.txt").exists());
        assertTrue(new File(session, "support-resources-123.txt").delete());
        assertTrue(session.delete());
        try { SessionResourceRecorder.persist(session, 123, text, true); fail("Cleared session recreated"); }
        catch (java.io.IOException expected) { }
        assertFalse(session.exists());
        try { recorder.startSupporting(session, "private-process-name", () -> false); fail("Unvalidated role"); }
        catch (IllegalArgumentException expected) { }
    }
}
