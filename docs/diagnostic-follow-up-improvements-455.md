# Follow-up evidence improvements for 4.5.5

These changes address missing evidence in #113, #121 and #122. They do not
establish fixes for the reported installation failure, loading hang or every
resource-limit termination. No affected-device test was performed.

## Installation results (#113)

Preserve Android's numeric PackageInstaller status and session ID when supplied,
plus fixed categories in the existing setup event trail. Missing status stays
unavailable instead of being recorded as Android's generic failure code.
Record whether the confirmation screen opened, was absent, or could not open.
An opened confirmation is a nonterminal observation, not a failed installation.
Reconciliation separately records a missing terminal result or success whose
verified package identity did not become visible. Existing prepared-package retry
and installation checks remain unchanged.

`aborted` does not mean the user necessarily pressed Cancel. No new raw status
messages, exception messages, intents, package paths or other-package names are
added to the structured evidence. Existing user-facing Android error text is
unchanged. The new fixed keys are diagnostic-only; no new UI/translation keys.

Contract: https://developer.android.com/reference/android/content/pm/PackageInstaller

## Delayed helper main threads (#121)

At the existing 15-second sampling interval, an outstanding main-thread probe of
at least 30 seconds permits one snapshot attempt per supporting-process observer
window. Capture only the main thread: epoch, pending duration, fixed thread
state, and at most 32 bounded class/method/line frames. No thread names, source
filenames, arguments, locals, account data or memory dumps. Maximum persisted
size is 16 KiB. No repeated capture or automatic freeze classification.

Use the existing bounded observer windows (four sessions, ten minutes each).
Persistence cannot recreate a cleared session. Full reports include up to four
helper snapshot files; shortened reports prioritize up to two excerpts before
resource history, still subject to the existing GitHub URL size limit. Shared
helpers may serve other games. A snapshot is context, not a deadlock diagnosis,
and missing/unavailable Java frames do not prove the absence of a native block.
Include the helper source in diagnostic-bootstrap's generated sampler set so
direct-patch packaging remains complete, although helper capture is not started
for standalone game resource sampling.

## Connection growth (#122)

Keep existing process-lifetime counters. Each resource observer captures its own
immutable baseline, reporting sparse call/failure deltas as
`bbWindowConnectionCalls`. Independent observers do not reset global counters.
These are calls since observation began, not live connections, not necessarily
the exact moment the game launched, and not activity exclusively for one game.

`bbTrackedReceivers` reports only BroadcastManager's current tracked registration
count, updated after successful registration and confirmed/already-gone cleanup.
Partial registration and unexpected cleanup failure retain accurate tracked
counts. It excludes other registration owners, Binder objects and lost references
from older code. -1 means unavailable. No unregister/registration behavior or
system connection limits changed in this diagnostic addition.

## Checks

```
.\gradlew.bat :Bcore:testDebugUnitTest --tests '*HelperThreadSnapshotTest' --tests '*ConnectionDiagnosticsTest' --tests '*SupportProcessRecorderTest' --tests '*ConnectionSitesTest' :app:testNonrootDebugUnitTest --tests '*PackageInstallEvidenceTest' --tests '*LaunchSessionRecorderTest' --tests '*BroadcastManagerLifecycleTest' --tests '*SupportingProcessDiagnosticsTest' --tests '*DiagnosticIssueLinkTest' :app:testRootDebugUnitTest --tests '*PackageInstallEvidenceTest' --tests '*RootInjectionDiagnosticsTest' --console=plain
```

45 tests passed: 12 Bcore, 19 Non-root, 14 Root; none skipped. Includes category
mapping, observer independence, receiver count matching through 1000 refreshes
and partial failures, snapshot thresholds/privacy/size/Clear behavior, report
and confirmation wiring, bounded issue links, and earlier Root diagnostic work.
Receiver/report wiring checks are source-level, not Android installer callback
simulation. Broadcast lifecycle checks execute production Java against isolated
platform fakes. No live Binder counts or Android helper hang were simulated.

No launcher APK build/install, release, Git push, account/save changes or device
settings changes. Version 4.5.5 (455) and earlier Root timeout changes remain
local alongside this work; unrelated module changes were preserved.
