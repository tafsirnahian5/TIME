package com.example.timeapp

import com.example.timeapp.data.WorkTarget
import com.example.timeapp.data.targetForWeek
import com.example.timeapp.data.targetProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TargetsTest {
    private val sep7 = LocalDate.of(2026, 9, 7)
    private val sep21 = LocalDate.of(2026, 9, 21)
    private val targets = listOf(WorkTarget(1, 15 * 60, sep7), WorkTarget(2, 20 * 60, sep21))

    /** The PDF: a changed target never rewrites earlier weeks' percentages. */
    @Test
    fun `each week is judged by the version in force then`() {
        assertNull(targets.targetForWeek(LocalDate.of(2026, 8, 31)))
        assertEquals(15 * 60, targets.targetForWeek(LocalDate.of(2026, 9, 14)))
        assertEquals(20 * 60, targets.targetForWeek(sep21))
        assertEquals(20 * 60, targets.targetForWeek(LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun `a zero version clears the target from then on`() {
        val cleared = targets + WorkTarget(3, 0, LocalDate.of(2026, 9, 28))
        assertNull(cleared.targetForWeek(LocalDate.of(2026, 10, 5)))
        assertEquals(20 * 60, cleared.targetForWeek(sep21))
    }

    @Test
    fun `progress is capped and absent without a target`() {
        assertEquals(0.5f, targetProgress(600, 1200)!!, 1e-6f)
        assertEquals(1f, targetProgress(2000, 1200)!!, 1e-6f)
        assertNull(targetProgress(600, null))
    }
}
