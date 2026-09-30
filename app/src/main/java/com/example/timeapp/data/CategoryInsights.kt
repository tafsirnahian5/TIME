package com.example.timeapp.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * One work category's history — the PDF's skill view ("tracked time, completed tasks,
 * sessions and recent progress") without XP or levels, which stay excluded. Every number is
 * summed from sessions and tasks, so it always reconciles with the Time report. Pure;
 * tested in CategoryInsightsTest.
 */
data class CategoryInsight(
    val totalMs: Long,
    val thisMonthMs: Long,
    val sessions: Int,
    val completedTasks: Int,
    /** Last [WEEKS] Monday-started weeks, oldest first, with tracked time in each. */
    val weekly: List<Pair<LocalDate, Long>>,
    /** Most recently worked tasks, newest first. */
    val recent: List<Entry>,
)

const val WEEKS = 8

fun categoryInsight(
    categoryId: Long,
    entries: List<Entry>,
    sessions: List<WorkSessionSpan>,
    today: LocalDate,
    zone: ZoneId,
): CategoryInsight {
    val mine = entries.filter { it.categoryId == categoryId }
    val ids = mine.map { it.id }.toSet()
    val spans = sessions.filter { it.entryId in ids }
    fun ms(s: WorkSessionSpan) = s.endedAt.toEpochMilli() - s.startedAt.toEpochMilli()
    val month = YearMonth.from(today)
    val thisWeek = today.weekStart()
    val weeks = (WEEKS - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
    // By the day the time was worked, split at midnight — the same rule as the Time report,
    // so the two always reconcile.
    val byDay = spans.msByDay(zone)
    val byWeek = byDay.entries.groupBy({ it.key.weekStart() }, { it.value })
    val lastWorked = spans.groupBy { it.entryId }.mapValues { (_, list) -> list.maxOf { it.endedAt } }
    return CategoryInsight(
        totalMs = spans.sumOf(::ms),
        thisMonthMs = byDay.filterKeys { YearMonth.from(it) == month }.values.sum(),
        sessions = spans.size,
        completedTasks = mine.count { it.status == EntryStatus.DONE },
        weekly = weeks.map { w -> w to byWeek[w].orEmpty().sum() },
        recent = mine.filter { it.id in lastWorked }.sortedByDescending { lastWorked[it.id] ?: Instant.EPOCH }.take(5),
    )
}
