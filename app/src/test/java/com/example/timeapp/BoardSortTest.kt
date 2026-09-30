package com.example.timeapp

import com.example.timeapp.data.BoardSort
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.Priority
import com.example.timeapp.data.SavedFilter
import com.example.timeapp.data.SavedFilters
import com.example.timeapp.data.sortBoardColumn
import com.example.timeapp.ui.plan.PlanScope
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BoardSortTest {
    private fun card(id: Long, order: Int, priority: Priority = Priority.NORMAL, due: LocalDate? = null) =
        Entry(id, EntryKind.TASK, "c$id", status = EntryStatus.PLANNED, boardOrder = order, priority = priority, dueOn = due)

    private val cards = listOf(
        card(1, 2, Priority.LOW, LocalDate.of(2026, 10, 3)),
        card(2, 0, Priority.URGENT, null),
        card(3, 1, Priority.HIGH, LocalDate.of(2026, 9, 29)),
        card(4, 3, Priority.HIGH, LocalDate.of(2026, 9, 29)),
    )

    @Test fun manualFollowsBoardOrder() = assertEquals(listOf(2L, 3L, 1L, 4L), sortBoardColumn(cards, BoardSort.MANUAL).map { it.id })

    @Test fun priorityHighestFirstTiesKeepManualOrder() =
        assertEquals(listOf(2L, 3L, 4L, 1L), sortBoardColumn(cards, BoardSort.PRIORITY).map { it.id })

    @Test fun dueSoonestFirstUndatedLast() = assertEquals(listOf(3L, 4L, 1L, 2L), sortBoardColumn(cards, BoardSort.DUE).map { it.id })

    @Test fun sortingNeverChangesTheCardsThemselves() {
        sortBoardColumn(cards, BoardSort.DUE)
        assertEquals(listOf(2, 0, 1, 3), cards.map { it.boardOrder })
    }

    @Test fun savedFiltersRoundTripAndDropEmptyOnes() {
        val filters = listOf(SavedFilter("Client work", PlanScope(projectId = 7)), SavedFilter("Android\tbits", PlanScope(tag = "android")))
        val decoded = SavedFilters.decode(SavedFilters.encode(filters))
        assertEquals(listOf("Client work", "Android bits"), decoded.map { it.name })
        assertEquals(PlanScope(tag = "android"), decoded[1].scope)
        assertEquals(emptyList<SavedFilter>(), SavedFilters.decode("Nothing\t\t\n\t5\t"))
    }
}
