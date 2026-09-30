package com.example.timeapp

import com.example.timeapp.data.DisplayFormats
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.durationText
import com.example.timeapp.data.whenLine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A Plan/Board row's detail line, and the one way durations are written. */
class WhenLineTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val day = LocalDate.of(2026, 9, 29)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(LocalTime.of(h, m)).atZone(zone).toInstant()
    private fun task(due: LocalDate? = null, start: java.time.Instant? = null, end: java.time.Instant? = null) =
        Entry(1, EntryKind.TASK, "t", status = EntryStatus.PLANNED, dueOn = due, startAt = start, endAt = end)

    @Before fun setUp() { DisplayFormats.use24h = true; DisplayFormats.monthFirst = false }
    @After fun reset() { DisplayFormats.use24h = true; DisplayFormats.monthFirst = false }

    @Test fun `nothing set says nothing`() {
        assertNull(task().whenLine(zone, at(day, 8)))
    }

    @Test fun `a due date alone`() {
        assertEquals("Due Tue 29 Sept", task(due = day).whenLine(zone, at(day, 8)))
    }

    @Test fun `booked time wins over a due date on the same day`() {
        val line = task(due = day, start = at(day, 21, 15), end = at(day, 23, 45)).whenLine(zone, at(day, 8))
        assertEquals("Tue 29 Sept · 21:15–23:45", line)
    }

    @Test fun `a due date on another day is added`() {
        val line = task(due = day.plusDays(3), start = at(day, 9), end = at(day, 10)).whenLine(zone, at(day, 8))
        assertEquals("Tue 29 Sept · 09:00–10:00 · due Fri 2 Oct", line)
    }

    @Test fun `durations are written one way`() {
        assertEquals("45 min", durationText(45))
        assertEquals("1 h", durationText(60))
        assertEquals("2 h 30 min", durationText(150))
        assertEquals("0 min", durationText(0))
    }
}
