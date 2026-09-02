package com.moodtools.identity;

import android.app.Activity;
import android.content.ContentResolver;
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

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;

/** Direct launcher entry for a generated exact-package shell. */
public final class IdentityShellActivity extends Activity {
    private static final String TAG = "IdentityShell";
    private static final String PAYLOAD_AUTHORITY =
            "com.moodtools.hub.nonroot.identity-payload";
    private static final String METHOD_GAME_IMPORT_SUCCEEDED =
            "identity_game_import_succeeded";
    private static final int USER_ID = 0;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        showLoadingView();
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
            core.ensureBlackProcessInitialized();
            if (!core.isInstalled(targetPackage, USER_ID)) {
                updateStatus("Importing original game…");
                Uri gamePayload = payloadUri(targetPackage, "game.apks");
                top.niunaijun.blackbox.entity.pm.InstallResult result =
                        core.installPackageAsUser(gamePayload, USER_ID);
                if (!result.success) {
                    throw new IllegalStateException(result.msg == null
                            ? "The original game payload could not be imported"
                            : result.msg);
                }
                if (!core.isInstalled(targetPackage, USER_ID)) {
                    throw new IllegalStateException("The original game import could not be verified");
                }
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
                    Log.w(TAG, "No staged module payload; launching the original game only");
                    return;
                }
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
