# Privacy and data inventory

Jester Mods does not need a conventional username/password account. The
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

## Launch and crash diagnostics

Diagnostics are stored in the launcher's private app storage. They can include
game/launcher versions, device model, Android version, ABI, module hashes,
launch stages, and filtered crash stack traces or native fault addresses.
Non-root mode records BlackBox sessions and suspected startup stalls; Root
mode reads matching game crashes from Android's crash buffer using root access.
Exception messages, raw crash logs, and memory dumps are not retained in Root
reports. Diagnostics do not automatically identify a faulty hook or prove that
a game reached its menu.

Reports are not automatically uploaded. Copy, export, or sharing requires an
explicit user action. The GitHub flow saves the report and opens a prefilled
issue; users must review, attach the saved file, and submit it themselves.
Clearing diagnostics removes that game's saved reports, not game data,
previously exported files, or Android's system crash logs.

## Public reports

Remove digital keys, identifiers, certificate chains, complete request bodies,
and private module files before posting logs or screenshots publicly.
