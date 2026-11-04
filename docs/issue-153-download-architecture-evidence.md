# Issue 153: include architecture evidence before add-on rejection

Soul Knight add-on download on Non-root 4.6.3/API 34 fails with
"Add-on architecture does not support the installed game". The attached full
transfer report contains no detected game ABI, device ABI list or required
add-on ABI list. Phone model, game version/source and a follow-up remain absent.
https://github.com/BenigJester/Jester-Mods-Launcher/issues/153

The shared applyUpdate path now records architecture evidence before either
device/game compatibility check, through the existing diagnostic callback or
single-transfer UI event trail. It covers download, repair, update and queue
callers. Evidence includes detected game ABI and 32/64-bit label, required module
ABIs, device ABIs, a fixed result category and the recorded game-support flag.
The last flag can expose disagreement with cached module compatibility.

Categories distinguish game_abi_unknown, game_abi_mismatch,
device_abi_mismatch and compatible. Only four known ABI tokens plus unknown/none
are emitted; arbitrary metadata and paths are not exported. The existing scanner
and all guards remain unchanged. This reports the scanner's conclusion, not an
independent verification of the installed process bitness. An unknown result is
not called 32-bit. It does not identify why APK inspection was unavailable.

ArchitectureLabelsTest passes five tests, including mismatch/unknown/compatible
cases, privacy/length bounds and recording before rejection. Non-root unit tests
and Root Kotlin compilation pass. Log: ignored
temp/issue153-architecture-evidence-check.log. No device or reporter APK tested.

No new UI strings or translation changes, reinstall, compatibility relaxation,
version bump, build publication, commit or push in this step. A future launcher
build is needed for users to supply the additional evidence; #153 is not fixed.
