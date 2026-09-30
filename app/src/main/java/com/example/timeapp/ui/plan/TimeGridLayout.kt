package com.example.timeapp.ui.plan

import com.example.timeapp.data.Entry
import java.time.LocalDate
import java.time.ZoneId

/** One block's slice of a single day, in minutes from that day's midnight. */
data class DaySpan(val id: Long, val startMin: Int, val endMin: Int)

/** Where a block sits: which of [columns] side-by-side lanes it takes. */
data class Lane(val column: Int, val columns: Int)

/** Shortest a block is drawn, so a 5-minute block is still a tappable, readable shape. */
const val MIN_BLOCK_MINUTES = 20

/**
 * The part of each timed entry that falls on [date], clipped to that day — a block from 23:00
 * to 01:00 shows on both days rather than overflowing one. Entries without both a start and
 * an end reserve no time and are not placed (they're the "due, no time reserved" list).
 */
fun spansOn(entries: List<Entry>, date: LocalDate, zone: ZoneId): List<DaySpan> {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    return entries.mapNotNull { e ->
        val s = e.startAt ?: return@mapNotNull null
        val en = e.endAt ?: return@mapNotNull null
        if (!en.isAfter(dayStart) || !s.isBefore(dayEnd)) return@mapNotNull null
        val from = maxOf(s, dayStart)
        val to = minOf(en, dayEnd)
        val startMin = java.time.Duration.between(dayStart, from).toMinutes().toInt()
        val endMin = java.time.Duration.between(dayStart, to).toMinutes().toInt()
        DaySpan(e.id, startMin, maxOf(endMin, startMin + 1))
    }
}

/**
 * Side-by-side lanes for overlapping blocks, calendar-style: blocks that overlap (directly or
 * through a chain) form a cluster; each takes the first lane free at its start, and the
 * whole cluster shares one lane count so widths line up. Overlap uses each block's *drawn*
 * extent (at least [MIN_BLOCK_MINUTES]), so short blocks never draw on top of each other.
 */
fun layoutLanes(spans: List<DaySpan>): Map<Long, Lane> {
    val sorted = spans.sortedWith(compareBy({ it.startMin }, { -it.endMin }))
    val result = mutableMapOf<Long, Lane>()
    var cluster = mutableListOf<Pair<DaySpan, Int>>()
    var laneEnds = mutableListOf<Int>()
    var clusterEnd = Int.MIN_VALUE

    fun flush() {
        val lanes = laneEnds.size
        cluster.forEach { (span, lane) -> result[span.id] = Lane(lane, lanes) }
        cluster = mutableListOf()
        laneEnds = mutableListOf()
    }

    sorted.forEach { span ->
        val drawnEnd = maxOf(span.endMin, span.startMin + MIN_BLOCK_MINUTES)
        if (span.startMin >= clusterEnd) flush()
        val lane = laneEnds.indexOfFirst { it <= span.startMin }.takeIf { it >= 0 } ?: laneEnds.size.also { laneEnds.add(0) }
        laneEnds[lane] = drawnEnd
        cluster.add(span to lane)
        clusterEnd = maxOf(if (cluster.size == 1) Int.MIN_VALUE else clusterEnd, drawnEnd)
    }
    flush()
    return result
}
