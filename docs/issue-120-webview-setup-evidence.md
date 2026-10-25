# Issue 120: distinguish WebView ownership failures

## Evidence and limit

The 4.5.3 report for 1945 Air Force (`com.os.airforce`, 15.84 / 1584,
module SHA-256 `bab2916b4a223deb02bf9d5291e53d02687e1067722600cdcbb168a2e01a39af`)
on realme RMX3933, Android 16/API 36, arm64-v8a/4096-byte pages, fails in
`WebViewDirectoryLease.acquire` during application binding for service creation.
The first captured stage is `webview_directory_setup`, before Activity resume.
Only an IOException type and frames are retained. They do not establish whether
all four owners were busy or an ownership directory/file could not be accessed.
The exact reporter APK and a matching affected device were not acquired.

## Change

Keep the existing four stable WebView profiles and exclusive process-lifetime
locks. Classify lease failures with a typed IOException and fixed reason enum:

- `invalid_identity`
- `ownership_directory_unavailable`
- `ownership_file_open_failed`
- `ownership_lock_failed`
- `all_owners_busy`

The existing first-failure/exception-chain report includes the reason even when
wrapped by Android's service-start exception. It still omits arbitrary exception
messages, filesystem paths, URLs and account details. No guessed classification
is applied to untyped IOExceptions from older builds or unrelated failures.

No lock/profile deletion, additional profiles, retries, startup-error suppression,
save/account migration or device security changes. Root and direct patch are
unchanged. No new UI or translations. This is an evidence improvement, **not a
confirmed fix** for issue 120. Request a fresh full report after release to choose
between contention and storage failure rather than changing startup speculatively.

## Verification

```
.\gradlew.bat :Bcore:testDebugUnitTest --tests '*WebViewDirectoryLeaseTest' :app:testNonrootDebugUnitTest --tests '*LaunchSessionRecorderTest' --console=plain
```

Isolated JVM tests cover real cross-process ownership, four-owner exhaustion,
release/reuse, game/user/process separation, invalid identity, inaccessible
directory/file targets, fail-closed behavior and report privacy through exception
wrappers. These do not test Android filesystem policy or prove game startup on
the affected phone. No launcher APK install or device data changes were performed.
