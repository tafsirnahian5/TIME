package com.example.timeapp

import com.example.timeapp.data.AutoBackup
import com.example.timeapp.data.AutoBackup.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class AutoBackupTest {
    private val t0 = Instant.parse("2026-09-28T09:00:00Z")
    private fun h(hours: Long) = t0.plusSeconds(hours * 3600)

    @Test fun dueAfterAlmostAPeriod() {
        assertTrue(AutoBackup.isDue(null, Frequency.DAILY, t0))
        assertFalse(AutoBackup.isDue(t0, Frequency.DAILY, h(19)))
        assertTrue(AutoBackup.isDue(t0, Frequency.DAILY, h(20)))
        assertFalse(AutoBackup.isDue(t0, Frequency.WEEKLY, h(24 * 6)))
        assertTrue(AutoBackup.isDue(t0, Frequency.WEEKLY, h(24 * 7)))
    }

    @Test fun namesSortByTimeAndSayIfProtected() {
        assertEquals("time-auto-20260928-090000.zip", AutoBackup.fileName(t0, ZoneOffset.UTC, encrypted = false))
        assertEquals("time-auto-20260928-090000.timebackup", AutoBackup.fileName(t0, ZoneOffset.UTC, encrypted = true))
    }

    @Test fun retentionKeepsTheNewestSevenOfOurOwnOnly() {
        val ours = (1..9).map { "time-auto-2026091$it-090000.zip" }
        val others = listOf("time-backup-123.zip", "holiday.jpg", "time-auto-notes.txt")
        val gone = AutoBackup.toDelete(others + ours.shuffled())
        assertEquals(listOf("time-auto-20260912-090000.zip", "time-auto-20260911-090000.zip"), gone)
        assertEquals(emptyList<String>(), AutoBackup.toDelete(ours.take(7) + others))
    }

    @Test fun reminderEveryNDaysOnlyWhenBehind() {
        assertFalse(AutoBackup.reminderDue(t0, null, days = 14, now = h(24 * 13)))
        assertTrue(AutoBackup.reminderDue(t0, null, days = 14, now = h(24 * 14)))
        assertFalse(AutoBackup.reminderDue(t0, h(24 * 14), days = 14, now = h(24 * 20)))
        assertTrue(AutoBackup.reminderDue(t0, h(24 * 14), days = 14, now = h(24 * 28)))
        assertFalse(AutoBackup.reminderDue(t0, null, days = 0, now = h(24 * 100)))
        // Never backed up: counted from install, so a new user isn't nagged on day one.
        assertFalse(AutoBackup.reminderDue(null, null, 14, now = h(24), installedAt = t0))
        assertTrue(AutoBackup.reminderDue(null, null, 14, now = h(24 * 15), installedAt = t0))
    }
}
