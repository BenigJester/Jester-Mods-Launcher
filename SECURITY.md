# Security policy

## Supported release

Security fixes target the latest official Jester Mods Root and Non-root
launcher release. Older builds may be denied protected module downloads when
their access protocol no longer meets the current server policy.

## Authenticity

The official Android signing-certificate SHA-256 is:

```text
AA65ABF5EB089BFD92E3138A9BFA0D6BA8E0F875FF0B26E295AF656D67CCDA29
```

The certificate fingerprint is public identity, not a secret. A matching
fingerprint proves the APK was signed by the official release key; compare the
APK file hash with the value published for that specific release as well.

## Protected-module design

The launcher creates a non-exportable P-256 proof key in Android Keystore.
Protected module authorization requires fresh signed nonces. The service also
requires Android hardware key attestation bound to the launcher package,
official release signer, device proof key, and access grant. Module capabilities
are short-lived, path-scoped, and revalidated before payload delivery.

Direct-patched games use a separate non-exportable launcher key. The patch
contains only its public key. A fresh, package-bound, time-bound, nonce-bearing
launch ticket must verify before the module loads. Publishing the verifier does
not disclose either private key.

Unlocked boot state is recorded but is not an automatic denial, because the
Root flavor intentionally supports rooted devices. Software-only attestation,
revoked attestation chains, custom launcher signers, wrong packages, missing
proofs, stale capabilities, and replayed nonces are rejected.

## Scope and limitations

The controls protect server delivery and make direct launching harder. They do
not claim unbreakable DRM on a device controlled by an authorized user. A
rooted owner can inspect process memory and modify local code. Report language
should distinguish a server authorization bypass from ordinary client-side
reverse engineering.

## Report privately

Open a private GitHub Security Advisory and include:

- affected launcher flavor, version, and build;
- APK SHA-256 and signer SHA-256;
- affected endpoint or component;
- reproducible steps and observed versus expected behavior;
- proof-of-concept material with credentials and user data removed.

Never place digital keys, recovery identifiers, device identifiers, signing
keys, private module payloads, or unreleased module details in a public issue.
