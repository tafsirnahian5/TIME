package com.example.timeapp

import com.example.timeapp.data.AgendaItem
import com.example.timeapp.data.CalendarFilter
import com.example.timeapp.data.Direction
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.buildAgenda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AgendaTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 28)
    private fun at(day: LocalDate, h: Int) = day.atTime(h, 0).toInstant(zone)

    private fun task(id: Long, due: LocalDate?, status: EntryStatus = EntryStatus.PLANNED) =
        Entry(id, EntryKind.TASK, "t$id", status = status, dueOn = due)
    private fun block(id: Long, day: LocalDate, h: Int) =
        Entry(id, EntryKind.EVENT, "b$id", status = EntryStatus.PLANNED, startAt = at(day, h), endAt = at(day, h + 1))
    private fun plan(id: Long, due: LocalDate, paid: Long = 0) =
        MoneyRecord(id, Direction.COST, "p$id", amountMinor = 1000, paidMinor = paid, dueOn = due, status = PlanStatus.PLANNED)

    @Test
    fun daysInOrderWithTimedFirstAndEmptyDaysLeftOut() {
        val a = buildAgenda(
            listOf(task(1, today), block(2, today, 15), block(3, today, 9), task(4, today.plusDays(3))),
            listOf(plan(5, today)),
            today, 30, CalendarFilter(), zone,
        )
        assertEquals(listOf(today, today.plusDays(3)), a.days.map { it.date })
        val ids = a.days[0].items.map { (it as? AgendaItem.Work)?.entry?.id ?: (it as AgendaItem.Due).plan.id }
        assertEquals(listOf(3L, 2L, 1L, 5L), ids)
    }

    @Test
    fun overdueCollectsUnfinishedAndUnpaidOnly() {
        val a = buildAgenda(
            listOf(task(1, today.minusDays(2)), task(2, today.minusDays(1), EntryStatus.DONE)),
            listOf(plan(3, today.minusDays(5)), plan(4, today.minusDays(5), paid = 1000)),
            today, 30, CalendarFilter(), zone,
        )
        assertEquals(2, a.overdue.size)
        assertTrue(a.days.isEmpty())
    }

    @Test
    fun filtersHideKindsAndFinishedWork() {
        val entries = listOf(task(1, today), block(2, today, 9), task(3, today, EntryStatus.DONE))
        val plans = listOf(plan(4, today))
        val noBlocks = buildAgenda(entries, plans, today, 30, CalendarFilter(appointments = false, costs = false, completed = false), zone)
        assertEquals(listOf(1L), noBlocks.days.single().items.map { (it as AgendaItem.Work).entry.id })
    }

    @Test
    fun windowEndsAfterTheGivenDays() {
        val a = buildAgenda(listOf(task(1, today.plusDays(30)), task(2, today.plusDays(29))), emptyList(), today, 30, CalendarFilter(), zone)
        assertEquals(listOf(today.plusDays(29)), a.days.map { it.date })
    }
}
