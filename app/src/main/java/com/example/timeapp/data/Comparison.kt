package com.example.timeapp.data

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Period comparison for reports — the PDF's rules: an in-progress week/month is "to date"
 * and compares with the **same elapsed part** of the previous one (Mon–Wed vs last Mon–Wed),
 * and a zero previous value is "No prior baseline", never an infinite percentage. Pure;
 * tested in ComparisonTest.
 */
enum class ComparePeriod { WEEK, MONTH }

/** [start] inclusive, [endExclusive] exclusive. */
data class DateSpan(val start: LocalDate, val endExclusive: LocalDate)

data class Comparison(val current: DateSpan, val previous: DateSpan, val toDate: Boolean)

fun comparisonFor(period: ComparePeriod, today: LocalDate): Comparison = when (period) {
    ComparePeriod.WEEK -> {
        val monday = today.weekStart()
        val elapsed = java.time.temporal.ChronoUnit.DAYS.between(monday, today) + 1
        val prevMonday = monday.minusWeeks(1)
        Comparison(DateSpan(monday, today.plusDays(1)), DateSpan(prevMonday, prevMonday.plusDays(elapsed)), toDate = elapsed < 7)
    }
    ComparePeriod.MONTH -> {
        val month = YearMonth.from(today)
        val prev = month.minusMonths(1)
        // Day 31 compares with the whole of a 30-day previous month, not a day past it.
        val prevEnd = prev.atDay(minOf(today.dayOfMonth, prev.lengthOfMonth())).plusDays(1)
        val toDate = today.dayOfMonth < month.lengthOfMonth()
        Comparison(DateSpan(month.atDay(1), today.plusDays(1)), DateSpan(prev.atDay(1), prevEnd), toDate)
    }
}

/**
 * "+12% vs this point last week", "Same as …", or "No prior baseline". [what] names the
 * previous period ("last week"); [higherIsBetter] only picks the word, never a colour or score.
 */
fun compareText(current: Long, previous: Long, what: String, toDate: Boolean): String {
    val point = if (toDate) L("this point %1\$s", what) else what
    if (previous == 0L) return if (current == 0L) L("Nothing %1\$s either", what) else L("No prior baseline")
    if (current == previous) return L("Same as %1\$s", point)
    val pct = (abs(current - previous).toDouble() / previous * 100).roundToLong()
    return L("%1\$s%2\$s%% vs %3\$s", (if (current > previous) "+" else "−"), pct, point)
}
