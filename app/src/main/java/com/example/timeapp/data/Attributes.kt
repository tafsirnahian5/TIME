package com.example.timeapp.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * Me attributes (Phase 15): the PDF's five attributes as **plain ratios**, each shown with the
 * formula it was worked out from. No XP, points or levels (the user's decision) — a ratio of
 * the user's own records, and "Not enough data" whenever the denominator is zero. Pure;
 * tested in AttributesTest.
 */
data class Attribute(
    val name: String,
    /** "82%", "7 of 9", or null when there is nothing to divide by. */
    val value: String?,
    /** The bar's fill, 0..1 (capped), or null without data. */
    val fraction: Float?,
    /** The formula with this period's real numbers in it. */
    val formula: String,
)

/** The weekly review's key — ISO week of the period's first day. Shared with the review screen. */
fun weeklyReviewKey(weekStart: LocalDate): String =
    "${weekStart.get(WeekFields.ISO.weekBasedYear())}-W${weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())}"

const val CONSISTENCY_MIN_MS = 60 * 60_000L
const val PLANNING_GRACE_MS = 10 * 60_000L

/**
 * [weekStarts]: the weeks in the period, oldest first (the last one is this week). [sessions]
 * are closed work spans inside the period. [taskBlocks] are task blocks (already expanded, one
 * entry per block) starting in the period. [reviewKeys] are every saved weekly review's key;
 * [firstReviewWeek] the week start of the earliest one.
 */
fun computeAttributes(
    weekStarts: List<LocalDate>,
    today: LocalDate,
    now: Instant,
    zone: ZoneId,
    sessions: List<WorkSessionSpan>,
    targets: List<WorkTarget>,
    commitments: List<Commitment>,
    entriesById: Map<Long, Entry>,
    restDays: Set<DayOfWeek>,
    taskBlocks: List<Entry>,
    reviewKeys: Set<String>,
    firstReviewWeek: LocalDate?,
): List<Attribute> {
    val from = weekStarts.first()
    val activeMs = sessions.sumOf { Duration.between(it.startedAt, it.endedAt).toMillis() }

    // Focus — active work ÷ the weekly target(s) in force for these weeks.
    val targetMin = weekStarts.sumOf { targets.targetForWeek(it)?.toLong() ?: 0L }
    val focus = if (targetMin <= 0) {
        Attribute(L("Focus"), null, null, L("Active work ÷ weekly target — set a weekly target in the Time report"))
    } else {
        val ratio = activeMs.toDouble() / (targetMin * 60_000.0)
        Attribute(
            L("Focus"), "${(ratio * 100).toInt()}%", ratio.toFloat().coerceIn(0f, 1f),
            L("Active work %1\$s ÷ weekly target %2\$s", hm(activeMs), (hm(targetMin * 60_000))),
        )
    }

    // Delivery — committed tasks done ÷ committed tasks due. Today's only count once done,
    // so an unfinished morning never reads as a miss.
    val live = commitments.filter { it.active && !it.date.isBefore(from) && !it.date.isAfter(today) }
    val isDone = { c: Commitment -> entriesById[c.entryId]?.status == EntryStatus.DONE }
    val due = live.filter { it.date.isBefore(today) || isDone(it) }
    val done = due.count(isDone)
    val delivery = ratioAttribute(L("Delivery"), done, due.size, L("Committed tasks done ÷ committed tasks due"))

    // Consistency — workdays with at least an hour of work ÷ workdays so far. Rest days are
    // left out entirely, and today counts only once it's met.
    val byDay = sessions.msByDay(zone)
    val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
        .filter { it.dayOfWeek !in restDays }
        .filter { d -> d.isBefore(today) || (byDay[d] ?: 0) >= CONSISTENCY_MIN_MS }
        .toList()
    val met = days.count { (byDay[it] ?: 0) >= CONSISTENCY_MIN_MS }
    val consistency = ratioAttribute(L("Consistency"), met, days.size, L("Workdays with at least 1 h of work ÷ workdays so far (rest days left out)"))

    // Planning — task blocks started within 10 min of their start ÷ blocks already begun.
    val begun = taskBlocks.filter { b -> b.startAt != null && !b.startAt.plusMillis(PLANNING_GRACE_MS).isAfter(now) }
    val onTime = begun.count { b ->
        sessions.any { s -> s.entryId == b.id && Duration.between(b.startAt, s.startedAt).abs().toMillis() <= PLANNING_GRACE_MS }
    }
    val planning = ratioAttribute(L("Planning"), onTime, begun.size, L("Bookings started within 10 min of their start ÷ bookings that have begun"))

    // Money review — weeks with a saved review ÷ weeks since the first review. This week only
    // counts once reviewed.
    val eligibleWeeks = if (firstReviewWeek == null) emptyList() else weekStarts
        .filter { !it.isBefore(firstReviewWeek) }
        .filter { w -> w.plusDays(7).isAfter(today).not() || weeklyReviewKey(w) in reviewKeys }
    val reviewed = eligibleWeeks.count { weeklyReviewKey(it) in reviewKeys }
    val money = ratioAttribute(L("Money review"), reviewed, eligibleWeeks.size, L("Weeks with a saved review ÷ weeks since your first review"))

    return listOf(focus, delivery, consistency, planning, money)
}

private fun ratioAttribute(name: String, num: Int, den: Int, formula: String): Attribute =
    if (den == 0) Attribute(name, null, null, formula)
    else Attribute(name, L("%1\$s of %2\$s", num, den), num.toFloat() / den, formula)

private fun hm(ms: Long): String = durationTextMs(ms)
