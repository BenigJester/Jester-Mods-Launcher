# Issue 111: existing shell routed into direct patch setup

Issues 111, 116 and 118 report `Existing diagnostic provider has an unexpected
application factory`, with GmsCore disabled. This error is also reproducible by
feeding the compiled Identity Shell template into direct-patch manifest setup:
the shell has the diagnostics provider but is not a directly patched game.
The reporters' exact installed APKs have not been acquired, so this does not
prove that every report has the same installed-package origin.

The launcher previously skipped installed-method detection for single-method
modules, and discarded detected methods missing from current signed choices.
Removing Soul Knight's shell option could therefore send an existing shell into
the patcher instead of continuing through the shell runtime.

Fix: keep an inspected, already-installed Identity Shell routed through the
existing shell path when a standard injection module recommends Direct Patch.
New installations still advertise only the signed choices; a saved shell
preference alone cannot activate this behavior. Root-only and external-controller
modules do not acquire shell support. Root mode clears the device override.
Signed metadata, payload verification, installed shell readiness checks and
launch authorization remain unchanged. Google account setup is not offered for
the existing shell runtime.

Defense in depth: direct patch preparation rejects an installed shell before
GmsCore setup, key creation or output preparation. The manifest/factory guard is
not loosened, and shells are never repacked as real games. No uninstall, data
copy, save migration or automatic switch to Direct Patch is introduced.

Tests cover compiled-shell reproduction, removal of the shell setup option,
saved-choice rejection, root-only/external-controller exclusions, badge routing,
signed-module integrity and GmsCore eligibility. On-device launch/resume and
reporter confirmation remain outstanding. No game installation was changed.
