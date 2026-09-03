# Standalone module template

This directory is the buildable reference module for Jester Mods. Copy the
whole directory when starting a module, rename the directory to the target game
package, and retarget its package-specific loader, native hooks, metadata, and
feature list.

The launcher expects `config.json`, `features.json`, `classes.dex`, and
`libmenu_native.so`. The module owns its menu UI, persisted feature state, root
bootstrap, and native hooks. The launcher owns downloading, signature and hash
verification, ABI selection, installation, updates, and launch routing.

The feature catalog in `cpp/Main.cpp` includes a reusable `MultiSelectSpinner`
example. Its first CSV item is the All row, and the remaining items are shown as
radio-styled choices in the same inline popup used by the ordinary Spinner. The
native callback receives `0` when all entries are selected; explicit subsets use
bit 30 as a marker and bits 0 through 29 as the selected-item mask.

## Choose which features to expose

`cpp/Main.cpp` defines `kHiddenFeatureIds` near the top of the template. Add the
explicit numeric IDs that should be omitted from a shared build:

```cpp
constexpr std::initializer_list<int> kHiddenFeatureIds = {
        3, 4, 30, 31, 32, -50
};
```

Both positive and signed negative IDs are supported. The descriptors keep their
original explicit IDs, so hiding one control never renumbers another control or
changes its `Changes` callback. When a category, connected group, nested collapse,
or collapse contains only explicit-ID children, hiding every child omits its
structural rows too.
The list is empty by default and therefore exposes the complete example catalog.

Display-only rows do not have callback IDs and remain visible. The automatic-ID
example also cannot be selected through `kHiddenFeatureIds` because its number is
derived from its position at runtime; use explicit IDs for every production control
that may need to be hidden. Hidden controls are not returned to Java, so their saved
preferences are not loaded for that menu session. This is a feature-list visibility
filter, not a security boundary: the corresponding native implementation remains in
the compiled library unless it is separately removed.

## Choose the non-root method

Every module must declare one non-root method in `config.json`. Copy one of the
complete examples from [`examples/config.injection.json`](examples/config.injection.json),
[`examples/config.direct-patch.json`](examples/config.direct-patch.json), or
[`examples/config.identity-shell.json`](examples/config.identity-shell.json), then
replace all placeholder package, title, version, and entry-point values.

### Injection

Use BlackBox injection when the original installed game can run in the managed
non-root runtime:

```json
"nonroot_method": "injection"
```

The launcher runs the original game in BlackBox and loads the verified module.
The installed Android package and its signing certificate are not replaced.

### Direct patch

Use a direct patched install only for a game that has been tested with the
launcher's package patcher:

```json
"nonroot_method": "direct_patch"
```

The launcher builds and signs a replacement game package containing the module.
The first install replaces the Play-signed package and can erase its local game
data; later module updates can update the launcher-signed package in place.

A direct-patch module must keep both `DirectLaunchGuard.java` and
`ModComponentFactory.java`. The launcher embeds its patch-signing public key and
sends a short-lived signed launch ticket when the user taps Play. Without a
valid ticket, `ModComponentFactory` delegates to the game's original component
factory: the original game opens, but the native payload and menu stay disabled.
Do not bypass or weaken this guard in a released module.

### Exact-package identity shell

Use an identity shell for a protected game that must observe its real package name while its
original APK remains the virtualized game payload:

```json
"nonroot_method": "identity_shell"
```

The launcher preserves the untouched game APK, creates a small game-branded shell with the exact
package identity, and hosts the original game inside that shell. A launcher-authorized launch uses
the complete module. A direct home-screen launch is compatibility-only: it may apply the minimum
package/signature bypasses required to start the original game, but it must not install menu or
feature hooks.

Keep both identity-shell Java entry points in `com.android.support.ModuleRuntime`. If the module
uses a package-specific entry-point namespace, its `ModuleRuntime` wrapper must delegate those
entry points to the shared runtime as this template does.

Root launch behavior does not use `nonroot_method`; root always injects the
verified module into the original game package.

## Keep native behavior method-aware

The template starts native hooks only after Java supplies an explicit runtime method:

- `ModuleRuntime.loadNative(String)` selects injection for both root and BlackBox.
- An authorized `ModComponentFactory` load selects direct patch only after
  `DirectLaunchGuard` accepts the launch ticket.
- `ModuleRuntime.loadNativeForIdentityShell(String)` selects a launcher-authorized identity shell.
- `ModuleRuntime.loadNativeForIdentityShellCompatibility(String)` selects an external shell launch
  that must exclude menu and feature hooks.
- Native code accepts the first method once, rejects attempts to change it, and leaves an
  unknown method disabled.

Keep signing-certificate bypasses and other package-identity compatibility patches inside a
`RequiresPackageIdentityBypass()` branch. Injection must skip their pattern scans and patch calls,
because the original Play-signed game does not need them and an outdated scan must not prevent
ordinary root or BlackBox hooks from loading. Apply those compatibility patches before the
`IsIdentityShellCompatibilityOnlyRuntime()` early return; keep all menu and feature hooks after it.
Use a narrower explicit method check for behavior that truly applies only to direct-patch APKs.
Do not infer the runtime method from `config.json`, library paths, package signatures, or
virtual-environment heuristics.

The shared early-load observer also supports ARM64 games translated on x86-64 PC emulators. It
falls back to the ELF scanner for libraries mapped from non-zero APK offsets and gives a detected
Houdini/NDK Translation mapping a slightly longer stabilization window before installing hooks.
Keep game-specific native hooks behind that observer unless the target requires an earlier custom
loader boundary.

## Design compatibility as a capability report

`cpp/Main.cpp` includes a commented, disabled reference implementation of the resilient startup
flow. Keep `kPackageIdentityExamplesConfigured` false until every placeholder has been replaced
from a supported game binary.

The discovery ladder intentionally tries a strict signature, a relaxed signature, a verified
direct-call target, and finally a fixed RVA with byte validation. It scans both readable executable
process maps and executable ELF segments so split APK-backed and native-bridge mappings are not
missed. Every result must be unique and its original instructions must match before it can be used.

Treat required startup compatibility separately from optional feature groups:

- A missing required package/signature target is `MapFailed` with `TargetProfileMismatch`.
- A critical write that cannot be read back exactly is restored and reported as
  `StartupPatchRejected`.
- A failed optional hook or resolver produces `ReadyWithLimits`; only that feature's control is
  replaced with an amber explanation while unrelated compatible features remain available.
- Fatal states return only the compatibility card and a features-unavailable explanation, avoiding
  controls that appear functional even though no hooks were installed.

`cpp/Includes/Macros.h` counts failed Dobby calls because logging alone is not a usable readiness
signal. Snapshot the counter around an optional hook group, keep a separate availability flag for
that group, and guard its stale callback as well as its feature descriptor. The Java compatibility
card checks `ready with limited` before `ready` so the native amber state is not accidentally shown
as green.

Do not make patterns progressively looser without a second structural check. Do not use an RVA as
a blind fallback. A device-specific mapping difference may justify searching more executable
regions; it never justifies writing to an address whose expected original bytes were not verified.

## Retargeting checklist

1. Rename `modules/com.example.module` to the target game's package name.
2. Set `package_name` to the target game package.
3. Move `java/com/example/module/ModuleRuntime.java` to the matching package
   path and change only that wrapper's `package` declaration.
4. Keep `entry_point` as `com.android.support.Main` unless every shared Java and
   JNI class reference has intentionally been migrated to a new namespace.
5. Set the supported game versions and ABIs in `config.json`.
6. Select `injection`, `direct_patch`, or `identity_shell` explicitly; never infer the active
   runtime in native code.
7. Replace the disabled native examples and compatibility placeholders in `cpp/Main.cpp` with
   verified, version-specific targets.
8. Keep the compatibility descriptor as the first native feature row.
9. Keep `features.json` synchronized with the user-facing `GetFeatureList` rows.
10. Build only the new module and resolve every Java/native contract check.

`ModuleRuntime.loadNative(String)` and `RootBootstrap.install(Application)` must
remain in the DEX for both root and non-root launch paths. The package-specific
`ModuleRuntime` delegates native loading to the shared `com.android.support`
runtime. The example game package and version `5.4` are placeholders, not values
suitable for a release.
