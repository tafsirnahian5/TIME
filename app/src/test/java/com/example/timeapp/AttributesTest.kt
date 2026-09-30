package com.example.timeapp

import com.example.timeapp.data.Commitment
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.WorkSessionSpan
import com.example.timeapp.data.WorkTarget
import com.example.timeapp.data.computeAttributes
import com.example.timeapp.data.weeklyReviewKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class AttributesTest {
    private val zone = ZoneOffset.UTC
    private val monday = LocalDate.of(2026, 9, 21)
    private val today = monday.plusDays(4) // Friday
    private val now = today.atTime(12, 0).toInstant(zone)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).toInstant(zone)
    private fun work(d: LocalDate, h: Int, minutes: Long, entry: Long? = null) =
        WorkSessionSpan(entry, at(d, h), at(d, h).plusSeconds(minutes * 60))
    private fun task(id: Long, status: EntryStatus = EntryStatus.PLANNED) = Entry(id, EntryKind.TASK, "t$id", status = status)

    private fun compute(
        sessions: List<WorkSessionSpan> = emptyList(),
        targets: List<WorkTarget> = emptyList(),
        commitments: List<Commitment> = emptyList(),
        entries: List<Entry> = emptyList(),
        restDays: Set<DayOfWeek> = emptySet(),
        blocks: List<Entry> = emptyList(),
        reviews: Set<String> = emptySet(),
        firstReview: LocalDate? = null,
        weeks: List<LocalDate> = listOf(monday),
    ) = computeAttributes(weeks, today, now, zone, sessions, targets, commitments, entries.associateBy { it.id }, restDays, blocks, reviews, firstReview)
        .associateBy { it.name }

    @Test fun nothingToDivideByIsNotEnoughDataNeverZero() {
        val a = compute()
        listOf("Focus", "Delivery", "Planning", "Money review").forEach { assertNull(it, a[it]!!.value) }
        // Elapsed workdays with no work are real data: an honest 0, not "no data".
        assertEquals("0 of 4", a["Consistency"]!!.value)
    }

    @Test fun focusIsActiveOverTarget() {
        val a = compute(sessions = listOf(work(monday, 9, 600)), targets = listOf(WorkTarget(1, 1200, monday)))
        assertEquals("50%", a["Focus"]!!.value)
        assertEquals("Active work 10 h ÷ weekly target 20 h", a["Focus"]!!.formula)
    }

    @Test fun deliveryCountsTodayOnlyOnceDone() {
        val c = listOf(
            Commitment(monday, 1, at(monday, 8)), Commitment(monday, 2, at(monday, 8)),
            Commitment(today, 3, at(today, 8)), Commitment(today, 4, at(today, 8)),
            Commitment(monday.plusDays(1), 5, at(monday, 8), removedAt = at(monday, 9)),
        )
        val e = listOf(task(1, EntryStatus.DONE), task(2), task(3, EntryStatus.DONE), task(4), task(5))
        assertEquals("2 of 3", compute(commitments = c, entries = e)["Delivery"]!!.value)
    }

    @Test fun consistencySkipsRestDaysAndAnUnmetToday() {
        val s = listOf(work(monday, 9, 90), work(monday.plusDays(1), 9, 30), work(monday.plusDays(3), 9, 61))
        // Mon met, Tue not, Wed rest, Thu met, Fri (today) not yet → 2 of 3.
        assertEquals("2 of 3", compute(sessions = s, restDays = setOf(DayOfWeek.WEDNESDAY))["Consistency"]!!.value)
    }

    @Test fun planningNeedsAStartNearTheBlock() {
        val blocks = listOf(
            Entry(1, EntryKind.TASK, "a", status = EntryStatus.PLANNED, startAt = at(monday, 9), endAt = at(monday, 10)),
            Entry(2, EntryKind.TASK, "b", status = EntryStatus.PLANNED, startAt = at(monday, 14), endAt = at(monday, 15)),
            Entry(3, EntryKind.TASK, "c", status = EntryStatus.PLANNED, startAt = at(today, 11, 55), endAt = at(today, 13)),
        )
        val s = listOf(work(monday, 9, 30, entry = 1), WorkSessionSpan(2, at(monday, 14, 25), at(monday, 15)))
        assertEquals("1 of 2", compute(sessions = s, blocks = blocks)["Planning"]!!.value)
    }

    @Test fun moneyReviewCountsFromTheFirstReview() {
        val weeks = (3 downTo 0).map { monday.minusWeeks(it.toLong()) }
        val reviews = setOf(weeklyReviewKey(weeks[1]), weeklyReviewKey(weeks[2]))
        // First review in week 1; weeks 1 and 2 ended and reviewed, this week not yet → 2 of 2.
        assertEquals("2 of 2", compute(weeks = weeks, reviews = reviews, firstReview = weeks[1])["Money review"]!!.value)
    }
}
