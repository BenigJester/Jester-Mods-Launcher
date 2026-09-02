<div align="center">
  <img src="app/src/main/res/drawable/menu_icon.png" alt="Jester Mods" width="112" />
  <h1>Jester Mods Launcher</h1>
  <p>Transparency source for the official signed Android launcher.</p>
</div>

## Why this repository exists

This repository lets users and researchers inspect how the launcher handles
network access, update signatures, module integrity, root/non-root execution,
and direct-patch launch authorization. It is published from a clean snapshot;
private development history, production game modules, server code, payloads,
credentials, signing keys, and reverse-engineering work material are excluded.

The source is available for transparency. It is not a promise that arbitrary
forks can use the production module service. See [SOURCE_AVAILABLE.md](SOURCE_AVAILABLE.md).

## Verify an official APK

Official Root and Non-root APKs use certificate SHA-256:

```text
AA65ABF5EB089BFD92E3138A9BFA0D6BA8E0F875FF0B26E295AF656D67CCDA29
```

Verify an APK with Android SDK Build Tools:

```powershell
apksigner verify --verbose --print-certs .\Jester-Mods.apk
Get-FileHash .\Jester-Mods.apk -Algorithm SHA256
```

Do not install an APK when its signer differs from the fingerprint above.
Release notes should publish the exact APK SHA-256 separately for each flavor.

## Security boundary

- Catalogs, launcher releases, module manifests, and module files are checked
  against signed metadata before use.
- Protected module downloads require a fresh Android Keystore proof plus
  hardware-backed attestation of the official package and release signer.
- A public-source build has a different signer and cannot receive production
  module payloads from the service.
- A direct-patched game loads its module only after verifying a fresh ticket
  signed by the launcher-held Android Keystore key embedded in that patched
  installation. A normal game-icon launch delegates to the original game.
- Production game modules and their native targets remain private. Only the
  neutral `modules/com.example.module/` template is included.

These controls reduce unauthorized delivery, copied credentials, and casual
direct launching. No client-side protection can promise absolute secrecy on a
device fully controlled by an authorized rooted user. See [SECURITY.md](SECURITY.md).

## Build the launcher

Requirements: JDK 17+, Android SDK Platform 35, Android NDK, CMake, Ninja, and
PowerShell on Windows.

```powershell
.\gradlew.bat :app:assembleRootDebug :app:assembleNonrootDebug --no-daemon
```

Debug APKs are locally signed and intentionally cannot use the production
module channel. Production builds fail closed unless an external release
keystore and matching public certificate fingerprint are supplied. The
private signing key is never part of this repository.

## Repository contents

| Path | Purpose |
| --- | --- |
| `app/src/main/` | Shared launcher UI, access, catalog, integrity, and update logic |
| `app/src/root/` | Root execution bridge and runtime |
| `app/src/nonroot/` | Non-root compatibility and guarded patch manager |
| `modules/com.example.module/` | Synthetic module template, including `DirectLaunchGuard` |
| `third_party/` | Vendored dependencies governed by their own licenses |
| `scripts/` | Local build and test helpers safe for the transparency snapshot |

The Cloudflare authorization service, publishing tools, real modules, compiled
module payloads, offsets, dumps, and operational notes are intentionally not
published. Their absence does not prevent review of the Android client’s trust
and guard behavior.

## Data and permissions

The launcher needs network access for signed metadata, access verification,
updates, and protected module delivery. Root execution is present only in the
Root flavor. Package installation permissions are used by update and supported
non-root patch flows. See [PRIVACY.md](PRIVACY.md) for the data inventory.

## Reporting concerns

Use a private GitHub Security Advisory for vulnerabilities. For authenticity
concerns, include the APK SHA-256, signer fingerprint, download URL, launcher
flavor, and version/build. Do not post active digital keys or device identifiers
in a public issue.
