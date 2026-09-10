package com.moodtools.hub.modules

import com.moodtools.hub.LauncherLocalization
import com.moodtools.hub.LauncherAdditionalTranslations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLanguageTest {
    @Test
    fun preferenceOrderMatchesOfflineTranslator() {
        assertEquals(LauncherLanguage.English, LauncherLanguage.fromPreference(0))
        assertEquals(LauncherLanguage.Filipino, LauncherLanguage.fromPreference(1))
        assertEquals(LauncherLanguage.Korean, LauncherLanguage.fromPreference(2))
        assertEquals(LauncherLanguage.Japanese, LauncherLanguage.fromPreference(3))
        assertEquals(LauncherLanguage.ChineseSimplified, LauncherLanguage.fromPreference(4))
        assertEquals(LauncherLanguage.Spanish, LauncherLanguage.fromPreference(5))
        assertEquals(LauncherLanguage.Vietnamese, LauncherLanguage.fromPreference(6))
        assertEquals(LauncherLanguage.Indonesian, LauncherLanguage.fromPreference(7))
        assertEquals(LauncherLanguage.Portuguese, LauncherLanguage.fromPreference(8))
        assertEquals(LauncherLanguage.English, LauncherLanguage.fromPreference(99))
    }

    @Test
    fun themePreferenceUsesMidnightAsSafeFallback() {
        assertEquals(LauncherTheme.Midnight, LauncherTheme.fromPreference(0))
        assertEquals(LauncherTheme.Aurora, LauncherTheme.fromPreference(1))
        assertEquals(LauncherTheme.Royal, LauncherTheme.fromPreference(2))
        assertEquals(LauncherTheme.Ember, LauncherTheme.fromPreference(3))
        assertEquals(LauncherTheme.Ocean, LauncherTheme.fromPreference(4))
        assertEquals(LauncherTheme.Sakura, LauncherTheme.fromPreference(5))
        assertEquals(LauncherTheme.Obsidian, LauncherTheme.fromPreference(6))
        assertEquals(LauncherTheme.Midnight, LauncherTheme.fromPreference(-1))
    }

    @Test
    fun launcherChromeTranslatesOfflineForEveryLanguage() {
        LauncherLanguage.entries.drop(1).forEach { language ->
            val translated = LauncherLocalization.translate("Settings", language)
            assertTrue(translated.isNotBlank())
            assertNotEquals("Settings", translated)
        }
        assertEquals("Unknown server title", LauncherLocalization.translate("Unknown server title", LauncherLanguage.Japanese))
    }

    @Test
    fun launcherScreensAndDynamicLabelsTranslateOffline() {
        val screenText = listOf(
            "A NEW ERA AWAITS",
            "Your support code",
            "Search update activity",
            "ADD ADD-ON",
            "Original game needed",
            "How exact-package shell works",
            "Support code copied"
        )
        LauncherLanguage.entries.drop(1).forEach { language ->
            screenText.forEach { text ->
                assertTrue(LauncherLocalization.translate(text, language).isNotBlank())
            }
            assertNotEquals(
                "Choose your preferred language for Jester Mods.",
                LauncherLocalization.translate("Choose your preferred language for Jester Mods.", language)
            )
            assertNotEquals(
                "Your choice is saved offline on this device.",
                LauncherLocalization.translate("Your choice is saved offline on this device.", language)
            )
            assertNotEquals("Game version 3.0.4", LauncherLocalization.translate("Game version 3.0.4", language))
            assertNotEquals("Sort: Recommended", LauncherLocalization.translate("Sort: Recommended", language))
            assertNotEquals("Copied support code", LauncherLocalization.translate("Copied support code", language))
            assertNotEquals("Help", LauncherLocalization.translate("Help", language))
            assertNotEquals("Open issue page", LauncherLocalization.translate("Open issue page", language))
        }
    }

    @Test
    fun previouslyHardcodedLauncherStatusAndAccessibilityTextTranslateOffline() {
        val staticText = listOf(
            "Preparing download",
            "Account identity",
            "Installation permission is needed",
            "Settings, launcher update available",
            "Limited access public add-on. In-game eligibility requirements may apply.",
            "ADD-ON RELEASE",
            "GAME RELEASE",
            "Requirements are satisfied. The in-game menu will show a compact runtime status only.",
            "Jester Mods starts the installed game and injects the verified add-on through the root runtime. The original game package and signing certificate stay unchanged.",
            "Show",
            "Hide"
        )
        LauncherLanguage.entries.drop(1).forEach { language ->
            staticText.forEach { text ->
                assertNotEquals(text, LauncherLocalization.translate(text, language))
            }
            val progress = LauncherLocalization.translate("Download 73 percent complete", language)
            assertNotEquals("Download 73 percent complete", progress)
            assertTrue(progress.contains("73"))

            val selection = LauncherLocalization.translate("Select Soul Knight", language)
            assertNotEquals("Select Soul Knight", selection)
            assertTrue(selection.contains("Soul Knight"))
        }
    }

    @Test
    fun addOnCatalogAndLibraryDetailsTranslateOfflineForEveryLanguage() {
        val staticText = listOf(
            "ADD-ON",
            "PLAY",
            "Discover what is included, confirm compatibility, and choose how to add it.",
            "Your complete space to review features, manage compatibility, and launch.",
            "All categories",
            "The add-on catalog is unavailable. Check your connection and try again.",
            "All available add-ons are already installed. You can find them in your library.",
            "No add-ons match these filters",
            "Try another search, category, or filter.",
            "Clear filters",
            "All add-ons",
            "Results",
            "Loading more add-ons…",
            "Installed game",
            "Architecture",
            "Compatible versions",
            "Compatible builds",
            "Recommended setup",
            "Alternative setup",
            "Check temporarily unavailable",
            "Google Play has a newer game release. This add-on is marked as being updated for it.",
            "Google Play has a newer release. This add-on is marked as being updated for it.",
            "ADD-ON PACKAGE",
            "Add-on version",
            "Add-on size",
            "Size unavailable",
            "Original game download",
            "Total download",
            "Package",
            "GET THE GAME",
            "Game companion installed",
            "Game companion needs attention",
            "Game companion install paused",
            "Secure installer window active",
            "The add-on update window has priority. Companion progress will return when that update finishes.",
            "Progress, cancellation, recovery, and diagnostics are available in the companion installer window.",
            "Google Play listing not detected",
            "Available from Google Play",
            "Update from Google Play",
            "The launcher couldn't confirm this package on Google Play, so the store action is hidden.",
            "Google Play handles the original game installation and updates.",
            "Download original game",
            "Compatible game update",
            "The available download is not newer than your installed game.",
            "Installer window active",
            "Use installer window",
            "Get from Google Play",
            "Update in Google Play",
            "Download game",
            "Download game update",
            "Open Google Play instead",
            "This device isn't supported",
            "This device version isn't supported",
            "The installed game architecture does not match the available Jester Mods add-on.",
            "Downloading add-on",
            "Download complete",
            "Couldn't download add-on",
            "Jester Mods will download the add-on for this installed game.",
            "Back to library",
            "Update add-on",
            "Add add-on",
            "Install the original game to continue",
            "A compatible game version and build are needed",
            "This installed version isn't supported",
            "Ready to add to your library",
            "Get game",
            "Get compatible game",
            "View details",
            "Add to launcher",
            "Keep this add-on ready for the game.",
            "Check for the latest version before you play.",
            "Download size",
            "Install original game",
            "Download update",
            "Check for updates",
            "Install the original game to make this Library entry ready to play.",
            "This game version and architecture are not supported yet.",
            "This game version is not supported yet.",
            "This game architecture is not supported by this add-on.",
            "Repair the add-on before opening this game.",
            "Create and install the patched game first. Android will ask before removing the current Play-signed installation.",
            "The embedded add-on changed. Update the patched game in place before playing.",
            "A previous patched installation was detected. Restore the official game before creating its exact-package shell.",
            "Preserve the untouched game and install its game-branded exact-package shell.",
            "Jester Mods will open the game with your features ready.",
            "Ready to play",
            "Running and ready",
            "Install the original game first. The launcher will re-check compatibility automatically.",
            "Keep the game on a supported version before opening it from the Library.",
            "This add-on only supports the listed device architecture.",
            "Repair the add-on package so the runtime menu can load cleanly.",
            "The add-on is downloaded. Build and install the patched game before the Play button becomes available.",
            "Install the refreshed patch in place so the game contains this add-on version.",
            "Jester Mods detected its previous patch and will keep it out of the new shell. Restore the official Google Play game first.",
            "Jester Mods will preserve the untouched game package and create a shell with its exact name and icon.",
            "The maintainer is updating this add-on for the newer game release shown by Google Play.",
            "Not installed",
            "Supported versions",
            "Supported builds",
            "Supported architecture",
            "Selected method"
        )

        LauncherLanguage.entries.drop(1).forEach { language ->
            val missing = staticText.filter { text ->
                LauncherLocalization.translate(text, language) == text
            }
            assertTrue("Missing $language translations: ${missing.joinToString(" | ")}", missing.isEmpty())
        }
    }

    @Test
    fun addOnCatalogAccessTypesTranslateOfflineForEveryLanguage() {
        val staticText = listOf(
            "Recommended for your device",
            "Private add-on",
            "Private add-on, approval is verified before use",
            "PRIVATE ACCESS",
            "VERIFIED",
            "PROTECTED",
            "Private add-on access is active for this device.",
            "Access is verified before this add-on can be downloaded or launched.",
            "GRANTED UNTIL",
            "ACCESS",
            "Approval required",
            "AVAILABLE UNTIL",
            "Limited access public add-on. In-game eligibility requirements may apply.",
            "LIMITED ACCESS",
            "PUBLIC",
            "In-game requirements may apply",
            "Public to browse and install",
            "Access to some or all features may depend on the game.",
            "This add-on is available to everyone, but access to some or all features may depend on requirements inside the game. Requirements vary; check the guidance shown inside the game.",
            "GAME COMPATIBILITY",
            "READY TO ADD",
            "IN YOUR LIBRARY",
            "UPDATE AVAILABLE",
            "REPAIR NEEDED",
            "GAME REQUIRED",
            "NOT COMPATIBLE"
        )
        LauncherLanguage.entries.drop(1).forEach { language ->
            val missing = staticText.filter { text ->
                LauncherLocalization.translate(text, language) == text
            }
            assertTrue("Missing $language access translations: ${missing.joinToString(" | ")}", missing.isEmpty())

            val privateAccess = "Private access 2 hours, available until Sep 10, 8:00 PM"
            assertNotEquals(privateAccess, LauncherLocalization.translate(privateAccess, language))
            listOf("18 add-ons", "Game 1.51.6", "Ready to add to your library").forEach { text ->
                assertNotEquals(text, LauncherLocalization.translate(text, language))
            }
        }
        assertEquals(
            "라이브러리에 추가할 준비가 되었습니다",
            LauncherLocalization.translate("Ready to add to your library", LauncherLanguage.Korean)
        )
    }

    @Test
    fun loadingDialogsAndDiagnosticsTranslateOfflineForEveryLanguage() {
        val text = listOf(
            "DIAGNOSTICS", "Inspect", "Copy diagnostics", "OK", "All changelogs",
            "Live launcher update report", "Live add-on transfer report",
            "Live add-on update check report", "PREPARING", "DOWNLOADING",
            "VERIFYING", "ACTIVATING", "INSTALLING", "SECURE TRANSFER",
            "Prepare", "Download", "Verify", "Install", "Activate", "Connect",
            "Compare", "Review", "Queue", "Preparing secure update",
            "Downloading update", "Verifying package", "Waiting for Android installer",
            "Verified package ready", "Preparing secure transfer", "Downloading package",
            "Activating package", "Waiting for Android", "Complete", "Failed", "Paused",
            "Checking access and signed release details",
            "Checking package hash, identity, and signature",
            "Validating the release identity, version, and changelog",
            "Complete Android's secure prompt. Jester Mods will verify the result when you return.",
            "The verified files are being activated atomically. Keep Jester Mods open.",
            "Android installation in progress", "Verified add-on activation in progress",
            "Loading verified release history…", "Loading feature details…",
            "Preparing the add-on catalog…", "Preparing install details…",
            "Preparing update details…", "Preparing release history…",
            "Preparing device access details…", "Includes launcher improvements and reliability fixes.",
            "Verified package", "Update paused", "Update failed", "Update available",
            "A NEW ERA AWAITS", "New version", "Begin the update", "Install update",
            "Waiting for Android…", "Stopping…", "Finishing…",
            "SECURE GAME COMPANION", "Game installer", "Game setup",
            "EXACT-PACKAGE SHELL", "VERIFIED DIRECT PATCH",
            "Preserving the original game inside its branded shell",
            "Preparing a locally verified patched installation",
            "Android owns the current confirmation. Jester Mods will verify the result when you return.",
            "Jester Mods will verify the installed build when you return.",
            "Reading and hashing package", "Proving integrity and identity",
            "Building Android's install request", "Writing secure install session",
            "Opening Android installer", "Installation complete", "Installation failed",
            "Installation cancelled", "Finalizing verification", "Validating installer contents",
            "LIBRARY UPDATES", "Installed add-on updates", "Your add-ons are current",
            "Some updates need attention", "Updating your library", "Updating all add-ons",
            "Installing add-on update", "All updates installed", "Update needs attention",
            "Updates available", "Another update is in progress", "Queued", "Updating", "Retry",
            "Preparing download…", "Opening from Browse add-ons", "Opening from Library",
            "Opening from Settings", "Jester Mods add-on transfer diagnostics",
            "Payload hashes and signed identity verified", "Activating the verified add-on atomically"
        )
        LauncherLanguage.entries.drop(1).forEach { language ->
            val missing = text.filter { LauncherLocalization.translate(it, language) == it }
            assertTrue("Missing $language dialog translations: ${missing.joinToString(" | ")}", missing.isEmpty())

            listOf(
                "SECURE ${LauncherLocalization.translate("Add-on download", language).uppercase()}",
                "Authorizing and preparing add-on package",
                "Receiving the signed add-on package",
                "Verifying add-on package",
                "Completing safe activation",
                "Android installer is active",
                "4 installer events captured",
                "3 setup events captured",
                "Preparing Township download",
                "Signed manifest accepted for build 107",
                "Township is ready in your library",
                "Township is ready in your library with Jester Mods 1.0.7"
            ).forEach { dynamic ->
                val translated = LauncherLocalization.translate(dynamic, language)
                assertNotEquals(dynamic, translated)
                if (language == LauncherLanguage.Korean) {
                    assertTrue("Mixed Korean dialog translation: $translated", "add-on package" !in translated)
                }
            }
        }
    }

    @Test
    fun generatedLauncherTranslationsPreserveEveryDynamicValue() {
        LauncherAdditionalTranslations.sourceText.forEach { source ->
            val placeholders = Regex("\\{(\\d+)\\}").findAll(source)
                .map { it.groupValues[1].toInt() }
                .toSet()
            val rendered = placeholders.fold(source) { text, index ->
                text.replace("{$index}", "VALUE_$index")
            }
            LauncherLanguage.entries.drop(1).forEach { language ->
                val translated = LauncherLocalization.translate(rendered, language)
                assertTrue("Blank $language translation for $source", translated.isNotBlank())
                placeholders.forEach { index ->
                    assertTrue(
                        "$language translation lost placeholder {$index} for $source",
                        translated.contains("VALUE_$index")
                    )
                }
            }
        }
    }
}
