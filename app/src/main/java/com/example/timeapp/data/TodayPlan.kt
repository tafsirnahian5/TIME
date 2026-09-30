package com.example.timeapp.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What Today shows (Phase 18A, the "T!ME — Today redesign" canvas), decided here as plain
 * functions so they're tested without a screen.
 */

/** One line of "Rest of today": a reserved block, due work, a habit reminder or a bill. */
data class TimelineItem(
    val kind: Kind,
    val title: String,
    /** Null = any time today. */
    val at: Instant?,
    val endAt: Instant? = null,
    val entryId: Long? = null,
    val planId: Long? = null,
    val habitId: Long? = null,
    val amountMinor: Long? = null,
    val fixed: Boolean = false,
) {
    enum class Kind { EVENT, TASK_BLOCK, TASK_DUE, HABIT, BILL }
}

/** A habit as the timeline needs it: its reminder time, and whether today's already done. */
data class HabitForToday(val id: Long, val name: String, val remindAt: LocalTime?, val done: Boolean)

/**
 * Everything still to come today, in time order, untimed things last: blocks that haven't ended
 * (any block of a split task), open tasks due today with no block today, habits with a reminder
 * later today and not yet done, and bills due today with money still owed.
 */
fun restOfToday(
    entries: List<Entry>,
    plans: List<MoneyRecord>,
    habits: List<HabitForToday>,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TimelineItem> {
    val today = now.atZone(zone).toLocalDate()
    val open = entries.filter { it.status != EntryStatus.DONE }
    val blocks = open.withBlocksExpanded().filter { e ->
        val s = e.startAt ?: return@filter false
        val end = e.endAt ?: s
        end.isAfter(now) && s.atZone(zone).toLocalDate() == today
    }.map { e ->
        TimelineItem(
            if (e.kind == EntryKind.EVENT) TimelineItem.Kind.EVENT else TimelineItem.Kind.TASK_BLOCK,
            e.title, e.startAt, e.endAt, entryId = e.id, fixed = e.isFixed,
        )
    }
    val blockedToday = blocks.mapNotNull { it.entryId }.toSet()
    val dueNoBlock = open.filter {
        it.kind == EntryKind.TASK && it.dueOn == today && it.id !in blockedToday
    }.map { TimelineItem(TimelineItem.Kind.TASK_DUE, it.title, null, entryId = it.id) }
    val habitItems = habits.filter { !it.done && it.remindAt != null }
        .map { h -> TimelineItem(TimelineItem.Kind.HABIT, h.name, today.atTime(h.remindAt).atZone(zone).toInstant(), habitId = h.id) }
        .filter { !it.at!!.isBefore(now) }
    val bills = plans.filter { it.isOpen && it.dueOn == today && it.remainingMinor > 0 }
        .map { TimelineItem(TimelineItem.Kind.BILL, it.title, null, planId = it.id, amountMinor = it.remainingMinor) }
    val timed = (blocks + habitItems).sortedBy { it.at }
    return timed + dueNoBlock + bills
}

/** "From before today": open tasks and bills whose date has passed — shown only when there are any. */
data class Carryover(val tasks: List<Entry>, val plans: List<MoneyRecord>) {
    val isEmpty: Boolean get() = tasks.isEmpty() && plans.isEmpty()
}

fun carryover(entries: List<Entry>, plans: List<MoneyRecord>, today: LocalDate): Carryover = Carryover(
    tasks = entries.filter {
        it.kind == EntryKind.TASK && it.status != EntryStatus.DONE && it.dueOn != null && it.dueOn.isBefore(today)
    }.sortedBy { it.dueOn },
    plans = plans.filter { it.isOpen && it.isOverdue(today) && it.remainingMinor > 0 }.sortedBy { it.dueOn },
)

/**
 * Up to [limit] open tasks worth committing to today, most pressing first: overdue, due today,
 * urgent/high priority, reserved for today — never one already chosen.
 */
fun commitmentSuggestions(
    entries: List<Entry>,
    today: LocalDate,
    chosen: Set<Long>,
    zone: ZoneId = ZoneId.systemDefault(),
    limit: Int = 3,
): List<Entry> {
    fun rank(e: Entry): Int = when {
        e.dueOn != null && e.dueOn.isBefore(today) -> 0
        e.dueOn == today -> 1
        e.priority == Priority.URGENT -> 2
        e.priority == Priority.HIGH -> 3
        e.blockSpans().any { it.first.atZone(zone).toLocalDate() == today } -> 4
        else -> 9
    }
    return entries.filter {
        it.kind == EntryKind.TASK && it.status != EntryStatus.DONE && it.status != EntryStatus.BLOCKED && it.id !in chosen
    }.map { it to rank(it) }.filter { it.second < 9 }
        .sortedWith(compareBy<Pair<Entry, Int>> { it.second }.thenBy { it.first.dueOn ?: LocalDate.MAX })
        .take(limit).map { it.first }
}

/** With nothing running: the one task to offer on the Now card — an open commitment first. */
fun startNext(entries: List<Entry>, commitmentIds: List<Long>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Entry? {
    val open = entries.filter { it.kind == EntryKind.TASK && it.status != EntryStatus.DONE && it.status != EntryStatus.BLOCKED }
    val byId = open.associateBy { it.id }
    return commitmentIds.firstNotNullOfOrNull { byId[it] }
        ?: commitmentSuggestions(entries, today, emptySet(), zone, limit = 1).firstOrNull()
}

/** Tasks finished today, and how many were on today's plate (finished today + still open and due). */
fun tasksToday(all: List<Entry>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
    val done = all.count { it.status == EntryStatus.DONE && it.completedAt?.atZone(zone)?.toLocalDate() == today }
    val stillDue = all.count {
        it.kind == EntryKind.TASK && it.status != EntryStatus.DONE && it.dueOn != null && !it.dueOn.isAfter(today)
    }
    return done to (done + stillDue)
}
