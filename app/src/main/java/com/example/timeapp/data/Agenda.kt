package com.example.timeapp.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * What the calendar shows (the PDF's calendar filters): tasks, appointments (schedule
 * blocks), money due, and whether finished work stays visible. Held by PlanViewModel, so it
 * survives switching between Month, Week, Day and Agenda.
 */
data class CalendarFilter(
    val tasks: Boolean = true,
    val appointments: Boolean = true,
    val costs: Boolean = true,
    val completed: Boolean = true,
) {
    val isDefault: Boolean get() = this == CalendarFilter()

    fun shows(e: Entry): Boolean {
        if (!completed && e.status == EntryStatus.DONE) return false
        return if (e.kind == EntryKind.EVENT) appointments else tasks
    }

    fun shows(p: MoneyRecord): Boolean = costs
}

/** The date an entry sits on in the calendar: its block's start, else its due date. */
fun Entry.calendarDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    startAt?.atZone(zone)?.toLocalDate() ?: dueOn

sealed class AgendaItem {
    data class Work(val entry: Entry) : AgendaItem()
    data class Due(val plan: MoneyRecord) : AgendaItem()
}

data class AgendaDay(val date: LocalDate, val items: List<AgendaItem>)

/**
 * The Agenda view: every day from [from] for [days] days that has something on it, in order —
 * timed blocks by start time first, then untimed work, then money due. Empty days are left out
 * (a list of blanks helps nobody); [overdue] collects unfinished work and unpaid plans dated
 * before [from], so the list never hides what was missed.
 */
data class Agenda(val overdue: List<AgendaItem>, val days: List<AgendaDay>)

fun buildAgenda(
    entries: List<Entry>,
    plans: List<MoneyRecord>,
    from: LocalDate,
    days: Int,
    filter: CalendarFilter,
    zone: ZoneId = ZoneId.systemDefault(),
): Agenda {
    val until = from.plusDays(days.toLong())
    val shownEntries = entries.filter { filter.shows(it) }
    val shownPlans = plans.filter { filter.shows(it) && it.remainingMinor > 0 }
    val overdue = shownEntries
        .filter { e -> e.status != EntryStatus.DONE && e.calendarDate(zone)?.isBefore(from) == true }
        .sortedBy { it.calendarDate(zone) }
        .map { AgendaItem.Work(it) } +
        shownPlans.filter { it.dueOn?.isBefore(from) == true }.sortedBy { it.dueOn }.map { AgendaItem.Due(it) }

    val byDay = mutableMapOf<LocalDate, MutableList<AgendaItem>>()
    shownEntries.forEach { e ->
        val d = e.calendarDate(zone) ?: return@forEach
        if (!d.isBefore(from) && d.isBefore(until)) byDay.getOrPut(d) { mutableListOf() } += AgendaItem.Work(e)
    }
    shownPlans.forEach { p ->
        val d = p.dueOn ?: return@forEach
        if (!d.isBefore(from) && d.isBefore(until)) byDay.getOrPut(d) { mutableListOf() } += AgendaItem.Due(p)
    }
    val ordered = byDay.toSortedMap().map { (date, items) ->
        AgendaDay(
            date,
            items.sortedWith(
                compareBy<AgendaItem> {
                    when (it) {
                        is AgendaItem.Work -> if (it.entry.startAt != null) 0 else 1
                        is AgendaItem.Due -> 2
                    }
                }.thenBy { (it as? AgendaItem.Work)?.entry?.startAt }
                    .thenBy { (it as? AgendaItem.Work)?.entry?.id ?: (it as AgendaItem.Due).plan.id },
            ),
        )
    }
    return Agenda(overdue, ordered)
}
