package com.moodtools.diagnostics;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;

public class DiagnosticRuntimeTest {
    @org.junit.Test public void exitHintsNeverExportSystemDescriptions() {
        org.junit.Assert.assertEquals("binder_limit_reported", DiagnosticRuntime.exitHint("Too many Binders sent to SYSTEM secret@example.com"));
        org.junit.Assert.assertEquals("binder_limit_reported", DiagnosticRuntime.exitHint("EXCESSIVE BINDER OBJECTS /private/path"));
        org.junit.Assert.assertEquals("binder_limit_reported", DiagnosticRuntime.exitHint("Binder proxy limit"));
        org.junit.Assert.assertEquals("excessive_cpu_reported", DiagnosticRuntime.exitHint("Excessive background CPU token=secret"));
        org.junit.Assert.assertEquals("unclassified_or_unavailable", DiagnosticRuntime.exitHint(null));
        org.junit.Assert.assertEquals("unclassified_or_unavailable", DiagnosticRuntime.exitHint("token=secret"));
    }
    @Test public void framesNeverIncludeExceptionMessagesOrFilePaths() {
        Throwable error = new IllegalStateException("private-account-token", new ClassNotFoundException("secret-url"));
        error.setStackTrace(new StackTraceElement[] {new StackTraceElement("game.Loader", "start", "/private/path", 42)});
        String text = DiagnosticRuntime.frames(error);
        assertTrue(text.contains("game.Loader.start:42"));
        assertTrue(text.contains("ClassNotFoundException"));
        assertFalse(text.contains("private-account-token")); assertFalse(text.contains("secret-url")); assertFalse(text.contains("/private/path"));
        Throwable cyclic = new RuntimeException(); cyclic.initCause(new RuntimeException(cyclic));
        assertTrue(DiagnosticRuntime.frames(cyclic).length() <= 16000);
    }
    @Test public void boundedReadsRejectOversizedStreams() throws Exception {
        assertEquals(3, DiagnosticRuntime.readBounded(new ByteArrayInputStream(new byte[3]), 3).length);
        assertThrows(java.io.IOException.class, () -> DiagnosticRuntime.readBounded(new ByteArrayInputStream(new byte[4]), 3));
    }
}
