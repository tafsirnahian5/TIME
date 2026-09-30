package com.example.timeapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.repository.EntryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Release 1.3's exit check: "switching never overlaps work time". The PDF's own example —
 * Portfolio at 09:00, switch to a client call at 09:18, back at 09:35. Needs a device.
 */
@RunWith(AndroidJUnit4::class)
class SwitchWorkTest {
    private lateinit var db: TimeDatabase
    private lateinit var repo: EntryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TimeDatabase::class.java).build()
        repo = EntryRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun at(hhmm: String) = Instant.parse("2026-09-11T${hhmm}:00Z")
    private suspend fun task(title: String) = repo.insert(Entry(0, EntryKind.TASK, title, status = EntryStatus.PLANNED)).let { id ->
        repo.observeEntries().first().single { it.id == id }
    }

    @Test
    fun switchClosesAndOpensAtTheSameInstant() = runBlocking {
        val portfolio = task("Portfolio")
        val call = task("Client call")
        repo.switchWork(null, portfolio, null, at("09:00"))
        repo.switchWork(repo.observeEntries().first().single { it.id == portfolio.id }, call, "resume from skills", at("09:18"))
        repo.switchWork(repo.observeEntries().first().single { it.id == call.id }, portfolio.copy(status = EntryStatus.PAUSED), null, at("09:35"))

        val spans = repo.sessionsInRange(Instant.EPOCH, at("23:59")).first().sortedBy { it.startedAt }
        assertEquals(2, spans.size) // the running third segment has no end yet
        assertEquals(at("09:18"), spans[0].endedAt)
        assertEquals(at("09:18"), spans[1].startedAt) // meets exactly — no gap, no overlap
        assertEquals(17L * 60_000, spans[1].endedAt.toEpochMilli() - spans[1].startedAt.toEpochMilli())

        val events = repo.observeEvents(portfolio.id).first()
        val switched = events.single { it.relatedEntryId == call.id }
        assertEquals("resume from skills", switched.note)
        assertEquals(EntryStatus.PAUSED, switched.toStatus)
    }

    /** A second Start tap on the running task must not close and reopen its segment. */
    @Test
    fun repeatedStartDoesNotDuplicateSegments() = runBlocking {
        val t = task("Portfolio")
        repo.startSession(t.id, at("09:00"))
        repo.startSession(t.id, at("09:00"))
        repo.startSession(t.id, at("09:01"))
        repo.endSession(t.id, at("10:00"))
        val spans = repo.sessionsInRange(Instant.EPOCH, at("23:59")).first()
        assertEquals(1, spans.size)
        assertEquals(at("09:00"), spans.single().startedAt)
    }
}
