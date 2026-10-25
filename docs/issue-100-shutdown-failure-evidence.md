# Issue 100: preserve and classify shutdown failures

Hungry Shark Evolution 14.6.0 (10760), BlackBox, launcher 4.5.2, Xiaomi Android 15.
The attached report records IllegalArgumentException in
ConnectivityManager.unregisterNetworkCallback during UnityPlayer.destroy and
Activity destruction. This establishes a cleanup failure, not why the Activity
started closing. Signal 9 afterward does not establish a native hook crash.

This is a diagnostic change, not a confirmed fix for the game's closing.

- BlackBox already kept its first exception but discarded subsequent ones;
  the direct-patch/identity-shell bootstrap overwrote earlier exceptions.
- Both now use one shared writer preserving the first observed exception and
  at most two distinct later captures in the existing per-process failure file.
- Framework destruction frames label `activity_teardown`. An
  IllegalArgumentException with unregisterNetworkCallback in the same cause
  chain additionally labels `network_callback_cleanup`. Neither label claims
  an original cause, successful cleanup, or a caller error message.
- A short overview of each capture is included before stack frames in the
  selected-attempt summary, so a later cleanup error remains visible even when
  the first stack is long. Legacy reports remain supported.
- Existing exception handlers still receive the original exception. No callback
  failure is swallowed, and no game/module code is changed.

Storage is limited to 64 KiB per process with bounded captures and frames.
Exception messages, filenames in frames, callback arguments and network content
are not added. Cleared session directories are not recreated. The shared writer
is copied into the diagnostic bootstrap by the existing generation task.

Verification: BlackBox JVM tests cover first/later ordering, deduplication,
capture/byte limits, privacy, nested and cyclic causes, cleanup classification,
legacy files and cleared directories. Launcher tests cover bounded fixed-category
overviews; bootstrap tests and both launcher compilation tasks verify integration.
No game install or device test is claimed. Existing direct-patched games need an
in-place patched-game update to embed the revised diagnostic bootstrap; merely
updating the launcher does not replace code inside an already installed game.

Check logs: `temp/issue100-final-check.log`,
`temp/issue100-nonroot-targeted-check.log`, and
`temp/issue100-root-shared-check.log` (local only). The full non-root run completed
377 tests with one failure and one skip: the failure is the pre-existing,
untracked module migration test's expected non-root methods, outside this change.
That test and the unrelated module changes were left untouched.
