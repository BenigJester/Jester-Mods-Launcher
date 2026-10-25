# Issues 85–87: companion setup guidance and snapshots

These Soul Knight reports stop before APK preparation because GmsCore is not
ready, but their shared message does not identify whether it is missing,
disabled, outdated or unverified, or show the selected per-game toggle.

The direct-patch readiness check now reads the companion state once and includes
its existing localized state label, the Google account support instruction and
a new optional route: turn off Use GmsCore and retry if Google login is not needed.
The same optional guidance is shown in the existing themed, scrollable account
support panel when support is selected but the companion is not ready. New text
is translated into all nine local languages.

After access authorization and before preparation, the setup view model captures
fixed diagnostic fields on the IO dispatcher: module eligibility, selected toggle,
whether GmsCore is required, and companion state. The setup report exports this
retained snapshot ahead of the event trail; progress updates cannot rotate it
away and sharing does not silently replace it with the current phone state.
The snapshot is explicitly labeled before preparation, not the precise later
failure time. Readiness is rechecked in the patch manager; a package change in
between can therefore produce a different state label in the actual error.
If snapshot collection fails, a fixed unavailable marker is retained and package
preparation still proceeds to its normal checks.

This does not inspect accounts, login completion, credentials or tokens. READY
means the expected companion version/signature is enabled, not that sign-in
succeeded. No preference is changed automatically, and turning support off does
not guarantee a game works without Google services. Signature/version checks,
module eligibility, access authorization, original-game validation and save-safety
checks are unchanged. Root and identity-shell setups do not collect these fields.

Checks cover all supported/selected/state combinations, localized error lines,
preparation/export ordering, the existing GmsCore toggle/readiness guards, and
the existing direct-patch tests. No device setup, account action, installation,
launcher-version bump or module change is performed.

Verification: 52 focused non-root setup/direct-patch tests and all 270 Root tests
passed; both launcher variants compile. Local-only logs:
`temp/issues85-87-setup-final-check.log` and `temp/issues85-87-root-check.log`.
The unrelated untracked non-root module migration test is not part of this run.
