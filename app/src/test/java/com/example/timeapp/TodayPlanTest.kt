package com.example.timeapp

import com.example.timeapp.data.Direction
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.HabitForToday
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.Priority
import com.example.timeapp.data.TimelineItem
import com.example.timeapp.data.carryover
import com.example.timeapp.data.commitmentSuggestions
import com.example.timeapp.data.restOfToday
import com.example.timeapp.data.startNext
import com.example.timeapp.data.tasksToday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class TodayPlanTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 28)
    private val now = today.atTime(10, 47).toInstant(zone)
    private fun at(h: Int, m: Int = 0) = today.atTime(h, m).toInstant(zone)

    private fun task(id: Long, title: String, status: EntryStatus = EntryStatus.PLANNED, due: LocalDate? = null,
                     start: java.time.Instant? = null, end: java.time.Instant? = null, priority: Priority = Priority.NORMAL,
                     kind: EntryKind = EntryKind.TASK, completedAt: java.time.Instant? = null) =
        Entry(id, kind, title, status = status, dueOn = due, startAt = start, endAt = end, priority = priority, completedAt = completedAt)

    private fun plan(id: Long, title: String, due: LocalDate?, amount: Long, paid: Long = 0) =
        MoneyRecord(id = id, direction = Direction.COST, title = title, amountMinor = amount, paidMinor = paid, dueOn = due, status = PlanStatus.PLANNED)

    @Test
    fun `rest of today is time-ordered, drops what has ended, puts untimed last`() {
        val items = restOfToday(
            entries = listOf(
                task(1, "Standup", start = at(9), end = at(9, 30), kind = EntryKind.EVENT),   // over
                task(2, "Review", start = at(14), end = at(15)),
                task(3, "Design review", start = at(11), end = at(12), kind = EntryKind.EVENT),
                task(4, "Tax papers", due = today),                                           // due, no block
                task(5, "Tomorrow's", start = at(9).plusSeconds(86_400), end = at(10).plusSeconds(86_400)),
                task(6, "Done already", status = EntryStatus.DONE, due = today),
            ),
            plans = listOf(plan(10, "Internet", today, 120_000), plan(11, "Rent", today.plusDays(2), 1_000_000)),
            habits = listOf(
                HabitForToday(20, "Walk", LocalTime.of(18, 0), done = false),
                HabitForToday(21, "Meditate", LocalTime.of(7, 0), done = false),   // reminder passed
                HabitForToday(22, "Read", LocalTime.of(20, 0), done = true),       // already done
            ),
            now = now, zone = zone,
        )
        assertEquals(listOf("Design review", "Review", "Walk", "Tax papers", "Internet"), items.map { it.title })
        assertEquals(TimelineItem.Kind.EVENT, items[0].kind)
        assertEquals(120_000L, items.last().amountMinor)
    }

    @Test
    fun `a task with a block today is not listed again as due`() {
        val items = restOfToday(listOf(task(1, "Review", due = today, start = at(14), end = at(15))), emptyList(), emptyList(), now, zone)
        assertEquals(1, items.size)
        assertEquals(TimelineItem.Kind.TASK_BLOCK, items.single().kind)
    }

    @Test
    fun `carryover holds only what is past and still open`() {
        val c = carryover(
            listOf(task(1, "Dentist", due = today.minusDays(3)), task(2, "Due today", due = today),
                task(3, "Old but done", status = EntryStatus.DONE, due = today.minusDays(1))),
            listOf(plan(10, "Electric", today.minusDays(1), 50_025, paid = 45_075), plan(11, "Paid off", today.minusDays(1), 1_000, paid = 1_000)),
            today,
        )
        assertEquals(listOf("Dentist"), c.tasks.map { it.title })
        assertEquals(listOf("Electric"), c.plans.map { it.title })
        assertTrue(carryover(emptyList(), emptyList(), today).isEmpty)
    }

    @Test
    fun `suggestions put overdue first and skip chosen, blocked and unremarkable tasks`() {
        val s = commitmentSuggestions(
            listOf(
                task(1, "Someday"),
                task(2, "Urgent", priority = Priority.URGENT),
                task(3, "Due today", due = today),
                task(4, "Overdue", due = today.minusDays(2)),
                task(5, "Chosen", due = today),
                task(6, "Blocked", status = EntryStatus.BLOCKED, due = today),
            ),
            today, chosen = setOf(5L), zone = zone,
        )
        assertEquals(listOf("Overdue", "Due today", "Urgent"), s.map { it.title })
    }

    @Test
    fun `start next prefers an open commitment, then the top suggestion`() {
        val entries = listOf(task(1, "Committed"), task(2, "Due today", due = today), task(3, "Done", status = EntryStatus.DONE))
        assertEquals("Committed", startNext(entries, listOf(3L, 1L), today, zone)?.title)
        assertEquals("Due today", startNext(entries, emptyList(), today, zone)?.title)
        assertNull(startNext(listOf(task(1, "Someday")), emptyList(), today, zone))
    }

    @Test
    fun `tasks today counts finished today against finished plus still due`() {
        val all = listOf(
            task(1, "a", status = EntryStatus.DONE, completedAt = at(9)),
            task(2, "b", status = EntryStatus.DONE, completedAt = at(9).minusSeconds(86_400)),   // yesterday
            task(3, "c", due = today),
            task(4, "d", due = today.minusDays(1)),
            task(5, "e", due = today.plusDays(1)),
        )
        assertEquals(1 to 3, tasksToday(all, today, zone))
    }
}
