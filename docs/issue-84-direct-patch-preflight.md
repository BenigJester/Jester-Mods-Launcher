# Issue 84: missing original signer during patched-game update

Soul Knight 8.7.0, non-root launcher 4.5.2, Android 13: an in-place patch update
reaches base rewriting, then fails because Google login needs the original game's
signer but the installed patch does not retain it. The report does not establish
where the installed copy came from; do not assume corruption or another modder.

The existing signer resolver and strict lowercase SHA-1 validation are retained.
It accepts retained original metadata, verifies an unpatched original with apksig,
or uses the explicit released-v1 migration metadata. It never substitutes the
launcher signing certificate or invents missing information.

Preparation now resolves/checks the signer immediately after inspecting the APK
set, before compatibility processing, signing-key creation, cached-output reuse,
patch-output cleanup or APK rewriting. The resolved value is passed to rewriting,
where the same validation remains as defense in depth. This removes the late
failure without adding a second full verification of the original APK.

The error explicitly says not to uninstall or clear game data and offers retrying
with Use GmsCore off if Google login is unnecessary. It does not switch preferences
automatically, promise the game works without Google services, bypass restoration
checks, or begin replacement. New guidance is localized in all nine local
languages using the existing themed setup error UI; no new dialog/theme is added.

This is an earlier check and safer guidance, not recovery of a missing signer or
a confirmed Google-login fix for this installed copy. No device install, save
change, module change or launcher-version bump is part of this commit.

Tests cover accepted/rejected signatures, disabled GmsCore, guard ordering,
defense-in-depth validation and translation coverage. Existing GmsCore archive,
DEX-routing and direct-patch policy tests are run alongside these checks.

Verification: all 49 direct-patch tests and 267 Root tests passed. Both launcher
variants compile. Local-only logs: `temp/issue84-preflight-check.log` and
`temp/issue84-root-check.log`. No affected-device success is claimed.
