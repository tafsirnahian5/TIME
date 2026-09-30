package com.example.timeapp

import com.example.timeapp.data.BoardColumn
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.boardColumnFor
import com.example.timeapp.data.canDeleteColumn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardColumnTest {
    private val columns = listOf(
        BoardColumn(1, "Ready", EntryStatus.PLANNED, 0),
        BoardColumn(2, "Doing", EntryStatus.DOING, 1),
        BoardColumn(3, "Review", EntryStatus.DOING, 2),
        BoardColumn(4, "Done", EntryStatus.DONE, 3),
    )
    private fun card(status: EntryStatus, columnId: Long? = null) = Entry(9, EntryKind.TASK, "t", status = status, columnId = columnId)

    @Test
    fun `a card stays in the column it was put in while the state matches`() {
        assertEquals(3L, boardColumnFor(card(EntryStatus.DOING, columnId = 3), columns)?.id)
    }

    @Test
    fun `a state change sends it to that state's first column`() {
        assertEquals(4L, boardColumnFor(card(EntryStatus.DONE, columnId = 3), columns)?.id)
        assertEquals(2L, boardColumnFor(card(EntryStatus.DOING), columns)?.id)
    }

    /** Overdue is display-only; it sits in a Planned column. */
    @Test
    fun `overdue cards live in the planned column`() {
        assertEquals(1L, boardColumnFor(card(EntryStatus.OVERDUE), columns)?.id)
    }

    @Test
    fun `a state's last column can't be deleted`() {
        assertTrue(canDeleteColumn(columns[2], columns))
        assertFalse(canDeleteColumn(columns[0], columns))
    }
}
