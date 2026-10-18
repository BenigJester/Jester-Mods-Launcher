# Shared non-root diagnostics bootstrap

The launcher injects the release DEX into regenerated direct-patch APKs. Identity shells
compile the same sources into their template. No per-game module rebuild is required.
Existing patches must be reapplied (patch schema 4); existing shells must be updated (protocol 38).

Reports reuse the launcher's history, acknowledgement, Clear and sharing UI. Collection is
per process: startup/lifecycle events, exception classes/frames and the existing bounded
15-second resource sampler. Exit history is queried inside the owning app on Android 11+;
available Android 12+ native tombstones are reduced by the launcher to its existing safe summary.
No raw native trace is retained in launcher storage. Activity resume does not prove gameplay
readiness, and a SIGKILL or missing exit trace is not proof of a module crash.

The exported provider is isolated in `:jester_diagnostics`. Patch factories return a plain
Application there; shell Applications skip BlackBox initialization. Requests require BOTH
the exact launcher package's UID and its embedded signing-certificate SHA-256. Report paths,
file counts and bytes are bounded. Calls cannot start an Activity. Launcher begin/import waits
are bounded and stale jobs are not repeatedly queued. Clear records a cutoff so late snapshots
cannot recreate cleared history, then removes remote diagnostic-only files on the next sync.
Snapshots retain the latest 16 remote sessions per sync; older sessions are not auto-deleted.

`blackbox/jester-diagnostics` lives inside the owning app's sandbox and intentionally bypasses
BlackBox guest IO redirection. Only setup/report data is cleared, never game saves or modules.
The Java handler chains to the previous handler; there are no new native signal handlers.

Checks: `:diagnostic-bootstrap:testDebugUnitTest`, `:app:testNonrootDebugUnitTest`.
Debug builds include a deliberately failing gameplay Application: reading the provider must
not instantiate it. This fixture Application is absent from release DEX assets.
