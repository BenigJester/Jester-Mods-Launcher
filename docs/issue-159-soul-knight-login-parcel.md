# Issue 159: Soul Knight login parcel/class lookup failure

https://github.com/BenigJester/Jester-Mods-Launcher/issues/159

Launcher 4.6.3, non-root direct patch, Soul Knight 8.7.0 (80700), INFINIX
X6837/API33. The selected attempt has Google account support disabled, companion
missing, profile off. BasePlayerActivity resumes; GamesResolutionActivity is
created, followed by an uncaught Error/RuntimeException wrapping
BadParcelableException and ClassNotFoundException during Parcel class lookup.
The shortened report contains no missing class name or full-file attachment.

This identifies a parcel class lookup failure during login, but not the missing
class, the originating data, or the correct loader/provider. It does not prove
that setting an extras class loader, adding a stub class, or changing GmsCore
routing would fix it. Issue 158 is a separate attempt with support enabled and
a user-requested termination after a long run, not evidence of this crash.

Added a fixed parcel_class_not_found hint when both exception types occur in
the bounded cause chain. Full failure records retain a missing SDK class only
when a ClassNotFoundException message is exactly a bounded qualified class name
under com.google.android.gms, org.microg or com.chillyroomsdk. Loader descriptions,
paths, arbitrary messages and other namespaces are rejected. The hint appears
in the existing shortened-report allowlist. Runtime exceptions still propagate;
no login behavior or game initialization was changed.

FailureEvidence is shared by BlackBox and the diagnostic bootstrap embedded in
direct patches. Existing patched APKs need an updated embedded bootstrap to
produce this evidence; updating only the launcher cannot replace code already
inside a game APK. Do not uninstall or clear saves to obtain a report.

Validation: diagnostic-bootstrap:testDebugUnitTest,
DiagnosticFailureEvidenceTest and compileNonrootDebugKotlin passed. New tests
execute strict class-name extraction and private-message rejection. No affected
device reproduction or successful login verification. Log: temp/issue159-check.log.
No commit, push, version bump, issue reply or publication in this step.
