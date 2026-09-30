package com.example.timeapp.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The personal workload report: what's scheduled or due over the next days against the work
 * time actually available. Capacity is each day's work hours, rest days excluded and only
 * what's left of today; demand is the unfinished blocks in the window plus the estimates of
 * tasks due in it with no block. Tasks due with no estimate can't be sized — they're counted
 * and named, never guessed. Pure; tested in WorkloadTest.
 */
data class DayLoad(val date: LocalDate, val capacityMs: Long, val blockedMs: Long, val dueEstimateMs: Long, val rest: Boolean) {
    val plannedMs: Long get() = blockedMs + dueEstimateMs
    val overMs: Long get() = (plannedMs - capacityMs).coerceAtLeast(0)
}

data class Workload(val days: List<DayLoad>, val unestimated: List<Entry>) {
    val capacityMs: Long get() = days.sumOf { it.capacityMs }
    val plannedMs: Long get() = days.sumOf { it.plannedMs }
    val overMs: Long get() = (plannedMs - capacityMs).coerceAtLeast(0)
}

/** "Fri" → FRIDAY, the settings' short day names. */
fun parseRestDays(names: Set<String>): Set<DayOfWeek> =
    names.mapNotNull { n -> DayOfWeek.entries.firstOrNull { it.name.take(3).equals(n.take(3), ignoreCase = true) } }.toSet()

fun workload(
    entries: List<Entry>,
    now: Instant,
    zone: ZoneId,
    workStart: LocalTime,
    workEnd: LocalTime,
    restDays: Set<DayOfWeek>,
    days: Int = 7,
): Workload {
    val today = now.atZone(zone).toLocalDate()
    // Every reserved block counts, a task split into two included.
    val open = entries.filter { it.status != EntryStatus.DONE }.withBlocksExpanded()
    val loads = (0 until days).map { offset ->
        val date = today.plusDays(offset.toLong())
        val rest = date.dayOfWeek in restDays
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        // Nothing before now counts: a morning already gone is neither capacity nor demand.
        val from = maxOf(dayStart, now)
        val capacity = if (rest) 0L else {
            val ws = maxOf(date.atTime(workStart).atZone(zone).toInstant(), now)
            val we = date.atTime(workEnd).atZone(zone).toInstant()
            Duration.between(ws, we).toMillis().coerceAtLeast(0)
        }
        val blocked = open.filter { it.startAt != null && it.endAt != null }.sumOf { e ->
            val s = maxOf(e.startAt!!, from)
            val en = minOf(e.endAt!!, dayEnd)
            if (en.isAfter(s)) Duration.between(s, en).toMillis() else 0L
        }
        val dueEstimate = open.filter { it.startAt == null && it.dueOn == date && it.kind == EntryKind.TASK }
            .sumOf { it.estimateMs ?: 0L }
        DayLoad(date, capacity, blocked, dueEstimate, rest)
    }
    val window = today..today.plusDays(days - 1L)
    val unestimated = open.filter { it.kind == EntryKind.TASK && it.startAt == null && it.dueOn != null && it.dueOn in window && it.estimateMs == null }
    return Workload(loads, unestimated)
}
