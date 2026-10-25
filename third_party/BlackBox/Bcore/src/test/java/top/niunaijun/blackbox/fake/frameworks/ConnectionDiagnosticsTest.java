package top.niunaijun.blackbox.fake.frameworks;

import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.utils.ConnectionDiagnostics;
import top.niunaijun.blackbox.utils.SessionResourceRecorder;
import static org.junit.Assert.*;

public class ConnectionDiagnosticsTest {
    @Before public void resetTestCounters() throws Exception {
        for (String name : new String[]{"calls", "failures"}) {
            Field field = ConnectionDiagnostics.class.getDeclaredField(name);
            field.setAccessible(true);
            Arrays.fill((long[]) field.get(null), 0);
        }
        Field observed = ConnectionDiagnostics.class.getDeclaredField("observed");
        observed.setAccessible(true); observed.setBoolean(null, false);
        Field last = ConnectionDiagnostics.class.getDeclaredField("lastFailure");
        last.setAccessible(true); last.set(null, "none");
        Field sites = ConnectionDiagnostics.class.getDeclaredField("sites");
        sites.setAccessible(true); ((java.util.Deque<?>) sites.get(null)).clear();
    }

    @Test public void observerBaselinesAreIndependentAndDoNotResetLifetimeCounters() {
        ConnectionDiagnostics.begin(ConnectionDiagnostics.Operation.bind);
        long[] first = ConnectionDiagnostics.checkpoint();
        ConnectionDiagnostics.begin(ConnectionDiagnostics.Operation.bind);
        ConnectionDiagnostics.failed(ConnectionDiagnostics.Operation.bind, null);
        long[] second = ConnectionDiagnostics.checkpoint();
        ConnectionDiagnostics.begin(ConnectionDiagnostics.Operation.receiver_add);
        assertEquals("bind:1/1,receiver_add:1/0", ConnectionDiagnostics.since(first));
        assertEquals("receiver_add:1/0", ConnectionDiagnostics.since(second));
        assertTrue(ConnectionDiagnostics.snapshot().contains("bind:2/1"));
        assertEquals("none_observed", ConnectionDiagnostics.since(ConnectionDiagnostics.checkpoint()));
        ConnectionDiagnostics.trackedReceiverCount(7);
        assertEquals(7, ConnectionDiagnostics.trackedReceiverCount());
        ConnectionDiagnostics.trackedReceiverCount(0);
        SessionResourceRecorder recorder = new SessionResourceRecorder();
        ConnectionDiagnostics.begin(ConnectionDiagnostics.Operation.unbind);
        String text = recorder.record(1, 2, 3, 4, 5, 6, 0);
        assertTrue(text.contains("bbWindowConnectionCalls=unbind:1/0"));
        assertTrue(text.contains("bbTrackedReceivers=0"));
    }

    public interface Service {
        int bindService(String privateText);
        boolean unbindService();
        Object registerReceiver();
        String getUntrackedValue();
    }
    public static class FakeService implements Service {
        public int bindService(String privateText) { return 0; }
        public boolean unbindService() { return true; }
        public Object registerReceiver() { throw new SecurityException("private-account-token"); }
        public String getUntrackedValue() { return "private-result"; }
    }
    private static class Hook extends ClassInvocationStub {
        Service service;
        protected Object getWho() { return new FakeService(); }
        protected void inject(Object base, Object proxy) { service = (Service) proxy; }
        public boolean isBadEnv() { return false; }
        // Real service proxies often override invoke; the outer boundary must cover those too.
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return super.invoke(proxy, method, args);
        }
    }

    @Test public void actualProxyCountsRejectionsAndPreservesOriginalBehaviorWithoutPrivateData() {
        assertEquals("not_observed", ConnectionDiagnostics.snapshot());
        Hook hook = new Hook(); hook.injectHook();
        assertEquals("private-result", hook.service.getUntrackedValue());
        assertEquals("not_observed", ConnectionDiagnostics.snapshot());
        assertEquals(0, hook.service.bindService("https://private.example/account-token"));
        assertTrue(hook.service.unbindService());
        try { hook.service.registerReceiver(); fail("Permission failure must propagate"); }
        catch (SecurityException expected) { assertEquals("private-account-token", expected.getMessage()); }
        String snapshot = ConnectionDiagnostics.snapshot();
        assertTrue(snapshot.contains("bind:1/1,unbind:1/0,receiver_add:1/1"));
        assertTrue(snapshot.endsWith("lastFailure=receiver_add:permission"));
        assertFalse(snapshot.contains("private"));
        assertFalse(snapshot.contains("example"));
        assertTrue(ConnectionDiagnostics.siteSnapshot().contains("operation=bind call=1"));
        assertFalse(ConnectionDiagnostics.siteSnapshot().contains("https://private.example"));
        assertFalse(ConnectionDiagnostics.siteSnapshot().contains("private-account-token"));
    }

    @Test public void largeCallVolumesRemainBoundedAndThreadSafe() throws Exception {
        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int call = 0; call < 10000; call++)
                    ConnectionDiagnostics.begin(ConnectionDiagnostics.Operation.network_add);
            });
            workers[i].start();
        }
        for (Thread worker : workers) worker.join();
        assertTrue(ConnectionDiagnostics.snapshot().contains("network_add:40000/0"));
        assertTrue(ConnectionDiagnostics.snapshot().length() < 2048);
        SessionResourceRecorder recorder = new SessionResourceRecorder();
        String resources = "";
        for (int i = 0; i < 1000; i++) resources = recorder.record(i, i, i, 1, 1, 1, 0);
        assertEquals(24, resources.lines().filter(line -> line.contains("bbConnectionCalls=")).count());
        assertTrue(resources.contains("network_add:40000/0"));
        assertTrue((resources + ConnectionDiagnostics.snapshot()).length() < 64 * 1024);
    }

    @Test public void operationNamesAreFixedAndArgumentsCannotCreateNewBuckets() {
        assertEquals(ConnectionDiagnostics.Operation.bind, ConnectionDiagnostics.operation("bindServiceInstance"));
        assertEquals(ConnectionDiagnostics.Operation.network_remove, ConnectionDiagnostics.operation("releaseNetworkRequest"));
        assertEquals(ConnectionDiagnostics.Operation.audio_add, ConnectionDiagnostics.operation("trackPlayer"));
        assertEquals(ConnectionDiagnostics.Operation.audio_remove, ConnectionDiagnostics.operation("releasePlayer"));
        assertEquals(ConnectionDiagnostics.Operation.audio_add, ConnectionDiagnostics.operation("registerRecordingCallback"));
        assertEquals(ConnectionDiagnostics.Operation.audio_remove, ConnectionDiagnostics.operation("unregisterPlaybackCallback"));
        assertNull(ConnectionDiagnostics.operation("private-account-token"));
        assertNull(ConnectionDiagnostics.operation("set"));
        assertNull(ConnectionDiagnostics.operation("remove", "unrelated.Service"));
        assertEquals(ConnectionDiagnostics.Operation.alarm_add, ConnectionDiagnostics.operation("set", "android.app.IAlarmManager"));
        assertEquals(ConnectionDiagnostics.Operation.alarm_remove, ConnectionDiagnostics.operation("remove", "android.app.IAlarmManager"));
        assertEquals(ConnectionDiagnostics.Operation.window_remove, ConnectionDiagnostics.operation("remove", "android.view.IWindowSession"));
        assertEquals(ConnectionDiagnostics.Operation.toast_add, ConnectionDiagnostics.operation("enqueueTextToast"));
        ConnectionDiagnostics.begin(null);
        ConnectionDiagnostics.failed(null, new RuntimeException("secret"));
        assertEquals("not_observed", ConnectionDiagnostics.snapshot());
    }
}
