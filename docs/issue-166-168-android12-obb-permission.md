# Issues 166/168: Android 12 OBB installer storage permission

PvZ2 13.5.2, launcher 4.6.4, API31. Issue 166 records AccessDeniedException
for the game's Android/obb folder before uninstall. Issue 168 records a verified
cached companion download followed by denied OBB destination access.

Android 12 and 12L MediaProvider's isCallingIdentityAllowedInstallerAccess
requires WRITE_EXTERNAL_STORAGE before considering installer permission/app-op:
https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/android12-release/src/com/android/providers/media/MediaProvider.java
https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/android12L-release/src/com/android/providers/media/MediaProvider.java

The launcher declared storage permissions only through API30, and its permission
activity/manager also stopped at API30. Installer permission was already checked
by companion installation and package replacement, so repeating that advice
alone would not address this missing Android 12 permission declaration.

Extended READ/WRITE_EXTERNAL_STORAGE declarations and the localized Android
permission prompt through API32. Preservation checks permission before probing
the source folder, since denied folders can appear nonexistent to File APIs.
The shared stage/delivery paths use the same permission rule. Updated the OBB
access error's version guidance. No all-files permission, folder rename,
permission bypass, uninstall-on-backup-failure or download-cache deletion added.

Validation: GameObbStoragePermissionTest verifies API31/32 prompting and manifest
declarations agree, already-granted permission proceeds, and API33+ avoids the
obsolete prompt. GameExpansionFilesTest covers existing backup/restore integrity
and failure preservation. Both launcher flavors compile. Log:
temp/issue166-obb-check.log. Actual API31 access remains unverified on the
reporters' phones; OEM denial can still stop setup safely.

No push, version bump, publication or device installation in this step.
