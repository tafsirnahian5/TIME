package com.example.timeapp

import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.db.toEntity
import com.example.timeapp.data.displayStatus
import com.example.timeapp.data.reopenedStatus
import com.example.timeapp.data.transitionLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EntryStatusTest {
    private val today = LocalDate.of(2026, 9, 28)
    private fun task(status: EntryStatus, due: LocalDate?) = Entry(1, EntryKind.TASK, "t", status = status, dueOn = due)

    @Test
    fun `overdue is derived from the due date`() {
        assertEquals(EntryStatus.OVERDUE, task(EntryStatus.PLANNED, today.minusDays(1)).displayStatus(today))
        assertEquals(EntryStatus.PLANNED, task(EntryStatus.PLANNED, today).displayStatus(today))
        assertEquals(EntryStatus.PAUSED, task(EntryStatus.PAUSED, today.minusDays(3)).displayStatus(today))
    }

    /** The regression: an edited overdue task was saved as OVERDUE and stayed overdue after
     * its due date moved. Overdue must never reach the database. */
    @Test
    fun `overdue is never written`() {
        assertEquals(EntryStatus.PLANNED, task(EntryStatus.OVERDUE, today.plusDays(7)).toEntity().status)
    }

    @Test
    fun `history labels read as actions`() {
        assertEquals("Switched to Client call", transitionLabel(EntryStatus.DOING, EntryStatus.PAUSED, "Client call", switched = true))
        assertEquals("Paused", transitionLabel(EntryStatus.DOING, EntryStatus.PAUSED, null, switched = false))
        assertEquals("Resumed", transitionLabel(EntryStatus.PAUSED, EntryStatus.DOING, null, switched = false))
        assertEquals("Started while blocked", transitionLabel(EntryStatus.BLOCKED, EntryStatus.DOING, null, switched = false))
        assertEquals("Reopened", transitionLabel(EntryStatus.DONE, EntryStatus.UNSCHEDULED, null, switched = false))
    }

    /** Reopening used to drop every task into the inbox, even one with a due date or block. */
    @Test
    fun `reopening returns a dated task to planned`() {
        assertEquals(EntryStatus.PLANNED, reopenedStatus(task(EntryStatus.DONE, today.plusDays(2))))
        assertEquals(EntryStatus.UNSCHEDULED, reopenedStatus(task(EntryStatus.DONE, null)))
        val blocked = task(EntryStatus.DONE, null).copy(startAt = java.time.Instant.parse("2026-09-28T09:00:00Z"))
        assertEquals(EntryStatus.PLANNED, reopenedStatus(blocked))
    }
}
