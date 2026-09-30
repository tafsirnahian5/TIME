package com.example.timeapp

import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.Ics
import com.example.timeapp.data.RecurrenceFreq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class IcsTest {
    private val dhaka = ZoneId.of("Asia/Dhaka")
    private fun plan(ics: String, existing: List<Entry> = emptyList()) = Ics.plan(Ics.parse(ics), existing, dhaka)
    private fun cal(vararg eventLines: String) = (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VEVENT") + eventLines + listOf("END:VEVENT", "END:VCALENDAR")).joinToString("\r\n")

    @Test
    fun `export then import keeps title, note, times and identity`() {
        val e = Entry(
            7, EntryKind.EVENT, "Standup; team, all", status = EntryStatus.PLANNED,
            startAt = Instant.parse("2026-09-28T03:00:00Z"), endAt = Instant.parse("2026-09-28T03:15:00Z"),
            description = "Line one\n- [ ] ask about \\ backslash, commas; semis — and a very long line that must be folded because it runs well past seventy-five octets",
        )
        val text = Ics.export(listOf(e))
        assertTrue(text.lines().all { it.toByteArray().size <= 76 }) // 75 + the CR
        val back = plan(text).single()
        assertEquals(e.title, back.title)
        assertEquals(e.description, back.note)
        assertEquals(e.startAt, back.start)
        assertEquals(e.endAt, back.end)
        assertEquals("entry-7@time.local", back.uid)
    }

    @Test
    fun `named zones, utc and floating times all land on the right instant`() {
        val london = plan(cal("UID:a", "SUMMARY:x", "DTSTART;TZID=Europe/London:20260928T090000", "DURATION:PT1H30M")).single()
        assertEquals(Instant.parse("2026-09-28T08:00:00Z"), london.start)
        assertEquals(Duration.ofMinutes(90), Duration.between(london.start, london.end))
        assertTrue(london.remarks.any { it.contains("Europe/London") })
        val floating = plan(cal("UID:b", "SUMMARY:x", "DTSTART:20260928T090000", "DTEND:20260928T100000")).single()
        assertEquals(Instant.parse("2026-09-28T03:00:00Z"), floating.start) // 09:00 in Dhaka
        val bad = plan(cal("UID:c", "SUMMARY:x", "DTSTART;TZID=Mars/Olympus:20260928T090000")).single()
        assertNotNull(bad.error)
    }

    @Test
    fun `all-day events become tasks due that day`() {
        val item = plan(cal("UID:d", "SUMMARY:Rent due", "DTSTART;VALUE=DATE:20261001")).single()
        assertTrue(item.allDay)
        assertEquals(LocalDate.of(2026, 10, 1), item.dueOn)
    }

    @Test
    fun `weekly byday with count and exdate maps onto the repeat rule`() {
        val item = plan(cal(
            "UID:e", "SUMMARY:Gym", "DTSTART:20260928T010000Z", "DTEND:20260928T020000Z",
            "RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=4", "EXDATE:20260930T010000Z",
        )).single()
        val rule = item.recurrence!!
        assertEquals(RecurrenceFreq.WEEKLY, rule.freq)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), rule.weekdays)
        assertEquals(LocalDate.of(2026, 10, 5), rule.endOn) // Mon, Wed, Fri, Mon
        assertEquals(setOf(LocalDate.of(2026, 9, 30)), rule.skipped)
    }

    @Test
    fun `rules outside the subset import the first occurrence and say why`() {
        val item = plan(cal("UID:f", "SUMMARY:x", "DTSTART:20260928T010000Z", "DTEND:20260928T020000Z", "RRULE:FREQ=YEARLY")).single()
        assertNull(item.recurrence)
        assertTrue(item.remarks.any { it.contains("first occurrence") })
        val ordinal = plan(cal("UID:g", "SUMMARY:x", "DTSTART:20260928T010000Z", "DTEND:20260928T020000Z", "RRULE:FREQ=MONTHLY;BYDAY=1MO")).single()
        assertNull(ordinal.recurrence)
    }

    @Test
    fun `unsupported fields are reported and cancelled events skipped`() {
        val text = listOf(
            "BEGIN:VCALENDAR",
            "BEGIN:VEVENT", "UID:h", "SUMMARY:Meeting", "DTSTART:20260928T010000Z", "DTEND:20260928T020000Z",
            "ATTENDEE:mailto:a@b.c", "ATTENDEE:mailto:d@e.f", "BEGIN:VALARM", "TRIGGER:-PT15M", "END:VALARM", "END:VEVENT",
            "BEGIN:VEVENT", "UID:i", "SUMMARY:Gone", "STATUS:CANCELLED", "DTSTART:20260929T010000Z", "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n")
        val parsed = Ics.parse(text)
        assertEquals(2, parsed.ignored["ATTENDEE"])
        assertEquals(1, parsed.ignored["VALARM"])
        assertEquals(listOf("Meeting"), Ics.plan(parsed, emptyList(), dhaka).map { it.title })
    }

    /** Occurrence identity: the same UID again is recognised as the saved event. */
    @Test
    fun `a re-imported uid points at the saved entry`() {
        val saved = Entry(42, EntryKind.EVENT, "Gym", status = EntryStatus.PLANNED, icsUid = "e")
        val item = plan(cal("UID:e", "SUMMARY:Gym", "DTSTART:20260928T010000Z", "DTEND:20260928T020000Z"), listOf(saved)).single()
        assertEquals(42L, item.existingId)
    }
}
