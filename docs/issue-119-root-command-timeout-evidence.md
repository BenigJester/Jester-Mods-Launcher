# Issue 119: Root command timeout evidence

## Report boundary

SAS4 (`com.ninjakiwi.sasza4`, 2.2.2 / 1009308), launcher 4.5.4, Xiaomi
25102PCBEG/API 36/arm64-v8a, KernelSU 3.4.2. Reported module payload SHA-256:
`0187962cf7140f63bc2e1456682ae950fd5a9a5243ef2ca11a65f8352854ca0f`.
The shell command timed out with no exit code; native failure stage, breakpoint,
kernel and timeout snapshots are unavailable. This does not establish an injector
breakpoint timeout, a game crash, or a KernelSU defect. No matching reporter APK
or affected-device capture was acquired.

## Demonstrated capture gap and change

`runRootCommand` previously drained all output but retained only the first
128 KiB. Progress after that prefix was irretrievably discarded. Keep a bounded
64 KiB prefix and latest tail instead, inserting a newline at the cut so separate
fragments cannot form one diagnostic field. Retained output remains at most
128 KiB. Report whether truncation occurred and whether the output reader
completed; a child retaining the pipe or a read error cannot masquerade as a
complete capture. There is no raw command output in the exported summary.

The injection command emits one fixed `root_shell_ready` marker before invoking
the injector. Existing native progress messages supply a last-observed phase:
injector started, waiting for process, attached, initializing, initialized,
waiting for breakpoint, breakpoint reached, injecting library, detached, completed.
The report stores only fixed categories, never the paths/package text appearing
in those log lines. A missing marker remains unavailable, not a diagnosis of
denied root permission. A captured phase proves progress only, not the location
or cause of the hang. Existing error fields remain independently reported.

The command deadline, exit-code handling, injection settings and game cleanup
behavior are unchanged. No native injector/assets rebuilt, force-stop added,
device/root/security settings changed or game data touched. No new UI strings or
translations. This repairs an evidence-retention bug; it is not a confirmed fix
for the reported timeout. A fresh affected-device report is still needed.

## Verification

```
.\gradlew.bat :app:testRootDebugUnitTest --tests '*RootInjectionDiagnosticsTest' --tests '*RootInjectorArchitectureTest' --console=plain
```

13 tests pass, none skipped. A real isolated JVM child writes the initial shell
marker, more than 128 KiB of noise, and a late breakpoint-wait message, then
remains alive until the test timeout. Both ends survive while the command still
reports timeout with no fabricated exit code. Existing successful/nonzero exit,
large-output draining, summary privacy, fixed-field parsing and bundled injector
checks also pass. A fixture newline was made explicit for Android-style LF
instead of the Windows host's CRLF. No launcher APK build/install or Android
game launch was performed; KernelSU behavior on the reporter's phone is untested.
