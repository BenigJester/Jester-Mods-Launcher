# Issue 123: Google login profile rejection evidence

## Evidence

Soul Knight setup failed on launcher 4.5.4 (454), non-root/API 36, with GmsCore
selected and READY. The event trail reaches base APK patching, then reports
`Google account support does not match this game build`. No full attachment,
installed game version, APK or file hashes were supplied. This message covers
several different rejection checks; it does not establish a broken companion,
a specific unsupported game build, or a game crash.

The current module supports Soul Knight 8.7.0 / 80700 and pins `classes2.dex` to
`6319de882fe3f93e479c5cc18fbaf021754e9c19dd61097798ea73fb82a4f73c`.
The local original at `Fast_analysis/Tests/soul-knight/Update-8.7.0/base.apk`
(relative to mood-tools) has APK SHA-256
`fe228467f98b57eb49d9ffe4becf9e7cec1941d844ace7ef4af7cc6a6f553530`.
Its pin matches and the current transformation succeeds, modifying only
`classes2.dex`. This is an isolated packaged-code check, not confirmation on
the reporter's device or evidence that their APK matches this original.

## Change

Replace ambiguous rejection exceptions with a typed failure retaining the same
translated user-facing messages. Add fixed diagnostic check codes for missing
DEX, hash mismatch, unsupported SDK shape, unpinned changed DEX, missing account
route, already-routed input, availability/signature shape mismatch and archive
size-limit rejection. Hash failures retain the DEX entry and expected/actual
SHA-256 (when present).

The existing setup progress/report path records the game version code, loaded
GmsCore profile and failure evidence as separate events below the existing
320-character/event limit. Entry names and hashes are allowlisted at the report
boundary. No paths, account text, URLs, APK contents or arbitrary exception
messages are added. Diagnostic callback failure cannot replace the original
setup error. Normal cleanup still runs; no partial installation is attempted.

Exact hashes, SDK-shape requirements, archive limits and the per-game toggle
remain enforced. No module pins, game files, accounts, saves, UI design or
translations changed. This improves diagnosis, not a confirmed fix for #123.
The existing request for the installed game version/source remains relevant.

## Checks

```
.\gradlew.bat :app:testNonrootDebugUnitTest --tests '*DirectGmsArchiveTest' --tests '*GmsCoreDexPatcherTest' --tests '*ModuleGmsRulesTest' --console=plain
```

17 tests passed, none skipped, including the local Soul Knight original, existing
Among Us/Wild Darkness fixtures, restore/reapply behavior, explicit missing/hash/
unpinned/unsupported/already-routed rejection codes and report field filtering.
No launcher APK build/install, game launch or live account test was performed.
