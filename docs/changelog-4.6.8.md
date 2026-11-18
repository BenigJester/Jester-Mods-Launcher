# Jester Mods Launcher 4.6.8

Build: 468

Fix:
• Fixed APKPure fallback lookups guessing variant parameters instead of using the exact supported-version download links.
• Fixed old APKPure download metadata surviving launcher upgrades.
• Fixed failed cached links remaining available after APKPure redirects to its homepage.

Improvements:
• Added clearer, translated messages when APKPure cannot provide a download file.
• Improved download validation and disabled retrying confirmed unavailable links; requirements can offer the supported-version search fallback.

These changes do not restore files that APKPure cannot currently serve. No game data is cleared by the metadata-cache refresh.
