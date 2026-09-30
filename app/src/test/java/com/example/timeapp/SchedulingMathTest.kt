package com.example.timeapp

import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.ui.plan.SchedulingMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SchedulingMathTest {
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 9, 29)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m).atZone(zone).toInstant()
    private fun block(id: Long, from: Int, to: Int, d: LocalDate = day) =
        Entry(id, EntryKind.EVENT, "b$id", status = EntryStatus.PLANNED, startAt = at(from, d = d), endAt = at(to, d = d))

    /** The regression: 60 min left with a 30-min future block used to propose only 30 min —
     * and applying replaced the block, leaving 30 min booked for 60 min of work. */
    @Test
    fun `a replacement slot must hold all the remaining work`() {
        val task = Entry(1, EntryKind.TASK, "t", status = EntryStatus.PLANNED, startAt = at(14), endAt = at(14, 30))
        val r = SchedulingMath.remaining(estimateMs = 90 * 60_000L, trackedMs = 30 * 60_000L, entry = task, now = at(9))
        assertEquals(60 * 60_000L, r.remainingMs)
        assertEquals(30 * 60_000L, r.bookedMs)
        assertFalse(r.alreadyCovered)
    }

    @Test
    fun `a booked block longer than the work left is over-allocation`() {
        val task = Entry(1, EntryKind.TASK, "t", status = EntryStatus.PLANNED, startAt = at(14), endAt = at(16))
        val r = SchedulingMath.remaining(60 * 60_000L, 0, task, now = at(9))
        assertTrue(r.alreadyCovered)
        assertEquals(60 * 60_000L, r.overAllocatedMs)
    }

    @Test
    fun `a past block is not counted as booked`() {
        val task = Entry(1, EntryKind.TASK, "t", status = EntryStatus.PLANNED, startAt = at(8), endAt = at(9))
        assertEquals(0L, SchedulingMath.remaining(60 * 60_000L, 0, task, now = at(10)).bookedMs)
    }

    @Test
    fun `slots respect work hours and skip to the next free day`() {
        val busy = listOf(block(2, 9, 18))
        val slot = SchedulingMath.nextFreeSlot(busy, 1, day, 60 * 60_000L, notBefore = at(8), workStart = LocalTime.of(9, 0), workEnd = LocalTime.of(18, 0))!!
        assertEquals(at(9, d = day.plusDays(1)), slot.first)
    }

    @Test
    fun `prerequisites push the earliest start past their blocks`() {
        val prereqs = listOf(block(5, 10, 12), block(6, 13, 15))
        assertEquals(at(15), SchedulingMath.notBeforeForDependencies(at(9), prereqs))
        assertEquals(at(9), SchedulingMath.notBeforeForDependencies(at(9), emptyList()))
    }
}
