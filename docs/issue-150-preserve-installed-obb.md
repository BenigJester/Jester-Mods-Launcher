# Issue 150: preserve game-downloaded OBBs before direct patch replacement

https://github.com/BenigJester/Jester-Mods-Launcher/issues/150

Existing support retains OBBs extracted from downloaded game archives and
restores them in DirectPackagePatchManager.install/authorizeLaunch. It did not
retain files downloaded by the installed game itself. Android's first
signer-changing replacement can therefore delete the only OBB copy.

After the existing installer permission and replacement confirmation, but
before opening Android's uninstall prompt, direct-patch replacement now copies
the installed game's main/patch OBBs into the existing private retained store.
The copy runs on the IO dispatcher and observes lifecycle cancellation. Failure
stops setup before uninstall, preserving original files and any previous backup.
In-place patch updates, identity shells and absent installed packages keep
their previous paths. Existing install/launch restoration is reused.

The shared staging implementation now accepts an input-stream provider so
archive extraction and installed-file retention share size/CRC verification,
free-space checks, temporary generations, cancellation and atomic publication.
Only package-matching main/patch filenames are accepted; OBB versions may differ
from APK versions. Unrelated files are ignored. Symlink sources, foreign OBBs,
duplicate main/patch files and oversized/empty files are rejected. Missing or
empty folders leave existing retained backups intact. Directory iteration uses
an exception-reporting API so a reported access denial is not treated as an
empty folder. Source files are never renamed, deleted or overwritten.

Uses the existing primary external-storage OBB location and permission handling.
Android's published storage FAQ describes an OBB-access exception for installers:
https://medium.com/androiddevelopers/android-11-storage-faq-78cefea52b7c
Actual OEM/filesystem access still needs device testing; an OS that hides a
folder as nonexistent cannot be distinguished from an absent folder by this
API. This is not backup of internal saves, Android/data, arbitrary directories
or secondary storage volumes, and does not change the existing uninstall warning.

The existing themed setup-progress/error UI is reused. Three new messages have
Filipino, Korean, Japanese, Simplified Chinese, Spanish, Vietnamese, Indonesian,
Portuguese and Arabic translations.

Validation: GameExpansionFilesTest (12), PackageReplacementRecoveryStoreTest (2)
and PackageReplacementLateResultTest (2) passed. Added checks cover preserving
and restoring game-downloaded files after simulated uninstall, keeping originals
and previous backups on cancellation/foreign files, interrupted-copy cleanup,
missing/empty folders and errors that must not appear as missing files.
Both compileNonrootDebugKotlin and compileRootDebugKotlin passed.
Logs: ignored temp/issue150-obb-check.log and temp/issue150-obb-tests.log.

No device uninstall, device install, version bump, commit, push or release was
performed. Real-device read/restore verification remains required.
