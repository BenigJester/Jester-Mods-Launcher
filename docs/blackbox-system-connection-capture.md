# Optional system-side connection check

For issue #45: the 4.4.2 reports confirm an Android Binder-limit termination, but
tracked game-side calls have no recorded failures and no clear runaway growth.
This is not evidence that every native connection or other launcher process is healthy.

The existing `Debug.getBinderLocalObjectCount()` counts Java Binder objects, not all
native local Binder objects. Android's kill threshold is a separate system-server
per-UID count, covering all processes belonging to the launcher. A normal app cannot
query that system-wide view without privileged diagnostic access.

Sources: [Android Java/native counters](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/jni/android_util_Binder.cpp),
[Android 16 dump implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/services/core/java/com/android/server/am/ActivityManagerService.java).

## Run on the affected phone

This requires a Windows computer with Android Platform Tools, an authorized USB-debugging
connection, and the installed Non-root launcher. Root is not required. Do not uninstall
anything, clear game data, change Android limits, or enable debugging on an untrusted computer.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\collect-blackbox-connections.ps1
```

Once prompted, open the affected game from Jester Mods and play normally until it
closes. The check runs for four minutes by default, saving a unique `.jsonl` file
in a new temporary folder. Share that file **with** the launcher's normal full report.
No launcher update is needed. For multiple phones use `-Serial DEVICE_ID`; for a
debug launcher use `-PackageName com.moodtools.hub.nonroot.debug`. Other Android
profiles can be selected explicitly with `-UserId`.

Only the launcher's UID count, fixed process roles/PIDs, and a small allowlist of
system interface counts are saved. Raw dumps, unrelated app/process names, command
errors, arguments, account data and raw logcat are not saved. Interface counts are
**system-wide context**, not attributed to the launcher. Correlation alone is not
proof of a leak in a particular game or service. Missing/denied/OEM-unsupported
information is unavailable, not zero. Each command has a timeout; sampling is bounded.

The helper does not change phone settings, launch/stop apps, force garbage collection,
or alter game data. The UID-specific form of `binder-proxies` is deliberately avoided
because Android runs garbage collection there, potentially changing the reproduction.
This is an investigation aid, **not a crash fix**. No affected Samsung was connected
while preparing it, so real-device capture is still required.

Parser checks: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_blackbox_connections.ps1`.
The execution-policy option applies only to this PowerShell process; it does not
change the saved Windows policy.
