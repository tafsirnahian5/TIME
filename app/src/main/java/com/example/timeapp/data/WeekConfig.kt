package com.example.timeapp.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The user's first day of the week (Settings › Workday & calendar; Saturday is common in
 * Bangladesh), read by every week calculation — reports, comparisons, weekly targets, habits'
 * weekly goals, category history and the calendar grids. Set from settings at process start
 * and whenever it changes; pure code takes it as a default parameter so tests stay explicit.
 * Recurrence interval maths deliberately keeps its own anchor, so existing series never shift.
 */
object WeekConfig {
    @Volatile var firstDay: DayOfWeek = DayOfWeek.MONDAY
}

/** The first day of [this] date's week. */
fun LocalDate.weekStart(first: DayOfWeek = WeekConfig.firstDay): LocalDate =
    minusDays(((dayOfWeek.value - first.value + 7) % 7).toLong())

/** Single-letter weekday headings in week order (Bangla uses its own short initials). */
fun weekdayInitials(first: DayOfWeek = WeekConfig.firstDay): List<String> =
    (0L..6L).map { d ->
        val day = first.plus(d)
        if (Lang.bangla) BN_INITIALS[day.value - 1] else day.name.first().toString()
    }

private val BN_INITIALS = listOf("সো", "ম", "বু", "বৃ", "শু", "শ", "র")

/** Blank cells before the 1st in a month grid. */
fun leadingBlanks(firstOfMonth: LocalDate, first: DayOfWeek = WeekConfig.firstDay): Int =
    (firstOfMonth.dayOfWeek.value - first.value + 7) % 7
