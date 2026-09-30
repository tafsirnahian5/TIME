package com.example.timeapp

import com.example.timeapp.data.ReminderTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderTimingTest {
    private val due = LocalDate.of(2026, 10, 5)

    /** The same wall-clock time follows the zone: 09:00 in Dhaka, then 09:00 in London. */
    @Test
    fun `a date-anchored reminder follows the local clock`() {
        val t = ReminderTiming.OnDay(-1, LocalTime.of(20, 30))
        assertEquals(Instant.parse("2026-10-04T14:30:00Z"), t.at(due, null, ZoneId.of("Asia/Dhaka")))
        assertEquals(Instant.parse("2026-10-04T19:30:00Z"), t.at(due, null, ZoneId.of("Europe/London")))
        assertNull(t.at(null, null))
    }

    @Test
    fun `before a block is an instant`() {
        val start = Instant.parse("2026-10-05T04:00:00Z")
        assertEquals(Instant.parse("2026-10-05T03:30:00Z"), ReminderTiming.BeforeStart(30).at(null, start))
        assertEquals("30 min before", ReminderTiming.BeforeStart(30).label())
        assertEquals("1 h before", ReminderTiming.BeforeStart(60).label())
    }

    @Test
    fun `stored columns round trip`() {
        assertEquals(ReminderTiming.OnDay(-1, LocalTime.of(9, 0)), ReminderTiming.from(-1, "09:00", null))
        assertEquals(ReminderTiming.BeforeStart(10), ReminderTiming.from(null, null, 10))
        assertNull(ReminderTiming.from(null, null, null))
        assertEquals("09:00 the day before", ReminderTiming.PLAN_DEFAULT.label())
    }
}
