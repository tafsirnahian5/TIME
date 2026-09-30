package com.example.timeapp

import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.ui.plan.DaySpan
import com.example.timeapp.ui.plan.Lane
import com.example.timeapp.ui.plan.layoutLanes
import com.example.timeapp.ui.plan.spansOn
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TimeGridLayoutTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val day = LocalDate.of(2026, 9, 28)
    private fun block(id: Long, from: String, to: String, toDay: LocalDate = day) = Entry(
        id, EntryKind.EVENT, "b$id", status = EntryStatus.PLANNED,
        startAt = day.atTime(java.time.LocalTime.parse(from)).atZone(zone).toInstant(),
        endAt = toDay.atTime(java.time.LocalTime.parse(to)).atZone(zone).toInstant(),
    )

    @Test
    fun `a block across midnight shows on both days, clipped`() {
        val late = block(1, "23:00", "01:00", toDay = day.plusDays(1))
        assertEquals(listOf(DaySpan(1, 23 * 60, 24 * 60)), spansOn(listOf(late), day, zone))
        assertEquals(listOf(DaySpan(1, 0, 60)), spansOn(listOf(late), day.plusDays(1), zone))
    }

    @Test
    fun `untimed tasks are not placed`() {
        val due = Entry(2, EntryKind.TASK, "due", status = EntryStatus.PLANNED, dueOn = day)
        assertEquals(emptyList<DaySpan>(), spansOn(listOf(due), day, zone))
    }

    @Test
    fun `separate blocks each get the full width`() {
        val lanes = layoutLanes(listOf(DaySpan(1, 540, 600), DaySpan(2, 600, 660)))
        assertEquals(Lane(0, 1), lanes[1])
        assertEquals(Lane(0, 1), lanes[2])
    }

    @Test
    fun `overlapping blocks sit side by side and share a lane count`() {
        // 1 overlaps 2, 2 overlaps 3, 1 and 3 don't: one cluster, two lanes, 3 reuses lane 0.
        val lanes = layoutLanes(listOf(DaySpan(1, 540, 600), DaySpan(2, 570, 660), DaySpan(3, 610, 700)))
        assertEquals(Lane(0, 2), lanes[1])
        assertEquals(Lane(1, 2), lanes[2])
        assertEquals(Lane(0, 2), lanes[3])
    }

    /** Two 5-minute blocks 10 minutes apart are drawn 20 minutes tall, so they must not stack. */
    @Test
    fun `short blocks are laid out by their drawn height`() {
        val lanes = layoutLanes(listOf(DaySpan(1, 540, 545), DaySpan(2, 550, 555)))
        assertEquals(2, lanes[1]!!.columns)
    }
}
