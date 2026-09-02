package com.moodtools.hub.modules

internal enum class ChangelogCategory(val label: String) {
    FIX("Fix"),
    ADD("Add"),
    IMPROVEMENTS("Improvements")
}

internal data class ChangelogRundownGroup(
    val category: ChangelogCategory,
    val items: List<String>
)

/** Turns signed release-note prose or pre-categorized notes into a consistent, scannable rundown. */
internal fun changelogRundown(notes: String, fallback: String): List<ChangelogRundownGroup> {
    val source = notes.trim().ifEmpty { fallback.trim() }
    if (source.isEmpty()) return emptyList()

    val grouped = ChangelogCategory.entries.associateWith { linkedSetOf<String>() }.toMutableMap()
    var explicitCategory: ChangelogCategory? = null

    source.lineSequence().forEach { sourceLine ->
        val line = sourceLine.trim().removeListMarker()
        if (line.isEmpty()) return@forEach

        val heading = CHANGELOG_HEADING.matchEntire(line)
        if (heading != null) {
            explicitCategory = heading.groupValues[1].toChangelogCategory()
            heading.groupValues[2].trim().takeIf(String::isNotEmpty)?.let { inlineNotes ->
                splitChangelogItems(inlineNotes).forEach { item ->
                    grouped.getValue(explicitCategory!!).add(item.normalizeChangelogVerb(explicitCategory!!))
                }
            }
            return@forEach
        }

        splitChangelogItems(line).forEach { item ->
            val category = explicitCategory ?: item.inferredChangelogCategory()
            grouped.getValue(category).add(item.normalizeChangelogVerb(category))
        }
    }

    return ChangelogCategory.entries.mapNotNull { category ->
        grouped.getValue(category).takeIf(Set<String>::isNotEmpty)?.let { items ->
            ChangelogRundownGroup(category, items.toList())
        }
    }
}

private fun splitChangelogItems(value: String): List<String> = value
    .split(SENTENCE_BOUNDARY)
    .flatMap { sentence -> sentence.split(ACTION_CLAUSE_BOUNDARY) }
    .map { it.trim().removeListMarker().trim() }
    .filter(String::isNotEmpty)

private fun String.removeListMarker(): String =
    replaceFirst(LIST_MARKER, "").trim().trimStart('#').trim()

private fun String.toChangelogCategory(): ChangelogCategory = when {
    startsWith("fix", ignoreCase = true) -> ChangelogCategory.FIX
    startsWith("add", ignoreCase = true) -> ChangelogCategory.ADD
    else -> ChangelogCategory.IMPROVEMENTS
}

private fun String.inferredChangelogCategory(): ChangelogCategory {
    val normalized = lowercase()
    return when {
        FIX_LEADING_VERB.containsMatchIn(normalized) -> ChangelogCategory.FIX
        ADD_LEADING_VERB.containsMatchIn(normalized) -> ChangelogCategory.ADD
        IMPROVEMENT_LEADING_VERB.containsMatchIn(normalized) -> ChangelogCategory.IMPROVEMENTS
        FIX_WORDS.any { it.containsMatchIn(normalized) } -> ChangelogCategory.FIX
        ADD_WORDS.any { it.containsMatchIn(normalized) } -> ChangelogCategory.ADD
        else -> ChangelogCategory.IMPROVEMENTS
    }
}

private fun String.normalizeChangelogVerb(category: ChangelogCategory): String {
    val compact = replace(WHITESPACE, " ").trim()
    return when (category) {
        ChangelogCategory.FIX -> compact.replaceFirst(FIX_LEADING_VERB, "Fixed ")
        ChangelogCategory.ADD -> compact.replaceFirst(ADD_LEADING_VERB, "Added ")
        ChangelogCategory.IMPROVEMENTS -> compact.replaceFirst(IMPROVEMENT_LEADING_VERB, "Improved ")
    }
}

private val CHANGELOG_HEADING = Regex(
    "^(fix(?:es|ed)?|add(?:s|ed)?|improvements?|improved)\\s*:\\s*(.*)$",
    RegexOption.IGNORE_CASE
)
private val LIST_MARKER = Regex("^(?:[•*+\\-–—]|\\d+[.)])\\s*")
private val SENTENCE_BOUNDARY = Regex("(?<=[.!?])\\s+(?=[A-Z0-9])")
private val ACTION_CLAUSE_BOUNDARY = Regex(
    ",\\s+(?:and\\s+)?(?=(?:adds?|fix(?:es|ed)?|improves?|uses?|shows?|keeps?|prevents?|restores?|supports?|removes?|loads?|persists?|checks?|allows?|provides?|introduces?|retains?|makes?)\\b)",
    RegexOption.IGNORE_CASE
)
private val WHITESPACE = Regex("\\s+")
private val FIX_LEADING_VERB = Regex("^(?:fix|fixes|fixed|fixing)\\s+", RegexOption.IGNORE_CASE)
private val ADD_LEADING_VERB = Regex("^(?:add|adds|added|adding)\\s+", RegexOption.IGNORE_CASE)
private val IMPROVEMENT_LEADING_VERB = Regex(
    "^(?:improve|improves|improved|improving)\\s+",
    RegexOption.IGNORE_CASE
)
private val FIX_WORDS = listOf(
    Regex("\\bfix(?:es|ed|ing)?\\b"),
    Regex("\\b(?:resolve|repair|restore|correct|prevent)(?:s|ed|ing)?\\b"),
    Regex("\\b(?:crash|failure|failed|error|broken|incompatible)\\b")
)
private val ADD_WORDS = listOf(
    Regex("\\badd(?:s|ed|ing)?\\b"),
    Regex("\\b(?:introduce|provide)(?:s|d|ing)?\\b"),
    Regex("\\bnew\\b")
)
