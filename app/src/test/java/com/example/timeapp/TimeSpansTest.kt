package com.example.timeapp

import com.example.timeapp.data.WorkSessionSpan
import com.example.timeapp.data.clippedTo
import com.example.timeapp.data.durationMs
import com.example.timeapp.data.msByDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimeSpansTest {
    private val dhaka = ZoneId.of("Asia/Dhaka")
    private fun at(d: String, t: String) = LocalDateTime.parse("${d}T$t").atZone(dhaka).toInstant()
    private fun span(from: String, to: String, dFrom: String = "2026-09-27", dTo: String = dFrom) =
        WorkSessionSpan(1, at(dFrom, from), at(dTo, to))
    private val hour = 3_600_000L

    /** The PDF's cross-midnight case: an hour on each side of midnight. */
    @Test
    fun `a session across midnight counts on both days`() {
        val byDay = span("23:00", "01:00", "2026-09-27", "2026-09-28").msByDay(dhaka)
        assertEquals(mapOf(LocalDate.of(2026, 9, 27) to hour, LocalDate.of(2026, 9, 28) to hour), byDay)
    }

    @Test
    fun `clipping keeps only the part inside the period`() {
        val s = span("23:00", "01:00", "2026-09-27", "2026-09-28")
        val monday = at("2026-09-28", "00:00")
        assertEquals(hour, s.clippedTo(monday, at("2026-10-05", "00:00"))!!.durationMs)
        assertEquals(hour, s.clippedTo(at("2026-09-21", "00:00"), monday)!!.durationMs)
        assertNull(span("09:00", "10:00").clippedTo(at("2026-09-27", "10:00"), at("2026-09-27", "11:00")))
    }

    @Test
    fun `days are summed across sessions`() {
        val byDay = listOf(span("09:00", "10:00"), span("11:00", "11:30")).msByDay(dhaka)
        assertEquals(mapOf(LocalDate.of(2026, 9, 27) to hour + hour / 2), byDay)
    }

    /** A 23-hour day (spring forward) is still split at the real midnight. */
    @Test
    fun `midnight split follows daylight saving`() {
        val ny = ZoneId.of("America/New_York")
        val s = WorkSessionSpan(1, LocalDateTime.parse("2026-03-07T23:00").atZone(ny).toInstant(), LocalDateTime.parse("2026-03-08T04:00").atZone(ny).toInstant())
        val byDay = s.msByDay(ny)
        assertEquals(hour, byDay[LocalDate.of(2026, 3, 7)])
        // 00:00 → 04:00 on the day clocks jump from 02:00 to 03:00 is three real hours.
        assertEquals(3 * hour, byDay[LocalDate.of(2026, 3, 8)])
    }
}
