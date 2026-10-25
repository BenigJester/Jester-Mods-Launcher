# Issue 93: sampled connection callers

1945 Air Force on launcher 4.5.2 / Realme Android 16 records a system Binder
limit termination. The busiest sampled helper has binderLocal=4691 with
bind=13212 and unbind=13180. Neither those roughly balanced call counts nor the
local Binder sample identifies which callers create retained system objects.
This is additional diagnostic evidence, not a confirmed leak fix.

The existing tracked-proxy boundary now samples the current Java stack on calls
1, 2, 4, then each multiple of 256 per operation. A process retains only its latest
16 samples, up to three non-plumbing code frames each, scanning at most 64 frames.
Only code class/method identities, line numbers, epoch timestamps, operation
categories and operation-wide cumulative counts are written. Arguments, return
values, callback objects, thread names, file paths, exception messages and lambda
runtime addresses are not collected. Identity strings have ASCII/length limits.
Sampling exceptions cannot change the underlying service result or exception.
The proxy passes its runtime class identity so renamed hook frames in release
builds do not consume the three caller slots merely because R8 renamed them.

The existing 15-second resource worker writes a separate, bounded 16-KiB snapshot
beside resources, for both game and supporting processes. No new threads, guest
launches, allocation hooks, Binder introspection or per-call disk I/O are added.
The normal rolling counters and their meaning are unchanged. Cleared directories
are not recreated. Export/import accepts the new own-process connection file.

Full reports include up to eight connection snapshots. For an explicitly reported
Binder-limit exit, short reports also include the last two samples from the helper
with the highest latest local/proxy Binder count. Small `sampledCaller` entries
survive exception-stack compaction. Samples may precede the selected attempt and
helpers can serve other games; labels explicitly retain that context.

Limitations: these are sampled callsites, not per-caller frequencies, live
connection balances, outstanding registration ownership, Binder allocation stacks
or system_server's per-UID totals. Native calls, hooks that catch their own failures
and uninstrumented processes remain outside the boundary. A sampled caller is not
automatically the cause of the limit. No game/module or connection behavior changes.

Checks cover sparse sampling, real proxy behavior, filtering/size caps, ring
retention, absent callers, safe persistence/clear, resource-counter compatibility,
and short-report preservation. Device confirmation still requires a new report
from the affected user. No installation or release is performed in this step.

Local checks: 26 BlackBox tests, 3 bootstrap tests, 31 focused non-root diagnostic
tests and 267 Root tests passed. Both launcher variants compile. Logs are under
ignored `temp/issue93-*-check.log`; the full non-root migration suite was not
rerun because its unrelated existing failure is documented in the #100 handoff.
