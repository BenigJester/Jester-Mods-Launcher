# Jester Mods Launcher 4.6.6

Build: 466

- Fixed requirements guidance when the installed game is newer than the add-on supports. Incompatible store releases no longer prompt a Google Play update.
- Improved APKPure downloads to look for exact supported game versions and builds, and retain compatible cached download details.
- Added a browser search for the game name and supported version when no matching APKPure download is confirmed. Older supported APKPure builds open in the browser with downgrade guidance.
- Moved preservation of existing game expansion files before package replacement confirmation. Storage-access denial now explains why setup stopped and tells users to keep their game and data.
- Corrected Android installer timeout messages and kept recovery checks running when the completion callback is missing while Android's prompt is foregrounded.
- Improved Root timeout diagnostics with separate process-monitoring and launch markers, command duration, configured timeout, and last-output age.
- Added offline translations for the new compatibility, browser search, installer recovery, and storage-access messages in all nine supported non-English languages.

The Root changes improve diagnostic evidence; they do not claim to fix the reported Root startup failures or change injection behavior. Android's downgrade and storage restrictions still apply.
