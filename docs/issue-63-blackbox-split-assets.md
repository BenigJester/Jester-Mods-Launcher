# Issue 63: Kingdom Rush startup in BlackBox

## Evidence

Reproduced with Kingdom Rush `com.ironhidegames.android.kingdomrush` 6.4.18
(1920006418), arm64, launcher 4.5.1, on Android 16. This is a different device
from the original report (Android 15); it confirms a local failure, not that every
report has the same cause.

Original base APK SHA-256:
`8C69C2FF687A5EACB2CD07919E01AF557450583B4BD5A6C2325C19FAEB192AEF`.
Original APKs, decoded code and device logs stay under ignored `temp/`, not Git.

Guest PID 24721 failed in `KRGameActivity.solveGamePath:401`: the game compared
a null asset-pack path. Its bundled Play Asset Delivery implementation looks up
the pack with `Arrays.binarySearch(PackageInfo.splitNames, pack)` and uses that
index in `ApplicationInfo.splitSourceDirs`. BlackBox supplied split paths in
ApplicationInfo but omitted PackageInfo.splitNames entirely. The installed
package includes `kr1_assets`, `kr1_gc32` and `kr1_gc64` packs.

A separate guest PID 24258 failed during SettingsProvider access:
`Package com.ironhidegames.android.kingdomrush does not belong to 10530`.
The existing system-provider hook rewrote cached guest AttributionSource objects
in place. A shared-state race is plausible but not proven by this stack alone.

## Changes

- Copy ApplicationInfo's split names into generated PackageInfo when names and
  paths have matching lengths. Preserve platform names, order and paths; never
  infer asset-pack identities from APK filenames or reorder dependency indices.
- For real system-provider calls, clone arguments and copy the attribution chain
  before applying the existing host package/UID rewrite. Guest Context attribution
  is no longer mutated in place. See the Android 12 correction below.
- Unwrap reflection failures so provider exceptions retain their original type.
  No permission failure is swallowed or retried.

Android source confirms that Builder copies its state but shares next-chain
states, which therefore require separate copies. It also forbids constructing
AttributionSource from a Parcel outside a Binder transaction:
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/AttributionSource.java

### Android 12 correction (#95, #96, #98)

The original implementation incorrectly assumed Builder's copy constructor was
available on API 31. It was added in API 33; the Android 12 reports fail in that
exact constructor lookup before game Application startup. This was a launcher
regression, unrelated to the Township module.

The replacement copies AttributionSourceState's instance fields into a new state,
recursively copies its next-state arrays, and constructs the source from that
state. This hidden framework state contract exists in Android 12 and is already
used by BlackBox's ContextCompat rewrite with hidden API access enabled. Binder
tokens, tags, renounced permissions and newer PID/device fields are preserved;
the original guest state is never rewritten. Errors still fail explicitly.

References:
- https://developer.android.com/reference/android/content/AttributionSource.Builder#Builder(android.content.AttributionSource)
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android12-release/core/java/android/content/AttributionSource.java

The regression test uses an API-31-shaped source with no Builder constructor and
checks nested-state isolation and field preservation. Device verification on
Android 12 is still required; JVM contract tests are not a live framework test.

## Verification / remaining gates

Run:

```powershell
./gradlew.bat :Bcore:testDebugUnitTest :app:compileNonrootDebugKotlin :app:compileRootDebugKotlin --console=plain
```

Regression tests check exact pack-to-path lookup, mismatched metadata handling,
deep-copy isolation using a framework-state test double, and preservation of
provider return values and exceptions. JVM tests do not
exercise Android's real AttributionSource implementation or game startup.

All 17 BlackBox JVM tests passed; both launcher flavors compile. The user tested
the updated launcher and confirmed the problem fixed. Individual cold-launch,
background/resume and independent-restart gates were not separately documented.
No game hooks, saves or device settings were changed by this source fix.
Games importing
archives with previously guessed split names are outside this host-installed
package reproduction and need separate exact-manifest validation if affected.
