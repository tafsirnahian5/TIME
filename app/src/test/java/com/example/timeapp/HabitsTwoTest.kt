package com.example.timeapp

import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.HabitSchedule
import com.example.timeapp.data.HabitScheduleKind
import com.example.timeapp.data.HabitTarget
import com.example.timeapp.data.StreakUnit
import com.example.timeapp.data.habitStats
import com.example.timeapp.data.showsToday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Habits 2.0: every N days, N a month, measurable targets. */
class HabitsTwoTest {
    private val start = LocalDate.of(2026, 9, 1)

    @Test
    fun `every third day from the start`() {
        val s = HabitSchedule(HabitScheduleKind.EVERY_N_DAYS, everyNDays = 3, anchor = start)
        assertTrue(s.isDueOn(start))
        assertFalse(s.isDueOn(start.plusDays(1)))
        assertTrue(s.isDueOn(start.plusDays(6)))
        assertFalse(s.isDueOn(start.minusDays(3)))
        assertEquals(s, HabitSchedule.parse(s.serialize()))
        assertEquals("Every 3 days", s.label())
    }

    @Test
    fun `days between an every-N habit's days never break its streak`() {
        val habit = Habit(1, "Water plants", HabitSchedule(HabitScheduleKind.EVERY_N_DAYS, everyNDays = 3, anchor = start), start)
        val marks = listOf(0L, 3L, 6L).associate { start.plusDays(it) to HabitMark.DONE }
        assertEquals(3, habitStats(habit, marks, start.plusDays(8)).currentStreak)
    }

    @Test
    fun `a monthly target counts months`() {
        val habit = Habit(1, "Call home", HabitSchedule(HabitScheduleKind.TIMES_PER_MONTH, timesPerMonth = 2), start)
        val marks = listOf(
            LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 10, 2),
        ).associateWith { HabitMark.DONE }
        val oct = habitStats(habit, marks, LocalDate.of(2026, 10, 10))
        assertEquals(StreakUnit.MONTHS, oct.unit)
        assertEquals(1, oct.currentStreak) // September met; October still open
        assertEquals(1 to 2, oct.doneThisWeek to oct.targetThisWeek)
        assertTrue(habit.showsToday(marks, LocalDate.of(2026, 10, 10)))
        assertEquals(HabitSchedule(HabitScheduleKind.TIMES_PER_MONTH, timesPerMonth = 2), HabitSchedule.parse("TIMES_PER_MONTH|2"))
    }

    @Test
    fun `a measured day counts only when it meets the target`() {
        val atLeast = HabitTarget(20.0, "pages")
        assertEquals(HabitMark.DONE, atLeast.markFor(25.0))
        assertEquals(HabitMark.LOGGED, atLeast.markFor(12.0))
        val atMost = HabitTarget(2.0, "sugar", atMost = true)
        assertEquals(HabitMark.DONE, atMost.markFor(0.0))
        assertEquals(HabitMark.LOGGED, atMost.markFor(3.0))
        assertEquals("at least 20 pages", atLeast.label())
        // Logged but short breaks the streak like a missed day, but today is still open.
        val habit = Habit(1, "Read", HabitSchedule(HabitScheduleKind.DAILY), start, target = atLeast)
        val marks = mapOf(start to HabitMark.DONE, start.plusDays(1) to HabitMark.LOGGED, start.plusDays(2) to HabitMark.DONE)
        assertEquals(1, habitStats(habit, marks, start.plusDays(2)).currentStreak)
        val todayLogged = mapOf(start to HabitMark.DONE, start.plusDays(1) to HabitMark.LOGGED)
        assertEquals(1, habitStats(habit, todayLogged, start.plusDays(1)).currentStreak)
    }
}
