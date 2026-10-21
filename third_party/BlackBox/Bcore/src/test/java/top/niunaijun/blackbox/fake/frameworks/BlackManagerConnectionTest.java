package top.niunaijun.blackbox.fake.frameworks;

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class BlackManagerConnectionTest {
    private static final class Manager extends BlackManager<IInterface> {
        @Override protected String getServiceName() { return "test"; }
    }

    private static final class Connection {
        final Set<IBinder.DeathRecipient> links = new HashSet<>();
        boolean failLink;
        int unlinks;
        final IBinder binder = (IBinder) Proxy.newProxyInstance(IBinder.class.getClassLoader(),
                new Class<?>[]{IBinder.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "linkToDeath":
                            if (failLink) throw new RemoteException();
                            links.add((IBinder.DeathRecipient) args[0]);
                            return null;
                        case "unlinkToDeath":
                            unlinks++;
                            return links.remove(args[0]);
                        case "pingBinder": case "isBinderAlive": return true;
                        default: throw new AssertionError(method.getName());
                    }
                });
        final IInterface service = () -> binder;
    }

    private static void cache(Manager manager, IInterface service) throws Throwable {
        Method method = BlackManager.class.getDeclaredMethod("cacheService", IInterface.class);
        method.setAccessible(true);
        try { method.invoke(manager, service); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    @Test public void repeatedReconnectsAndExplicitClearsDoNotAccumulateDeathLinks() throws Throwable {
        Manager manager = new Manager();
        Connection connection = new Connection();
        for (int i = 0; i < 1000; i++) {
            cache(manager, connection.service);
            assertEquals(1, connection.links.size());
            assertSame(connection.service, manager.getService());
        }
        manager.clearServiceCache();
        manager.clearServiceCache();
        assertTrue(connection.links.isEmpty());
        assertEquals(1000, connection.unlinks);
        assertFalse(manager.isServiceHealthy());
    }

    @Test public void delayedDeathFromPreviousConnectionCannotEraseReplacement() throws Throwable {
        Manager manager = new Manager();
        Connection first = new Connection(), second = new Connection();
        cache(manager, first.service);
        IBinder.DeathRecipient oldCallback = first.links.iterator().next();
        cache(manager, second.service);
        assertTrue(first.links.isEmpty());
        oldCallback.binderDied();
        assertSame(second.service, manager.getService());
        second.links.iterator().next().binderDied();
        assertTrue(second.links.isEmpty());
        assertFalse(manager.isServiceHealthy());
    }

    @Test public void failedDeathRegistrationDoesNotLeaveAUsableCachedConnection() throws Throwable {
        Manager manager = new Manager();
        Connection connection = new Connection();
        connection.failLink = true;
        try {
            cache(manager, connection.service);
            fail("Broken connection must not be cached");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getCause() instanceof RemoteException);
        }
        assertTrue(connection.links.isEmpty());
        assertFalse(manager.isServiceHealthy());
    }

    @Test public void serviceBindDoesNotReplayInvocationFailures() throws Exception {
        Path path = Paths.get("src/main/java/top/niunaijun/blackbox/fake/service/IActivityManagerProxy.java");
        if (!Files.isRegularFile(path)) path = Paths.get("third_party/BlackBox/Bcore").resolve(path);
        String source = new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
        String bind = source.substring(source.indexOf("public static Object BindServiceCommon"),
                source.indexOf("private static boolean isGoogleServiceIntent"));
        String rejection = bind.substring(bind.indexOf("catch (InvocationTargetException failure)"),
                bind.lastIndexOf("catch (Exception e)"));
        assertTrue(rejection.contains("throw failure.getCause()"));
        assertFalse(rejection.contains("method.invoke"));
    }
}
