package com.example.timeapp

import com.example.timeapp.data.InboxItem
import com.example.timeapp.data.InboxKind
import com.example.timeapp.data.InboxSection
import com.example.timeapp.data.inboxSections
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class InboxTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val today = LocalDate.of(2026, 9, 29)
    private fun item(id: Long, day: LocalDate, h: Int, m: Int = 0) =
        InboxItem(id, InboxKind.TASK, "t$id", "", null, day.atTime(LocalTime.of(h, m)).atZone(zone).toInstant(), read = false)

    @Test fun `groups by the phone's day, newest first`() {
        val items = listOf(
            item(1, today.minusDays(3), 9),
            item(2, today.minusDays(1), 23, 59),
            item(3, today, 0, 1),
            item(4, today, 9),
        )
        val sections = inboxSections(items, today, zone)
        assertEquals(listOf(InboxSection.TODAY, InboxSection.YESTERDAY, InboxSection.EARLIER), sections.map { it.first })
        assertEquals(listOf(4L, 3L), sections[0].second.map { it.id })
        assertEquals(listOf(2L), sections[1].second.map { it.id })
        assertEquals(listOf(1L), sections[2].second.map { it.id })
    }

    @Test fun `empty groups are left out`() {
        val sections = inboxSections(listOf(item(1, today.minusDays(5), 8)), today, zone)
        assertEquals(listOf(InboxSection.EARLIER), sections.map { it.first })
    }

    @Test fun `same moment keeps the newer row first`() {
        val a = item(1, today, 9)
        val b = a.copy(id = 2)
        assertEquals(listOf(2L, 1L), inboxSections(listOf(a, b), today, zone).single().second.map { it.id })
    }
}
