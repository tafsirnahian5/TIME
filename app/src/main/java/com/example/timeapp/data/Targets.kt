package com.example.timeapp.data

import java.time.LocalDate

/**
 * A weekly work-time target, versioned. The PDF: "work-hour targets … can change
 * prospectively. Keep their historical version for reports so old percentages do not
 * silently change." So a change never edits the past: it's a new version effective from a
 * week's Monday, and every week is judged against the version in force then. [weeklyMinutes]
 * 0 means "no target" from that week on. Pure; tested in TargetsTest.
 */
data class WorkTarget(val id: Long, val weeklyMinutes: Int, val effectiveFrom: LocalDate)

/** The target in force for the week starting [weekStart] (a Monday), or null for none. */
fun List<WorkTarget>.targetForWeek(weekStart: LocalDate): Int? =
    filter { !it.effectiveFrom.isAfter(weekStart) }.maxByOrNull { it.effectiveFrom }?.weeklyMinutes?.takeIf { it > 0 }

/** Share of the target reached, capped at 1 for the bar; null without a target. */
fun targetProgress(trackedMinutes: Long, targetMinutes: Int?): Float? =
    targetMinutes?.takeIf { it > 0 }?.let { (trackedMinutes.toFloat() / it).coerceIn(0f, 1f) }
