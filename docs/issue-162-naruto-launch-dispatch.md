# Issue 162: Naruto Senki launch timeout

Full report: launcher 4.6.4, OPPO CPH1803, Android 8.1/API27,
Naruto Senki V2 2.1.6-fix (12), ARM64. Guest import succeeds, then
game_activity_launch_requested at 1793624995213 is followed by
launch_confirmation_timeout at 1793625024508. No main-process attempt is
recorded. The timeout does not establish a game crash or a slow game load.

The retained exact-version APK declares minSdk21, targetSdk34, launcher
re.naruto.game.AppActivity, and both ARM64/ARMv7 native libraries. These metadata
checks alone do not verify actual Android 8.1 runtime compatibility.

BActivityManager.startActivity catches remote/runtime errors and logs them only
to logcat; its void caller then waits for resume regardless. Added bounded
session events before/after the remote dispatch, on missing/dead service or
remote/runtime failure, and when dispatch does not complete. Runtime exceptions
use the existing fixed InstallFailureDiagnostics classifier. Successful dispatch
only means the service call returned, not that Android created a game process.

No exception messages, intent extras/URIs, component identities or file paths
are exported. Recording failures are isolated. Existing retries, service-cache
clearing, timeouts and automatic-relaunch policy are unchanged.

Non-root compilation and BlackBoxLaunchReplayTest pass. Log:
temp/issue162-check.log. No affected-device reproduction. A fresh report is
needed to distinguish a dispatch failure from a later proxy/process startup
failure; the original cause remains unresolved. No version bump, push or release.
