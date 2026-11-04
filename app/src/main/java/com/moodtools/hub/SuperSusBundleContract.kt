package com.moodtools.hub

/** Exact resource build; this is a managed bundle, never an ELF/mount profile. */
internal object SuperSusBundleContract {
    const val PACKAGE = "com.je.supersus"
    const val VERSION = "1.82.9.031"
    const val VERSION_CODE = 182090301L
    const val ORIGINAL = "f31bd4299950cf943cd2ee4dc385e2fe150d50979545b716161bbecf21383477"
    const val PATCHED = "ec94e163054384072bb4dc0799a53d4b512dd8b395b192a2bf54adce966db963"
    const val RESOURCE_MANIFEST = "8f64ad02bc17fef35b1dd634b1b76853f03684cf8d31ef17c68079d01aef8633"
    const val PAYLOAD_BYTES = 8131516L
    private const val FILE = "updatescript_500.dll.ab_u_bfcaf68de4d7ed060ce9d02839b0324f"

    fun livePath(userId: Int): String {
        require(userId >= 0)
        return "/storage/emulated/" + userId + "/Android/data/" + PACKAGE + "/files/bundles/data/code/" + FILE
    }

    fun backupDirectory(userId: Int): String {
        require(userId >= 0)
        return "/data/local/tmp/moodtools-supersus-bundle/u" + userId + "/v" + VERSION_CODE
    }

    fun installCommand(userId: Int, staged: String): String {
        require(staged.startsWith("/") && '\n' !in staged && '\u0000' !in staged)
        return command(userId, staged, false)
    }

    fun restoreCommand(userId: Int): String = command(userId, "", true)

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun command(userId: Int, staged: String, restore: Boolean): String {
        val live = livePath(userId)
        val resource = live.substringBeforeLast("/data/code/") + "/assetsversion.json"
        return """
            set -eu
            live=@LIVE@
            backup_dir=@BACKUP@
            backup="${'$'}backup_dir/original.ab"
            next="${'$'}live.jester-next"
            source_file=@SOURCE@
            original=@ORIGINAL@
            patched=@PATCHED@
            wanted=@WANTED@
            restore=@RESTORE@
            die() { echo "bundle-error:${'$'}1"; exit 41; }
            bundle_sha256() { sha256sum "${'$'}1" | cut -d' ' -f1; }
            metadata() { stat -c '%u:%g:%a' "${'$'}1"; ls -Zd "${'$'}1" | awk '{print ${'$'}1}'; }
            [ "${'$'}(id -u)" = 0 ] || die root-required
            [ -f "${'$'}live" ] && [ ! -L "${'$'}live" ] || die missing-or-linked-bundle
            if [ "${'$'}restore" = 0 ]; then
                [ -f @RESOURCE@ ] && [ ! -L @RESOURCE@ ] || die missing-resource-manifest
                [ "${'$'}(bundle_sha256 @RESOURCE@)" = @RESOURCE_HASH@ ] || die unsupported-resource-build
            fi
            old="${'$'}(bundle_sha256 "${'$'}live")"
            [ "${'$'}old" = "${'$'}original" ] || [ "${'$'}old" = "${'$'}patched" ] || die unexpected-live-hash
            am force-stop --user @USER@ @PACKAGE@
            if pidof @PACKAGE@ >/dev/null; then die game-still-running; fi
            if [ "${'$'}restore" = 1 ] && [ "${'$'}old" = "${'$'}original" ]; then
                echo "bundle-original:${'$'}original"; exit 0
            fi
            [ ! -L "${'$'}backup_dir" ] || die linked-backup-directory
            mkdir -p "${'$'}backup_dir"
            chown 0:0 "${'$'}backup_dir"
            chmod 0700 "${'$'}backup_dir"
            [ ! -L "${'$'}backup" ] || die linked-backup
            if [ ! -e "${'$'}backup" ]; then
                [ "${'$'}old" = "${'$'}original" ] || die original-backup-missing
                [ ! -e "${'$'}backup.pending" ] || die backup-pending-needs-review
                cp "${'$'}live" "${'$'}backup.pending"
                [ "${'$'}(bundle_sha256 "${'$'}backup.pending")" = "${'$'}original" ] || die backup-copy-failed
                chown 0:0 "${'$'}backup.pending"
                chmod 0400 "${'$'}backup.pending"
                mv "${'$'}backup.pending" "${'$'}backup"
            fi
            [ -f "${'$'}backup" ] && [ "${'$'}(bundle_sha256 "${'$'}backup")" = "${'$'}original" ] || die invalid-original-backup
            if [ "${'$'}restore" = 1 ]; then source_file="${'$'}backup"; fi
            [ -f "${'$'}source_file" ] && [ ! -L "${'$'}source_file" ] || die missing-or-linked-source
            [ "${'$'}(bundle_sha256 "${'$'}source_file")" = "${'$'}wanted" ] || die staged-hash-mismatch
            before="${'$'}(metadata "${'$'}live")"
            owner="${'$'}(stat -c '%u:%g' "${'$'}live")"
            mode="${'$'}(stat -c '%a' "${'$'}live")"
            label="${'$'}(ls -Zd "${'$'}live" | awk '{print ${'$'}1}')"
            [ ! -L "${'$'}next" ] || die linked-next-file
            if [ -e "${'$'}next" ]; then
                pending="${'$'}(bundle_sha256 "${'$'}next")"
                [ "${'$'}pending" = "${'$'}original" ] || [ "${'$'}pending" = "${'$'}patched" ] || die unexpected-next-file
            fi
            cp "${'$'}source_file" "${'$'}next"
            [ "${'$'}(bundle_sha256 "${'$'}next")" = "${'$'}wanted" ] || die next-hash-mismatch
            chown "${'$'}owner" "${'$'}next" || die next-owner-failed
            chmod "${'$'}mode" "${'$'}next" || die next-mode-failed
            if [ "${'$'}(ls -Zd "${'$'}next" | awk '{print ${'$'}1}')" != "${'$'}label" ]; then
                chcon "${'$'}label" "${'$'}next" || die next-label-failed
            fi
            [ "${'$'}(metadata "${'$'}next")" = "${'$'}before" ] || die next-metadata-mismatch
            [ "${'$'}(bundle_sha256 "${'$'}live")" = "${'$'}old" ] || die live-changed-during-prepare
            sync
            mv -f "${'$'}next" "${'$'}live"
            [ "${'$'}(bundle_sha256 "${'$'}live")" = "${'$'}wanted" ] || die installed-hash-mismatch
            [ "${'$'}(metadata "${'$'}live")" = "${'$'}before" ] || die installed-metadata-mismatch
            sync
            echo "bundle-verified:${'$'}wanted"
        """.trimIndent()
            .replace("@LIVE@", quote(live))
            .replace("@BACKUP@", quote(backupDirectory(userId)))
            .replace("@SOURCE@", quote(staged))
            .replace("@ORIGINAL@", quote(ORIGINAL))
            .replace("@PATCHED@", quote(PATCHED))
            .replace("@WANTED@", quote(if (restore) ORIGINAL else PATCHED))
            .replace("@RESTORE@", if (restore) "1" else "0")
            .replace("@RESOURCE_HASH@", quote(RESOURCE_MANIFEST))
            .replace("@RESOURCE@", quote(resource))
            .replace("@PACKAGE@", quote(PACKAGE))
            .replace("@USER@", userId.toString())
    }
}
