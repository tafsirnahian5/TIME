package com.example.timeapp

import com.example.timeapp.data.WorkSessionSpan
import com.example.timeapp.data.WorkdaySegment
import com.example.timeapp.data.WorkdaySegmentKind
import com.example.timeapp.data.workdaySummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WorkdayTest {
    private val day = LocalDate.of(2026, 9, 28)
    private fun t(hhmm: String) = Instant.parse("2026-09-28T${hhmm}:00Z")
    private fun seg(kind: WorkdaySegmentKind, from: String, to: String?) = WorkdaySegment(0, day, kind, t(from), to?.let(::t))
    private val min = 60_000L

    /** The PDF's worked example. */
    @Test
    fun `the worked example adds up`() {
        val segments = listOf(
            seg(WorkdaySegmentKind.ON_DUTY, "09:00", "10:00"),
            seg(WorkdaySegmentKind.BREAK, "10:00", "10:15"),
            seg(WorkdaySegmentKind.ON_DUTY, "10:15", "11:00"),
            seg(WorkdaySegmentKind.BREAK, "11:00", "11:15"),
            seg(WorkdaySegmentKind.ON_DUTY, "11:15", "12:00"),
        )
        val sessions = listOf(
            WorkSessionSpan(1, t("09:00"), t("10:00")),
            WorkSessionSpan(1, t("10:15"), t("11:00")),
            WorkSessionSpan(2, t("11:15"), t("11:40")),
            WorkSessionSpan(null, t("20:00"), t("20:30")), // an evening timer outside the day
        )
        val s = workdaySummary(segments, sessions, t("23:00"))
        assertEquals(150 * min, s.onDutyMs)
        assertEquals(30 * min, s.breakMs)
        assertEquals(130 * min, s.activeMs)
        assertEquals(20 * min, s.untrackedMs)
        assertEquals(30 * min, s.outsideMs)
    }

    /** The running segment counts up to now — it used to be left out entirely. */
    @Test
    fun `an open segment counts to now`() {
        val s = workdaySummary(listOf(seg(WorkdaySegmentKind.ON_DUTY, "09:00", null)), emptyList(), t("10:30"))
        assertEquals(90 * min, s.onDutyMs)
        assertEquals(90 * min, s.untrackedMs)
    }
}
