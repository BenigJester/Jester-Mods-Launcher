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

Do not automatically change DenyList, switch the user's root provider, disable
SELinux, or change injection timing based solely on this report. A new report
from the affected device is still needed to establish the failure stage.
