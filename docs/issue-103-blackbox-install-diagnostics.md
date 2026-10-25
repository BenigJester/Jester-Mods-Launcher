# Issue 103: BlackBox game installation evidence

The attached report identifies 1945 Air Force (`com.os.airforce`) 15.84 (1584),
module 1.0.9 (109), launcher 4.5.3 (453), ITEL P671L / Android 14, arm64-v8a.
The local nonroot release mapping maps `t4.h4.i:1031` to the failed install-result
check in `NonRootBlackBoxRuntime.launch`, before `guest_package_installed`.
No guest main-process attempt or module startup was recorded. The report does
not establish a game/module crash or the underlying installation cause.

The launch path now records `guest_install_started`, `guest_install_result`,
and, if the call throws, `guest_install_exception`. Both normal host cloning and
private-native installation use the same evidence boundary. Existing timeline
rendering includes these events in full reports and recent startup summaries.

Reuses `InstallFailureDiagnostics`: only fixed result categories and exception
kinds are exported, plus install mode and bounded split count. No raw installer
messages, file paths, package labels, account content or new logcat capture.
Unknown results remain `unclassified`; no retry or save-data changes are added.

Validation: `:app:testNonrootDebugUnitTest --tests '*BlackBoxInstallDiagnosticsTest'`.
This is a diagnostics improvement, not a confirmed compatibility fix. The
reporter must retry a build containing it and share fresh diagnostics to identify
the underlying failure. Existing reports cannot recover omitted error details.
