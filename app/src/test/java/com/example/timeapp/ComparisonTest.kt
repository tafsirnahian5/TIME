package com.example.timeapp

import com.example.timeapp.data.ComparePeriod
import com.example.timeapp.data.DateSpan
import com.example.timeapp.data.compareText
import com.example.timeapp.data.comparisonFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ComparisonTest {
    @Test
    fun `a week in progress compares with the same days last week`() {
        val c = comparisonFor(ComparePeriod.WEEK, LocalDate.of(2026, 9, 30)) // Wednesday
        assertEquals(DateSpan(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 1)), c.current)
        assertEquals(DateSpan(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 24)), c.previous) // Mon–Wed
        assertTrue(c.toDate)
        assertFalse(comparisonFor(ComparePeriod.WEEK, LocalDate.of(2026, 10, 4)).toDate) // Sunday: whole week
    }

    @Test
    fun `the 31st compares with the whole of a shorter month`() {
        val c = comparisonFor(ComparePeriod.MONTH, LocalDate.of(2026, 10, 31))
        assertEquals(DateSpan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)), c.previous)
        val mid = comparisonFor(ComparePeriod.MONTH, LocalDate.of(2026, 10, 12))
        assertEquals(DateSpan(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 13)), mid.previous)
    }

    /** The PDF: zero previous values say "No prior baseline", never an infinite percentage. */
    @Test
    fun `comparison text never divides by zero`() {
        assertEquals("No prior baseline", compareText(500, 0, "last week", toDate = true))
        assertEquals("Nothing last week either", compareText(0, 0, "last week", toDate = true))
        assertEquals("+25% vs this point last week", compareText(125, 100, "last week", toDate = true))
        assertEquals("−50% vs last month", compareText(50, 100, "last month", toDate = false))
        assertEquals("Same as this point last week", compareText(100, 100, "last week", toDate = true))
    }
}
