package com.example.timeapp.data

import com.example.timeapp.ui.plan.PlanScope

/**
 * How the Board orders the cards inside a column. Only [MANUAL] reads `boardOrder`; the other
 * two are views over the same cards and **never rewrite it** (the PDF: an explicit sort mode
 * that doesn't reshuffle the manual order) — switching back to Manual finds every card where
 * the user left it.
 */
enum class BoardSort(val label: String) { MANUAL("Manual"), PRIORITY("Priority"), DUE("Due date") }

fun sortBoardColumn(cards: List<Entry>, sort: BoardSort): List<Entry> = when (sort) {
    BoardSort.MANUAL -> cards.sortedWith(compareBy<Entry>({ it.boardOrder }, { it.id }))
    // Highest priority first; ties keep the manual order so the view stays stable.
    BoardSort.PRIORITY -> cards.sortedWith(compareByDescending<Entry> { it.priority.ordinal }.thenBy { it.boardOrder }.thenBy { it.id })
    // Soonest first; a scheduled block counts as its date; undated cards go last.
    BoardSort.DUE -> cards.sortedWith(
        compareBy<Entry> { it.dueOn ?: it.startAt?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() ?: java.time.LocalDate.MAX }
            .thenBy { it.boardOrder }.thenBy { it.id },
    )
}

/** A named project/tag filter the user saved for the List and Board (the PDF's saved filters). */
data class SavedFilter(val name: String, val scope: PlanScope)

/**
 * Stored as one settings string, one filter per line: `name\tprojectId\ttag` (empty = any).
 * Tabs and newlines can't be typed into the name field's single line, and are stripped anyway.
 */
object SavedFilters {
    fun encode(filters: List<SavedFilter>): String = filters.joinToString("\n") { f ->
        listOf(clean(f.name), f.scope.projectId?.toString().orEmpty(), f.scope.tag.orEmpty()).joinToString("\t")
    }

    fun decode(raw: String?): List<SavedFilter> = raw.orEmpty().lines().mapNotNull { line ->
        val parts = line.split("\t")
        val name = parts.getOrNull(0)?.trim().orEmpty()
        if (name.isEmpty()) return@mapNotNull null
        val scope = PlanScope(projectId = parts.getOrNull(1)?.toLongOrNull(), tag = parts.getOrNull(2)?.takeIf { it.isNotBlank() })
        if (!scope.isActive) null else SavedFilter(name, scope)
    }

    fun clean(name: String) = name.replace('\t', ' ').replace('\n', ' ').trim()
}
