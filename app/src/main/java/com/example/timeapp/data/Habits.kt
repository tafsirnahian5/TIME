package com.example.timeapp.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Habits — the third pillar beside money and task time.
 *
 * Deliberately *not* built on [Recurrence]: a repeating task materialises one row per
 * occurrence because each occurrence carries its own state (tracked time, payments). A habit
 * has nothing per day except whether it happened, so it is one [Habit] row plus one
 * [HabitCheck] per day that was marked — nothing is pre-generated and a missing day costs
 * no storage.
 *
 * Patterned on Loop Habit Tracker's restraint (the PDF's reference S19): check-ins, streaks
 * and a completion rate, no points or scores. The one rule borrowed from the PDF's
 * consistency definition: **rest days never count against you** — a day marked
 * [HabitMark.SKIPPED] neither breaks nor extends a streak and is left out of the rate.
 */
enum class HabitScheduleKind { DAILY, WEEKDAYS, TIMES_PER_WEEK, EVERY_N_DAYS, TIMES_PER_MONTH }

data class HabitSchedule(
    val kind: HabitScheduleKind,
    /** [HabitScheduleKind.WEEKDAYS] only. */
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** [HabitScheduleKind.TIMES_PER_WEEK] only: any N days in a week (see `WeekConfig`). */
    val timesPerWeek: Int = 3,
    /** [HabitScheduleKind.EVERY_N_DAYS]: every [everyNDays] days counted from [anchor]. */
    val everyNDays: Int = 2,
    val anchor: LocalDate? = null,
    /** [HabitScheduleKind.TIMES_PER_MONTH]: any N days in a calendar month. */
    val timesPerMonth: Int = 10,
) {
    /** A target over a period (week or month) rather than a set of days. */
    val isPeriodTarget: Boolean get() = kind == HabitScheduleKind.TIMES_PER_WEEK || kind == HabitScheduleKind.TIMES_PER_MONTH

    /** Whether [date] is one of the habit's own days. Every day counts for a weekly target —
     * the week, not the day, is what gets judged. */
    fun isDueOn(date: LocalDate): Boolean = when (kind) {
        HabitScheduleKind.DAILY, HabitScheduleKind.TIMES_PER_WEEK, HabitScheduleKind.TIMES_PER_MONTH -> true
        HabitScheduleKind.WEEKDAYS -> date.dayOfWeek in weekdays
        HabitScheduleKind.EVERY_N_DAYS -> anchor == null ||
            java.time.temporal.ChronoUnit.DAYS.between(anchor, date).let { it >= 0 && it % everyNDays.coerceAtLeast(1) == 0L }
    }

    fun label(): String = when (kind) {
        HabitScheduleKind.DAILY -> L("Every day")
        HabitScheduleKind.WEEKDAYS ->
            if (weekdays.size == 7) L("Every day")
            else weekdays.sortedBy { it.value }.joinToString(", ") { it.displayLabel() }
        HabitScheduleKind.TIMES_PER_WEEK -> if (timesPerWeek == 1) L("Once a week") else L("%1\$s times a week", timesPerWeek)
        HabitScheduleKind.EVERY_N_DAYS -> if (everyNDays == 1) L("Every day") else L("Every %1\$s days", everyNDays)
        HabitScheduleKind.TIMES_PER_MONTH -> if (timesPerMonth == 1) L("Once a month") else L("%1\$s times a month", timesPerMonth)
    }

    /** `DAILY`, `WEEKDAYS|MONDAY,FRIDAY`, `TIMES_PER_WEEK|3`. */
    fun serialize(): String = when (kind) {
        HabitScheduleKind.DAILY -> kind.name
        HabitScheduleKind.WEEKDAYS -> kind.name + "|" + weekdays.sortedBy { it.value }.joinToString(",") { it.name }
        HabitScheduleKind.TIMES_PER_WEEK -> kind.name + "|" + timesPerWeek
        HabitScheduleKind.EVERY_N_DAYS -> kind.name + "|" + everyNDays + "|" + (anchor?.toString() ?: "")
        HabitScheduleKind.TIMES_PER_MONTH -> kind.name + "|" + timesPerMonth
    }

    companion object {
        fun parse(raw: String): HabitSchedule {
            val parts = raw.split("|")
            return when (runCatching { HabitScheduleKind.valueOf(parts[0]) }.getOrNull()) {
                HabitScheduleKind.WEEKDAYS -> {
                    val days = parts.getOrNull(1).orEmpty().split(",")
                        .mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
                    // An empty weekday set could never be due; treat it as daily rather than
                    // a habit that silently never appears.
                    if (days.isEmpty()) HabitSchedule(HabitScheduleKind.DAILY) else HabitSchedule(HabitScheduleKind.WEEKDAYS, days)
                }
                HabitScheduleKind.TIMES_PER_WEEK ->
                    HabitSchedule(HabitScheduleKind.TIMES_PER_WEEK, timesPerWeek = (parts.getOrNull(1)?.toIntOrNull() ?: 3).coerceIn(1, 7))
                HabitScheduleKind.EVERY_N_DAYS -> HabitSchedule(
                    HabitScheduleKind.EVERY_N_DAYS,
                    everyNDays = (parts.getOrNull(1)?.toIntOrNull() ?: 2).coerceIn(1, 365),
                    anchor = parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                )
                HabitScheduleKind.TIMES_PER_MONTH ->
                    HabitSchedule(HabitScheduleKind.TIMES_PER_MONTH, timesPerMonth = (parts.getOrNull(1)?.toIntOrNull() ?: 10).coerceIn(1, 31))
                else -> HabitSchedule(HabitScheduleKind.DAILY)
            }
        }
    }
}

/**
 * [DONE]: happened (for a measurable habit, its target met). [SKIPPED]: a rest day.
 * [LOGGED]: a measurable habit's amount was recorded but missed the target — kept with its
 * value, judged like an unmarked day (and, while today is open, not judged yet). Stored by name.
 */
enum class HabitMark { DONE, SKIPPED, LOGGED }

/**
 * A measurable habit's goal (Loop / Track & Graph): an [amount] of [unit] each due day, at
 * least ("read ≥ 20 pages") or at most ("sugar ≤ 2").
 */
data class HabitTarget(val amount: Double, val unit: String, val atMost: Boolean = false) {
    fun met(value: Double): Boolean = if (atMost) value <= amount else value >= amount

    /** The mark a recorded [value] gives the day. */
    fun markFor(value: Double): HabitMark = if (met(value)) HabitMark.DONE else HabitMark.LOGGED

    fun label(): String = (if (atMost) L("at most ") else L("at least ")) + formatAmount(amount) + (if (unit.isBlank()) "" else " $unit")
}

/** 20 → "20", 1.5 → "1.5". */
fun formatAmount(v: Double): String = if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()

data class Habit(
    val id: Long,
    val name: String,
    val schedule: HabitSchedule,
    val startedOn: LocalDate,
    val categoryId: Long? = null,
    val category: WorkCategory? = null,   // resolved at read time, like Entry.category
    val remindAt: LocalTime? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    /** Set for a measurable habit. */
    val target: HabitTarget? = null,
    /** A timed habit: this many minutes on a utility timer, checked off when it rings. */
    val timerMinutes: Int? = null,
)

data class HabitCheck(val habitId: Long, val date: LocalDate, val mark: HabitMark, val value: Double? = null, val note: String? = null)

/** The tap cycle on a day: nothing → done → rest day → nothing. */
fun HabitMark?.next(): HabitMark? = when (this) {
    null, HabitMark.LOGGED -> HabitMark.DONE
    HabitMark.DONE -> HabitMark.SKIPPED
    HabitMark.SKIPPED -> null
}

enum class StreakUnit { DAYS, WEEKS, MONTHS }

data class HabitStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val unit: StreakUnit,
    /** Share of judged days (or weeks) in the recent window that were done, 0..1. Null when
     * nothing has been judged yet — the PDF's "Not enough data", never a misleading 0%. */
    val recentRate: Double?,
    /** Weekly-target habits: done so far this week, and the target that applies. */
    val doneThisWeek: Int = 0,
    val targetThisWeek: Int = 0,
)

/** Monday of [date]'s week — the same week start [Recurrence] uses. */

const val HABIT_RATE_WINDOW_DAYS = 30L
const val HABIT_RATE_WINDOW_WEEKS = 8L

/**
 * Streaks and rate for one habit as of [today].
 *
 * A day (or week) still in progress is never counted as missed: today not yet done leaves the
 * streak where yesterday left it. Judging starts at whichever is earlier, [Habit.startedOn]
 * or the first mark, so back-filling a day before the habit was created is honoured rather
 * than ignored.
 */
fun habitStats(habit: Habit, marks: Map<LocalDate, HabitMark>, today: LocalDate): HabitStats {
    val start = listOfNotNull(habit.startedOn, marks.keys.minOrNull()).min()
    return when (habit.schedule.kind) {
        HabitScheduleKind.TIMES_PER_WEEK -> weeklyStats(habit.schedule.timesPerWeek, start, marks, today)
        HabitScheduleKind.TIMES_PER_MONTH -> monthlyStats(habit.schedule.timesPerMonth, start, marks, today)
        else -> dailyStats(habit.schedule, start, marks, today)
    }
}

private fun dailyStats(schedule: HabitSchedule, start: LocalDate, marks: Map<LocalDate, HabitMark>, today: LocalDate): HabitStats {
    var run = 0
    var best = 0
    var judged = 0
    var done = 0
    val windowStart = today.minusDays(HABIT_RATE_WINDOW_DAYS - 1)
    var day = start
    while (!day.isAfter(today)) {
        if (schedule.isDueOn(day)) {
            val inWindow = !day.isBefore(windowStart)
            when (marks[day]) {
                HabitMark.DONE -> {
                    run++
                    if (inWindow) { judged++; done++ }
                }
                HabitMark.SKIPPED -> Unit // a rest day: neither breaks nor extends
                // Logged below target counts as not done — but today is still open.
                null, HabitMark.LOGGED -> if (day != today) {
                    run = 0
                    if (inWindow) judged++
                }
            }
            best = maxOf(best, run)
        }
        day = day.plusDays(1)
    }
    return HabitStats(run, best, StreakUnit.DAYS, if (judged == 0) null else done.toDouble() / judged)
}

/**
 * One week of a weekly-target habit: (done, target). The target counts only days the habit
 * could actually happen on — not before it started, not marked as rest — so a partial first
 * week or a week off lowers the bar and never fails it. Shared by the streak and the report
 * so the two can never disagree about whether a week was met.
 */
internal fun weekTally(times: Int, weekStart: LocalDate, start: LocalDate, marks: Map<LocalDate, HabitMark>): Pair<Int, Int> {
    val days = (0L..6L).map { weekStart.plusDays(it) }.filter { !it.isBefore(start) }
    val available = days.count { marks[it] != HabitMark.SKIPPED }
    return days.count { marks[it] == HabitMark.DONE } to minOf(times, available)
}

private fun weeklyStats(times: Int, start: LocalDate, marks: Map<LocalDate, HabitMark>, today: LocalDate): HabitStats {
    var run = 0
    var best = 0
    var judged = 0
    var met = 0
    val thisWeek = today.weekStart()
    val windowStart = thisWeek.minusWeeks(HABIT_RATE_WINDOW_WEEKS - 1)
    var doneThisWeek = 0
    var targetThisWeek = 0
    var week = start.weekStart()
    while (!week.isAfter(thisWeek)) {
        val (doneCount, target) = weekTally(times, week, start, marks)
        val isCurrent = week == thisWeek
        if (isCurrent) { doneThisWeek = doneCount; targetThisWeek = target }
        val inWindow = !week.isBefore(windowStart)
        when {
            target == 0 -> Unit // a whole week off
            doneCount >= target -> {
                run++
                if (inWindow) { judged++; met++ }
            }
            !isCurrent -> {
                run = 0
                if (inWindow) judged++
            }
        }
        best = maxOf(best, run)
        week = week.plusWeeks(1)
    }
    return HabitStats(run, best, StreakUnit.WEEKS, if (judged == 0) null else met.toDouble() / judged, doneThisWeek, targetThisWeek)
}

/** One month of a monthly-target habit, the same shape as [weekTally]: days before the start
 * or marked rest lower the bar. */
internal fun monthTally(times: Int, month: java.time.YearMonth, start: LocalDate, marks: Map<LocalDate, HabitMark>): Pair<Int, Int> {
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { !it.isBefore(start) }
    val available = days.count { marks[it] != HabitMark.SKIPPED }
    return days.count { marks[it] == HabitMark.DONE } to minOf(times, available)
}

private fun monthlyStats(times: Int, start: LocalDate, marks: Map<LocalDate, HabitMark>, today: LocalDate): HabitStats {
    var run = 0
    var best = 0
    var judged = 0
    var met = 0
    val thisMonth = java.time.YearMonth.from(today)
    val windowStart = thisMonth.minusMonths(HABIT_RATE_WINDOW_MONTHS - 1)
    var doneNow = 0
    var targetNow = 0
    var month = java.time.YearMonth.from(start)
    while (!month.isAfter(thisMonth)) {
        val (doneCount, target) = monthTally(times, month, start, marks)
        val isCurrent = month == thisMonth
        if (isCurrent) { doneNow = doneCount; targetNow = target }
        val inWindow = !month.isBefore(windowStart)
        when {
            target == 0 -> Unit
            doneCount >= target -> {
                run++
                if (inWindow) { judged++; met++ }
            }
            !isCurrent -> {
                run = 0
                if (inWindow) judged++
            }
        }
        best = maxOf(best, run)
        month = month.plusMonths(1)
    }
    return HabitStats(run, best, StreakUnit.MONTHS, if (judged == 0) null else met.toDouble() / judged, doneNow, targetNow)
}

const val HABIT_RATE_WINDOW_MONTHS = 6L

/**
 * Whether the habit belongs on Today's list: due today by its schedule, or — for a weekly
 * target — this week's target still open. Already marked today always shows, so a mistaken
 * check can be undone from the same place it was made.
 */
fun Habit.showsToday(marks: Map<LocalDate, HabitMark>, today: LocalDate): Boolean {
    if (archived) return false
    if (marks[today] != null) return true
    if (!schedule.isPeriodTarget) return schedule.isDueOn(today)
    val stats = habitStats(this, marks, today)
    return stats.doneThisWeek < stats.targetThisWeek
}

/**
 * The next reminder instant strictly after [after]: the first day the habit is due, at
 * [remindAt]. Weekly-target habits remind every day; whether this week's target is already
 * met is decided at delivery, not here (the reminders rule: nothing is decided at arm time).
 */
fun nextHabitReminder(schedule: HabitSchedule, remindAt: LocalTime, after: LocalDateTime): LocalDateTime {
    var day = after.toLocalDate()
    repeat(8) {
        val candidate = day.atTime(remindAt)
        if (candidate.isAfter(after) && schedule.isDueOn(day)) return candidate
        day = day.plusDays(1)
    }
    // Unreachable for any schedule with at least one due weekday; fall back to a week out.
    return after.toLocalDate().plusDays(7).atTime(remindAt)
}

/** "12 days", "1 week". */
fun HabitStats.streakLabel(count: Int = currentStreak): String = when (unit) {
    StreakUnit.DAYS -> if (count == 1) L("1 day") else L("%1\$s days", count)
    StreakUnit.WEEKS -> if (count == 1) L("1 week") else L("%1\$s weeks", count)
    StreakUnit.MONTHS -> if (count == 1) L("1 month") else L("%1\$s months", count)
}

/**
 * One habit over a report period [from, toExclusive), as of [today].
 *
 * Day habits: [done] of [judged] due days, where judged leaves out rest days, days before the
 * habit started, the future, and today while it is still open. Weekly-target habits: [done]
 * check-ins, and [judged] weeks whose Monday falls in the period and that have ended or
 * already met their target, [met] of them met. Null [rate] means nothing was judged yet.
 */
data class HabitPeriodSummary(
    val done: Int,
    val judged: Int,
    val met: Int,
    val restDays: Int,
    val weekly: Boolean,
) {
    val rate: Double? get() = if (judged == 0) null else met.toDouble() / judged
}

fun habitPeriodSummary(
    habit: Habit,
    marks: Map<LocalDate, HabitMark>,
    from: LocalDate,
    toExclusive: LocalDate,
    today: LocalDate,
): HabitPeriodSummary {
    val start = listOfNotNull(habit.startedOn, marks.keys.minOrNull()).min()
    val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it.isBefore(toExclusive) && !it.isAfter(today) }.toList()
    val done = days.count { marks[it] == HabitMark.DONE }
    val rest = days.count { marks[it] == HabitMark.SKIPPED }
    if (habit.schedule.kind == HabitScheduleKind.TIMES_PER_MONTH) {
        var judged = 0
        var met = 0
        var month = java.time.YearMonth.from(from).let { if (it.atDay(1).isBefore(from)) it.plusMonths(1) else it }
        while (month.atDay(1).isBefore(toExclusive) && !month.atDay(1).isAfter(today)) {
            val (count, target) = monthTally(habit.schedule.timesPerMonth, month, start, marks)
            val ended = month.atEndOfMonth().isBefore(today)
            if (target > 0 && (ended || count >= target) && !month.atEndOfMonth().isBefore(start)) {
                judged++
                if (count >= target) met++
            }
            month = month.plusMonths(1)
        }
        return HabitPeriodSummary(done, judged, met, rest, weekly = true)
    }
    if (habit.schedule.kind == HabitScheduleKind.TIMES_PER_WEEK) {
        var judged = 0
        var met = 0
        var week = from.weekStart().let { if (it.isBefore(from)) it.plusWeeks(1) else it }
        while (week.isBefore(toExclusive) && !week.isAfter(today)) {
            val (count, target) = weekTally(habit.schedule.timesPerWeek, week, start, marks)
            val ended = week.plusDays(6).isBefore(today)
            if (target > 0 && (ended || count >= target) && !week.plusDays(6).isBefore(start)) {
                judged++
                if (count >= target) met++
            }
            week = week.plusWeeks(1)
        }
        return HabitPeriodSummary(done, judged, met, rest, weekly = true)
    }
    val judgedDays = days.filter { d ->
        !d.isBefore(start) && habit.schedule.isDueOn(d) && marks[d] != HabitMark.SKIPPED &&
            (d != today || marks[d] == HabitMark.DONE)
    }
    val metDays = judgedDays.count { marks[it] == HabitMark.DONE }
    return HabitPeriodSummary(done, judgedDays.size, metDays, rest, weekly = false)
}

