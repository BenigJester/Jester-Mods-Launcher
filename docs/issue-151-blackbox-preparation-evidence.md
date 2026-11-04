# Issue 151: Minecraft before game-process creation

Source: https://github.com/BenigJester/Jester-Mods-Launcher/issues/151

The full report is from launcher 4.6.3, TECNO LI9, Android 15/API35,
Minecraft 1.26.52.3 (972605203), ARM64, 4096-byte pages. It records
launch_requested at 1793328928823 and blackbox_service_ready at
1793328928827, but no guest_install_started or game-process attempt.
The supporting launcher PID 31187 has a user-requested termination at
1793328942933, roughly 14 seconds after launch was requested. There is no
matching game failure, exception or native crash. This does not identify a
Minecraft/module failure, a Google-service failure, or a confirmed hang.

Previously unrecorded operations between service readiness and game import
included retiring old guests, checking stored/running/resumable state,
stopping the target, removing stale module payloads, preparing three Google
compatibility packages, and looking up the host game's ApplicationInfo.

Added diagnostic checkpoints around these operations. Google preparation
records each fixed package's host lookup, guest lookup, install, and safe
install-result category. A checkpoint is written before the operation;
completion is written only after return. Exceptions record a bounded category
and propagate unchanged. The pre-existing best-effort target stop remains
best-effort. Missing host Google packages and unsuccessful install results
retain their previous behavior. No new timeout, retry, thread, package
removal or launch fallback was added.

Only fixed stage names and the existing three Google package constants are
exported. No raw exception messages, APK paths or account details are added.
Existing recording provides timestamps for comparing step duration.

Validation: BlackBoxPreparationDiagnosticsTest (3),
BlackBoxInstallDiagnosticsTest (3), NonRootBlackBoxRuntimeTest (4), and
compileNonrootDebugKotlin passed. New checks execute the production stage
wrapper and verify pre-operation persistence, successful return/completion,
original-exception propagation, private-message exclusion and null-session
behavior. Log: ignored temp/issue151-preparation-check.log.

This is diagnostic coverage, not a confirmed fix for issue 151. A new launcher
build and affected-device report are required to locate the incomplete step.
No device install, issue reply/closure, version bump, push or release in this
step.
