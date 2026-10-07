<div align="center">
  <img src="app/src/main/res/drawable/menu_icon.png" alt="Jester Mods logo" width="88" />
  <h1>Jester Mods Launcher</h1>
  <p>An Android launcher for managing game add-ons in Root and Non-root modes.</p>
  <p>
    <a href="https://github.com/BenigJester/Jester-Mods-Launcher/releases">Downloads</a> ·
    <a href="#build-from-source">Build from source</a> ·
    <a href="SECURITY.md">Security</a> ·
    <a href="PRIVACY.md">Privacy</a>
  </p>
</div>

## About the project

Jester Mods brings add-on discovery, installation, compatibility checks, and
signed updates into one launcher. Choose the Root or Non-root APK for your device.

This repository publishes the Android client for transparency and security
review. It includes the launcher UI, catalog and update logic, integrity checks,
and a neutral example module. Production game modules, backend code, credentials,
and signing keys remain private.

Local builds are for development and review; they cannot download protected
production modules. See the [source-available terms](SOURCE_AVAILABLE.md) before
redistributing or modifying the project.

## Download and verify

Get the official APKs from [GitHub Releases](https://github.com/BenigJester/Jester-Mods-Launcher/releases)
or the [launcher download page](https://jester.moodtools.workers.dev/game/jester-mods-launcher).

| APK | Use |
| --- | --- |
| Root | Devices with root access |
| Non-root | Devices without root access, using the supported compatibility or patch method |

Both official APKs use this signing-certificate SHA-256:

```text
AA65ABF5EB089BFD92E3138A9BFA0D6BA8E0F875FF0B26E295AF656D67CCDA29
```

Check the certificate with Android SDK Build Tools and compare the file hash
with the hash published for that release:

```powershell
apksigner verify --verbose --print-certs .\Jester-Moods-Root.apk
Get-FileHash .\Jester-Moods-Root.apk -Algorithm SHA256
```

Use the Non-root filename when verifying that APK. Do not install a file with
a different signer.

## Build from source

You need JDK 17+, Android SDK Platform 35, Android NDK, CMake, and Ninja.
The Windows helpers also require PowerShell.

```powershell
.\gradlew.bat :app:assembleRootDebug :app:assembleNonrootDebug --no-daemon
```

The resulting APKs are under `app/build/outputs/apk/`. Debug builds use a local
signing key. Official release signing requires an external keystore, which is
not included here. Production-only Root external-controller methods are
excluded from this snapshot; the Root injection path remains available for review.

### Source map

| Path | Contents |
| --- | --- |
| `app/src/main/` | Shared UI, access, catalog, integrity, and update logic |
| `app/src/root/` | Public Root execution bridge and injection runtime |
| `app/src/nonroot/` | Non-root compatibility and guarded patch manager |
| `app/src/test/` | Launcher unit tests |
| `modules/com.example.module/` | Neutral example module |
| `third_party/` | Dependencies and their license notices |
| `scripts/` | Local build and test helpers |

## Security, privacy, and licensing

Signed metadata protects launcher updates and module integrity. Protected
downloads use Android Keystore proofs and attestation; direct-patched games
verify a launcher-issued ticket before loading a module. These checks do not
promise absolute protection on a device controlled by its owner.

For details, see [Security](SECURITY.md), [Privacy](PRIVACY.md),
[Source-available terms](SOURCE_AVAILABLE.md), and
[Third-party notices](THIRD_PARTY_NOTICES.md).

Report vulnerabilities through a
[private GitHub Security Advisory](https://github.com/BenigJester/Jester-Mods-Launcher/security/advisories/new).
Include the launcher version, flavor, APK hash, and reproduction steps.
Keep digital keys and device identifiers out of public issues.
