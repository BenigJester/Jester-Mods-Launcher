# Cached access clock hardening

Offline launcher access and private approval retain a fixed checkpoint from the
last successful online verification: signed server issue time, elapsed realtime,
and Android boot count. Failed requests and cached reads do not move it forward.
Elapsed time enforces expiry even inside the five-minute wall-clock tolerance.

A changed, missing, or unreadable boot count, missing checkpoint, or reset uptime
requires online verification before using cached access. This includes older
caches after upgrading. Online checks can refresh a valid grant; a failed request
cannot authorize an unverified cache. Private approval is checked again after a
failed request so expiry during the request is not overlooked.

Scope: normal clock changes and reboots on the unmodified launcher. This is not
protection against rooted storage tampering or a modified APK. Already-running
games remain outside the new-session access policy. No server change is needed.

Checks: Root and Non-root unit tests for `LauncherOfflineLeaseVerifierTest`,
`LauncherPrivateLeaseVerifierTest`, and `NewSessionAccessPolicyTest`. No live
device-clock modification or APK installation was performed.

Android references:
- https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime()
- https://developer.android.com/reference/android/provider/Settings.Global#BOOT_COUNT
