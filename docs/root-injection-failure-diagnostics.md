# Root injection failures (#49)

Opening the game is only the first part of a Root injection launch. The bundled
injector deliberately stops the target if injection fails. That closure need not
produce an Android crash record, so the crash-only report reader previously
showed nothing while the launcher displayed a generic launch error.

The launcher now pins the diagnostic session returned immediately before the
injection command and saves an explicit failure in that existing session. Clear
does not recreate it. Crash evidence, when available, remains a separate section.
No new BlackBox-style stall detector is added to Root mode.

Reports contain fixed failure-stage categories, exit code, timeout status, a
bounded numeric root version, identified root-provider family, and boolean hints
for architecture mismatch, permission rejection, and target cleanup. They do not
retain arbitrary injector output, paths, root module lists, or exception messages.
Unknown output stays unclassified rather than being guessed as a Magisk defect.

Root commands now drain stdout/stderr concurrently with their deadline, retaining
at most 128 KiB while continuing to drain. Previously a sufficiently verbose
command could fill its pipe while the launcher waited for it to exit. The fix
removes that risk; it does not establish that it caused this user's failure.

## Internet research

- [Magisk's own su implementation](https://github.com/topjohnwu/Magisk/blob/master/native/src/core/su/su.cpp)
  supports selectable mount namespaces and SELinux execution contexts. The name
  of the root provider alone cannot identify which injection step failed.
- [AndKittyInjector's upstream documentation](https://github.com/MJx0/AndKittyInjector)
  describes a ptrace-based injector with launch, breakpoint, and memfd modes;
  it does not establish a blanket Magisk incompatibility.
- [NeoZygisk documentation](https://github.com/JingMatrix/NeoZygisk#the-denylist-explained)
  explains per-app mount visibility and its own DenyList behavior. This applies
  to that optional module, not proof the reporter uses it or that plain Magisk's
  DenyList blocks this injector.

Do not automatically change DenyList, switch the user's root provider, or disable
SELinux based solely on this report.

## 4.5.1 follow-up: confirmed failure stage, unconfirmed device resolution

The reporter's 4.4.5 report established `breakpoint_failed`, exit 1, no command
timeout and target cleanup. Attach, injector initialization and ELF validation
had already passed. The root bootstrap and module had not yet been injected.
`Command timed out: false` does not exclude the injector's own five-second
breakpoint timeout. The old summary discarded that distinction; 4.5.0 uses the
same breakpoint logic and cannot reconstruct it from this saved report.

Our native `--bp-ld` path watched only `dlopen`. Current upstream
[waitBreakpoint](https://github.com/MJx0/AndKittyInjector/blob/main/AndKittyInjector/src/Injector/KittyInjector.cpp)
watches both `dlopen` and `android_dlopen_ext` concurrently. 4.5.1 adopts that
focused change without importing unrelated upstream injection changes or
relaxing the existing forbidden-library/return-path checks. Identical addresses
are deduplicated; custom-symbol and native-bridge targets remain single-target.

All installed hardware slots are cleared before injection or callback stepping;
partial setup is cleaned up and failure stays fail-closed. A shared monotonic
deadline covers repeated ignored hits/signals, rather than restarting five
seconds on each wait. No software-breakpoint or no-breakpoint retry is added.

Diagnostics retain only an allowlisted breakpoint result and a bounded numeric
setup errno, not arbitrary command output, paths, addresses or library names.
Results distinguish setup rejection, timeout, target exit, wait/register/step/
continue/stop failure and cleanup failure. Existing reports remain readable.
These are machine-readable fields, not new UI labels or translated strings.

Verification requires rebuilding **both bundled injector assets** (arm64-v8a and
x86_64), not just Kotlin. Run the RootInjectionDiagnosticsTest tests and compile
both launch editions. Native compilation and local regression checks do not
prove this fixes the reporter's Realme RMX3286/crDroid Android 16/Magisk 31.0.
An affected-device retry is still needed; do not label #49 resolved yet.

Native rebuild (NDK 29.0.13846066, from the repository root):

```powershell
& "$env:ANDROID_HOME/ndk/29.0.13846066/ndk-build.cmd" -C third_party/AndKittyInjector/AndKittyInjector NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk NDK_APPLICATION_MK=Application.mk 'APP_ABI=arm64-v8a x86_64' NDK_OUT=../../../temp/root-451-native/obj NDK_LIBS_OUT=../../../temp/root-451-native/libs -j4
```

Copy each generated `temp/root-451-native/libs/<abi>/AndKittyInjector` to the
matching `app/src/root/assets/root-runtime/<abi>/moodtools-injector` before
building the Root APK. The regression test rejects old bundled assets.

## Follow-up after the reporter tested 4.5.1

Report `1792638502598-8d2c8f32-a646-428a-811d-b61cee4d40cb` confirms
`Breakpoint result: timeout`, exit 1, no outer command timeout, and target
cleanup on the same Realme/Android 16/Magisk setup. The two-loader-entry change
did not resolve this device's launch failure. Successful breakpoint setup is
not evidence that a matching load occurred on the traced main thread.

Upstream [AndKittyInjector d39b458](https://github.com/MJx0/AndKittyInjector/blob/d39b4584cc/AndKittyInjector/src/main.cpp)
removes the process-wide SIGSTOP/SIGCONT sequence from `inject`: its comment
identifies a sibling holding the loader mutex as a deadlock risk. We adopt only
that stop-policy change, retaining seize/interrupt of the main thread, the
attach fallback, both loader breakpoints, forbidden-path checks, five-second
deadline and failure cleanup. Detach resumes the traced thread; no process-wide
SIGCONT is needed because this path no longer issues a process-wide SIGSTOP.

Do not claim the old code definitely left all siblings frozen: Linux
[ptrace documentation](https://man7.org/linux/man-pages/man2/ptrace.2.html)
states SIGCONT wakes all threads of a group-stopped process, including when
thread-directed. The old "resume main thread only" comment was misleading.
Removing this stop/continue sequence follows upstream and avoids unnecessary
group-stop races, but does not prove this was the reporter's exact timeout cause.
Loading on another thread, missed startup timing, or rejected breakpoint hits
remain possibilities. No blind injection retry or longer timeout is introduced.

New privacy-safe report fields identify `Thread pause mode: main_thread_only`
and bounded numeric `Breakpoint hits`/`Breakpoint skipped` counts. These count
validated callback invocations and existing safety-filter rejections, not every
signal or system-wide load. A zero-hit timeout is now distinguishable from
repeated rejected hits. No thread names, paths, registers, accounts or raw logs
are added. Older reports show unavailable fields rather than guessed values.

Build/test logs: `temp/root49-thread-pause-native.log` and
`temp/root49-thread-pause-check.log`. Both native assets must be copied from the
rebuild above. Host unit tests cover report filtering, source-policy guards and
stale-asset detection; these are not a live native thread/loader test. No game
was installed or started, and affected-device confirmation remains required.
Version remains 4.5.1 until an explicit next-version request.

## Follow-up after 4.5.2: observe the zero-hit timeout

Issue #49's newest report still shows a timeout with zero accepted or rejected
breakpoint callbacks on the Realme RMX3286, crDroid Android 16, Magisk 31.0.
A matched local baseline on an Infinix X6873, Android 16, APatch 11224 uses
launcher 4.5.2 and The Wild Darkness 1.4.31 (1431). Its loaded module SHA-256
matches the report (`f30e25bfb5f2e34d55d4c1797e8e7bcdd8edc173e26d6fefc518fcad412a164e`).
The same main process remained alive and foreground with that module mapped.
This excludes Android 16 alone; it does not isolate Magisk from kernel, ROM,
startup timing or other device differences. No gameplay or sign-in claim follows.

New failure-report fields retain the numeric kernel release, seize versus attach,
installed hardware-slot count and observed wait duration. At either deadline
exit, a bounded read of the traced thread's proc status and wait channel occurs
**before** the existing cleanup stop. Reports keep only fixed state and wait
categories and a bounded thread count. This avoids labeling the injector's own
cleanup stop as the original waiting state. Unknown or hidden wait symbols are
unavailable; unrecognized symbols become `other`, not arbitrary text.

The [kernel proc documentation](https://docs.kernel.org/filesystems/proc.html)
describes status fields and the wait channel. These are separate, non-atomic
observations, not a userspace call stack, loader-lock owner, deadlock diagnosis,
or proof that no other thread loaded libraries. No names, raw kernel symbols,
addresses, file paths, account data or raw logs enter the shared report.
Old injectors produce unavailable fields. Capture is best-effort and cannot
replace existing stop, slot-clear and detach cleanup. The wait deadline,
breakpoint targets, callback filters and failure policy are unchanged.

Both bundled assets were rebuilt with NDK 29.0.13846066 using the command above
and output directories `temp/root49-wait-native/{obj,libs}`. The standalone
`third_party/AndKittyInjector/tests/RootWaitDiagnosticsTest.cpp` exercises bounded
parsing, missing files, a live self proc read, and two forked child traces: one
unused breakpoint timeout and one successful breakpoint. It never attaches to
a game. On the connected APatch device both native cases passed; the timeout
sample was sleeping, channel other, one thread, about 251 ms for a 250 ms test
deadline. The temporary device fixture is removed afterward.

Native checks do not reproduce the affected Magisk phone. A newly built Root
launcher and a fresh failed launch report from that user are still required.
There is no retry without a breakpoint, root-setting change, game/module edit,
save clearing or version bump. Logs remain local under ignored
`temp/root49-wait-*.log`; do not publish device logs with source changes.
