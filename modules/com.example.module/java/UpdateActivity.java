package com.android.support;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

// Keep updater copy self-contained: Full Repack copies its smali into games whose resource IDs
// differ from this template, so these bootstrap strings intentionally do not reference R.string.
@SuppressLint("SetTextI18n")
public final class UpdateActivity extends Activity {
    private static final int BOOTSTRAP_VERSION = 4;
    private static final long MINIMUM_SCREEN_TIME_MS = 650L;
    private static final long MAX_NATIVE_PAYLOAD_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_DEX_PAYLOAD_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_CHANGELOG_CHARS = 8000;
    private static final int MAX_CHANGELOG_HISTORY_ENTRIES = 50;
    private static final int MAX_CHANGELOG_HISTORY_CHARS = 128 * 1024;
    private static final int MAX_RELEASE_GRANT_CHARS = 4096;
    private static final int DOWNLOAD_BUFFER_BYTES = 256 * 1024;
    private static final long DOWNLOAD_PROGRESS_BYTES = 512L * 1024L;
    private static final long DOWNLOAD_PROGRESS_MS = 200L;
    private static final long PARALLEL_DOWNLOAD_MIN_BYTES = 8L * 1024L * 1024L;
    private static final int PARALLEL_DOWNLOAD_PARTS = 4;
    private static final long RELEASE_GATE_TTL_MS = 20L * 60L * 1000L;
    private static final String UPDATE_TYPE_MINOR = "minor";
    private static final String UPDATE_TYPE_RELEASE = "release";
    private static final String RETURN_SCHEME = "moodtools-update";
    private static final String RETURN_HOST = "resume";
    private static final String GATE_PREFERENCES = "moodtools_update_gate";
    private static final String GATE_NONCE = "nonce";
    private static final String GATE_BUILD = "build";
    private static final String GATE_EXPIRES = "expires";
    private static final String META_ENDPOINT = "com.android.support.MOD_UPDATE_ENDPOINT";
    private static final String META_ORIGINAL_LAUNCHER = "com.android.support.ORIGINAL_LAUNCHER";
    private static final String DEFAULT_ENDPOINT =
            "https://jester.moodtools.workers.dev/api/mod-update";

    private TextView statusView;
    private TextView detailView;
    private TextView offerBadgeView;
    private TextView offerVersionView;
    private Button changelogToggleButton;
    private TextView changelogView;
    private ProgressBar progressBar;
    private LinearLayout offerPanel;
    private Button installButton;
    private Button skipButton;
    private Button retryButton;
    private Button continueButton;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long screenStartedAt;
    private volatile boolean launching;
    private volatile int operationGeneration;
    private volatile boolean offerVisible;
    private static volatile boolean moduleRestartPending;
    private volatile boolean restartRequired;
    private volatile boolean changelogExpanded;
    private UpdateOffer pendingOffer;
    private FullReleaseInfo pendingFullRelease;
    private String pendingFullReleaseChangelog = "";
    private Object backCallback;
    private long returnedReleaseBuild = -1L;
    private String returnedReleaseGrant;
    private volatile boolean overlayPermissionPrompted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        allowTaskInRecents();
        screenStartedAt = SystemClock.elapsedRealtime();
        boolean releaseReturn = readReleaseReturn(getIntent());
        buildScreen();
        if (!hasOverlayPermission()) {
            showLaunchChoice();
            requestOverlayPermission();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = Api33BackNavigation.register(this, new Runnable() {
                @Override
                public void run() {
                    handleBackNavigation();
                }
            });
        }
        if (releaseReturn) {
            beginUpdateCheck();
        } else {
            showLaunchChoice();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (readReleaseReturn(intent)) beginUpdateCheck();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        rebuildScreenForCurrentConfiguration();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launching && hasOverlayPermission()) {
            launching = false;
            showLaunchChoice();
            return;
        }
        if (!overlayPermissionPrompted || !hasOverlayPermission()) return;
        overlayPermissionPrompted = false;
        launching = false;
        if (!offerVisible && !restartRequired) {
            showLaunchChoice();
        }
    }

    private void buildScreen() {
        getWindow().setStatusBarColor(Color.rgb(7, 11, 16));
        getWindow().setNavigationBarColor(Color.rgb(7, 11, 16));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(7, 11, 16));
        scrollView.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int horizontalPadding = Math.max(dp(22),
                (getResources().getDisplayMetrics().widthPixels - dp(560)) / 2);
        root.setPadding(horizontalPadding, dp(30), horizontalPadding, dp(30));
        root.setBackgroundColor(Color.rgb(7, 11, 16));

        TextView brand = new TextView(this);
        brand.setText("MOOD TOOLS");
        brand.setTextColor(Color.rgb(73, 220, 181));
        brand.setTextSize(13);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand, matchWrap(dp(8)));

        TextView title = new TextView(this);
        title.setText(getApplicationInfo().loadLabel(getPackageManager()));
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(dp(22)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(5)));

        statusView = new TextView(this);
        statusView.setText("Checking for menu updates...");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(17);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setGravity(Gravity.CENTER);
        root.addView(statusView, matchWrap(dp(12)));

        detailView = new TextView(this);
        detailView.setText("Check for updates, then use Play when you are ready to start.");
        detailView.setTextColor(Color.rgb(171, 181, 194));
        detailView.setTextSize(14);
        detailView.setGravity(Gravity.CENTER);
        root.addView(detailView, matchWrap(dp(22)));

        offerPanel = new LinearLayout(this);
        offerPanel.setOrientation(LinearLayout.VERTICAL);
        offerPanel.setPadding(dp(18), dp(18), dp(18), dp(18));
        offerPanel.setBackground(cardBackground());
        offerPanel.setVisibility(View.GONE);

        offerBadgeView = new TextView(this);
        offerBadgeView.setText("UPDATE AVAILABLE");
        offerBadgeView.setTextColor(Color.rgb(73, 220, 181));
        offerBadgeView.setTextSize(11);
        offerBadgeView.setTypeface(Typeface.DEFAULT_BOLD);
        offerBadgeView.setLetterSpacing(0.08f);
        offerPanel.addView(offerBadgeView, matchWrap(dp(7)));

        offerVersionView = new TextView(this);
        offerVersionView.setTextColor(Color.WHITE);
        offerVersionView.setTextSize(18);
        offerVersionView.setTypeface(Typeface.DEFAULT_BOLD);
        offerPanel.addView(offerVersionView, matchWrap(dp(16)));

        TextView changelogLabel = new TextView(this);
        changelogLabel.setText("CHANGELOG HISTORY");
        changelogLabel.setTextColor(Color.rgb(145, 158, 174));
        changelogLabel.setTextSize(11);
        changelogLabel.setTypeface(Typeface.DEFAULT_BOLD);
        changelogLabel.setLetterSpacing(0.06f);
        offerPanel.addView(changelogLabel, matchWrap(dp(7)));

        changelogView = new TextView(this);
        changelogView.setTextColor(Color.rgb(221, 227, 233));
        changelogView.setTextSize(14);
        changelogView.setLineSpacing(dp(3), 1f);
        changelogView.setTextIsSelectable(true);
        offerPanel.addView(changelogView, matchWrap(dp(20)));

        changelogToggleButton = actionButton("Show previous versions", false);
        changelogToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changelogExpanded = !changelogExpanded;
                if (pendingOffer != null) {
                    updateChangelogDisplay(pendingOffer);
                } else {
                    updateFullReleaseChangelogDisplay();
                }
            }
        });
        offerPanel.addView(changelogToggleButton, matchWrap(dp(10)));

        installButton = actionButton("Update now", true);
        installButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                acceptPendingUpdate();
            }
        });
        offerPanel.addView(installButton, matchWrap(dp(10)));

        skipButton = actionButton("Not now", false);
        skipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                skipPendingUpdate();
            }
        });
        offerPanel.addView(skipButton, matchWrap(0));
        root.addView(offerPanel, matchWrap(dp(14)));

        retryButton = actionButton("Retry", true);
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                beginUpdateCheck();
            }
        });
        root.addView(retryButton, matchWrap(dp(10)));

        continueButton = actionButton("Play", false);
        continueButton.setVisibility(View.GONE);
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (restartRequired) {
                    restartAppForMenuUpdate();
                } else {
                    playGame();
                }
            }
        });
        root.addView(continueButton, matchWrap(0));

        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scrollView);
    }

    private void rebuildScreenForCurrentConfiguration() {
        if (statusView == null || detailView == null) return;
        UiSnapshot snapshot = new UiSnapshot(this);
        buildScreen();
        snapshot.restore();
    }

    private final class UiSnapshot {
        private final CharSequence statusText;
        private final CharSequence detailText;
        private final CharSequence offerBadgeText;
        private final CharSequence offerVersionText;
        private final CharSequence changelogText;
        private final CharSequence changelogToggleText;
        private final CharSequence installText;
        private final CharSequence skipText;
        private final CharSequence retryText;
        private final CharSequence continueText;
        private final int progressVisibility;
        private final boolean progressIndeterminate;
        private final int progressMax;
        private final int progress;
        private final int offerVisibility;
        private final int changelogToggleVisibility;
        private final int skipVisibility;
        private final int retryVisibility;
        private final int continueVisibility;
        private final boolean installEnabled;
        private final boolean skipEnabled;
        private final boolean retryEnabled;
        private final boolean continueEnabled;

        UiSnapshot(UpdateActivity ignored) {
            statusText = statusView.getText();
            detailText = detailView.getText();
            offerBadgeText = offerBadgeView.getText();
            offerVersionText = offerVersionView.getText();
            changelogText = changelogView.getText();
            changelogToggleText = changelogToggleButton.getText();
            installText = installButton.getText();
            skipText = skipButton.getText();
            retryText = retryButton.getText();
            continueText = continueButton.getText();
            progressVisibility = progressBar.getVisibility();
            progressIndeterminate = progressBar.isIndeterminate();
            progressMax = progressBar.getMax();
            progress = progressBar.getProgress();
            offerVisibility = offerPanel.getVisibility();
            changelogToggleVisibility = changelogToggleButton.getVisibility();
            skipVisibility = skipButton.getVisibility();
            retryVisibility = retryButton.getVisibility();
            continueVisibility = continueButton.getVisibility();
            installEnabled = installButton.isEnabled();
            skipEnabled = skipButton.isEnabled();
            retryEnabled = retryButton.isEnabled();
            continueEnabled = continueButton.isEnabled();
        }

        void restore() {
            statusView.setText(statusText);
            detailView.setText(detailText);
            offerBadgeView.setText(offerBadgeText);
            offerVersionView.setText(offerVersionText);
            changelogView.setText(changelogText);
            changelogToggleButton.setText(changelogToggleText);
            installButton.setText(installText);
            skipButton.setText(skipText);
            retryButton.setText(retryText);
            continueButton.setText(continueText);
            progressBar.setVisibility(progressVisibility);
            progressBar.setIndeterminate(progressIndeterminate);
            progressBar.setMax(progressMax);
            progressBar.setProgress(progress);
            offerPanel.setVisibility(offerVisibility);
            changelogToggleButton.setVisibility(changelogToggleVisibility);
            skipButton.setVisibility(skipVisibility);
            retryButton.setVisibility(retryVisibility);
            continueButton.setVisibility(continueVisibility);
            installButton.setEnabled(installEnabled);
            skipButton.setEnabled(skipEnabled);
            retryButton.setEnabled(retryEnabled);
            continueButton.setEnabled(continueEnabled);
        }
    }

    private Button actionButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(211, 220, 229));
        button.setTextSize(15);
        button.setAllCaps(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? Color.rgb(29, 115, 96) : Color.rgb(28, 37, 49));
        if (!primary) background.setStroke(dp(1), Color.rgb(62, 76, 94));
        background.setCornerRadius(dp(12));
        button.setBackground(background);
        button.setMinHeight(dp(48));
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        return button;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(15, 22, 31));
        background.setStroke(dp(1), Color.rgb(42, 58, 72));
        background.setCornerRadius(dp(16));
        return background;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = bottomMargin;
        return params;
    }

    private void showLaunchChoice() {
        operationGeneration++;
        offerVisible = false;
        pendingOffer = null;
        pendingFullRelease = null;
        restartRequired = false;
        offerPanel.setVisibility(View.GONE);
        progressBar.setIndeterminate(false);
        progressBar.setMax(1);
        progressBar.setProgress(0);
        retryButton.setText("Check for updates");
        retryButton.setVisibility(View.VISIBLE);
        retryButton.setEnabled(true);
        continueButton.setText("Play");
        continueButton.setVisibility(View.VISIBLE);
        continueButton.setEnabled(true);
        setStatus("Choose how to start",
                "Checking for menu updates uses the internet. Play skips the update check and starts with the installed menu without contacting the update service.");
        retryButton.announceForAccessibility("Choose Check for updates or Play");
    }

    private void playGame() {
        final int generation = ++operationGeneration;
        offerVisible = false;
        pendingOffer = null;
        pendingFullRelease = null;
        restartRequired = false;
        retryButton.setEnabled(false);
        retryButton.setVisibility(View.GONE);
        continueButton.setEnabled(false);
        continueButton.setVisibility(View.GONE);
        progressBar.setIndeterminate(true);
        setStatus("Starting game",
                "Loading the installed menu. Play is the only action that launches the game.");
        new Thread(new Runnable() {
            @Override
            public void run() {
                launchInstalledMenu(generation, "Starting game",
                        "Opening with the installed menu.");
            }
        }, "MoodToolsGameStart").start();
    }

    private void beginUpdateCheck() {
        final int generation = ++operationGeneration;
        offerVisible = false;
        pendingOffer = null;
        pendingFullRelease = null;
        offerPanel.setVisibility(View.GONE);
        installButton.setEnabled(true);
        skipButton.setEnabled(true);
        retryButton.setText("Retry");
        retryButton.setVisibility(View.GONE);
        continueButton.setText("Play");
        continueButton.setVisibility(View.GONE);
        progressBar.setIndeterminate(true);
        progressBar.setProgress(0);
        setStatus("Checking for menu updates...",
                "This only checks for updates. Use Play when you are ready to start the game.");

        new Thread(new Runnable() {
            @Override
            public void run() {
                checkAndLoadUpdate(generation);
            }
        }, "MoodToolsUpdater").start();
    }

    private void checkAndLoadUpdate(int generation) {
        try {
            String abi = NativePayloadLoader.currentAbi();
            NativePayloadLoader.BundledPayload bundled =
                    NativePayloadLoader.readBundledPayload(this);
            if (bundled == null || !abi.equals(bundled.abi)) {
                showPlayReady(generation, "Using the bundled menu",
                        "This package does not contain updater metadata for " + abi + ".");
                return;
            }

            URL endpoint = buildManifestUrl(abi);
            JSONObject envelope = getJson(endpoint);
            if (envelope == null) {
                showPlayReady(generation, "Menu is up to date", "No online update is configured.");
                return;
            }

            FullReleaseInfo fullRelease = readFullReleaseInfo(envelope, endpoint);
            JSONObject payload = ModUpdateTrust.verifyAndDecode(envelope);
            validatePayload(payload, abi);

            if (!containsSha(payload.getJSONArray("baseSha256"), bundled.sha256)) {
                NativePayloadLoader.clearCachedPayload(this);
                DexPayloadLoader.clearCachedPayload(this);
                DexModuleStore.clear(this);
                showPlayReady(generation, "Menu is ready",
                        "The online payload targets a different installed menu build.");
                return;
            }

            long build = payload.getLong("build");
            String version = payload.optString("version", String.valueOf(build));
            String currentNotes = payload.optString("notes", "").trim();
            String updateType = payload.optString("updateType", UPDATE_TYPE_MINOR)
                    .toLowerCase(Locale.US);
            String releasePath = payload.optString("releasePath", "");
            PayloadFile nativeFile = readNativeFile(payload, abi);
            PayloadFile dexFile = readDexFile(payload);

            NativePayloadLoader.CachedPayload cachedNative =
                    NativePayloadLoader.readValidCachedPayload(this, bundled, abi);
            DexPayloadLoader.CachedPayload cachedDex =
                    DexPayloadLoader.readValidCachedPayload(this, bundled);
            DexModuleStore.CachedModule cachedModule = DexModuleStore.readValid(this, bundled);

            boolean moduleDex = dexFile != null && dexFile.isModule();
            boolean nativeReady = nativeFile == null
                    || (!moduleDex && nativeFile.sha256.equals(bundled.sha256))
                    || (cachedNative != null && cachedNative.build >= build
                    && nativeFile.sha256.equals(cachedNative.sha256));
            boolean dexReady = dexFile == null
                    || (dexFile.isModule()
                    ? cachedModule != null && cachedModule.build >= build
                    && dexFile.sha256.equals(cachedModule.sha256)
                    : cachedDex != null && cachedDex.build >= build
                    && dexFile.sha256.equals(cachedDex.sha256)
                    && dexFile.entryClass.equals(cachedDex.entryClass));
            long installedUpdateBuild = Math.max(
                    cachedNative == null ? 0L : cachedNative.build,
                    Math.max(cachedDex == null ? 0L : cachedDex.build,
                            cachedModule == null ? 0L : cachedModule.build));
            long requiredReleaseBuild = payload.optLong("requiredReleaseBuild",
                    UPDATE_TYPE_RELEASE.equals(updateType) ? build : 0L);
            if (requiredReleaseBuild > 0 && nativeFile != null
                    && nativeFile.sha256.equals(bundled.sha256)) {
                installedUpdateBuild = Math.max(installedUpdateBuild, requiredReleaseBuild);
            }
            boolean releaseGateRequired = requiredReleaseBuild > 0
                    && installedUpdateBuild < requiredReleaseBuild;
            String changelog = loadChangelogHistory(endpoint, payload, build, version,
                    updateType, currentNotes, installedUpdateBuild);

            if (!isOperationCurrent(generation)) return;
            if (fullRelease != null
                    && fullRelease.shouldShow(installedGameVersion(), installedUpdateBuild)) {
                showFullReleaseOffer(generation, fullRelease, changelog);
                return;
            }
            String releaseGrant = releaseGateRequired
                    && returnedReleaseBuild == build && returnedReleaseGrant != null
                    ? returnedReleaseGrant : "";
            UpdateOffer offer = new UpdateOffer(endpoint, bundled, abi, build, version,
                    changelog, updateType, releasePath, releaseGrant, requiredReleaseBuild,
                    releaseGateRequired, nativeFile, dexFile, cachedDex, cachedModule,
                    nativeReady, dexReady);
            if (!nativeReady || !dexReady) {
                if (offer.requiresReleaseRoute() && releaseGrant.length() > 0) {
                    returnedReleaseBuild = -1L;
                    returnedReleaseGrant = null;
                    applyUpdateOffer(generation, offer);
                    return;
                }
                showUpdateOffer(generation, offer);
                return;
            }
            applyUpdateOffer(generation, offer);
        } catch (Throwable error) {
            if (!isOperationCurrent(generation)) return;
            showPlayReady(generation, "Update check unavailable",
                    safeMessage(error) + " Press Play to use the installed menu.");
        }
    }

    private void showUpdateOffer(final int generation, final UpdateOffer offer) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isOperationCurrent(generation)) return;
                pendingOffer = offer;
                pendingFullRelease = null;
                offerVisible = true;
                progressBar.setIndeterminate(false);
                progressBar.setMax(1);
                progressBar.setProgress(0);
                setStatus(offer.requiresReleaseRoute()
                                ? (offer.isRelease() ? "Release update available" : "Minor update available")
                                : "Minor update available",
                        offer.requiresReleaseRoute()
                                ? "Linkvertise verification is required once because a previous release update was missed. Your browser will return here to finish the latest update."
                                : "Direct in-app update. No browser or Linkvertise verification is needed.");
                offerBadgeView.setTextColor(offer.requiresReleaseRoute()
                        ? Color.rgb(255, 181, 71) : Color.rgb(73, 220, 181));
                offerBadgeView.setText(offer.requiresReleaseRoute()
                        ? (offer.isRelease() ? "RELEASE UPDATE  |  LINKVERTISE"
                        : "MINOR UPDATE  |  LINKVERTISE REQUIRED")
                        : "MINOR UPDATE  |  DIRECT IN-APP");
                offerVersionView.setText("Version " + offer.version + "  |  "
                        + offer.componentDescription() + "  |  " + formatBytes(offer.downloadBytes()));
                changelogExpanded = false;
                updateChangelogDisplay(offer);
                installButton.setText(offer.requiresReleaseRoute()
                        ? "Continue to Linkvertise"
                        : "Update in game");
                installButton.setEnabled(true);
                skipButton.setEnabled(true);
                skipButton.setVisibility(View.VISIBLE);
                retryButton.setVisibility(View.GONE);
                continueButton.setVisibility(View.GONE);
                offerPanel.setVisibility(View.VISIBLE);
                offerPanel.announceForAccessibility(offer.requiresReleaseRoute()
                        ? "Update available. Linkvertise verification required."
                        : "Minor update available. Direct in-app update.");
            }
        });
    }

    private void updateChangelogDisplay(UpdateOffer offer) {
        ChangelogDisplay display = ChangelogDisplay.from(offer == null ? "" : offer.changelog);
        if (changelogToggleButton != null) {
            changelogToggleButton.setVisibility(display.hasPrevious() ? View.VISIBLE : View.GONE);
            changelogToggleButton.setText(changelogExpanded ? "Hide previous versions"
                    : "Show previous versions");
        }
        if (changelogView != null) {
            changelogView.setText(changelogExpanded || !display.hasPrevious()
                    ? display.fullText : display.latestText);
        }
    }

    private void updateFullReleaseChangelogDisplay() {
        ChangelogDisplay display = ChangelogDisplay.from(pendingFullReleaseChangelog);
        if (changelogToggleButton != null) {
            changelogToggleButton.setVisibility(display.hasPrevious() ? View.VISIBLE : View.GONE);
            changelogToggleButton.setText(changelogExpanded ? "Hide previous versions"
                    : "Show previous versions");
        }
        if (changelogView != null) {
            changelogView.setText(changelogExpanded || !display.hasPrevious()
                    ? display.fullText : display.latestText);
        }
    }

    private void acceptPendingUpdate() {
        final FullReleaseInfo fullRelease = pendingFullRelease;
        if (fullRelease != null) {
            openFullRelease(fullRelease);
            return;
        }
        final UpdateOffer offer = pendingOffer;
        if (offer == null) return;
        if (offer.requiresReleaseRoute() && offer.releaseGrant.length() == 0) {
            openReleaseGate(offer);
            return;
        }
        final int generation = operationGeneration;
        pendingOffer = null;
        offerVisible = false;
        installButton.setEnabled(false);
        skipButton.setEnabled(false);
        offerPanel.setVisibility(View.GONE);
        setStatus("Preparing menu " + offer.version + "...",
                "Keep this screen open while the signed components are verified.");
        progressBar.setIndeterminate(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                applyUpdateOffer(generation, offer);
            }
        }, "MoodToolsUpdateInstaller").start();
    }

    private void showFullReleaseOffer(final int generation, final FullReleaseInfo fullRelease,
                                      final String fullReleaseChangelog) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isOperationCurrent(generation)) return;
                pendingOffer = null;
                pendingFullRelease = fullRelease;
                pendingFullReleaseChangelog = fullReleaseChangelog == null ? "" : fullReleaseChangelog;
                offerVisible = true;
                progressBar.setIndeterminate(false);
                progressBar.setMax(1);
                progressBar.setProgress(0);
                setStatus("Full APK update available",
                        "This installed menu needs the website release. Open the files page, download the full APK/APKS once, then future minor updates can install in-app.");
                offerBadgeView.setTextColor(Color.rgb(255, 181, 71));
                offerBadgeView.setText("FULL APK UPDATE  |  WEBSITE");
                offerVersionView.setText(fullRelease.title + " version " + fullRelease.version);
                changelogExpanded = false;
                updateFullReleaseChangelogDisplay();
                installButton.setText("Open files page");
                installButton.setEnabled(true);
                skipButton.setEnabled(true);
                skipButton.setVisibility(View.VISIBLE);
                retryButton.setVisibility(View.GONE);
                continueButton.setVisibility(View.GONE);
                offerPanel.setVisibility(View.VISIBLE);
                offerPanel.announceForAccessibility("Full APK update available. Open files page.");
            }
        });
    }

    private void openFullRelease(FullReleaseInfo fullRelease) {
        try {
            setStatus("Opening Mood Tools files",
                    "Download and install the latest full APK/APKS once.");
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(fullRelease.url));
            browser.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(browser);
        } catch (Throwable error) {
            showBlockingError(error);
        }
    }

    private void openReleaseGate(UpdateOffer offer) {
        try {
            byte[] nonceBytes = new byte[32];
            new SecureRandom().nextBytes(nonceBytes);
            String nonce = Base64.encodeToString(nonceBytes,
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            getSharedPreferences(GATE_PREFERENCES, MODE_PRIVATE).edit()
                    .putString(GATE_NONCE, nonce)
                    .putLong(GATE_BUILD, offer.build)
                    .putLong(GATE_EXPIRES, System.currentTimeMillis() + RELEASE_GATE_TTL_MS)
                    .apply();

            URL gateUrl = new URL(offer.endpoint, offer.releasePath + "?nonce="
                    + URLEncoder.encode(nonce, "UTF-8"));
            if (!"https".equalsIgnoreCase(gateUrl.getProtocol())
                    || gateUrl.getPort() != offer.endpoint.getPort()
                    || !offer.endpoint.getHost().equalsIgnoreCase(gateUrl.getHost())) {
                throw new SecurityException("The release gate left the trusted update host.");
            }

            setStatus("Continue on the Mood Tools website",
                    "Complete the official Linkvertise route. Continue there to return and start the update.");
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(gateUrl.toString()));
            browser.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(browser);
        } catch (Throwable error) {
            showBlockingError(error);
        }
    }

    private boolean readReleaseReturn(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return false;
        Uri data = intent.getData();
        if (data == null
                || !RETURN_SCHEME.equalsIgnoreCase(data.getScheme())
                || !RETURN_HOST.equalsIgnoreCase(data.getHost())
                || !("/" + getPackageName()).equals(data.getPath())) {
            return false;
        }

        String nonce = data.getQueryParameter("nonce");
        String grant = data.getQueryParameter("grant");
        long build;
        try {
            build = Long.parseLong(data.getQueryParameter("build"));
        } catch (Throwable ignored) {
            return false;
        }
        android.content.SharedPreferences preferences =
                getSharedPreferences(GATE_PREFERENCES, MODE_PRIVATE);
        boolean valid = build > 0
                && build == preferences.getLong(GATE_BUILD, -1L)
                && System.currentTimeMillis() <= preferences.getLong(GATE_EXPIRES, 0L)
                && nonce != null && nonce.matches("^[A-Za-z0-9_-]{43}$")
                && nonce.equals(preferences.getString(GATE_NONCE, ""))
                && grant != null && grant.length() <= MAX_RELEASE_GRANT_CHARS
                && grant.matches("^[A-Za-z0-9_.-]+$");
        if (!valid) return false;

        returnedReleaseBuild = build;
        returnedReleaseGrant = grant;
        preferences.edit().clear().apply();
        return true;
    }

    private void skipPendingUpdate() {
        if (pendingOffer == null && pendingFullRelease == null) return;
        final int generation = operationGeneration;
        pendingOffer = null;
        pendingFullRelease = null;
        offerVisible = false;
        installButton.setEnabled(false);
        skipButton.setEnabled(false);
        offerPanel.setVisibility(View.GONE);
        showPlayReady(generation, "Update postponed",
                "Press Play to use the installed menu, or check again later.");
    }

    private void applyUpdateOffer(int generation, UpdateOffer offer) {
        try {
            File downloadedNative = null;
            File downloadedDex = null;
            if (!offer.nativeReady) {
                setStatusFromWorker("Downloading menu " + offer.version + "...",
                        "Keep this screen open. Play will be available after verification.");
                downloadedNative = downloadPayload(offer.endpoint, offer.nativeFile, offer.build,
                        NativePayloadLoader.updateRoot(this), offer.abi,
                        "libYourSaviour-" + offer.nativeFile.sha256 + ".so",
                        MAX_NATIVE_PAYLOAD_BYTES, offer.releaseGrant, generation);
            }
            if (!offer.dexReady) {
                setStatusFromWorker("Downloading menu " + offer.version + "...",
                        "Keep this screen open. Play will be available after verification.");
                downloadedDex = downloadPayload(offer.endpoint, offer.dexFile, offer.build,
                        DexPayloadLoader.updateRoot(this), "payload",
                        "classes-" + offer.dexFile.sha256 + ".dex",
                        MAX_DEX_PAYLOAD_BYTES, offer.releaseGrant, generation);
            }
            if (!isOperationCurrent(generation)) return;

            setStatusFromWorker("Verifying update...", "Saving signed menu components.");
            if (offer.nativeFile == null) {
                // A DEX-only update leaves the currently selected native payload unchanged.
            } else if (offer.nativeFile.sha256.equals(offer.bundled.sha256)
                    && (offer.dexFile == null || !offer.dexFile.isModule())) {
                NativePayloadLoader.clearCachedPayload(this);
            } else if (downloadedNative != null) {
                NativePayloadLoader.installPayload(this, downloadedNative, offer.build,
                        offer.nativeFile.sha256, offer.bundled.sha256, offer.abi);
            }

            if (offer.dexFile == null) {
                DexPayloadLoader.clearCachedPayload(this);
                DexModuleStore.clear(this);
            } else if (downloadedDex != null) {
                if (offer.dexFile.isModule()) {
                    DexPayloadLoader.clearCachedPayload(this);
                    DexModuleStore.install(this, downloadedDex, offer.build,
                            offer.dexFile.sha256, offer.bundled.sha256);
                } else {
                    DexModuleStore.clear(this);
                    DexPayloadLoader.installPayload(this, downloadedDex, offer.build,
                            offer.dexFile.sha256, offer.bundled.sha256, offer.dexFile.entryClass);
                }
            } else if (offer.dexFile.isModule()
                    ? offer.cachedModule == null : offer.cachedDex == null) {
                throw new IllegalStateException("The verified DEX payload is no longer available.");
            }

            if (!isOperationCurrent(generation)) return;
            boolean moduleUpdatedNow = offer.dexFile != null && offer.dexFile.isModule() && !offer.dexReady;
            boolean needsRestart = moduleRestartPending || moduleUpdatedNow;
            moduleRestartPending = needsRestart;
            String readyDetail = needsRestart
                    ? "Java menu changes are installed. Restart once so Android can load the updated menu module."
                    : "The signed menu is ready. Press Play to start the game.";
            showReady(generation,
                    "Menu " + offer.version
                            + (offer.nativeReady && offer.dexReady ? " is up to date" : " updated"),
                    readyDetail,
                    needsRestart);
        } catch (Throwable error) {
            if (!isOperationCurrent(generation)) return;
            showPlayReady(generation, "Update could not be installed",
                    safeMessage(error) + " Press Play to use the installed menu.");
        }
    }

    private URL buildManifestUrl(String abi) throws Exception {
        String endpoint = readMetaString(META_ENDPOINT, DEFAULT_ENDPOINT).trim();
        URL base = new URL(endpoint);
        if (!"https".equalsIgnoreCase(base.getProtocol())) {
            throw new SecurityException("The update endpoint must use HTTPS.");
        }
        String separator = endpoint.contains("?") ? "&" : "?";
        return new URL(endpoint + separator
                + "package=" + URLEncoder.encode(getPackageName(), "UTF-8")
                + "&abi=" + URLEncoder.encode(abi, "UTF-8")
                + "&bootstrap=" + BOOTSTRAP_VERSION);
    }

    private JSONObject getJson(URL url) throws Exception {
        HttpURLConnection connection = open(url);
        try {
            int status = connection.getResponseCode();
            if (status == 404 || status == 204) return null;
            if (status != 200) throw new Exception("Update server returned HTTP " + status + ".");
            InputStream input = new BufferedInputStream(connection.getInputStream());
            try {
                StringBuilder text = new StringBuilder();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (text.length() > 256 * 1024) throw new SecurityException("Update manifest is too large.");
                    text.append(new String(buffer, 0, read, "UTF-8"));
                }
                return new JSONObject(text.toString());
            } finally {
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private String loadChangelogHistory(URL manifestUrl, JSONObject currentPayload, long currentBuild,
                                        String currentVersion, String currentType,
                                        String currentNotes, long installedUpdateBuild) {
        String fallback = formatChangelogEntry(true, currentVersion, currentType, currentNotes);
        if (installedUpdateBuild <= 0L) return fallback;
        try {
            String path = "/api/mod-update-history?package="
                    + URLEncoder.encode(getPackageName(), "UTF-8")
                    + "&build=" + currentBuild;
            URL historyUrl = new URL(manifestUrl, path);
            if (!"https".equalsIgnoreCase(historyUrl.getProtocol())
                    || historyUrl.getPort() != manifestUrl.getPort()
                    || !manifestUrl.getHost().equalsIgnoreCase(historyUrl.getHost())) {
                return fallback;
            }
            JSONObject envelope = getJson(historyUrl);
            if (envelope == null) return fallback;
            JSONObject history = ModUpdateTrust.verifyAndDecode(envelope);
            if (history.getInt("schema") != 1
                    || !getPackageName().equals(history.getString("packageName"))
                    || !currentPayload.getString("slug").equals(history.getString("slug"))
                    || currentBuild != history.getLong("currentBuild")
                    || !installedGameVersion().equals(history.getString("gameVersion"))) {
                return fallback;
            }

            JSONArray entries = history.getJSONArray("entries");
            if (entries.length() == 0 || entries.length() > MAX_CHANGELOG_HISTORY_ENTRIES) {
                return fallback;
            }
            StringBuilder display = new StringBuilder();
            Set<Long> seenBuilds = new HashSet<>();
            boolean includesCurrent = false;
            int totalCharacters = 0;
            for (int index = 0; index < entries.length(); index++) {
                JSONObject entry = entries.getJSONObject(index);
                long build = entry.getLong("build");
                String version = entry.getString("version").trim();
                String type = entry.getString("updateType").toLowerCase(Locale.US);
                String notes = entry.optString("notes", "").trim();
                totalCharacters += notes.length();
                if (build <= 0 || build > currentBuild || version.length() == 0
                        || version.length() > 64 || notes.length() > MAX_CHANGELOG_CHARS
                        || totalCharacters > MAX_CHANGELOG_HISTORY_CHARS
                        || (!UPDATE_TYPE_MINOR.equals(type) && !UPDATE_TYPE_RELEASE.equals(type))) {
                    return fallback;
                }
                if (!seenBuilds.add(build)) continue;
                if (build <= installedUpdateBuild) continue;
                boolean latest = build == currentBuild;
                if (latest) includesCurrent = true;
                appendChangelogEntry(display, latest, version, type, notes);
            }
            if (!includesCurrent) {
                StringBuilder withCurrent = new StringBuilder(fallback);
                if (display.length() > 0) withCurrent.append("\n\n").append(display);
                return withCurrent.toString();
            }
            return display.length() == 0 ? fallback : display.toString();
        } catch (Throwable ignored) {
            // History is optional. A missing, old, or malformed history document must not
            // prevent the verified current update from being offered.
            return fallback;
        }
    }

    private String installedGameVersion() throws Exception {
        String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        return version == null ? "" : version;
    }

    private static String formatChangelogEntry(boolean latest, String version, String type,
                                               String notes) {
        StringBuilder text = new StringBuilder();
        appendChangelogEntry(text, latest, version, type, notes);
        return text.toString();
    }

    private static void appendChangelogEntry(StringBuilder target, boolean latest, String version,
                                             String type, String notes) {
        if (target.length() > 0) target.append("\n\n");
        target.append(latest ? "LATEST" : "PREVIOUS")
                .append(" | VERSION ").append(version)
                .append(" | ").append(type.toUpperCase(Locale.US));
        target.append('\n').append(notes.length() == 0
                ? "No changelog was provided for this update." : notes);
    }

    private void validatePayload(JSONObject payload, String abi) throws Exception {
        int schema = payload.getInt("schema");
        if (schema != 1 && schema != 2 && schema != 3) {
            throw new SecurityException("Unsupported update schema.");
        }
        if (!getPackageName().equals(payload.getString("packageName"))) {
            throw new SecurityException("Update belongs to another app.");
        }
        String slug = payload.optString("slug", "");
        if (!slug.matches("^[a-z0-9][a-z0-9-]{0,63}$")) {
            throw new SecurityException("Invalid update channel.");
        }
        if (payload.getInt("minimumBootstrap") > BOOTSTRAP_VERSION) {
            throw new SecurityException("This update requires a newer initializer.");
        }
        long build = payload.getLong("build");
        if (build <= 0) throw new SecurityException("Invalid update build number.");
        String version = payload.optString("version", "").trim();
        if (version.length() == 0 || version.length() > 64) {
            throw new SecurityException("Invalid update version label.");
        }
        String changelog = payload.optString("notes", "");
        if (changelog.length() > MAX_CHANGELOG_CHARS) {
            throw new SecurityException("The signed update changelog is too large.");
        }
        String updateType = payload.optString("updateType", UPDATE_TYPE_MINOR)
                .toLowerCase(Locale.US);
        if (!UPDATE_TYPE_MINOR.equals(updateType) && !UPDATE_TYPE_RELEASE.equals(updateType)) {
            throw new SecurityException("Unsupported update type.");
        }
        long requiredReleaseBuild = payload.optLong("requiredReleaseBuild",
                UPDATE_TYPE_RELEASE.equals(updateType) ? build : 0L);
        if (requiredReleaseBuild < 0 || requiredReleaseBuild > build) {
            throw new SecurityException("Invalid release gate requirement.");
        }
        if (UPDATE_TYPE_RELEASE.equals(updateType) || requiredReleaseBuild > 0) {
            String expectedPath = "/mod-update-release/" + slug + "/"
                    + (requiredReleaseBuild > 0 ? requiredReleaseBuild : build);
            if (!slug.matches("^[a-z0-9][a-z0-9-]{0,63}$")
                    || !expectedPath.equals(payload.optString("releasePath", ""))) {
                throw new SecurityException("Invalid release update route.");
            }
        }
        PayloadFile nativeFile = readNativeFile(payload, abi);
        PayloadFile dexFile = readDexFile(payload);
        if (nativeFile == null && dexFile == null) {
            throw new SecurityException("The signed update has no compatible payload.");
        }
        if (nativeFile != null) validateFile(nativeFile, MAX_NATIVE_PAYLOAD_BYTES, "native");
        if (dexFile != null) {
            validateFile(dexFile, MAX_DEX_PAYLOAD_BYTES, "DEX");
            if (dexFile.isModule() && (schema != 3 || Build.VERSION.SDK_INT < 28)) {
                throw new SecurityException("Replaceable menu DEX requires Android 9 or newer.");
            }
            if (!dexFile.isModule() && !DexPayloadLoader.isAllowedEntryClass(dexFile.entryClass)) {
                throw new SecurityException("Invalid DEX entry point.");
            }
        }
    }

    private FullReleaseInfo readFullReleaseInfo(JSONObject envelope, URL endpoint) {
        try {
            JSONObject json = envelope.optJSONObject("fullRelease");
            if (json == null) return null;
            String title = json.optString("title", "Mood Tools").trim();
            String version = json.optString("version", "").trim();
            String path = json.optString("path", "").trim();
            long requiredBelowUpdateBuild = json.optLong("requiredBelowUpdateBuild", 0L);
            if (title.length() == 0 || title.length() > 80
                    || version.length() == 0 || version.length() > 64
                    || !path.matches("^/(?:game|go)/[a-z0-9][a-z0-9-]{0,63}$")
                    || requiredBelowUpdateBuild < 0) {
                return null;
            }
            URL filesUrl = new URL(endpoint, path);
            if (!"https".equalsIgnoreCase(filesUrl.getProtocol())
                    || filesUrl.getPort() != endpoint.getPort()
                    || !endpoint.getHost().equalsIgnoreCase(filesUrl.getHost())) {
                return null;
            }
            return new FullReleaseInfo(title, version, filesUrl.toString(),
                    requiredBelowUpdateBuild);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private PayloadFile readNativeFile(JSONObject payload, String abi) throws Exception {
        int schema = payload.getInt("schema");
        JSONObject files = payload.getJSONObject("files");
        JSONObject file = schema == 1
                ? files.optJSONObject(abi)
                : files.optJSONObject("native") == null
                ? null : files.optJSONObject("native").optJSONObject(abi);
        return file == null ? null : PayloadFile.nativeFile(file);
    }

    private PayloadFile readDexFile(JSONObject payload) throws Exception {
        if (payload.getInt("schema") < 2) return null;
        JSONObject file = payload.getJSONObject("files").optJSONObject("dex");
        return file == null ? null : PayloadFile.dexFile(file);
    }

    private void validateFile(PayloadFile file, long maximumSize, String label) {
        if (file.sha256.length() != 64 || file.size <= 0 || file.size > maximumSize
                || !file.path.startsWith("/api/mod-update-payload/")
                || file.path.contains("..") || file.path.contains("?") || file.path.contains("#")) {
            throw new SecurityException("Invalid " + label + " payload metadata.");
        }
    }

    private File downloadPayload(URL manifestUrl, PayloadFile payload, long build,
                                 File parentRoot, String subdirectory, String destinationName,
                                 long maximumSize, String releaseGrant, int generation) throws Exception {
        if (!isOperationCurrent(generation)) throw new InterruptedException("Update cancelled.");
        URL downloadUrl = new URL(manifestUrl, payload.path);
        if (!"https".equalsIgnoreCase(downloadUrl.getProtocol())
                || downloadUrl.getPort() != manifestUrl.getPort()
                || !manifestUrl.getHost().equalsIgnoreCase(downloadUrl.getHost())) {
            throw new SecurityException("Update download left the trusted host.");
        }
        File root = new File(parentRoot, subdirectory);
        if (!root.exists() && !root.mkdirs()) throw new Exception("Could not create update storage.");
        File temporary = new File(root, "payload-" + build + ".tmp");
        File destination = new File(root, destinationName);
        if (destination.isFile() && payload.sha256.equals(hashFor(payload, destination))) {
            destination.setReadable(true, true);
            destination.setWritable(false, false);
            return destination;
        }

        if (temporary.exists()) {
            temporary.setWritable(true, true);
            temporary.delete();
        }
        HttpURLConnection connection = open(downloadUrl);
        if (releaseGrant != null && releaseGrant.length() > 0) {
            connection.setRequestProperty("X-MoodTools-Update-Grant", releaseGrant);
        }
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new Exception("Update download returned HTTP " + status + ".");
            long contentLength = connection.getContentLength();
            if (contentLength > maximumSize || (contentLength > 0 && contentLength != payload.size)) {
                throw new SecurityException("Update download size does not match its signed manifest.");
            }

            long total;
            boolean downloadedInRanges = false;
            if (payload.size >= PARALLEL_DOWNLOAD_MIN_BYTES) {
                connection.disconnect();
                connection = null;
                downloadedInRanges = tryDownloadPayloadInRanges(downloadUrl, temporary, payload,
                        maximumSize, releaseGrant, generation);
            }
            if (downloadedInRanges) {
                total = payload.size;
            } else {
                if (connection == null) {
                    connection = open(downloadUrl);
                    if (releaseGrant != null && releaseGrant.length() > 0) {
                        connection.setRequestProperty("X-MoodTools-Update-Grant", releaseGrant);
                    }
                    status = connection.getResponseCode();
                    if (status != 200) {
                        throw new Exception("Update download returned HTTP " + status + ".");
                    }
                    contentLength = connection.getContentLength();
                    if (contentLength > maximumSize || (contentLength > 0 && contentLength != payload.size)) {
                        throw new SecurityException("Update download size does not match its signed manifest.");
                    }
                }
                InputStream input = new BufferedInputStream(connection.getInputStream());
                FileOutputStream fileOutput = new FileOutputStream(temporary);
                BufferedOutputStream output = new BufferedOutputStream(fileOutput);
                total = 0L;
                try {
                    byte[] buffer = new byte[DOWNLOAD_BUFFER_BYTES];
                    long lastProgressBytes = 0L;
                    long lastProgressAt = SystemClock.elapsedRealtime();
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (!isOperationCurrent(generation)) {
                            throw new InterruptedException("Update cancelled.");
                        }
                        total += read;
                        if (total > maximumSize || total > payload.size) {
                            throw new SecurityException("Update download exceeded its signed size.");
                        }
                        output.write(buffer, 0, read);
                        long now = SystemClock.elapsedRealtime();
                        if (total == payload.size
                                || total - lastProgressBytes >= DOWNLOAD_PROGRESS_BYTES
                                || now - lastProgressAt >= DOWNLOAD_PROGRESS_MS) {
                            publishProgress(total, payload.size, generation);
                            lastProgressBytes = total;
                            lastProgressAt = now;
                        }
                    }
                    publishProgress(total, payload.size, generation);
                    output.flush();
                    fileOutput.getFD().sync();
                } finally {
                    try {
                        output.close();
                    } finally {
                        input.close();
                    }
                }
            }

            if (total != payload.size || !payload.sha256.equals(hashFor(payload, temporary))) {
                throw new SecurityException("Downloaded payload failed verification.");
            }
            if (!isOperationCurrent(generation)) throw new InterruptedException("Update cancelled.");
            if (destination.exists()) {
                destination.setWritable(true, true);
                destination.delete();
            }
            if (!temporary.renameTo(destination)) throw new Exception("Could not activate downloaded update.");
            if (!destination.setReadable(true, true) || !destination.setWritable(false, false)) {
                throw new SecurityException("Downloaded update could not be made read-only.");
            }
            return destination;
        } finally {
            if (connection != null) connection.disconnect();
            if (temporary.exists()) {
                temporary.setWritable(true, true);
                temporary.delete();
            }
        }
    }

    private boolean tryDownloadPayloadInRanges(final URL downloadUrl, final File temporary,
                                               final PayloadFile payload, final long maximumSize,
                                               final String releaseGrant, final int generation)
            throws Exception {
        if (payload.size <= 0 || payload.size > maximumSize) return false;
        if (temporary.exists()) {
            temporary.setWritable(true, true);
            temporary.delete();
        }
        RandomAccessFile seed = null;
        try {
            seed = new RandomAccessFile(temporary, "rw");
            seed.setLength(payload.size);
        } finally {
            if (seed != null) seed.close();
        }

        final Object errorLock = new Object();
        final Throwable[] error = new Throwable[1];
        final Object progressLock = new Object();
        final long[] downloaded = new long[]{0L, 0L, SystemClock.elapsedRealtime()};
        final int parts = Math.max(1, Math.min(PARALLEL_DOWNLOAD_PARTS,
                (int) ((payload.size + PARALLEL_DOWNLOAD_MIN_BYTES - 1L)
                        / PARALLEL_DOWNLOAD_MIN_BYTES)));
        final long partSize = (payload.size + parts - 1L) / parts;
        Thread[] workers = new Thread[parts];

        for (int index = 0; index < parts; index++) {
            final long start = index * partSize;
            final long end = Math.min(payload.size - 1L, start + partSize - 1L);
            if (start > end) continue;
            workers[index] = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        downloadPayloadRange(downloadUrl, temporary, payload, maximumSize,
                                releaseGrant, generation, start, end, downloaded, progressLock);
                    } catch (Throwable thrown) {
                        synchronized (errorLock) {
                            if (error[0] == null) error[0] = thrown;
                        }
                    }
                }
            }, "MoodToolsRangeDownload-" + index);
            workers[index].start();
        }

        for (Thread worker : workers) {
            if (worker == null) continue;
            while (worker.isAlive()) {
                if (!isOperationCurrent(generation)) {
                    worker.interrupt();
                    throw new InterruptedException("Update cancelled.");
                }
                worker.join(250L);
            }
        }

        synchronized (errorLock) {
            if (error[0] != null) {
                temporary.setWritable(true, true);
                temporary.delete();
                return false;
            }
        }
        publishProgress(payload.size, payload.size, generation);
        return true;
    }

    private void downloadPayloadRange(URL downloadUrl, File temporary, PayloadFile payload,
                                      long maximumSize, String releaseGrant, int generation,
                                      long start, long end, long[] downloaded,
                                      Object progressLock) throws Exception {
        HttpURLConnection rangeConnection = open(downloadUrl);
        if (releaseGrant != null && releaseGrant.length() > 0) {
            rangeConnection.setRequestProperty("X-MoodTools-Update-Grant", releaseGrant);
        }
        rangeConnection.setRequestProperty("Range", "bytes=" + start + "-" + end);
        InputStream input = null;
        RandomAccessFile output = null;
        try {
            int status = rangeConnection.getResponseCode();
            if (status != 206) throw new Exception("Range request returned HTTP " + status + ".");
            long expected = end - start + 1L;
            long contentLength = rangeConnection.getContentLength();
            if (contentLength > 0 && contentLength != expected) {
                throw new SecurityException("Range size does not match the signed update.");
            }
            input = new BufferedInputStream(rangeConnection.getInputStream());
            output = new RandomAccessFile(temporary, "rw");
            output.seek(start);
            byte[] buffer = new byte[DOWNLOAD_BUFFER_BYTES];
            long local = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (!isOperationCurrent(generation)) {
                    throw new InterruptedException("Update cancelled.");
                }
                local += read;
                if (local > expected || start + local > maximumSize || start + local > payload.size) {
                    throw new SecurityException("Update range exceeded its signed size.");
                }
                output.write(buffer, 0, read);
                synchronized (progressLock) {
                    downloaded[0] += read;
                    long now = SystemClock.elapsedRealtime();
                    if (downloaded[0] == payload.size
                            || downloaded[0] - downloaded[1] >= DOWNLOAD_PROGRESS_BYTES
                            || now - downloaded[2] >= DOWNLOAD_PROGRESS_MS) {
                        publishProgress(downloaded[0], payload.size, generation);
                        downloaded[1] = downloaded[0];
                        downloaded[2] = now;
                    }
                }
            }
            if (local != expected) throw new SecurityException("Update range ended early.");
        } finally {
            if (output != null) output.close();
            if (input != null) input.close();
            rangeConnection.disconnect();
        }
    }

    private String hashFor(PayloadFile payload, File file) throws Exception {
        return payload.entryClass == null
                ? NativePayloadLoader.sha256(file) : DexPayloadLoader.sha256(file);
    }

    private HttpURLConnection open(URL url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(15000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json, application/octet-stream");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("User-Agent", "MoodToolsUpdater/" + BOOTSTRAP_VERSION);
        return connection;
    }

    private void publishProgress(final long downloaded, final long total, final int generation) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isOperationCurrent(generation)) return;
                progressBar.setIndeterminate(false);
                progressBar.setMax(1000);
                progressBar.setProgress((int) Math.min(1000L, downloaded * 1000L / total));
                detailView.setText(String.format(Locale.US, "%.1f / %.1f MB",
                        downloaded / 1048576.0, total / 1048576.0));
            }
        });
    }

    private void launchInstalledMenu(int generation, String title, String detail) {
        if (!isOperationCurrent(generation)) return;
        try {
            NativePayloadLoader.ensureLoaded(this);
            DexPayloadLoader.invokeCached(this, NativePayloadLoader.readBundledPayload(this),
                    DexPayloadLoader.PHASE_BEFORE_GAME_LAUNCH);
            if (!isOperationCurrent(generation)) return;
            setStatusFromWorker(title, detail);
            launchAfterMinimumDelay(title);
        } catch (Throwable error) {
            if (!isOperationCurrent(generation)) return;
            setStatusFromWorker("Starting game",
                    "Menu pre-load was skipped after a native loader retry. Opening the game now.");
            launchAfterMinimumDelay("Starting game");
        }
    }

    private void launchAfterMinimumDelay(String status) {
        final int generation = operationGeneration;
        final long elapsed = SystemClock.elapsedRealtime() - screenStartedAt;
        final long delay = Math.max(150L, MINIMUM_SCREEN_TIME_MS - elapsed);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isOperationCurrent(generation)) return;
                progressBar.setIndeterminate(false);
                progressBar.setMax(1);
                progressBar.setProgress(1);
                setStatus(status, "Opening the game...");
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isOperationCurrent(generation)) return;
                        launchOriginalGame();
                    }
                }, delay);
            }
        });
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT < 23 || isFinishing()) return;
        operationGeneration++;
        mainHandler.removeCallbacksAndMessages(null);
        overlayPermissionPrompted = true;
        launching = false;
        progressBar.setIndeterminate(false);
        progressBar.setMax(1);
        progressBar.setProgress(0);
        setStatus("Allow display over other apps",
                "Enable this permission for the menu, then return here to continue.");
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable error) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Throwable fallbackError) {
                showBlockingError(fallbackError);
            }
        }
    }

    private void launchOriginalGame() {
        if (launching || isFinishing()) return;
        if (!hasOverlayPermission()) {
            requestOverlayPermission();
            return;
        }
        allowTaskInRecents();
        launching = true;
        try {
            String originalLauncher = readMetaString(META_ORIGINAL_LAUNCHER, "");
            if (originalLauncher.length() == 0) {
                throw new IllegalStateException("Original game launcher is not configured.");
            }
            Intent gameIntent = new Intent();
            gameIntent.setClassName(getPackageName(), originalLauncher);
            gameIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(gameIntent);
            finish();
        } catch (Throwable error) {
            launching = false;
            showBlockingError(error);
        }
    }

    private void allowTaskInRecents() {
        if (Build.VERSION.SDK_INT < 21) return;
        try {
            Object service = getSystemService(Context.ACTIVITY_SERVICE);
            if (!(service instanceof ActivityManager)) return;
            for (ActivityManager.AppTask task : ((ActivityManager) service).getAppTasks()) {
                try {
                    task.setExcludeFromRecents(false);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void restartAppForMenuUpdate() {
        if (launching || isFinishing()) return;
        launching = true;
        restartRequired = false;
        pendingOffer = null;
        pendingFullRelease = null;
        offerVisible = false;
        offerPanel.setVisibility(View.GONE);
        progressBar.setIndeterminate(true);
        retryButton.setVisibility(View.VISIBLE);
        retryButton.setText("Restarting...");
        retryButton.setEnabled(false);
        continueButton.setVisibility(View.VISIBLE);
        continueButton.setText("Please wait");
        continueButton.setEnabled(false);
        setStatus("Restarting menu",
                "Applying the updated menu module. The app will close briefly and reopen automatically.");
        continueButton.announceForAccessibility("Restarting menu. Please wait.");
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                performMenuUpdateRestart();
            }
        }, 900L);
    }

    private void performMenuUpdateRestart() {
        if (isFinishing() || isDestroyed()) return;
        try {
            Intent restartIntent = new Intent(this, UpdateActivity.class);
            restartIntent.setAction(Intent.ACTION_MAIN);
            restartIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            int flags = PendingIntent.FLAG_CANCEL_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            Bundle pendingOptions = null;
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                pendingOptions = options.toBundle();
            }
            PendingIntent pendingRestart = PendingIntent.getActivity(
                    this, 7004, restartIntent, flags, pendingOptions);
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                long triggerAt = SystemClock.elapsedRealtime() + 2400L;
                try {
                    if (Build.VERSION.SDK_INT >= 23) {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                triggerAt, pendingRestart);
                    } else {
                        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                triggerAt, pendingRestart);
                    }
                } catch (SecurityException ignored) {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerAt, pendingRestart);
                }
            } else {
                startActivity(restartIntent);
            }
            finishAffinity();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        } catch (Throwable error) {
            launching = false;
            showBlockingError(error);
        }
    }

    private void showBlockingError(final Throwable error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                pendingOffer = null;
                pendingFullRelease = null;
                offerVisible = false;
                restartRequired = false;
                offerPanel.setVisibility(View.GONE);
                progressBar.setIndeterminate(false);
                progressBar.setProgress(0);
                setStatus("Menu could not initialize", safeMessage(error));
                retryButton.setVisibility(View.VISIBLE);
                retryButton.setText("Check for updates");
                retryButton.setEnabled(true);
                continueButton.setVisibility(View.VISIBLE);
                continueButton.setText("Play");
                continueButton.setEnabled(true);
            }
        });
    }

    private void showPlayReady(final int generation, final String title, final String detail) {
        showReady(generation, title, detail, false);
    }

    private void showReady(final int generation, final String title, final String detail,
                           final boolean requiresRestart) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isOperationCurrent(generation)) return;
                pendingOffer = null;
                offerVisible = false;
                restartRequired = requiresRestart;
                offerPanel.setVisibility(View.GONE);
                progressBar.setIndeterminate(false);
                progressBar.setMax(1);
                progressBar.setProgress(1);
                retryButton.setText("Check for updates");
                retryButton.setVisibility(View.VISIBLE);
                retryButton.setEnabled(true);
                continueButton.setText(requiresRestart ? "Restart" : "Play");
                continueButton.setVisibility(View.VISIBLE);
                continueButton.setEnabled(true);
                setStatus(title, detail);
            }
        });
    }

    private void setStatusFromWorker(final String title, final String detail) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                setStatus(title, detail);
            }
        });
    }

    private void setStatus(String title, String detail) {
        statusView.setText(title);
        detailView.setText(detail == null || detail.length() == 0 ? " " : detail);
    }

    private String readMetaString(String key, String fallback) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            if (info.metaData == null) return fallback;
            String value = info.metaData.getString(key);
            return value == null || value.trim().length() == 0 ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean containsSha(JSONArray values, String expected) {
        for (int index = 0; index < values.length(); index++) {
            if (expected.equals(normalizeSha(values.optString(index, "")))) return true;
        }
        return false;
    }

    private static String normalizeSha(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.trim().length() == 0
                ? "The update service could not be reached."
                : message.trim();
    }

    private boolean isOperationCurrent(int generation) {
        return generation == operationGeneration
                && !isFinishing()
                && !isDestroyed();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        if (offerVisible) {
            skipPendingUpdate();
            return;
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        operationGeneration++;
        pendingOffer = null;
        offerVisible = false;
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            Api33BackNavigation.unregister(this, backCallback);
            backCallback = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @SuppressLint("NewApi")
    private static final class Api33BackNavigation {
        static Object register(Activity activity, final Runnable action) {
            android.window.OnBackInvokedCallback callback =
                    new android.window.OnBackInvokedCallback() {
                        @Override
                        public void onBackInvoked() {
                            action.run();
                        }
                    };
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return callback;
        }

        static void unregister(Activity activity, Object value) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (android.window.OnBackInvokedCallback) value);
        }

        private Api33BackNavigation() {
        }
    }

    private static final class UpdateOffer {
        final URL endpoint;
        final NativePayloadLoader.BundledPayload bundled;
        final String abi;
        final long build;
        final String version;
        final String changelog;
        final String updateType;
        final String releasePath;
        final String releaseGrant;
        final long requiredReleaseBuild;
        final boolean releaseGateRequired;
        final PayloadFile nativeFile;
        final PayloadFile dexFile;
        final DexPayloadLoader.CachedPayload cachedDex;
        final DexModuleStore.CachedModule cachedModule;
        final boolean nativeReady;
        final boolean dexReady;

        UpdateOffer(URL endpoint, NativePayloadLoader.BundledPayload bundled, String abi,
                    long build, String version, String changelog, String updateType,
                    String releasePath, String releaseGrant, long requiredReleaseBuild,
                    boolean releaseGateRequired, PayloadFile nativeFile, PayloadFile dexFile,
                    DexPayloadLoader.CachedPayload cachedDex, DexModuleStore.CachedModule cachedModule,
                    boolean nativeReady, boolean dexReady) {
            this.endpoint = endpoint;
            this.bundled = bundled;
            this.abi = abi;
            this.build = build;
            this.version = version;
            this.changelog = changelog;
            this.updateType = updateType;
            this.releasePath = releasePath;
            this.releaseGrant = releaseGrant;
            this.requiredReleaseBuild = requiredReleaseBuild;
            this.releaseGateRequired = releaseGateRequired;
            this.nativeFile = nativeFile;
            this.dexFile = dexFile;
            this.cachedDex = cachedDex;
            this.cachedModule = cachedModule;
            this.nativeReady = nativeReady;
            this.dexReady = dexReady;
        }

        boolean isRelease() {
            return UPDATE_TYPE_RELEASE.equals(updateType);
        }

        boolean requiresReleaseRoute() {
            return releaseGateRequired;
        }

        long downloadBytes() {
            long total = 0L;
            if (!nativeReady && nativeFile != null) total += nativeFile.size;
            if (!dexReady && dexFile != null) total += dexFile.size;
            return total;
        }

        String componentDescription() {
            String dexLabel = dexFile != null && dexFile.isModule() ? "Menu DEX" : "Plugin DEX";
            if (nativeFile != null && dexFile != null) return "Native + " + dexLabel;
            return nativeFile != null ? "Native" : dexLabel;
        }
    }

    private static final class FullReleaseInfo {
        final String title;
        final String version;
        final String url;
        final long requiredBelowUpdateBuild;

        FullReleaseInfo(String title, String version, String url, long requiredBelowUpdateBuild) {
            this.title = title;
            this.version = version;
            this.url = url;
            this.requiredBelowUpdateBuild = requiredBelowUpdateBuild;
        }

        boolean shouldShow(String installedGameVersion, long installedUpdateBuild) {
            if (compareVersionLabels(version, installedGameVersion) > 0) return true;
            return requiredBelowUpdateBuild > 0
                    && installedUpdateBuild > 0
                    && installedUpdateBuild < requiredBelowUpdateBuild;
        }

        private static int compareVersionLabels(String left, String right) {
            if (left == null) left = "";
            if (right == null) right = "";
            String[] leftParts = left.split("[^0-9]+");
            String[] rightParts = right.split("[^0-9]+");
            int count = Math.max(leftParts.length, rightParts.length);
            boolean sawNumber = false;
            for (int i = 0; i < count; i++) {
                long leftValue = parsePart(leftParts, i);
                long rightValue = parsePart(rightParts, i);
                sawNumber = sawNumber || leftValue != 0L || rightValue != 0L;
                if (leftValue < rightValue) return -1;
                if (leftValue > rightValue) return 1;
            }
            return sawNumber ? 0 : left.compareToIgnoreCase(right);
        }

        private static long parsePart(String[] parts, int index) {
            if (index < 0 || index >= parts.length || parts[index].length() == 0) return 0L;
            try {
                return Long.parseLong(parts[index]);
            } catch (Throwable ignored) {
                return 0L;
            }
        }
    }

    private static final class ChangelogDisplay {
        final String latestText;
        final String fullText;

        ChangelogDisplay(String latestText, String fullText) {
            this.latestText = latestText;
            this.fullText = fullText;
        }

        static ChangelogDisplay from(String value) {
            String text = value == null || value.length() == 0
                    ? "No changelog was provided for this update." : value;
            int previous = text.indexOf("\n\nPREVIOUS |");
            return previous > 0
                    ? new ChangelogDisplay(text.substring(0, previous), text)
                    : new ChangelogDisplay(text, text);
        }

        boolean hasPrevious() {
            return !latestText.equals(fullText);
        }
    }

    private static final class PayloadFile {
        final String path;
        final String sha256;
        final long size;
        final String entryClass;
        final String mode;

        private PayloadFile(String path, String sha256, long size, String entryClass, String mode) {
            this.path = path;
            this.sha256 = sha256;
            this.size = size;
            this.entryClass = entryClass;
            this.mode = mode;
        }

        static PayloadFile nativeFile(JSONObject json) throws Exception {
            return new PayloadFile(json.getString("path"),
                    normalizeSha(json.getString("sha256")), json.getLong("size"), null, "native");
        }

        static PayloadFile dexFile(JSONObject json) throws Exception {
            return new PayloadFile(json.getString("path"),
                    normalizeSha(json.getString("sha256")), json.getLong("size"),
                    json.optString("entryClass", ""), json.optString("mode", "plugin"));
        }

        boolean isModule() {
            return DexModuleStore.MODE_MODULE.equals(mode);
        }
    }
}
