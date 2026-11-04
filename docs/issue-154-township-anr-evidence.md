# Issue 154: Township ANR after resume

https://github.com/BenigJester/Jester-Mods-Launcher/issues/154

The attached full report is from launcher 4.6.3, Xiaomi 23108RN04Y,
Android 15/API35, Township 39.1.2 (1039120), ARM64, 4096-byte pages,
module 1.1.2 (112). Reported module native SHA-256:
`ac4bbe9daf33c73821b0c0dcca5451b3181109b40c91a2e5dfae070d76303458`.

PID 29296 starts at 1793456948136, resumes GPlayActivity at 1793456954009,
and records menu attachment at 1793456955881. Android records an ANR exit at
1793456998589. A supporting process (29376) has an earlier background ANR at
1793456982120; this does not establish that it caused the game ANR. Main-thread
probes in the available resource samples return promptly, including the last
sample roughly 4.5 seconds before exit. These samples do not explain the
system's timeout. Growing CPU/memory and connection totals alone do not prove
resource exhaustion, a hook failure or a registration leak.

The existing exporter captures native system traces, but for ANRs it records
only exit reason and background/startup/resumed context. This report therefore
lacks the Android thread snapshot needed to investigate waits or blocked calls.
ApplicationExitInfo.getTraceInputStream supports ANR traces on API30+:
https://developer.android.com/reference/android/app/ApplicationExitInfo#getTraceInputStream()

Added bounded ANR trace summaries to the same asynchronous exit reader. Select
only the expected PID's ART trace section, retain the main-thread stack and up
to seven explicitly referenced lock owners, and bound the input to 2 MiB, the
parsed threads to 256, and frames to 24 per thread. Export only numeric thread
IDs, fixed states, Java method identities and native library basenames/offsets.
Raw process headers, paths, thread names, method arguments, native symbols and
intent/account text are omitted. Android's description supplies only fixed
input/broadcast/service/provider timeout categories, or unclassified.

Unavailable, oversized, malformed and mismatched-process traces have explicit
fallbacks. A thread snapshot is not proof of the original cause. Exit evidence
schema advances to 3 so existing schema-2 entries can be refreshed while Android
still retains their traces. Native crash decoding and the reader's existing
size/deadline handling remain intact. This also applies to non-root direct
patch/identity-shell sessions collected by this backend.

Validation: AnrTraceSummaryTest (4), NativeTombstoneSummaryTest (3),
DiagnosticExitDetailsTest (1), and compileNonrootDebugKotlin passed. Fixtures
cover matching PID, main/lock owner selection, cyclic waits, frame/input bounds,
missing/malformed sections and private-data exclusion. These are isolated
parser tests; no device freeze was reproduced. Log: temp/issue154/validation.log.

This is a diagnostic improvement, not a confirmed Township fix. A new launcher
build and fresh affected-device report are required. No game/module behavior,
version, issue state or remote release changed in this step.
