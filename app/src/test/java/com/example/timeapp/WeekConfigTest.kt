package com.example.timeapp

import com.example.timeapp.data.leadingBlanks
import com.example.timeapp.data.weekStart
import com.example.timeapp.data.weekdayInitials
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekConfigTest {
    private val tue = LocalDate.of(2026, 9, 29) // a Tuesday

    @Test
    fun `a week starts on the chosen day`() {
        assertEquals(LocalDate.of(2026, 9, 28), tue.weekStart(DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 9, 27), tue.weekStart(DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 9, 26), tue.weekStart(DayOfWeek.SATURDAY))
        val sat = LocalDate.of(2026, 9, 26)
        assertEquals(sat, sat.weekStart(DayOfWeek.SATURDAY))
    }

    @Test
    fun `grid headings and blanks agree with the week start`() {
        assertEquals(listOf("S", "S", "M", "T", "W", "T", "F"), weekdayInitials(DayOfWeek.SATURDAY))
        val oct1 = LocalDate.of(2026, 10, 1) // a Thursday
        assertEquals(3, leadingBlanks(oct1, DayOfWeek.MONDAY))
        assertEquals(5, leadingBlanks(oct1, DayOfWeek.SATURDAY))
    }
}
