# Direct-patch manifest cleanup

The shared non-root direct patcher ports only the PairIP section of
`Automatic Scripts/Standalone-ModMenu-Repacker.ps1` (`Update-ManifestForMenu`):

- Case-insensitive `pairip` matching against manifest attribute string values.
- Remove matching attributes from `<application>`, keeping the application and its children.
- Remove any other matching element with its complete subtree, except the manifest root (rejected).
- Remove empty `<queries>` containers and reject remaining active PairIP attribute values.

The implementation edits binary XML directly, leaving string indexes, resource
IDs, namespaces, and unrelated declarations intact. Removed strings may remain
unused in the string pool. A removed PairIP component factory is not restored
through the diagnostics delegate. The menu/diagnostic bootstrap is then installed
as usual. Patch marker schema 5 invalidates prior cached output for rebuilding.

This applies to the base-manifest rewrite for the shared `direct_patch` route.
BlackBox, root injection, identity-shell startup, game DEX/native instructions,
APK signature verification, and the module launch-ticket policy are unchanged.
The old `.cmd` file only invokes PowerShell and supplies no PairIP implementation.

This is not a pending-license-response implementation or a guarantee of PairIP
compatibility. Code-triggered checks and dependencies on a removed Application
or factory may still prevent a game from starting. Existing installations need
the newly patched APK installed; updating only a module cannot rewrite a manifest.

Validation: `PairIpManifestTest` compiles fixtures with Android's aapt2 and parses
the cleaned/injected output with aapt2. Coverage includes mixed-case matches,
application attribute removal, whole-subtree removal, mixed/empty queries,
unrelated entries, package/version preservation, idempotence, byte-identical
no-op behavior, and malformed-input rejection. Live game compatibility remains
a separate device check.
