# Config-driven Google account support

## Module configuration

Non-root direct-patch modules can declare automatic ReVanced GmsCore support:

```json
{
  "nonroot_method": "direct_patch",
  "gmscore_support": true
}
```

Set `gmscore_support` to false to exclude a game. Omitted means false for legacy
modules. Only a JSON boolean is accepted, and true requires a direct-patch method.
The flag enables the setup UI and automatically includes routing in the prepared
patch. There is no user toggle or per-package preference; old preferences are
ignored. The module flag remains the authority for including/excluding a game,
not a claim that every Google feature is implemented by the companion.

The publisher carries this as `gmsCoreSupport` in both the signed manifest and
catalog. Download, installed config, catalog and signature verification preserve
and check it. Rebuild/re-publish the affected module after changing the flag.
Once this launcher implementation is installed, adding a game using a supported
Google SDK layout or excluding one needs only a module update, not a package
allowlist change in the launcher. Different or obfuscated SDK implementations
can still require an adapter/launcher update.

## Setup

Library → game → **Google account support** → **Get GmsCore** opens the pinned
official ReVanced release page. Install the standard companion through Android,
open it to set up the account, then update the patched game from Library.
Supported modules always include the adapter. A missing, disabled, outdated or
untrusted companion blocks preparing that supported patch with setup guidance;
it never silently prepares a different, non-GmsCore variant.

The companion package, enabled state, minimum version and signing certificate
are checked. No silent download or installation occurs. Google login stays in
the independently installed companion, not an embedded launcher account screen.
The device's official Play Services and accounts are unchanged.

Pin: `app.revanced.android.gms`, minimum version `250932004`, trusted SHA-256
signer `d732e4055c97f8d7a43170a6dc737dc71b6f3e0410940e1b3706c6ade1ff3488`.
New companion signing keys must be reviewed explicitly.

## Adapter and verification boundaries

### Game-specific rules in signed modules

Renamed SDK builds can additionally declare `sdk_symbols`: exact availability
class/method/static-kind, signature-helper class/method, and at most 64 explicit
routing classes. These only select the existing fixed SDK operations under exact
DEX hash guards; they cannot supply method bodies, scripts, offsets or general
replacement strings. Method signatures remain fixed and must match uniquely.
The signature helper must remain static; availability preserves its declared
static/instance ABI. Alias selectors participate in signed equality and cache
identity. The Wild Darkness 1.4.31 uses this extension. Publishing such a profile
requires bootstrap 3; ordinary schema-1 profiles still require bootstrap 2.
The launcher now advertises bootstrap 3, retaining support for older modules.

Optional `gmscore_patch` in config.json carries a restricted schema-1 profile:

```json
"gmscore_patch": {
  "schema": 1,
  "revision": "game-build-account-v1",
  "dex_sha256": { "classes.dex": "<64 lowercase SHA-256 hex characters>" },
  "account_type": "app.revanced"
}
```

`account_type` is optional; the only supported value is `app.revanced`. It changes
exact `com.google` account-type string instructions **only inside the known
Google common/auth/signin/games SDK namespaces**. It does not change system
accounts, arbitrary game code, Google API scopes, Binder descriptors or passwords.
No arbitrary scripts, byte offsets, executable payloads or method bodies are
accepted. Every declared original DEX hash must match, every changed DEX must
have a pin, and a requested operation must find matching sites. Unknown fields,
schemas, missing hashes, wrong builds and unsupported targets fail closed.

The optional profile is carried as `gmsCorePatch` in the signed catalog and module
manifest. Signature verification checks the whole profile against the installed
config. Both its revision and a canonical fingerprint participate in the applied
profile/cache identity, so changing a rule triggers an in-place patch update even
if its human revision was accidentally left unchanged. Module exclusion still restores
all affected original DEX bytes from verified backups.

Among Us 2026.9.29 / 7492 is the first custom profile, in its own module config.
It pins all three original base DEX files and requests the account-type mapping
identified in startup diagnostics. The local exact-game fixture test confirms
that its scoped account-type sites are rewritten. The user confirmed Google login
and guest-account merging in device testing. No other game receives that custom operation.

Publishing a module with custom rules sets `minimumBootstrap` to 2. The updated
launcher verifier, standalone downloader and authorization request use bootstrap
2, while legacy modules continue working. An older launcher refuses these new
modules instead of silently ignoring the rules. This is the one-time launcher
update; subsequent changes using schema-1 operations ship in module updates.
New engine operations still require a reviewed launcher update.

Local checks for this rule implementation: all 344 non-root launcher tests passed
(including the exact Among Us fixture; none skipped), the non-root debug APK
built, and root Kotlin compilation passed. The focused server/recipe suite passed
64 tests. The broader website suite passed 157/158; its unrelated access-export
test still expects a private `mini-militia2` catalog slug that is absent locally.
No device installation, module publishing or production deployment was performed.

To exclude a custom-profile game entirely, set `gmscore_support` false **and remove
`gmscore_patch`**, then republish. Remove only `gmscore_patch` to retain the generic
SDK adapter without the custom operation. Unknown profiles are never executed.

The adapter searches the game's original **base APK DEX files** for known
common/auth/signin/games Google SDK classes. It routes exact package/Chimera
literals and adapts exactly one recognized static availability method and exactly
one recognized signature-check method. Signer acceptance is limited to the
trusted companion; other packages keep the original verifier.

Unknown/missing/duplicate method shapes, unsupported register layouts, or an
already externally adapted DEX fail closed. If a supported game has no matching
client, its module profile must be corrected or GmsCore excluded in the module;
there is no automatic downgrade. SDK code that exists
only in a separate feature split is not currently adapted.

Java class names, service actions and Binder protocol descriptors are not renamed.
dexlib2 relocates branches, switches, exception tables and debug items; original
method annotations are retained. The bridge comes from the existing diagnostics
bootstrap. Unchanged DEX, native libraries and game resources are not modified
by this compatibility layer.

The supported game version and ABI are still enforced by normal module checks.
The installed game's actual verified original certificate provides ReVanced's
per-client metadata; there is no Soul Knight identity copied onto another game.
The launcher never changes Android's package-signature verification or grants
account permissions.

## Reapply, exclusion and opting out

Changed DEX originals are retained in `assets/moodtools/gms-originals/` with
hashes in the patch marker. Updates rebuild from those verified originals.
Receiving module config that disables the capability
and updating restores those exact bytes and removes the feature's manifest
metadata. It does not uninstall GmsCore or sign out any account.

Soul Knight's tested 8.7.0 DEX baseline is now declared in its module's
`gmscore_patch` config, not in the shared launcher patcher. The game-neutral
`directpatch/GmsCoreDexPatcher.kt` consumes the same signed rules as other games.
This makes support module-controlled; it does not imply every game is compatible.
The earlier Soul Knight v1 patch marker/backed-up DEX is supported for migration.
Old launcher patches that never saved the original game's signer cannot invent
it: enablement may require preparing from the original game. **Back up saves
before any signer-changing reinstall; no automatic uninstall/migration is done.**

Changing the effective option changes the patch revision and numeric/hex cache
identity. Directory containment protections are unchanged. Legacy direct-patch
and Identity Shell diagnostics remain present during this migration.

## Diagnostics and testing

Reports include the selected option, applied profile and companion readiness.
The bridge emits fixed numeric availability transitions: 0 ready, 1 missing,
2 old, 3 disabled, 9 untrusted/blocked. These are not proof of login/save sync.
No accounts, passwords, tokens or arbitrary login error messages are collected.

The user confirmed integrated Soul Knight Google login on their device. All
other newly opted-in modules are **test candidates**, not verified compatibility.
Test startup/menu, Google login if used, disposable save upload/download,
background/resume, independent restart, module update and module-controlled exclusion per game.

Self-contained tests cover multi-DEX routing, missing/duplicate SDK rejection,
deterministic reapply, exact opt-out, backup tamper rejection, module eligibility,
signed metadata tampering, directory guards and translations. A separate local
proprietary Soul Knight fixture verifies every rewritten class against the
previous experiment (normalizing helper package, string width and preserved
annotations); it is skipped when local artifacts are absent.

## Upstream and licensing

- [ReVanced GmsCore](https://github.com/ReVanced/GmsCore)
- [Pinned release](https://github.com/ReVanced/GmsCore/releases/tag/v0.3.13.2.250932)
- [Per-client metadata](https://github.com/ReVanced/GmsCore/blob/main/play-services-base/core/src/main/kotlin/org/microg/gms/common/PackageSpoofUtils.kt)
- [microG implementation status](https://github.com/microg/GmsCore/wiki/Implementation-Status)
  lists Play Games support as partial.
- dexlib2 2.5.2: upstream notice bundled in the non-root APK licenses assets.

No companion APK, proprietary game DEX, credentials or device logs are committed.
