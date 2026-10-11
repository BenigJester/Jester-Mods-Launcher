# BlackBox session diagnostics

## User flow

Every fresh BlackBox game launch starts a structured, device-local session. Reopening an already
running game resumes its original session. Returning to Library after a failure shows a one-time
review prompt; dismissing it does not delete the report. Slow startup is labeled a **possible** stall,
not a confirmed freeze. The game's details page always exposes Diagnostics, with a localized
explanation when no reports exist yet.

Users can copy a compact summary. Share full report shows a public-sharing warning, saves the
complete text file using Android's document picker, then opens a prefilled issue in
BenigJester/Jester-Mods-Launcher. The browser URL contains only a bounded excerpt, not the full
report. Users sign in to GitHub, manually attach the saved file, review and submit the issue.
Nothing is posted automatically and no GitHub credentials are embedded in the launcher.
Share with another app preserves Android's share-sheet export as an alternative.
Clear diagnostics is available in history and report views. Confirmation deletes only that game's
local sessions and (for BlackBox) its stale current-session pointer; it cannot be undone. It does
not stop the game, touch saves, delete exported/shared files or clear Android's system crash buffer.
Deleted session directories are not recreated by late guest events. New launches record again.
For a live guest whose reports were cleared, the next Play/Resume starts a fresh game process and
session; no launcher restart is required. Clearing itself never stops the game.

Resume eligibility uses a live virtual activity/token/binder in an existing host AppTask, not
process presence or Recents visibility. Task reconciliation includes hidden guest AppTasks.
A resume retains the previous session only after a new guest resume callback with the same PID.
An unconfirmed resume gets one fresh-launch fallback in the same press. Fresh launches wait up
to 12 seconds for a real activity callback before reporting startup failure; a slow guest may
still open later, and no optimistic host event is treated as proof that the activity resumed.
If BlackBox's loading screen is stuck, press Home and reopen the launcher. No in-game button or
ADB connection is required. The launcher does not terminate the guest to obtain a report.

## Recording and retention

- Recording starts before waiting for the BlackBox service. Each guest process pins its session
  during process initialization; a late old process cannot write into a new session.
- Checkpoints cover service readiness, package installation, process initialization, application
  initialization, activity transitions, and module initialization/native loading/attachment.
- Metadata includes device/API/ABI/page size, launcher/game/module versions, native module and
  config hashes, host Google package versions, WebView provider version, and Internet permission.
- A startup with no recorded activity resume after 60 seconds is suspicious. Explicit stops and
  game switches are recorded, not treated as freezes. A late successful resume clears suspicion.
- The main guest process's death before activity resume is reported independently of system
  crash-record availability. Auxiliary Google processes are not attributed as game deaths.
- A stalled main guest attempts one bounded Java thread snapshot on a diagnostic daemon thread.
  The host never waits for this snapshot; a completely wedged runtime may provide no stacks.
- Per-process and host event files are capped at 64 KiB. Session history is retained until the user
  selects Clear diagnostics; starting a game never prunes previous reports. Superseded resume
  attempts are hidden. New launches still use isolated files rather than mixing game sessions.
  Total history storage grows until manually cleared.
- Full exports are capped at 512 KiB, summaries at 12,000 characters, and shared cache copies at eight.

No automatic upload, touch/keyboard recording, screenshots, raw logcat, exception messages, native
memory dumps, abort messages, or URL/query collection. Export headers and technical stacks stay in
English for support; all new UI labels support the launcher's nine non-English languages and theme palette.

## Crash coverage and limitations

Java handlers save exception types, causes, and frames before invoking their existing behavior.
Module loading/attachment failures are also recorded. The existing crash policy is not changed;
Play Store policy arming is a separate informational checkpoint.

From Android 11, the real host retrieves its own process exit reasons and correlates them using
recorded PIDs and session timestamps. From Android 12, native system tombstones may be available.
Only signal/code/fault address, crashing TID, registers, relative PCs, library basenames, symbols and
build IDs are decoded from the [AOSP schema](https://android.googlesource.com/platform/system/core/+/refs/heads/main/debuggerd/proto/tombstone.proto).
Android may omit or evict these records. No custom signal handler is installed, so Android's native
crash reporting and termination remain intact. A system exit reader has a 1.5-second wait deadline;
an unresponsive reader cannot build up a queue of repeated requests.

Android 8–10 provide checkpoints and Java failures, but this implementation cannot guarantee
native fault addresses on those versions. ANR exit reasons are recorded; raw system ANR traces are
not exported. A normal activity resume proves only that the activity opened, not that the game's
own loading screen completed.

A native PC shows where the crash occurred, not necessarily the bug's origin. Keep the exact
game libraries and unstripped module ELF/debug symbols for every published build. Match build IDs
and module hashes before symbolication. Individual hook target/trampoline registrations are not
automatically observable from BlackBox; adding per-hook trace entries would require changes to
the individual module's native hook wrappers. The tagged Kairosoft Main.cpp is not modified here.

## Required device validation before release

1. Install a test non-root build and launch a working game. Verify no failure prompt, theme behavior,
   localized labels (including Arabic RTL), copy summary and share-file access in another app.
2. Resume that game. Verify it keeps the same physical-session report instead of falsely timing out.
3. Use a dedicated test guest that stalls before activity resume. Wait over 60 seconds, press Home,
   reopen Library, and verify a possible-stall prompt and checkpoint/thread snapshot where available.
4. Trigger a controlled uncaught Java exception in the test guest. Verify persisted cause/frame data,
   one-time prompting and unchanged crash termination/policy behavior.
5. Trigger a controlled native crash in a test guest on Android 12+. Verify PID attribution, signal,
   fault address and ELF-relative PCs/build IDs; match them against its exact symbols.
6. Start another session and reboot/reopen. Verify failure retention, acknowledgement and isolation.
7. Obtain a report from an actually affected device and compare its final checkpoint with a good launch.

Do not inject deliberate crashes into a user's real game save. No production build, install, push,
version bump or release publication is performed by this implementation task.

## Root flavor: crash-only reports

Root installs a separate collector; no BlackBox checkpoints, startup timeout, thread watchdog,
ANR/stall classification or slow-launch prompt is enabled. Both injection and external-controller
launches start a new game session. Prior sessions and captured crashes are retained until the user
selects Clear diagnostics. Android's system crash policy and signal handlers are not replaced.

While the launcher UI is resumed, root reads a finite snapshot of the crash buffer at most once
every 15 seconds, with a three-second process deadline and concurrent bounded output draining.
No background service is kept alive. Before the next launch it collects the previous session once.
The system crash buffer is never cleared. Only exact game package/subprocess identities within
that session's timestamp window are accepted, and a fatal Java/native record must be present.
Report files contain Java exception types/frames or native signal/fault address, registers,
relative PCs, library basenames/build IDs, and game/device/payload hash metadata. Exception
messages, thread names, abort messages, memory dumps and other apps' logs are not saved/shared.
The themed, nine-language Diagnostics card is always visible, explaining crash-only coverage
when empty. Captured crashes populate history and trigger an acknowledgeable next-launch prompt.
Root does not show successful-session/startup reports.

This uses the standard [AOSP logcat crash buffer and epoch format](https://android.googlesource.com/platform/system/logging/+/refs/heads/main/logcat/logcat.cpp).
Records rotated out of the last 2,000 lines/1 MiB snapshot, cleared by another tool, or suppressed
by an OEM cannot be recovered. Session attribution is by package and time, not a continuous PID
watcher: manually relaunching the same game outside the launcher before the next launcher session
can also put its crash in that window. Native PCs still require the exact game/module symbols.

Before release, test controlled Java and native crashes in a disposable rooted test app, return to
the launcher and verify report persistence/one-time prompts, Copy/Share and light/dark/Arabic UI.
Check a normal exit, force-stop, slow launch, a different app's crash, successive sessions and root
access revocation: none should create false crash/stall prompts. Verify on an actual affected OEM.
