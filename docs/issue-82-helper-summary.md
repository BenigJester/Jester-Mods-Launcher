# Issue 82: keep the busiest helper visible in shared diagnostics

The Mini Militia 5.6.0/launcher 4.5.1 report records a resource-limit exit with
`binder_limit_reported`. The game samples are relatively steady. One shared
helper has 4331 local Binder objects, but its connection counters were cut off
in the GitHub issue. This does not establish a leak, the responsible helper
package, or the cause of the exit.

The summary previously printed three long game samples before one sample per
helper, in filesystem order. GitHub's encoded URL budget could remove the busiest
helper while retaining lower-count launcher and service samples.

The new summary selects the latest timestamped sample per helper, ranks those
samples by their larger local/proxy Binder count, and uses deterministic tie
breaks. Missing, malformed, overflowing or negative-unavailable counters do not
outrank available counts. This is a triage ordering, not a system_server per-UID
count, an OEM limit, peak-usage analysis or an assignment of blame.

For a selected attempt with an explicit `binder_limit_reported` failure hint,
the highest-ranked helper's existing complete row appears immediately after
metadata, before long failure excerpts or repeated game samples. Remaining
helper rows stay in the later section without duplicating the first row. Other
failure types keep their original failure-first ordering. Existing per-attempt
time filtering and the eight-file capture bound remain unchanged.

GitHub shortening still removes stack frames first, then trims the tail at line
boundaries. A single oversized line is omitted rather than submitted partially.
No URL limit is increased. Full report files are unchanged; no additional data,
package names, Binder contents, account data or raw logs are collected.

Tests cover out-of-order samples, unknown/overflowing values, ordering and
deduplication, retention of a full high-count row through URL shortening, and
existing link/stack behavior. Log: `temp/issue82-summary-check.log`.
No game crash fix, device installation, version bump or server change is included.
