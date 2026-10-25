# Issue 67: download challenge rejected before transfer

The 4.5.1 report and duplicate attachments stop at zero bytes with
`The launcher proof challenge is invalid`. This is a client check, not APKPure
metadata or a module startup failure. Its previous single message combined
version, key binding, nonce format and expiry validation. Phone clock skew is
a plausible cause, not established by that report.

## Change

- Each parsed API response can carry an in-memory clock anchor from that same
  HTTPS response's `Date` header and the request's elapsed-realtime start.
  It is an internal JSONObject subclass; timing values are not JSON fields,
  persisted preferences, log entries or shared state across requests.
- Validation uses server time plus monotonic elapsed duration. Conservatively
  adding the full request duration prevents network delay from extending a
  challenge. Missing/unparseable Date retains wall-clock validation, with an
  explicit phone-clock/expiry hint rather than silently skipping time checks.
- All six proof-challenge call sites use the same validation and refetch helper.
  Only an expired unsubmitted challenge is fetched once more. A second expiry,
  malformed nonce, wrong key/version, excessive validity window, service denial,
  transport error or clock ambiguity is not retried by this helper. It never
  replays registration, authorization, attestation submission or payload access.
- Module and private-catalog capability checks use their own response clocks.
  The standalone updater's later authorization-expiry check retains the module
  response clock, instead of reverting to the phone's wall clock.
- Fixed diagnostic check categories distinguish version, key, nonce, missing
  expiry, expired challenge, excessive window and clock ambiguity. No keys,
  nonces, tokens, URLs, response bodies or raw header values are added to errors.

HTTPS host restrictions, no-redirect policy, TLS certificate validation,
signature verification, nonce/key binding, lifetime bounds, server expiry and
one-use/replay enforcement remain intact. Offline leases, signed access claims,
device key storage and offline anti-rollback checks are unchanged. A clock so
wrong that TLS fails is not bypassed. There are no server or game-module changes,
and no server deployment is required. No new UI labels require translation.

## Verification

`LauncherProofChallengeTest` checks phone clocks ahead/behind, wall-clock jumps,
monotonic expiry, invalid elapsed time, missing Date fallback, fixed failure
categories and privacy, exactly one expired-challenge retry, no retry for other
failures, and downstream authorization expiry. Logs:
`temp/issue67-proof-check.log` and `temp/issue67-all-tests.log`.
These are deterministic local checks, not confirmation on the reporter's phone.
Keep #67 open until a new launcher build is tested there.

Platform references:
[URLConnection.getHeaderFieldDate](https://developer.android.com/reference/java/net/URLConnection#getHeaderFieldDate(java.lang.String,%20long))
and [SystemClock.elapsedRealtime](https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime()).
