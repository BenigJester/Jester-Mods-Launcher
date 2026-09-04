package com.moodtools.identity;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.entity.pm.InstallResult;

/** Direct launcher entry for a generated exact-package shell. */
public final class IdentityShellActivity extends Activity {
    private static final String TAG = "IdentityShell";
    private static final String PAYLOAD_AUTHORITY =
            "com.moodtools.hub.nonroot.identity-payload";
    private static final String METHOD_GAME_IMPORT_SUCCEEDED =
            "identity_game_import_succeeded";
    private static final int USER_ID = 0;
    private static final long SERVICE_READY_TIMEOUT_MS = 12000L;
    private static final AtomicBoolean SETUP_IN_PROGRESS = new AtomicBoolean(false);
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        showLoadingView();
        if (!SETUP_IN_PROGRESS.compareAndSet(false, true)) {
            updateStatus("Game setup is already running…");
            return;
        }
        boolean fullModuleAuthorized = IdentityLaunchGuard.authorize(this, getIntent());
        Thread worker = new Thread(
                () -> prepareAndLaunch(fullModuleAuthorized), "identity-shell-launch");
        worker.setDaemon(true);
        worker.start();
    }

    private void showLoadingView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(48, 48, 48, 48);
        layout.setBackgroundColor(Color.BLACK);
        ProgressBar progress = new ProgressBar(this);
        status = new TextView(this);
        status.setText("Preparing game…");
        status.setTextColor(Color.WHITE);
        status.setTextSize(16f);
        status.setGravity(Gravity.CENTER);
        layout.addView(progress);
        layout.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(layout);
    }

    private void prepareAndLaunch(boolean fullModuleAuthorized) {
        String targetPackage = getPackageName();
        try {
            BlackBoxCore core = BlackBoxCore.get();
            updateStatus("Starting compatibility service…");
            core.ensureBlackProcessReady(SERVICE_READY_TIMEOUT_MS);
            if (!core.isInstalled(targetPackage, USER_ID)) {
                updateStatus("Importing original game…");
                importOriginalGame(core, targetPackage);
            }
            releaseImportedGameBackup(targetPackage);

            updateStatus("Preparing add-on…");
            core.stopPackage(targetPackage, USER_ID);
            refreshModuleIfAvailable(targetPackage);
            IdentityLaunchGuard.persistMode(this, fullModuleAuthorized);
            if (!fullModuleAuthorized) {
                Log.i(TAG, "External launch: identity compatibility only");
            }
            updateStatus("Opening game…");
            if (!core.launchApk(targetPackage, USER_ID)) {
                throw new IllegalStateException("The game has no launchable activity");
            }
            runOnUiThread(this::finish);
        } catch (Throwable error) {
            Log.e(TAG, "Identity-shell launch failed", error);
            showFailure(error.getMessage());
        } finally {
            SETUP_IN_PROGRESS.set(false);
        }
    }

    /**
     * Stage the cross-package provider stream in this shell before asking the BlackBox service
     * to parse it. Some OEM Binder implementations interrupt a long provider stream when it is
     * opened from the remote service process. A shell-owned file keeps that transfer local and
     * also gives us a safe source for one service-recovery retry.
     */
    private void importOriginalGame(BlackBoxCore core, String targetPackage) throws Exception {
        File stagedPayload = new File(getCacheDir(), "identity-game-import.apks");
        if (stagedPayload.exists() && !stagedPayload.delete()) {
            throw new IllegalStateException("Could not replace the staged original game");
        }
        try {
            try (InputStream input = getContentResolver().openInputStream(
                    payloadUri(targetPackage, "game.apks"))) {
                if (input == null) {
                    throw new IllegalStateException("The preserved original game is unavailable");
                }
                copyToFile(input, stagedPayload);
            }

            InstallResult lastResult = null;
            final int expectedSplitCount = countPayloadSplits(stagedPayload);
            for (int attempt = 0; attempt < 2; attempt++) {
                core.ensureBlackProcessReady(SERVICE_READY_TIMEOUT_MS);
                lastResult = core.installPackageAsUser(stagedPayload, USER_ID);
                boolean recoverable = isRecoverableInstallFailure(lastResult.msg);
                if ((lastResult.success || recoverable)
                        && waitForImportedGame(core, targetPackage, expectedSplitCount)) return;
                if (attempt == 0 && (lastResult.success || recoverable)) {
                    updateStatus("Recovering game setup…");
                    Thread.sleep(750L);
                    continue;
                }
                break;
            }

            String detail = lastResult == null ? null : lastResult.msg;
            if (lastResult != null && lastResult.success) {
                detail = "The original game's installed split set could not be preserved";
            }
            throw new IllegalStateException(detail == null || detail.trim().isEmpty()
                    ? "The original game payload could not be imported"
                    : detail.trim());
        } finally {
            if (stagedPayload.exists() && !stagedPayload.delete()) {
                Log.w(TAG, "Could not clear staged original-game import");
            }
        }
    }

    private boolean waitForImportedGame(BlackBoxCore core, String targetPackage,
                                        int expectedSplitCount)
            throws InterruptedException {
        for (int check = 0; check < 8; check++) {
            if (core.isInstalled(targetPackage, USER_ID)) {
                ApplicationInfo info = core.getBPackageManager()
                        .getApplicationInfo(targetPackage, 0, USER_ID);
                int importedSplitCount = info == null || info.splitSourceDirs == null
                        ? 0 : info.splitSourceDirs.length;
                if (importedSplitCount == expectedSplitCount) return true;
                Log.w(TAG, "Imported split-set incomplete: expected=" + expectedSplitCount
                        + " actual=" + importedSplitCount);
            }
            Thread.sleep(250L);
        }
        return false;
    }

    private int countPayloadSplits(File payload) throws Exception {
        int apkCount = 0;
        try (ZipFile zip = new ZipFile(payload)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().toLowerCase(java.util.Locale.US)
                        .endsWith(".apk")) {
                    apkCount++;
                }
            }
        }
        if (apkCount == 0) {
            throw new IllegalStateException("The preserved original game contains no APK files");
        }
        return apkCount - 1;
    }

    private boolean isRecoverableInstallFailure(String detail) {
        if (detail == null) return false;
        String normalized = detail.toLowerCase(java.util.Locale.US);
        return normalized.contains("remote exception")
                || normalized.contains("service unavailable")
                || normalized.contains("binder");
    }

    private void copyToFile(InputStream input, File target) throws Exception {
        try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            output.getFD().sync();
        }
        if (target.length() <= 0L) {
            throw new IllegalStateException("The preserved original game is empty");
        }
    }

    private void releaseImportedGameBackup(String targetPackage) {
        try {
            Bundle result = getContentResolver().call(
                    new Uri.Builder()
                            .scheme(ContentResolver.SCHEME_CONTENT)
                            .authority(PAYLOAD_AUTHORITY)
                            .build(),
                    METHOD_GAME_IMPORT_SUCCEEDED,
                    targetPackage,
                    null);
            long reclaimed = result == null ? 0L : result.getLong("reclaimed_bytes", 0L);
            if (reclaimed > 0L) {
                Log.i(TAG, "Released imported game backup (" + reclaimed + " bytes)");
            }
        } catch (Throwable cleanupError) {
            // Import is complete. Keep playing and retry storage reclamation on the next launch.
            Log.w(TAG, "Could not release the imported game backup", cleanupError);
        }
    }

    private void refreshModuleIfAvailable(String targetPackage) throws Exception {
        File directory = BEnvironment.getDataFilesDir(targetPackage, USER_ID);
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Could not create the add-on directory");
        }
        String[] files = {"classes.dex", "libmenu_native.so", "config.json"};
        boolean copiedAny = false;
        for (String name : files) {
            try (InputStream input = getContentResolver().openInputStream(
                    payloadUri(targetPackage, name))) {
                if (input == null) continue;
                copyAtomically(input, new File(directory, name), "classes.dex".equals(name));
                copiedAny = true;
            } catch (java.io.FileNotFoundException unavailable) {
                // Jester Mods can be absent after first setup; keep the last verified payload.
            }
        }
        if (!copiedAny) {
            for (String name : files) {
                if (!new File(directory, name).isFile()) {
                    throw new IllegalStateException(
                            "The add-on payload is incomplete. Open Jester Mods and update or reinstall this add-on");
                }
            }
        }
        for (String name : files) {
            File payload = new File(directory, name);
            if (!payload.isFile() || payload.length() <= 0L) {
                throw new IllegalStateException(
                        "The add-on payload is incomplete. Open Jester Mods and update or reinstall this add-on");
            }
        }
    }

    private Uri payloadUri(String packageName, String fileName) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(PAYLOAD_AUTHORITY)
                .appendPath("payload")
                .appendPath(packageName)
                .appendPath(fileName)
                .build();
    }

    private void copyAtomically(InputStream input, File target, boolean readOnly) throws Exception {
        File incoming = new File(target.getParentFile(), target.getName() + ".incoming");
        if (incoming.exists() && !incoming.delete()) {
            throw new IllegalStateException("Could not replace " + target.getName());
        }
        try (FileOutputStream output = new FileOutputStream(incoming)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            output.getFD().sync();
        }
        if (incoming.length() <= 0) throw new IllegalStateException(target.getName() + " is empty");
        if (readOnly && !incoming.setReadOnly()) {
            throw new IllegalStateException("Could not protect " + target.getName());
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Could not replace " + target.getName());
        }
        if (!incoming.renameTo(target)) {
            throw new IllegalStateException("Could not finalize " + target.getName());
        }
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> status.setText(message));
    }

    private void showFailure(String detail) {
        String message = detail == null || detail.trim().isEmpty()
                ? "Open Jester Mods and prepare this game again."
                : detail.trim();
        runOnUiThread(() -> status.setText(
                "Setup required\n\n" + message + "\n\nOpen Jester Mods to repair this game."));
    }
}
