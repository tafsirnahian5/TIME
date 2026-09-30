package com.example.timeapp

import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.Ics
import com.example.timeapp.data.ScheduleBlock
import com.example.timeapp.data.blockSpans
import com.example.timeapp.data.withBlocksExpanded
import com.example.timeapp.ui.plan.SchedulingMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScheduleBlocksTest {
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.of(2026, 10, 1)
    private fun at(h: Int, m: Int = 0): Instant = day.atTime(h, m).atZone(zone).toInstant()

    private val split = Entry(
        1, EntryKind.TASK, "Case study", status = EntryStatus.PLANNED,
        startAt = at(14), endAt = at(14, 45), estimateMs = 90 * 60_000L,
        extraBlocks = listOf(ScheduleBlock(9, 1, at(10), at(10, 45))),
    )

    @Test fun spansAreEarliestFirst() = assertEquals(listOf(at(10) to at(10, 45), at(14) to at(14, 45)), split.blockSpans())

    @Test fun expansionKeepsIdAndCarriesEachBlock() {
        val expanded = listOf(split).withBlocksExpanded()
        assertEquals(listOf(1L, 1L), expanded.map { it.id })
        assertEquals(setOf(at(14), at(10)), expanded.map { it.startAt }.toSet())
        assertTrue(expanded.all { it.extraBlocks.isEmpty() })
    }

    @Test fun freeSlotAvoidsEveryBlock() {
        val slot = SchedulingMath.findFreeSlot(listOf(split), excludeEntryId = 99, day = day, durationMs = 60 * 60_000L,
            notBefore = at(9, 30), workStart = LocalTime.of(9, 0), workEnd = LocalTime.of(18, 0))
        // 09:30–10:00 is too short; 10:45–11:45 is the first hour that fits.
        assertEquals(at(10, 45) to at(11, 45), slot)
    }

    @Test fun bookedCountsAllFutureBlocks() {
        val r = SchedulingMath.remaining(90 * 60_000L, 20 * 60_000L, split, now = at(9))
        assertEquals(90 * 60_000L, r.bookedMs)
        assertEquals(20 * 60_000L, r.overAllocatedMs)
        // Once the first block has begun it is no longer "booked".
        assertEquals(45 * 60_000L, SchedulingMath.remaining(90 * 60_000L, 0, split, now = at(10, 5)).bookedMs)
    }

    @Test fun icsExportsOneEventPerBlockWithDistinctUids() {
        val ics = Ics.export(listOf(split), now = at(8))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("UID:entry-1@time.local"))
        assertTrue(ics.contains("UID:entry-1-block-1@time.local"))
    }
}
