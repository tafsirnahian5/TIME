package com.example.timeapp.data

import java.time.DayOfWeek
import java.time.LocalDate

/** A heatmap cell's state. [NONE] is not due or out of range; [OPEN] is today still to do. */
enum class DayState { DONE, REST, MISSED, LOGGED, OPEN, NONE }

/**
 * A habit's last [weeks] weeks as columns of seven days (first day of the week on top), oldest
 * column first — the yearly heatmap (Loop's history view). Days before the habit started, in
 * the future, or not due are [DayState.NONE]. Pure; tested in HabitHistoryTest.
 */
fun habitHeatmap(habit: Habit, marks: Map<LocalDate, HabitMark>, today: LocalDate, weeks: Int = 53, first: DayOfWeek = WeekConfig.firstDay): List<List<Pair<LocalDate, DayState>>> {
    val start = listOfNotNull(habit.startedOn, marks.keys.minOrNull()).min()
    val lastWeek = today.weekStart(first)
    return (weeks - 1 downTo 0).map { back ->
        val weekStart = lastWeek.minusWeeks(back.toLong())
        (0L..6L).map { i ->
            val d = weekStart.plusDays(i)
            d to when {
                d.isAfter(today) || d.isBefore(start) -> DayState.NONE
                marks[d] == HabitMark.DONE -> DayState.DONE
                marks[d] == HabitMark.SKIPPED -> DayState.REST
                marks[d] == HabitMark.LOGGED -> DayState.LOGGED
                d == today -> DayState.OPEN
                // A period target has no "missed day" — only the week or month is judged.
                habit.schedule.isPeriodTarget || !habit.schedule.isDueOn(d) -> DayState.NONE
                else -> DayState.MISSED
            }
        }
    }
}

/**
 * Done-rate per weekday over [from, today): of the days due on that weekday (rest days and
 * today left out), how many were done. Null where nothing was judged. For period targets,
 * every day counts as a chance, so the rate reads as "how often on this weekday".
 */
fun weekdayRates(habit: Habit, marks: Map<LocalDate, HabitMark>, from: LocalDate, today: LocalDate): Map<DayOfWeek, Double?> {
    val start = maxOf(from, listOfNotNull(habit.startedOn, marks.keys.minOrNull()).min())
    // Today counts once it's done (as the done-rate above it does) — it read "—" for a day
    // already ticked off. Still open, it isn't judged: the day isn't over.
    val days = generateSequence(start) { it.plusDays(1) }
        .takeWhile { it.isBefore(today) || (it == today && marks[it] == HabitMark.DONE) }
        .filter { habit.schedule.isDueOn(it) && marks[it] != HabitMark.SKIPPED }.toList()
    return DayOfWeek.entries.associateWith { dow ->
        val judged = days.filter { it.dayOfWeek == dow }
        if (judged.isEmpty()) null else judged.count { marks[it] == HabitMark.DONE }.toDouble() / judged.size
    }
}

/** Check-ins per week for the last [weeks] weeks, oldest first: (week start, done count). */
fun weeklyCompletions(marks: Map<LocalDate, HabitMark>, today: LocalDate, weeks: Int = 12, first: DayOfWeek = WeekConfig.firstDay): List<Pair<LocalDate, Int>> {
    val last = today.weekStart(first)
    return (weeks - 1 downTo 0).map { back ->
        val w = last.minusWeeks(back.toLong())
        w to (0L..6L).count { marks[w.plusDays(it)] == HabitMark.DONE }
    }
}
