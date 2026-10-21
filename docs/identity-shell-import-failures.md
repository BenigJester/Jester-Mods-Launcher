# Identity-shell import failures (#50–#54)

The reports concern Soul Knight 8.7.0, launcher 4.4.3, on a Realme RMX3834
running Android 15. #50 and #51 contain the same session; #52–#54 show further
attempts. They stop in `importOriginalGame`, before game activity launch or
module staging. They do not establish a module-hook defect.

## Corrections

`BPackageManagerService.installPackageAsUserLocked` previously caught exceptions
and returned an `InstallResult` whose default `success` was still true. It now
starts unsuccessful, sets success only after installation and registration
complete, and records a fixed exception category and stage on failure.

Identity import now records bounded checkpoints for local staging, payload byte
count, available storage, expected split count, install attempts, classified
install results, and verification of the installed package's split count. Null
application information is unavailable (`-1`), not a successfully verified
zero-split installation. No new retries or polling windows were added.

Events retain only fixed categories and numeric/boolean values. Arbitrary error
messages, source paths and labels are not exported. Available storage is a
snapshot; a zero reading can mean unavailable and does not prove a full disk.
The existing diagnostic timeline and sharing UI carry these events; no new UI
or translation keys are needed.

## Remaining validation

The old reports omit the install result and verification counts. The false
success bug is confirmed by code inspection, but is not established as the
Realme user's root cause. The user needs to test a build containing these changes
and, if setup still fails, share the new full report.

The identity-shell activity is packaged into a generated shell APK. Updating
only the launcher does not replace an already-installed shell's code. Any test
release must use the existing shell-repair path (`prepareInstalledShellRepair`),
which rebuilds from the new template and requests an in-place Android update
with `requiresUninstall=false`. Do not instruct the user to uninstall the game or
clear their data. Existing preserved source files remain available until import
is actually verified. Repairing only the add-on files is not a shell-code update.
