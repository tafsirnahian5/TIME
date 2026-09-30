package com.example.timeapp

import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.HabitMark.DONE
import com.example.timeapp.data.HabitMark.SKIPPED
import com.example.timeapp.data.HabitSchedule
import com.example.timeapp.data.HabitScheduleKind
import com.example.timeapp.data.habitStats
import com.example.timeapp.data.habitPeriodSummary
import com.example.timeapp.data.next
import com.example.timeapp.data.nextHabitReminder
import com.example.timeapp.data.showsToday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class HabitStatsTest {
    // Monday 28 Sep 2026 … Sunday 4 Oct 2026.
    private val mon = LocalDate.of(2026, 9, 28)
    private fun d(offset: Long) = mon.plusDays(offset)
    private fun habit(schedule: HabitSchedule, startedOn: LocalDate = mon) = Habit(1, "Read", schedule, startedOn)
    private val daily = HabitSchedule(HabitScheduleKind.DAILY)

    @Test
    fun `consecutive done days make a streak`() {
        val marks = mapOf(d(0) to DONE, d(1) to DONE, d(2) to DONE)
        val s = habitStats(habit(daily), marks, today = d(2))
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)
        assertEquals(1.0, s.recentRate!!, 1e-9)
    }

    /** Today still open is not a miss: the streak stands until the day is over. */
    @Test
    fun `an unmarked today does not break the streak`() {
        val marks = mapOf(d(0) to DONE, d(1) to DONE)
        val s = habitStats(habit(daily), marks, today = d(2))
        assertEquals(2, s.currentStreak)
        assertEquals(1.0, s.recentRate!!, 1e-9)
    }

    @Test
    fun `a missed past day resets the streak but keeps the best`() {
        val marks = mapOf(d(0) to DONE, d(1) to DONE, d(3) to DONE)
        val s = habitStats(habit(daily), marks, today = d(3))
        assertEquals(1, s.currentStreak)
        assertEquals(2, s.bestStreak)
        assertEquals(0.75, s.recentRate!!, 1e-9)
    }

    /** The PDF's rule: rest days never count against you. */
    @Test
    fun `a rest day neither breaks nor extends the streak and is left out of the rate`() {
        val marks = mapOf(d(0) to DONE, d(1) to SKIPPED, d(2) to DONE)
        val s = habitStats(habit(daily), marks, today = d(2))
        assertEquals(2, s.currentStreak)
        assertEquals(1.0, s.recentRate!!, 1e-9)
    }

    @Test
    fun `weekday habits ignore their off days`() {
        val mwf = HabitSchedule(HabitScheduleKind.WEEKDAYS, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val marks = mapOf(d(0) to DONE, d(2) to DONE, d(4) to DONE, d(7) to DONE)
        val s = habitStats(habit(mwf), marks, today = d(8)) // Tuesday, unmarked and not due
        assertEquals(4, s.currentStreak)
    }

    @Test
    fun `nothing judged yet reads as no data rather than zero`() {
        val s = habitStats(habit(daily), emptyMap(), today = mon)
        assertNull(s.recentRate)
        assertEquals(0, s.currentStreak)
    }

    /** Back-filling a day before the habit was created is honoured. */
    @Test
    fun `a mark before the start date moves the start back`() {
        val marks = mapOf(d(-1) to DONE, d(0) to DONE)
        assertEquals(2, habitStats(habit(daily), marks, today = d(0)).currentStreak)
    }

    @Test
    fun `weekly target counts met weeks and never fails the current one early`() {
        val thrice = HabitSchedule(HabitScheduleKind.TIMES_PER_WEEK, timesPerWeek = 3)
        val marks = mapOf(d(0) to DONE, d(2) to DONE, d(4) to DONE, d(7) to DONE)
        val s = habitStats(habit(thrice), marks, today = d(8))
        assertEquals(1, s.currentStreak)
        assertEquals(1, s.doneThisWeek)
        assertEquals(3, s.targetThisWeek)
        assertEquals(1.0, s.recentRate!!, 1e-9)
    }

    @Test
    fun `a missed weekly target resets the streak`() {
        val thrice = HabitSchedule(HabitScheduleKind.TIMES_PER_WEEK, timesPerWeek = 3)
        val marks = mapOf(d(0) to DONE, d(7) to DONE)
        val s = habitStats(habit(thrice), marks, today = d(14))
        assertEquals(0, s.currentStreak)
        assertEquals(0.0, s.recentRate!!, 1e-9)
    }

    /** Rest days lower the bar: 5 days off leaves 2 available, so 2 done meets "3 a week". */
    @Test
    fun `rest days lower a weekly target instead of failing it`() {
        val thrice = HabitSchedule(HabitScheduleKind.TIMES_PER_WEEK, timesPerWeek = 3)
        val marks = (0L..4L).associate { d(it) to SKIPPED } + mapOf(d(5) to DONE, d(6) to DONE)
        assertEquals(1, habitStats(habit(thrice), marks, today = d(7)).currentStreak)
    }

    /** A habit started on a Saturday has only two days in its first week. */
    @Test
    fun `a partial first week only asks for the days it had`() {
        val thrice = HabitSchedule(HabitScheduleKind.TIMES_PER_WEEK, timesPerWeek = 3)
        val marks = mapOf(d(5) to DONE, d(6) to DONE)
        assertEquals(1, habitStats(habit(thrice, startedOn = d(5)), marks, today = d(7)).currentStreak)
    }

    @Test
    fun `today list shows due habits and hides a met weekly target`() {
        val thrice = HabitSchedule(HabitScheduleKind.TIMES_PER_WEEK, timesPerWeek = 2)
        assertTrue(habit(daily).showsToday(emptyMap(), d(1)))
        assertFalse(habit(thrice).showsToday(mapOf(d(0) to DONE, d(1) to DONE), d(2)))
        // Marked today always shows, so a mistake can be undone where it was made.
        assertTrue(habit(thrice).showsToday(mapOf(d(0) to DONE, d(2) to DONE), d(2)))
        val mondays = HabitSchedule(HabitScheduleKind.WEEKDAYS, setOf(DayOfWeek.MONDAY))
        assertFalse(habit(mondays).showsToday(emptyMap(), d(1)))
    }

    @Test
    fun `next reminder skips days the habit is not due`() {
        val mondays = HabitSchedule(HabitScheduleKind.WEEKDAYS, setOf(DayOfWeek.MONDAY))
        val at = LocalTime.of(7, 30)
        assertEquals(d(0).atTime(at), nextHabitReminder(mondays, at, d(0).atTime(6, 0)))
        assertEquals(d(7).atTime(at), nextHabitReminder(mondays, at, d(0).atTime(7, 30)))
        assertEquals(d(1).atTime(at), nextHabitReminder(daily, at, d(0).atTime(8, 0)))
    }

    @Test
    fun `schedule round-trips and tap cycles through rest`() {
        listOf(
            daily,
            HabitSchedule(HabitScheduleKind.WEEKDAYS, setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)),
            HabitSchedule(HabitScheduleKind.TIMES_PER_WEEK, timesPerWeek = 4),
        ).forEach { assertEquals(it.serialize(), HabitSchedule.parse(it.serialize()).serialize()) }
        val cycle: HabitMark? = null
        assertEquals(DONE, cycle.next())
        assertEquals(SKIPPED, DONE.next())
        assertNull(SKIPPED.next())
    }

    @Test
    fun `period summary counts due days and leaves rest and today-open out`() {
        val marks = mapOf(d(0) to DONE, d(1) to SKIPPED, d(3) to DONE)
        // Mon..Sun week, today Thursday (d3): Mon done, Tue rest, Wed missed, Thu done.
        val s = habitPeriodSummary(habit(daily), marks, d(0), d(7), today = d(3))
        assertEquals(2, s.done)
        assertEquals(3, s.judged)
        assertEquals(2, s.met)
        assertEquals(1, s.restDays)
        // Thursday not yet done is not judged against the habit.
        val open = habitPeriodSummary(habit(daily), marks - d(3), d(0), d(7), today = d(3))
        assertEquals(2, open.judged)
    }

    @Test
    fun `period summary judges weekly targets by whole weeks`() {
        val twice = HabitSchedule(HabitScheduleKind.TIMES_PER_WEEK, timesPerWeek = 2)
        val marks = mapOf(d(0) to DONE, d(2) to DONE, d(7) to DONE)
        val s = habitPeriodSummary(habit(twice), marks, d(0), d(14), today = d(15))
        assertEquals(3, s.done)
        assertEquals(2, s.judged)
        assertEquals(1, s.met)
    }
}
