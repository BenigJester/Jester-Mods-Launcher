# Issue 122: bounded package broadcast registrations

Reported Shadow Fight 2: `com.nekki.shadowfight` 2.46.0 (30000121), launcher
4.5.4, Samsung SM-S921U1 / Android 16, arm64-v8a, 4096-byte pages. The supplied
full report records exit reason 9 and `binder_limit_reported` for the game and
supporting processes. A compatibility helper samples 6198 Binder proxies and
376 receiver registrations without a recorded unregister. Samples are not the
system server's per-UID allocation count and do not identify the leaking owner.

Source defect found independently: `BroadcastManager.onPackageInstalled` removed
the package's receiver list without calling Android `unregisterReceiver` first.
The following registration created another receiver for every manifest filter,
leaving the previous ones registered but no longer tracked. Normal BlackBox
launch preparation refreshes the host Google compatibility packages and game,
so this path can run repeatedly within one server process.

Fix: replacement registration first releases the previous package's receivers
under the existing map lock. Full uninstall uses the same cleanup. Registrations
are forgotten only after successful release or Android's already-unregistered
exception; other failures stay tracked and prevent duplicate registration until
cleanup can be retried. Partial successful registrations remain tracked for the
next retry. Other packages and per-user partial uninstall behavior are preserved.

Validation: `BroadcastManagerLifecycleTest` compiles and executes the production
Java manager against isolated Android/package-manager fakes. It checks 1000
refreshes, changed filters, other packages, partial installs, failed cleanup and
retry, repeated uninstall, missing package settings and both registration API
branches. Existing `BinderDelegateCleanupTest` checks also pass: four tests total.
Android Binder resource ownership and real game launch behavior are not simulated.

This removes a concrete leak, not proof that all causes of issue 122 are fixed.
Affected-device confirmation remains required. No APK installation, account,
save-data or device-setting changes were performed. Restart the launcher after
installing an updated build: registrations lost by an older running process
cannot be reconstructed from its overwritten lists.

Platform contract: https://developer.android.com/reference/android/content/Context#unregisterReceiver(android.content.BroadcastReceiver)
