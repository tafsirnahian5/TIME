package com.example.timeapp

import com.example.timeapp.data.Recurrence
import com.example.timeapp.data.RecurrenceFreq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.example.timeapp.data.sameRuleAs

class RecurrenceTest {

    @Test
    fun `daily and weekly step by the interval`() {
        val start = LocalDate.of(2026, 3, 1)
        assertEquals(LocalDate.of(2026, 3, 2), Recurrence(RecurrenceFreq.DAILY).nextAfter(start))
        assertEquals(LocalDate.of(2026, 3, 8), Recurrence(RecurrenceFreq.WEEKLY).nextAfter(start))
        assertEquals(LocalDate.of(2026, 3, 15), Recurrence(RecurrenceFreq.WEEKLY, interval = 2).nextAfter(start))
    }

    /** The PDF's day-31 case: clamp to the last valid day rather than skipping the month,
     * so a monthly bill never vanishes from February. */
    @Test
    fun `monthly on the 31st clamps to the last valid day`() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val feb = Recurrence(RecurrenceFreq.MONTHLY).nextAfter(jan31)
        assertEquals(LocalDate.of(2026, 2, 28), feb)
    }

    @Test
    fun `a leap february still gets an occurrence`() {
        val jan31 = LocalDate.of(2028, 1, 31)
        assertEquals(LocalDate.of(2028, 2, 29), Recurrence(RecurrenceFreq.MONTHLY).nextAfter(jan31))
    }

    @Test
    fun `an end date stops the series`() {
        val rule = Recurrence(RecurrenceFreq.MONTHLY, endOn = LocalDate.of(2026, 3, 31))
        assertEquals(LocalDate.of(2026, 3, 15), rule.nextAfter(LocalDate.of(2026, 2, 15)))
        assertNull(rule.nextAfter(LocalDate.of(2026, 3, 15)))
    }

    @Test
    fun `zero or negative interval cannot stall the expander`() {
        // A bad interval must still advance, or the bounded expander would loop forever.
        val next = Recurrence(RecurrenceFreq.DAILY, interval = 0).nextAfter(LocalDate.of(2026, 3, 1))
        assertEquals(LocalDate.of(2026, 3, 2), next)
    }

    @Test
    fun `rules round-trip through their stored form`() {
        listOf(
            Recurrence(RecurrenceFreq.DAILY),
            Recurrence(RecurrenceFreq.WEEKLY, interval = 3),
            Recurrence(RecurrenceFreq.MONTHLY, interval = 2, endOn = LocalDate.of(2027, 1, 1)),
        ).forEach { rule ->
            assertEquals(rule, Recurrence.parse(rule.serialize()))
        }
    }

    @Test
    fun `absent or corrupt stored rules parse to no recurrence`() {
        assertNull(Recurrence.parse(null))
        assertNull(Recurrence.parse(""))
        assertNull(Recurrence.parse("NONSENSE|1|"))
    }

    @Test
    fun `labels read naturally`() {
        assertEquals("Monthly", Recurrence(RecurrenceFreq.MONTHLY).label())
        assertEquals("Every 2 weeks", Recurrence(RecurrenceFreq.WEEKLY, interval = 2).label())
    }

    /** Habit-shaped "Mon, Wed, Fri": walks the listed days, then wraps to next week. */
    @Test
    fun `weekly on listed weekdays walks the days then wraps`() {
        val rule = Recurrence(RecurrenceFreq.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val mon = LocalDate.of(2026, 9, 28) // a Monday
        assertEquals(LocalDate.of(2026, 9, 30), rule.nextAfter(mon))
        assertEquals(LocalDate.of(2026, 10, 2), rule.nextAfter(LocalDate.of(2026, 9, 30)))
        assertEquals(LocalDate.of(2026, 10, 5), rule.nextAfter(LocalDate.of(2026, 10, 2)))
    }

    @Test
    fun `every two weeks on weekdays skips a whole week after the last day`() {
        val rule = Recurrence(RecurrenceFreq.WEEKLY, interval = 2, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        assertEquals(LocalDate.of(2026, 10, 1), rule.nextAfter(LocalDate.of(2026, 9, 28)))
        assertEquals(LocalDate.of(2026, 10, 12), rule.nextAfter(LocalDate.of(2026, 10, 1)))
    }

    /** A first occurrence on an unlisted day (created on a Sunday for a Mon/Fri habit) still
     * lands on the next listed day rather than drifting. */
    @Test
    fun `an unlisted start day moves to the next listed day`() {
        val rule = Recurrence(RecurrenceFreq.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertEquals(LocalDate.of(2026, 10, 5), rule.nextAfter(LocalDate.of(2026, 10, 4)))
        assertEquals(LocalDate.of(2026, 10, 2), rule.nextAfter(LocalDate.of(2026, 9, 29)))
    }

    @Test
    fun `serialize round-trips every field`() {
        val rule = Recurrence(
            RecurrenceFreq.WEEKLY, 2, LocalDate.of(2027, 1, 1),
            setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY), remind = true,
            skipped = setOf(LocalDate.of(2026, 10, 5)),
        )
        assertEquals(rule, Recurrence.parse(rule.serialize()))
    }

    /** Rows written before weekdays/reminders/skips existed must still parse unchanged. */
    @Test
    fun `old three-field rules still parse`() {
        assertEquals(Recurrence(RecurrenceFreq.MONTHLY, 1, null), Recurrence.parse("MONTHLY|1|"))
        assertEquals(Recurrence(RecurrenceFreq.DAILY, 3, LocalDate.of(2026, 12, 31)), Recurrence.parse("DAILY|3|2026-12-31"))
    }

    @Test
    fun `label names the weekdays`() {
        assertEquals("Weekly on Mon, Fri", Recurrence(RecurrenceFreq.WEEKLY, weekdays = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)).label())
        assertEquals("Every 2 weeks", Recurrence(RecurrenceFreq.WEEKLY, 2).label())
    }

    @Test
    fun `same rule ignores reminder and skipped bookkeeping`() {
        val base = Recurrence(RecurrenceFreq.MONTHLY)
        assertTrue(base.sameRuleAs(base.copy(remind = true, skipped = setOf(LocalDate.of(2026, 1, 1)))))
        assertFalse(base.sameRuleAs(base.copy(interval = 2)))
        assertFalse(base.sameRuleAs(null))
    }

    /** Stepping from each previous occurrence used to drift: Jan 31 → Feb 28 → Mar 28 → … */
    @Test
    fun `monthly returns to the anchor day after a short month`() {
        val rule = Recurrence(RecurrenceFreq.MONTHLY)
        val jan31 = LocalDate.of(2026, 1, 31)
        val feb = rule.nextAfter(jan31, anchor = jan31)!!
        assertEquals(LocalDate.of(2026, 2, 28), feb)
        assertEquals(LocalDate.of(2026, 3, 31), rule.nextAfter(feb, anchor = jan31))
        assertEquals(LocalDate.of(2026, 4, 30), rule.nextAfter(LocalDate.of(2026, 3, 31), anchor = jan31))
    }

    @Test
    fun `skip rule leaves out months without the day`() {
        val rule = Recurrence(RecurrenceFreq.MONTHLY, monthEnd = com.example.timeapp.data.MonthEndRule.SKIP_MONTH)
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 3, 31), rule.nextAfter(jan31, anchor = jan31))
        assertEquals(LocalDate.of(2026, 5, 31), rule.nextAfter(LocalDate.of(2026, 3, 31), anchor = jan31))
        // The 30th only skips February.
        val jan30 = LocalDate.of(2026, 1, 30)
        assertEquals(LocalDate.of(2026, 3, 30), rule.nextAfter(jan30, anchor = jan30))
        // A day every month has is unaffected.
        assertEquals(LocalDate.of(2026, 2, 15), rule.nextAfter(LocalDate.of(2026, 1, 15)))
    }

    @Test
    fun `yearly-ish skip on the 29th of February stays bounded`() {
        val rule = Recurrence(RecurrenceFreq.MONTHLY, interval = 12, monthEnd = com.example.timeapp.data.MonthEndRule.SKIP_MONTH)
        val leap = LocalDate.of(2028, 2, 29)
        assertEquals(LocalDate.of(2032, 2, 29), rule.nextAfter(leap, anchor = leap))
    }

    @Test
    fun `month end rule round trips and labels itself`() {
        val rule = Recurrence(RecurrenceFreq.MONTHLY, monthEnd = com.example.timeapp.data.MonthEndRule.SKIP_MONTH)
        assertEquals(rule, Recurrence.parse(rule.serialize()))
        assertEquals("Monthly, skipping short months", rule.label())
        // Rows written before the field existed read as the default.
        assertEquals(com.example.timeapp.data.MonthEndRule.LAST_VALID_DAY, Recurrence.parse("MONTHLY|1||||")!!.monthEnd)
    }

    @Test
    fun `checklist lines come out as steps and leave the rest`() {
        val (items, rest) = com.example.timeapp.ui.richtext.extractChecklist("Intro\n\n- [ ] one\n- [x] two\n\nOutro\n- [ ] three")
        assertEquals(listOf("one" to false, "two" to true, "three" to false), items.map { it.text to it.checked })
        assertEquals("Intro\n\nOutro", rest)
    }
}
