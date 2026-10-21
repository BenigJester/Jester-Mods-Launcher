package top.niunaijun.blackbox.fake.service;

import org.junit.Test;
import android.os.IBinder;
import android.os.IInterface;
import com.samsung.android.content.clipboard.SemClipboardManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class SemClipboardCompatibilityTest {
    public interface Clipboard extends IInterface {
        Object getClipData();
        void setClipData();
    }

    public static class FakeClipboard implements Clipboard {
        Object result = new Object();
        RuntimeException failure;
        @Override public IBinder asBinder() { return null; }
        @Override public Object getClipData() {
            if (failure != null) throw failure;
            return result;
        }
        @Override public void setClipData() { throw new SecurityException("denied"); }
    }

    @Test public void focusTimeReadUsesHookedCacheAndCacheReplacementIsDetected() throws Throwable {
        FakeClipboard service = new FakeClipboard();
        service.failure = new SecurityException("denied");
        SemClipboardManager.reset();
        SemClipboardManager.nextService = service;
        try {
            ISemClipboardManagerProxy hook = new ISemClipboardManagerProxy();
            hook.injectHook();
            assertNotSame(service, SemClipboardManager.cachedService());
            assertNull(((Clipboard) SemClipboardManager.cachedService()).getClipData());
            assertFalse(hook.isBadEnv());
            SemClipboardManager.reset();
            assertTrue(hook.isBadEnv());
        } finally {
            SemClipboardManager.reset();
        }
    }

    @Test public void readsPreserveResultsAndOnlyPermissionDenialsBecomeEmpty() throws Throwable {
        FakeClipboard service = new FakeClipboard();
        assertSame(service.result, ISemClipboardManagerProxy.invokeClipboard(service,
                Clipboard.class.getMethod("getClipData"), null));
        service.failure = new SecurityException("denied");
        assertNull(ISemClipboardManagerProxy.invokeClipboard(service,
                Clipboard.class.getMethod("getClipData"), null));
        service.failure = new IllegalStateException("unrelated failure");
        try {
            ISemClipboardManagerProxy.invokeClipboard(service, Clipboard.class.getMethod("getClipData"), null);
            fail("Unrelated errors must propagate");
        } catch (IllegalStateException expected) {
            assertSame(service.failure, expected);
        }
        try {
            ISemClipboardManagerProxy.invokeClipboard(service, Clipboard.class.getMethod("setClipData"), null);
            fail("Writes must not report success after permission denial");
        } catch (SecurityException expected) {
            // Only the OEM focus-time read gets an empty result.
        }
    }

    @Test public void optionalOemCacheHookIsRegisteredAlongsideStandardClipboard() throws Exception {
        String base = "third_party/BlackBox/Bcore/src/main/java/top/niunaijun/blackbox/fake/";
        String source = source(base + "service/ISemClipboardManagerProxy.java");
        assertTrue(source.contains("\"getService\", \"getSemService\""));
        assertTrue(source.contains("\"sService\", \"mSemService\""));
        assertTrue(source.contains("catch (ClassNotFoundException ignored)"));
        assertTrue(source.contains("serviceField.set(null, proxyInvocation)"));
        assertTrue(source.contains("serviceField.get(null) != getProxyInvocation()"));
        assertTrue(source.contains("MethodParameterUtils.replaceAllAppPkg(args)"));
        assertTrue(source.contains("MethodParameterUtils.replaceFirstUid(args)"));
        String hooks = source(base + "hook/HookManager.java");
        assertTrue(hooks.contains("addInjector(new IClipboardManagerProxy())"));
        assertTrue(hooks.contains("addInjector(new ISemClipboardManagerProxy())"));
    }

    private static String source(String path) throws Exception {
        Path file = Paths.get(path);
        if (!Files.isRegularFile(file)) file = Paths.get("..", path);
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
