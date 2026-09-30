package com.example.timeapp

import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitSchedule
import com.example.timeapp.data.HabitScheduleKind
import com.example.timeapp.data.widget.todayWidgetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TodayWidgetTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val now = Instant.parse("2026-09-28T04:00:00Z") // 10:00 in Dhaka
    private val today = LocalDate.of(2026, 9, 28)
    private val entries = listOf(
        Entry(1, EntryKind.TASK, "Portfolio page", status = EntryStatus.DOING),
        Entry(2, EntryKind.EVENT, "Client call", status = EntryStatus.PLANNED,
            startAt = Instant.parse("2026-09-28T08:00:00Z"), endAt = Instant.parse("2026-09-28T08:30:00Z")),
        Entry(3, EntryKind.TASK, "Pay rent", status = EntryStatus.PLANNED, dueOn = today),
    )
    private val habits = listOf(Habit(9, "Read", HabitSchedule(HabitScheduleKind.DAILY), today))

    @Test
    fun `unlocked shows what's on today`() {
        val c = todayWidgetContent(entries, habits, emptyMap(), locked = false, now, zone)
        assertEquals(listOf("● Now: Portfolio page", "14:00  Client call", "Due  Pay rent", "Habits: Read"), c.lines)
    }

    /** The PDF: a locked widget never exposes protected content — counts only. */
    @Test
    fun `locked shows counts and never a name`() {
        val c = todayWidgetContent(entries, habits, emptyMap(), locked = true, now, zone)
        val all = (c.lines + c.footer).joinToString(" ")
        listOf("Portfolio", "Client", "rent", "Read").forEach { assertFalse(all.contains(it)) }
        assertTrue(all.contains("2 things"))
        assertTrue(all.contains("1 habit"))
    }

    @Test
    fun `a habit already checked in drops off`() {
        val marks = mapOf(9L to mapOf(today to com.example.timeapp.data.HabitMark.DONE))
        val c = todayWidgetContent(emptyList(), habits, marks, locked = false, now, zone)
        assertEquals(listOf("Nothing left for today"), c.lines)
    }
}
