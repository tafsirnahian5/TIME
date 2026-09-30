package com.example.timeapp.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Work time belongs to the period it happened in. Reports used to take every session that
 * *started* in a period and count all of it there, so a 23:00–01:00 session was two hours on
 * the first day and none on the second, and Sunday-night work that ran past midnight inflated
 * the week it started in. The PDF's verification table asks for exactly this case:
 * "cross-midnight work … correct period split and totals". Pure; tested in TimeSpansTest.
 */
val WorkSessionSpan.durationMs: Long get() = endedAt.toEpochMilli() - startedAt.toEpochMilli()

/** The part of this span inside [from, to), or null when there is none. */
fun WorkSessionSpan.clippedTo(from: Instant, to: Instant): WorkSessionSpan? {
    val start = maxOf(startedAt, from)
    val end = minOf(endedAt, to)
    return if (end.isAfter(start)) copy(startedAt = start, endedAt = end) else null
}

/** This span's time on each local calendar day it touches (split at midnight, DST-aware). */
fun WorkSessionSpan.msByDay(zone: ZoneId): Map<LocalDate, Long> {
    val out = linkedMapOf<LocalDate, Long>()
    var cursor = startedAt
    while (cursor.isBefore(endedAt)) {
        val day = cursor.atZone(zone).toLocalDate()
        val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant(), endedAt)
        out[day] = (out[day] ?: 0L) + (end.toEpochMilli() - cursor.toEpochMilli())
        cursor = end
    }
    return out
}

/** Every span's time, summed per local calendar day. */
fun List<WorkSessionSpan>.msByDay(zone: ZoneId): Map<LocalDate, Long> {
    val out = sortedMapOf<LocalDate, Long>()
    forEach { span -> span.msByDay(zone).forEach { (day, ms) -> out[day] = (out[day] ?: 0L) + ms } }
    return out
}
