package com.example.timeapp

import com.example.timeapp.data.Reminder
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.isWithinQuietHours
import com.example.timeapp.data.splitMissed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

/** The PDF: "recovery should surface missed items without a burst of stale alarms". */
class ReminderRecoveryTest {
    private val now = Instant.parse("2026-09-28T09:00:00Z")
    private fun r(id: Long, kind: ReminderOwnerKind, minutesAgo: Long) = Reminder(id, kind, id, now.minusSeconds(minutesAgo * 60))

    @Test
    fun `slightly late reminders still fire as themselves, old ones are summarised`() {
        val pending = listOf(
            r(1, ReminderOwnerKind.TASK, 30), // late, within the hour
            r(2, ReminderOwnerKind.PLAN, 60 * 9), // overnight with the phone off
            r(3, ReminderOwnerKind.EVENT, -120), // still ahead
            r(4, ReminderOwnerKind.TIMER, 61),
        )
        val (fire, missed) = splitMissed(pending, now)
        assertEquals(listOf(1L, 3L), fire.map { it.id })
        assertEquals(listOf(2L, 4L), missed.map { it.id })
    }

    /** A habit's reminder walks forward to its next due day by itself, so it's never "missed". */
    @Test
    fun `habit reminders are never summarised`() {
        val (fire, missed) = splitMissed(listOf(r(5, ReminderOwnerKind.HABIT, 60 * 24)), now)
        assertEquals(1, fire.size)
        assertTrue(missed.isEmpty())
    }

    @Test
    fun `quiet hours run 22 to 7`() {
        assertTrue(isWithinQuietHours(LocalTime.of(22, 0)))
        assertTrue(isWithinQuietHours(LocalTime.of(6, 59)))
        assertFalse(isWithinQuietHours(LocalTime.of(7, 0)))
        assertFalse(isWithinQuietHours(LocalTime.of(21, 59)))
    }

    @Test
    fun `quiet hours can be set, wrap midnight, or be off`() {
        val start = LocalTime.of(13, 0); val end = LocalTime.of(14, 0)
        assertTrue(isWithinQuietHours(LocalTime.of(13, 30), start, end))
        assertFalse(isWithinQuietHours(LocalTime.of(14, 0), start, end))
        assertTrue(isWithinQuietHours(LocalTime.of(23, 0), LocalTime.of(23, 0), LocalTime.of(6, 0)))
        assertFalse(isWithinQuietHours(LocalTime.of(23, 30), enabled = false))
        assertFalse(isWithinQuietHours(LocalTime.of(23, 30), start, start))
    }
}
