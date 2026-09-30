package com.example.timeapp

import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.HabitSchedule
import com.example.timeapp.data.HabitScheduleKind
import com.example.timeapp.data.HabitTarget
import com.example.timeapp.data.widget.habitWidgetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitWidgetTest {
    private val today = LocalDate.of(2026, 9, 30)
    private val habits = listOf(
        Habit(1, "Read", HabitSchedule(HabitScheduleKind.DAILY), today, target = HabitTarget(20.0, "pages")),
        Habit(2, "Walk", HabitSchedule(HabitScheduleKind.DAILY), today),
        Habit(3, "Stretch", HabitSchedule(HabitScheduleKind.DAILY), today),
    )
    private val marks = mapOf(3L to mapOf(today to HabitMark.DONE))

    @Test
    fun `open habits first, only plain ones check off from the widget`() {
        val c = habitWidgetContent(habits, marks, locked = false, today = today)
        assertEquals(listOf(1L, 2L, 3L), c.rows.map { it.habitId })
        assertFalse(c.rows[0].checkable) // measurable: needs an amount
        assertTrue(c.rows[1].checkable)
        assertFalse(c.rows[2].checkable) // already done
        assertEquals("1 of 3 done today", c.footer)
    }

    /** A locked widget names nothing. */
    @Test
    fun `locked shows counts only`() {
        val c = habitWidgetContent(habits, marks, locked = true, today = today)
        assertTrue(c.rows.isEmpty())
        assertFalse(c.footer.contains("Read"))
    }
}
