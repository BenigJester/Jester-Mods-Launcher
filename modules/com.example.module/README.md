# Standalone module template

This directory is the buildable reference module for Jester Moods. Copy the
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

## Choose the non-root method

Every module must declare one non-root method in `config.json`. Copy one of the
complete examples from [`examples/config.injection.json`](examples/config.injection.json)
or [`examples/config.direct-patch.json`](examples/config.direct-patch.json), then
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

Root launch behavior does not use `nonroot_method`; root always injects the
verified module into the original game package.

## Keep native behavior method-aware

The template starts native hooks only after Java supplies an explicit runtime method:

- `ModuleRuntime.loadNative(String)` selects injection for both root and BlackBox.
- An authorized `ModComponentFactory` load selects direct patch only after
  `DirectLaunchGuard` accepts the launch ticket.
- Native code accepts the first method once, rejects attempts to change it, and leaves an
  unknown method disabled.

Keep signing-certificate bypasses and other replacement-APK compatibility patches inside an
`IsDirectPatchRuntime()` branch. Injection must skip their pattern scans as well as their
patch calls, because the original Play-signed game does not need them and an outdated scan
must not prevent ordinary root or BlackBox hooks from loading. Do not infer the runtime method
from `config.json`, library paths, package signatures, or virtual-environment heuristics.

The shared early-load observer also supports ARM64 games translated on x86-64 PC emulators. It
falls back to the ELF scanner for libraries mapped from non-zero APK offsets and gives a detected
Houdini/NDK Translation mapping a slightly longer stabilization window before installing hooks.
Keep game-specific native hooks behind that observer unless the target requires an earlier custom
loader boundary.

## Retargeting checklist

1. Rename `modules/com.example.module` to the target game's package name.
2. Set `package_name` to the target game package.
3. Move `java/com/example/module/ModuleRuntime.java` to the matching package
   path and change only that wrapper's `package` declaration.
4. Keep `entry_point` as `com.android.support.Main` unless every shared Java and
   JNI class reference has intentionally been migrated to a new namespace.
5. Set the supported game versions and ABIs in `config.json`.
6. Select `injection` or `direct_patch` explicitly; never infer the active runtime in native code.
7. Replace the disabled native examples in `cpp/Main.cpp` with verified hooks.
8. Keep the compatibility descriptor as the first native feature row.
9. Keep `features.json` synchronized with the user-facing `GetFeatureList` rows.
10. Build only the new module and resolve every Java/native contract check.

`ModuleRuntime.loadNative(String)` and `RootBootstrap.install(Application)` must
remain in the DEX for both root and non-root launch paths. The package-specific
`ModuleRuntime` delegates native loading to the shared `com.android.support`
runtime. The example game package and version `5.4` are placeholders, not values
suitable for a release.
