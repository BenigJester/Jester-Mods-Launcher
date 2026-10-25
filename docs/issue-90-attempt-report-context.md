# Selected-attempt report context

Issue #90's Soul Knight report included an older low-memory exit alongside a
newer attempt whose Android exit reason was 13 (other system termination).
The old exit does not establish why the newer attempt ended.

Full non-root reports now put the selected PID/time-window timeline and its
existing matching failure/exit files first. Other session events and exit files
remain available under an explicit history heading. Shared host events, retained
per-PID failure files, and session thread snapshots carry context caveats.
The short report uses the same timeline selector.

Legacy and imported Patch/Identity shell exit records without a category receive
the existing fixed Android reason label at report rendering time. Stored evidence
is unchanged; no raw system descriptions or new private information are collected.
Unknown timestamps are not attributed to a selected attempt, and PID reuse respects
the next attempt boundary. Session-level reports remain explicitly session-level.

This improves report interpretation, not game compatibility. Status/warning rules
are unchanged; reason 13 is not treated as proof of a crash, low memory, or an OEM
battery restriction. No automatic clearing was added.

Regression checks: `DiagnosticProcessAttemptTest` covers historical/other-process
events, reused PIDs, missing timestamps, unchanged reason-13 status, and fixed,
idempotent labels for legacy exit evidence.
