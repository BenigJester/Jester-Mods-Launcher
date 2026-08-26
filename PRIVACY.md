# Privacy and data inventory

Jester Moods does not need a conventional username/password account. The
launcher service processes the minimum identifiers needed to grant access,
bind that access to an installation/device proof, prevent replay, and deliver
the correct signed module files.

## Data sent to the service

- random installation and recovery identifiers;
- a device-derived identifier used for access binding;
- launcher flavor, access protocol, version/build, package name, and ABI;
- public proof-key identifiers and public keys;
- one-time nonces and signatures;
- Android key-attestation certificate data, including package, signer digest,
  OS/patch level, hardware security level, and verified-boot state;
- requested module package and payload path;
- normal HTTP metadata such as IP address and user agent at the hosting edge.

Private launcher proof keys and direct-launch ticket keys remain non-exportable
in Android Keystore. The launcher does not upload game save files as part of
its access or module-delivery protocol.

## Local data

The launcher stores access leases, proof-key references, verified catalogs,
downloaded launcher/module artifacts, and library state in its app storage.
Supported direct-patch flows create a launcher-signed replacement package only
after an explicit user action. Android may erase the original game’s local data
on the first signer-changing replacement; the UI must disclose that risk.

## Public reports

Remove digital keys, identifiers, certificate chains, complete request bodies,
and private module files before posting logs or screenshots publicly.

