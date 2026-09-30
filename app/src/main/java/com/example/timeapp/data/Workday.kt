package com.example.timeapp.data

import java.time.Duration
import java.time.Instant

/**
 * One day's workday figures, the PDF's worked example: "09:00–12:00 is a three-hour day span.
 * Two 15-minute breaks leave 2h 30m on duty. Active work of 2h 10m leaves 20m untracked. A
 * timer outside this day is reported separately, not inserted into the day twice."
 * Pure; tested in WorkdayTest.
 */
data class WorkdaySummary(
    val onDutyMs: Long,
    val breakMs: Long,
    /** Work sessions inside on-duty time. */
    val activeMs: Long,
    /** Work done today outside any on-duty interval — counted in reports, not in this day. */
    val outsideMs: Long,
) {
    val untrackedMs: Long get() = (onDutyMs - activeMs).coerceAtLeast(0)
}

/** Figures for [segments] and today's [sessions] (already clipped to the day), with anything
 * still open counted up to [now] — the day's totals used to leave out the running segment,
 * so On duty read 0 all morning. */
fun workdaySummary(segments: List<WorkdaySegment>, sessions: List<WorkSessionSpan>, now: Instant): WorkdaySummary {
    fun end(s: WorkdaySegment) = s.endedAt ?: maxOf(now, s.startedAt)
    val duty = segments.filter { it.kind == WorkdaySegmentKind.ON_DUTY }.map { it.startedAt to end(it) }
    val onDuty = duty.sumOf { (a, b) -> Duration.between(a, b).toMillis() }
    val breaks = segments.filter { it.kind == WorkdaySegmentKind.BREAK }.sumOf { Duration.between(it.startedAt, end(it)).toMillis() }
    var inside = 0L
    var total = 0L
    sessions.forEach { s ->
        total += s.durationMs
        duty.forEach { (a, b) ->
            val from = maxOf(a, s.startedAt)
            val to = minOf(b, s.endedAt)
            if (to.isAfter(from)) inside += Duration.between(from, to).toMillis()
        }
    }
    return WorkdaySummary(onDuty, breaks, inside, (total - inside).coerceAtLeast(0))
}
