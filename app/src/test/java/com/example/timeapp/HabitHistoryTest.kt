package com.example.timeapp

import com.example.timeapp.data.DayState
import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.HabitSchedule
import com.example.timeapp.data.HabitScheduleKind
import com.example.timeapp.data.habitHeatmap
import com.example.timeapp.data.weekdayRates
import com.example.timeapp.data.weeklyCompletions
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitHistoryTest {
    private val today = LocalDate.of(2026, 9, 30) // Wednesday
    private val start = LocalDate.of(2026, 9, 21) // Monday
    private val daily = Habit(1, "Read", HabitSchedule(HabitScheduleKind.DAILY), start)
    private val marks = mapOf(
        start to HabitMark.DONE,
        start.plusDays(1) to HabitMark.SKIPPED,
        start.plusDays(7) to HabitMark.DONE, // Monday this week
    )

    @Test
    fun `heatmap columns are weeks and days before the start are empty`() {
        val map = habitHeatmap(daily, marks, today, weeks = 3, first = DayOfWeek.MONDAY)
        assertEquals(3, map.size)
        assertEquals(DayState.NONE, map[0][0].second) // before the start
        assertEquals(DayState.DONE, map[1][0].second)
        assertEquals(DayState.REST, map[1][1].second)
        assertEquals(DayState.MISSED, map[1][2].second)
        assertEquals(DayState.OPEN, map[2][2].second) // today
        assertEquals(DayState.NONE, map[2][3].second) // future
    }

    @Test
    fun `weekday rates leave out rest days and today`() {
        val rates = weekdayRates(daily, marks, start, today)
        assertEquals(1.0, rates[DayOfWeek.MONDAY]!!, 0.0)
        assertEquals(0.0, rates[DayOfWeek.TUESDAY]!!, 0.0) // the 29th missed; the 22nd was rest
        assertEquals(0.0, rates[DayOfWeek.WEDNESDAY]!!, 0.0) // the 23rd missed; today (the 30th) not judged
    }

    @Test
    fun `today counts in the weekday rate once it is done`() {
        val rates = weekdayRates(daily, marks + (today to HabitMark.DONE), start, today)
        assertEquals(0.5, rates[DayOfWeek.WEDNESDAY]!!, 0.0) // the 23rd missed, today done
    }

    @Test
    fun `weekly completions count done days per week`() {
        assertEquals(listOf(1, 1), weeklyCompletions(marks, today, weeks = 2, first = DayOfWeek.MONDAY).map { it.second })
    }
}
