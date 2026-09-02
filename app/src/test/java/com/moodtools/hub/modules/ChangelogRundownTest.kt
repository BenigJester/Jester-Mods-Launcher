package com.moodtools.hub.modules

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangelogRundownTest {
    @Test
    fun categorizesPlainReleaseNoteSentencesAndNormalizesLeadingVerbs() {
        val result = changelogRundown(
            notes = "Fixes failed installs. Adds diagnostics. Improved download feedback.",
            fallback = "Unused"
        )

        assertEquals(
            listOf(
                ChangelogRundownGroup(ChangelogCategory.FIX, listOf("Fixed failed installs.")),
                ChangelogRundownGroup(ChangelogCategory.ADD, listOf("Added diagnostics.")),
                ChangelogRundownGroup(
                    ChangelogCategory.IMPROVEMENTS,
                    listOf("Improved download feedback.")
                )
            ),
            result
        )
    }

    @Test
    fun preservesExplicitHeadingsAndBulletsInDisplayOrder() {
        val result = changelogRundown(
            notes = """
                Improvements:
                • Faster startup
                Fix:
                • Fixed stale library state
                Add: Added diagnostics
            """.trimIndent(),
            fallback = "Unused"
        )

        assertEquals(
            listOf(
                ChangelogRundownGroup(ChangelogCategory.FIX, listOf("Fixed stale library state")),
                ChangelogRundownGroup(ChangelogCategory.ADD, listOf("Added diagnostics")),
                ChangelogRundownGroup(ChangelogCategory.IMPROVEMENTS, listOf("Faster startup"))
            ),
            result
        )
    }

    @Test
    fun usesImprovementFallbackForBlankNotes() {
        assertEquals(
            listOf(
                ChangelogRundownGroup(
                    ChangelogCategory.IMPROVEMENTS,
                    listOf("Maintenance and reliability improvements.")
                )
            ),
            changelogRundown("", "Maintenance and reliability improvements.")
        )
    }

    @Test
    fun leadingActionWinsAndLongActionClausesBecomeSeparateBullets() {
        val result = changelogRundown(
            notes = "Adds recovery controls for failed installs, uses precise progress, and shows an animated install state.",
            fallback = "Unused"
        )

        assertEquals(
            listOf(
                ChangelogRundownGroup(
                    ChangelogCategory.ADD,
                    listOf("Added recovery controls for failed installs")
                ),
                ChangelogRundownGroup(
                    ChangelogCategory.IMPROVEMENTS,
                    listOf("uses precise progress", "shows an animated install state.")
                )
            ),
            result
        )
    }
}
