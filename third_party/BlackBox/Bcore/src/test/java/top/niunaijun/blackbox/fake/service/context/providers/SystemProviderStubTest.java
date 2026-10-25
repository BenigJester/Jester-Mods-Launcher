package top.niunaijun.blackbox.fake.service.context.providers;

import org.junit.Test;
import static org.junit.Assert.*;

public class SystemProviderStubTest {
    public static class State {
        public int uid, pid, deviceId;
        public String packageName, attributionTag;
        public Object token;
        public String[] renouncedPermissions;
        public State[] next;
    }

    // API 31-shaped test double: deliberately has no Builder copy constructor.
    public static class Attribution {
        private final State mAttributionSourceState;
        public Attribution(State state) { mAttributionSourceState = state; }
    }

    @Test public void copiedChainCanBeRewrittenWithoutChangingTheGuest() throws Exception {
        State state = new State();
        state.uid = 123;
        state.pid = 789;
        state.deviceId = 42;
        state.packageName = "guest";
        state.attributionTag = "tag";
        state.token = new Object();
        state.renouncedPermissions = new String[]{"permission"};
        state.next = new State[]{new State(), null};
        state.next[0].uid = 456;
        state.next[0].next = new State[0];
        Attribution source = new Attribution(state);
        Attribution copy = (Attribution) SystemProviderStub.copyAttributionChain(source);
        State copied = copy.mAttributionSourceState;
        assertNotSame(source, copy);
        assertNotSame(state, copied);
        assertNotSame(state.next, copied.next);
        assertNotSame(state.next[0], copied.next[0]);
        assertSame(state.token, copied.token);
        assertArrayEquals(state.renouncedPermissions, copied.renouncedPermissions);
        assertEquals(state.uid, copied.uid);
        assertEquals(state.pid, copied.pid);
        assertEquals(state.deviceId, copied.deviceId);
        assertEquals(state.attributionTag, copied.attributionTag);
        assertEquals(state.packageName, copied.packageName);
        assertNull(copied.next[1]);
        assertEquals(0, copied.next[0].next.length);
        copied.uid = 999;
        copied.packageName = "host";
        copied.next[0].uid = 999;
        assertEquals(123, state.uid);
        assertEquals("guest", state.packageName);
        assertEquals(456, state.next[0].uid);
    }

    public static class Provider {
        final SecurityException failure = new SecurityException("caller mismatch");
        public String read() { return "value"; }
        public String denied() { throw failure; }
    }

    @Test public void providerResultAndOriginalFailureArePreserved() throws Throwable {
        Provider provider = new Provider();
        assertEquals("value", SystemProviderStub.invokeProvider(provider,
                Provider.class.getMethod("read"), null));
        try {
            SystemProviderStub.invokeProvider(provider, Provider.class.getMethod("denied"), null);
            fail("Expected the original provider failure");
        } catch (SecurityException failure) {
            assertSame(provider.failure, failure);
        }
    }
}
