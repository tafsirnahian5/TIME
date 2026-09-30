package com.example.timeapp

import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.WorkSessionSpan
import com.example.timeapp.data.categoryInsight
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CategoryInsightsTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val today = LocalDate.of(2026, 9, 30) // Wednesday
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant()
    private val entries = listOf(
        Entry(1, EntryKind.TASK, "Portfolio", status = EntryStatus.DONE, categoryId = 7),
        Entry(2, EntryKind.TASK, "Skills page", status = EntryStatus.PAUSED, categoryId = 7),
        Entry(3, EntryKind.TASK, "Groceries", status = EntryStatus.DONE, categoryId = 9),
    )
    private val sessions = listOf(
        WorkSessionSpan(1, at(LocalDate.of(2026, 8, 31), 9), at(LocalDate.of(2026, 8, 31), 11)), // Aug, 2h
        WorkSessionSpan(2, at(today, 9), at(today, 10)), // this week, 1h
        WorkSessionSpan(1, at(LocalDate.of(2026, 9, 21), 9), at(LocalDate.of(2026, 9, 21), 10)), // 1h
        WorkSessionSpan(3, at(today, 12), at(today, 18)), // other category
    )

    /** Profile totals reconcile with source sessions (the PDF's 1.7 exit check). */
    @Test
    fun `totals come only from this category's sessions`() {
        val i = categoryInsight(7, entries, sessions, today, zone)
        assertEquals(4 * 3_600_000L, i.totalMs)
        assertEquals(2 * 3_600_000L, i.thisMonthMs)
        assertEquals(3, i.sessions)
        assertEquals(1, i.completedTasks)
    }

    @Test
    fun `weeks run oldest to newest and recent work is newest first`() {
        val i = categoryInsight(7, entries, sessions, today, zone)
        assertEquals(8, i.weekly.size)
        assertEquals(LocalDate.of(2026, 9, 28), i.weekly.last().first)
        assertEquals(3_600_000L, i.weekly.last().second)
        assertEquals(listOf(2L, 1L), i.recent.map { it.id })
    }

    /** A session across the month boundary counts only its September hour this month. */
    @Test
    fun `month time is split at midnight`() {
        val late = WorkSessionSpan(1, at(LocalDate.of(2026, 8, 31), 23), at(LocalDate.of(2026, 9, 1), 1))
        val i = categoryInsight(7, entries, listOf(late), today, zone)
        assertEquals(2 * 3_600_000L, i.totalMs)
        assertEquals(3_600_000L, i.thisMonthMs)
    }
}
