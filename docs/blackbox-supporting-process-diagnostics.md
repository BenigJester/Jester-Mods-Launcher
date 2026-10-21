# BlackBox supporting-process diagnostics

Issue #45 still reproduces on the reporter's Samsung after 4.4.2. Its two new
reports show Android's Binder-limit hint, but do not show a runaway cache or a
recorded connection-boundary failure. Unequal registration and removal counters
alone do not prove a leak. Do not describe this change as a confirmed crash fix.

The launcher automatically samples itself and the BlackBox compatibility service.
When an existing game process requests a shared guest helper, that helper can
also sample itself through its existing, one-way client Binder. Diagnostics do
not start another guest, acquire another provider, or add death recipients.

Supporting files use `support-resources-PID.txt`, not `process-PID.txt`, so they
do not become game attempts. Each retains 24 samples at a 15-second target
interval. Observation ends after 10 minutes, a stop, Clear, or eviction from the
four-session-per-process limit. A later observation can start another window.
Missing samples are unavailable evidence, not zero resource usage. Supporting
activity lifecycle state is deliberately unavailable.

Full reports include supporting samples and available same-UID exit records.
Compact reports include the latest supporting sample per process. Status remains
based on the selected game attempt; supporting exits are context, not evidence
that the game crashed. Shared helpers may serve other games simultaneously.

Fixed counters also cover selected hooked toast, window, alarm and audio callback requests.
Ambiguous `set` and `remove` methods are matched against their declaring Android
interface. No request arguments, toast contents, account data, raw system dumps,
or exception messages are collected. Android build ID and security patch level
help distinguish affected firmware.

## Research and limits

- [Android ActivityManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/am/ActivityManagerService.java)
  handles excessive Binder proxies at UID level. Ordinary app counters cannot
  replace system_server's per-UID count or expose an OEM-specific threshold.
- [Android Binder debug counters](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/jni/android_util_Binder.cpp)
  do not reveal every native Binder allocation. Java hook counters also miss
  native calls and unhooked paths.
- [First-hand App Inventor report](https://community.appinventor.mit.edu/t/my-application-minimizes-itself-on-android-14/117997/28)
  describes the same kill text and a repeated-notifier cause. It is a lead for
  adding measurements, not proof that this launcher has the same defect.
- [Android issue 241667307](https://issuetracker.google.com/issues/241667307)
  reports the same system kill with repeated audio recording callback registration.
  This is another reason to measure callbacks, not a confirmed diagnosis here.

No verified, drop-in BlackBox fix was established by the internet search. The
affected user must test a launcher containing this change and share a new report.
No PC or Samsung device owned by the maintainer is required for this automatic
capture, but system-wide privileged evidence may still be necessary if the
additional samples cannot isolate the cause.
