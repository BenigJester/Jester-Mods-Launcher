# Issue 139: reconcile late Android installation success

https://github.com/BenigJester/Jester-Mods-Launcher/issues/139

Soul Knight direct-patch setup, Non-root launcher 4.6.2, Android API 33,
Google support disabled. The report records pending user action for installer
session 679016389, then a local result-not-observed timeout and failed setup,
followed by Android status 0 (success) for the same session. This establishes
an ordering problem, not proof the game itself launches successfully.

Source cause: finishPackageReplacementWithFailure retains the prepared request
for retry but previously reset its phase to IDLE. A subsequent successful result
is accepted by the existing package-name check and appended to diagnostics, then
schedulePackageReplacementReconciliation immediately returns because it only
accepts WAITING_FOR_INSTALL_RESULT. The UI therefore keeps its failed state.

The local observation timeout now retains a distinct
WAITING_FOR_LATE_INSTALL_RESULT phase. A successful callback transitions that
phase back to WAITING_FOR_INSTALL_RESULT and uses the existing bounded installed
identity check. Only verified identity completes setup, clears the stale UI
failure/retry state, clears the recovery record, and refreshes the Library.

Explicit installation failures, preparation errors, and user cancellation still
use IDLE and are not revived. No automatic reinstall, uninstall, new download,
deadline increase, signature relaxation, data clearing or game launch is added.
The existing package-name callback filter and exact patch/shell checks remain.
The shared flow handles both Direct Patch and Identity Shell; Root's unsupported
replacement path is unchanged.

Validation: PackageReplacementLateResultTest checks every phase transition and
the timeout/callback/identity-verification/clear-state wiring. Existing
PackageInstallEvidenceTest and PackageReplacementRecoveryStoreTest also pass.
Non-root debug tests and Root debug Kotlin compilation pass; see ignored
temp/issue139-late-result-check.log. No APK was installed or published.
These isolated checks do not reproduce OEM installer timing on a device.

Scope: this recovers a callback delivered while the same request remains pending
in the Activity. Callback persistence across process death, session correlation,
early callbacks during commit, and the separate companion installer are not
changed. The fix does not claim to resolve all installation failures.

Android contract reference:
https://developer.android.com/reference/android/content/pm/PackageInstaller.Session#commit(android.content.IntentSender)
uses an asynchronous status receiver; returning to the launcher is not the
terminal installation result.

No GitHub reply, commit, push, release or version bump is performed in this step.
