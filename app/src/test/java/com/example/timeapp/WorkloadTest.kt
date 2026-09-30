package com.example.timeapp

import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.parseRestDays
import com.example.timeapp.data.workload
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WorkloadTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val mon = LocalDate.of(2026, 9, 28)
    private val now = mon.atTime(13, 0).atZone(zone).toInstant() // Monday 13:00
    private val h = 3_600_000L
    private fun at(d: LocalDate, hh: Int) = d.atTime(hh, 0).atZone(zone).toInstant()

    @Test
    fun `capacity counts work hours left, skipping rest days`() {
        val w = workload(emptyList(), now, zone, LocalTime.of(9, 0), LocalTime.of(17, 0), setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), days = 7)
        assertEquals(4 * h, w.days[0].capacityMs) // 13:00–17:00 today
        assertEquals(8 * h, w.days[1].capacityMs)
        assertEquals(0L, w.days[4].capacityMs) // Friday
        assertEquals(4 * h + 8 * h * 4, w.capacityMs) // Mon rest + Tue–Thu + Sun
    }

    @Test
    fun `demand is future block time plus estimates of unblocked due tasks`() {
        val entries = listOf(
            Entry(1, EntryKind.EVENT, "Call", status = EntryStatus.PLANNED, startAt = at(mon, 12), endAt = at(mon, 15)), // 1h already past
            Entry(2, EntryKind.TASK, "Report", status = EntryStatus.PLANNED, dueOn = mon.plusDays(1), estimateMs = 3 * h),
            Entry(3, EntryKind.TASK, "Done one", status = EntryStatus.DONE, dueOn = mon.plusDays(1), estimateMs = 5 * h),
            Entry(4, EntryKind.TASK, "Unsized", status = EntryStatus.PLANNED, dueOn = mon.plusDays(2)),
        )
        val w = workload(entries, now, zone, LocalTime.of(9, 0), LocalTime.of(17, 0), emptySet(), days = 3)
        assertEquals(2 * h, w.days[0].blockedMs)
        assertEquals(3 * h, w.days[1].dueEstimateMs)
        assertEquals(listOf(4L), w.unestimated.map { it.id })
    }

    @Test
    fun `over-commitment is demand beyond capacity`() {
        val entries = listOf(Entry(1, EntryKind.TASK, "Big", status = EntryStatus.PLANNED, dueOn = mon, estimateMs = 6 * h))
        val w = workload(entries, now, zone, LocalTime.of(9, 0), LocalTime.of(17, 0), emptySet(), days = 1)
        assertEquals(2 * h, w.overMs)
    }

    @Test
    fun `rest day names parse from settings`() {
        assertEquals(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), parseRestDays(setOf("Fri", "Sat")))
    }
}
