package com.example.timeapp.ui.plan

import com.example.timeapp.data.Entry
import com.example.timeapp.data.blockSpans
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The practical-depth version of the PDF's "Rescheduling support" free-slot search: find the
 * first gap on [day], within [workStart]-[workEnd], that fits [durationMs] and doesn't overlap
 * any other scheduled entry. Used by RescheduleScreen instead of a hardcoded slot.
 */
object SchedulingMath {
    private val zone = ZoneId.systemDefault()

    fun findFreeSlot(
        entries: List<Entry>,
        excludeEntryId: Long,
        day: LocalDate,
        durationMs: Long,
        notBefore: Instant,
        workStart: LocalTime = LocalTime.of(9, 0),
        workEnd: LocalTime = LocalTime.of(18, 0),
    ): Pair<Instant, Instant>? {
        if (durationMs <= 0) return null
        val dayStart = day.atTime(workStart).atZone(zone).toInstant()
        val dayEnd = day.atTime(workEnd).atZone(zone).toInstant()
        var cursor = maxOf(dayStart, notBefore)
        if (!cursor.isBefore(dayEnd)) return null

        val blocks = entries
            .filter { it.id != excludeEntryId }
            .flatMap { it.blockSpans() }
            .filter { (s, e) -> e.isAfter(dayStart) && s.isBefore(dayEnd) }
            .sortedBy { it.first }

        for ((s, e) in blocks) {
            if (s.isAfter(cursor) && Duration.between(cursor, s).toMillis() >= durationMs) {
                return cursor to cursor.plusMillis(durationMs)
            }
            if (e.isAfter(cursor)) cursor = e
        }
        return if (Duration.between(cursor, dayEnd).toMillis() >= durationMs) {
            cursor to cursor.plusMillis(durationMs)
        } else {
            null
        }
    }

    /** The first free slot on any of the [days] days from [from], or null. */
    fun nextFreeSlot(
        entries: List<Entry>,
        excludeEntryId: Long,
        from: LocalDate,
        durationMs: Long,
        notBefore: Instant,
        days: Int = 7,
        workStart: LocalTime = LocalTime.of(9, 0),
        workEnd: LocalTime = LocalTime.of(18, 0),
    ): Pair<Instant, Instant>? = (0 until days).asSequence()
        .map { from.plusDays(it.toLong()) }
        .mapNotNull { day -> findFreeSlot(entries, excludeEntryId, day, durationMs, notBefore, workStart, workEnd) }
        .firstOrNull()

    /**
     * The PDF's steps 1–2 of "Rescheduling support", for a task with at most one block:
     * remaining = max(estimate − tracked, 0); a still-future block is [bookedMs] of that. A
     * new slot **replaces** that block, so it must hold all of [remainingMs] — sizing it for
     * only the unbooked part (as this used to) and then replacing the block lost the booked
     * time. [overAllocatedMs] > 0 means the booked block is longer than the work left.
     */
    data class Remaining(val remainingMs: Long, val bookedMs: Long) {
        val overAllocatedMs: Long get() = (bookedMs - remainingMs).coerceAtLeast(0)
        val alreadyCovered: Boolean get() = bookedMs in 1..Long.MAX_VALUE && bookedMs >= remainingMs
    }

    fun remaining(estimateMs: Long, trackedMs: Long, entry: Entry, now: Instant): Remaining {
        // Every block not yet begun is booked time — Reschedule replaces all of them at once.
        val booked = entry.blockSpans().filter { it.first.isAfter(now) }.sumOf { Duration.between(it.first, it.second).toMillis() }
        return Remaining((estimateMs - trackedMs).coerceAtLeast(0), booked)
    }

    /**
     * The earliest a task may be placed: after [now] and after every scheduled block of the
     * prerequisites it still waits on (the PDF: free slots "respecting ... dependencies").
     */
    fun notBeforeForDependencies(now: Instant, prerequisites: List<Entry>): Instant =
        prerequisites.flatMap { it.blockSpans() }.map { it.second }.fold(now) { acc, end -> maxOf(acc, end) }

    /** "09:00" → 09:00, falling back to [default] for anything unparseable. */
    fun parseWorkTime(raw: String, default: LocalTime): LocalTime =
        runCatching { LocalTime.parse(raw) }.getOrDefault(default)
}
