package com.example.timeapp

import com.example.timeapp.data.SeriesOccurrence
import com.example.timeapp.data.planSeriesSplit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** "This and following" on a repeating task or bill — see [planSeriesSplit]. */
class SeriesSplitTest {
    private fun occ(id: Long, day: Int, locked: Boolean = false) =
        SeriesOccurrence(id, LocalDate.of(2026, 10, day), locked)

    @Test
    fun `later occurrences are regenerated unless they carry history`() {
        val rows = listOf(occ(1, 1, locked = true), occ(2, 8), occ(3, 15), occ(4, 22, locked = true), occ(5, 29))
        val split = planSeriesSplit(rows, rows[1])
        assertEquals(listOf(3L, 5L), split.delete)
        assertEquals(listOf(4L), split.moveToNew)
        assertEquals(listOf(1L), split.keepInOld)
        assertEquals(LocalDate.of(2026, 10, 7), split.endOldOn)
    }

    /** A paid October must survive "delete this and following" from September. */
    @Test
    fun `history is never in the delete list`() {
        val rows = listOf(occ(1, 1), occ(2, 8, locked = true), occ(3, 15, locked = true))
        val split = planSeriesSplit(rows, rows[0])
        assertEquals(emptyList<Long>(), split.delete)
        assertEquals(listOf(2L, 3L), split.moveToNew)
    }

    @Test
    fun `splitting at the first occurrence leaves nothing in the old series`() {
        val rows = listOf(occ(1, 1), occ(2, 8))
        val split = planSeriesSplit(rows, rows[0])
        assertEquals(emptyList<Long>(), split.keepInOld)
        assertEquals(listOf(2L), split.delete)
    }
}
