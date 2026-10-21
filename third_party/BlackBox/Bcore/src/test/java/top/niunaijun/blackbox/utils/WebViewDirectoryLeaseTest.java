package top.niunaijun.blackbox.utils;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class WebViewDirectoryLeaseTest {
    @Test public void ownershipIsExclusiveAcrossRealOperatingSystemProcesses() throws Exception {
        File root = Files.createTempDirectory("webview-process-").toFile();
        String primary = "0:a.game:a.game";
        File javaExecutable = new File(System.getProperty("java.home"), "bin/java.exe");
        if (!javaExecutable.isFile()) javaExecutable = new File(System.getProperty("java.home"), "bin/java");
        String classpath = new File(WebViewDirectoryLease.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + File.pathSeparator + new File(OwnerProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process child = new ProcessBuilder(javaExecutable.getAbsolutePath(), "-cp", classpath,
                OwnerProcess.class.getName(), root.getAbsolutePath(), primary).redirectErrorStream(true).start();
        try {
            assertEquals("ready", new java.io.BufferedReader(new java.io.InputStreamReader(
                    child.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)).readLine());
            try (WebViewDirectoryLease overlap = WebViewDirectoryLease.acquire(root, primary)) {
                assertTrue(overlap.alternate);
                assertNotEquals(primary, overlap.suffix);
            }
            child.getOutputStream().close();
            assertTrue(child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, child.exitValue());
            try (WebViewDirectoryLease nextLaunch = WebViewDirectoryLease.acquire(root, primary)) {
                assertEquals(primary, nextLaunch.suffix);
            }
        } finally {
            child.destroyForcibly();
            child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            for (File file : root.listFiles()) assertTrue(file.delete());
            assertTrue(root.delete());
        }
    }

    public static class OwnerProcess {
        public static void main(String[] args) throws Exception {
            try (WebViewDirectoryLease lease = WebViewDirectoryLease.acquire(new File(args[0]), args[1])) {
                System.out.println("ready");
                System.out.flush();
                System.in.read();
            }
        }
    }

    @Test public void overlappingOwnersUseBoundedAlternatesAndReuseOriginalAfterExit() throws Exception {
        File root = Files.createTempDirectory("webview-owners-").toFile();
        List<WebViewDirectoryLease> leases = new ArrayList<>();
        String primary = "0:com.king.candycrushsaga:com.king.candycrushsaga";
        try {
            for (int i = 0; i < 4; i++) {
                WebViewDirectoryLease lease = WebViewDirectoryLease.acquire(root, primary);
                assertEquals(i == 0 ? primary : "bb-alternate-" + i + ":" + primary, lease.suffix);
                assertEquals(i != 0, lease.alternate);
                leases.add(lease);
            }
            assertThrows(java.io.IOException.class, () -> WebViewDirectoryLease.acquire(root, primary));
            assertEquals(4, root.list().length); // No PID/time-dependent unbounded profile creation.
            leases.remove(0).close();
            try (WebViewDirectoryLease reused = WebViewDirectoryLease.acquire(root, primary)) {
                assertEquals(primary, reused.suffix);
                assertFalse(reused.alternate);
            }
        } finally {
            for (WebViewDirectoryLease lease : leases) lease.close();
            for (File file : root.listFiles()) assertTrue(file.delete());
            assertTrue(root.delete());
        }
    }

    @Test public void gamesUsersAndProcessesDoNotShareOwnership() throws Exception {
        File root = Files.createTempDirectory("webview-identities-").toFile();
        List<WebViewDirectoryLease> leases = new ArrayList<>();
        try {
            for (String suffix : new String[]{"0:a.game:a.game", "1:a.game:a.game",
                    "0:a.game:a.game:worker", "0:b.game:b.game"}) {
                WebViewDirectoryLease lease = WebViewDirectoryLease.acquire(root, suffix);
                leases.add(lease);
                assertEquals(suffix, lease.suffix);
                assertFalse(lease.alternate);
            }
            assertThrows(java.io.IOException.class, () -> WebViewDirectoryLease.acquire(root, "../escape"));
            assertThrows(java.io.IOException.class, () -> WebViewDirectoryLease.acquire(root, ""));
        } finally {
            for (WebViewDirectoryLease lease : leases) lease.close();
            for (File file : root.listFiles()) assertTrue(file.delete());
            assertTrue(root.delete());
        }
    }
}
