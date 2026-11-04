# Issue 143: distinguish import parsing from metadata conversion

https://github.com/BenigJester/Jester-Mods-Launcher/issues/143

Blackmoor 2 2608.26/260826, Non-root launcher 4.6.2, Xiaomi Mi A1,
Android 9/API 28, ARM64. The report records a host clone with six splits,
then install_exception:parse:other before any game-process attempt. The only
exception stack supplied belongs to the launcher's rethrown IllegalStateException,
not the original server-side install failure. No full file is attached as of
the October 29 investigation; the maintainer has requested it.

The old parse stage spans parserHostInstalledPackage/parserApk, optional split
metadata, copying host ApplicationInfo, Settings.getPackageLPw (including BPackage
component/signing conversion and generated ApplicationInfo), and stopping old
guest processes. A failure in any of those can produce the same category.
parserApk itself catches exceptions and returns null, which normally produces
package_parse_failed instead. Therefore the supplied category alone cannot prove
the hidden PackageParser or split APK parsing is the failing operation.

Android 9 AOSP confirms that ApplicationInfo.splitNames and PackageParser's
signing-details fields already exist. Removing split metadata or signature
collection merely because the device runs API 28 is not justified:
https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/core/java/android/content/pm/ApplicationInfo.java
https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/core/java/android/content/pm/PackageParser.java

Changes are diagnostic only:

- Separate split_metadata, host_metadata, package_settings and stop_process
  stages from parse; keep the previous import ordering and operations intact.
- Classify missing field/method/class, linkage, null-reference and reflection
  failures through the existing bounded cause traversal.
- Extend only the fixed result-category allowlist. No raw exception messages,
  field names, paths, accounts or arbitrary class identities are exported.
  Unknown categories remain unclassified; old categories remain accepted.

Validation: InstallFailureDiagnosticsTest (2), BlackBoxInstallDiagnosticsTest (3)
and IdentityImportDiagnosticsTest (2) pass, seven checks total. The source-wiring
test verifies checkpoint order; exception-category tests execute the production
classifier and check nested causes/private-message rejection. Non-root compilation
and the BlackBox Java build pass. Log: ignored temp/issue143-import-diagnostics-check.log.
These tests do not parse the reporter's six APKs or emulate Android 9.

No parsing fallback, split selection, package/signature validation, stored guest,
save, account or device setting changes. No device install, version bump,
GitHub comment, commit, push or release in this step. This does not fix #143;
an affected-device full/fresh report is still required to choose a safe fix.
