package com.example.timeapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timeapp.data.Clocks
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.PhaseEnd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The one work timer (Phase 9) against a real in-memory Room database, with the clocks
 * injected: boot 7, monotonic time starting at 1,000,000 ms. Needs a device.
 */
@RunWith(AndroidJUnit4::class)
class WorkTimerTest {
    private lateinit var db: TimeDatabase
    private lateinit var repo: EntryRepository
    private val t0 = Instant.parse("2026-09-28T09:00:00Z")
    private fun at(min: Long, boot: Int = 7) = Clocks(t0.plusSeconds(min * 60), 1_000_000 + min * 60_000, boot)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TimeDatabase::class.java).build()
        repo = EntryRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun task(title: String) = repo.insert(Entry(0, EntryKind.TASK, title, status = EntryStatus.PLANNED))
    private suspend fun status(id: Long) = db.entryDao().getById(id)!!.status
    private suspend fun spans() = repo.sessionsInRange(Instant.EPOCH, t0.plusSeconds(86_400)).first().sortedBy { it.startedAt }

    /** Focus and a task's Start are one timer: switching closes and opens at one instant. */
    @Test
    fun switchingMeetsExactlyAndPausesTheOldTask() = runBlocking {
        val a = task("Portfolio")
        val b = task("Client call")
        repo.timer.start(a, FocusMode.STOPWATCH, c = at(0))
        repo.timer.start(b, resumeNote = "skills page", c = at(18))
        repo.timer.pause(c = at(35))
        val s = spans()
        assertEquals(2, s.size)
        assertEquals(s[0].endedAt, s[1].startedAt)
        assertEquals(17L * 60_000, s[1].endedAt.toEpochMilli() - s[1].startedAt.toEpochMilli())
        assertEquals(EntryStatus.PAUSED, status(a))
        assertEquals(EntryStatus.PAUSED, status(b))
        assertEquals("skills page", db.entryDao().getById(a)!!.resumeNote)
    }

    /** A countdown's expiry ends work at the due moment, not at a late delivery, and asks. */
    @Test
    fun countdownExpiryStopsAtTheDueMomentWithoutCompleting() = runBlocking {
        val a = task("Essay")
        repo.timer.setLengths(countdownMinutes = 25)
        repo.timer.start(a, FocusMode.COUNTDOWN, c = at(0))
        // The alarm is delivered ten minutes late.
        val end = repo.timer.onPhaseExpired(t0.plusSeconds(25 * 60), c = at(35))
        assertEquals(PhaseEnd.CountdownDone, end)
        assertEquals(t0.plusSeconds(25 * 60), spans().single().endedAt)
        assertEquals(EntryStatus.PAUSED, status(a))
        assertTrue(repo.timer.runtime().awaitingChoice)
        // A stale alarm for the same phase changes nothing.
        assertNull(repo.timer.onPhaseExpired(t0.plusSeconds(25 * 60), c = at(36)))
    }

    @Test
    fun aPomodoroBreakIsNotWorkTime() = runBlocking {
        repo.timer.setLengths(workMinutes = 25, breakMinutes = 5, autoStart = true)
        repo.timer.start(null, FocusMode.POMODORO, c = at(0))
        repo.timer.onPhaseExpired(t0.plusSeconds(25 * 60), c = at(25))
        assertEquals(FocusPhase.BREAK, repo.timer.runtime().phase)
        repo.timer.onPhaseExpired(t0.plusSeconds(30 * 60), c = at(30))
        repo.timer.pause(c = at(40))
        val s = spans()
        assertEquals(listOf(25L, 10L), s.map { (it.endedAt.toEpochMilli() - it.startedAt.toEpochMilli()) / 60_000 })
        assertEquals(t0.plusSeconds(30 * 60), s[1].startedAt) // the break's 5 minutes are not in either
    }

    /** Across a reboot: closed at the last checkpoint, flagged, never guessed longer. */
    @Test
    fun rebootClosesAtTheCheckpointForReview() = runBlocking {
        val a = task("Report")
        repo.timer.start(a, c = at(0))
        repo.timer.checkpoint(c = at(40))
        assertTrue(repo.timer.recoverAfterReboot(c = at(300, boot = 8)))
        val s = db.workSessionDao().getActive()
        assertNull(s)
        val closed = spans().single()
        assertEquals(t0.plusSeconds(40 * 60), closed.endedAt)
        assertTrue(db.workSessionDao().observeNeedingReview().first().isNotEmpty())
        assertEquals(EntryStatus.PAUSED, status(a))
        assertEquals(false, repo.timer.runtime().running)
    }

    /** A time fix keeps the original and its reason; one that would double-count is refused. */
    @Test
    fun editingKeepsTheOriginalAndRefusesOverlap() = runBlocking {
        val a = task("Report")
        repo.timer.start(a, c = at(0))
        repo.timer.start(null, c = at(30))
        repo.timer.pause(c = at(60))
        val first = spans().first()
        val id = db.workSessionDao().observeForEntry(a).first().single().id
        val clash = repo.timer.editSession(id, first.startedAt, t0.plusSeconds(45 * 60), null, "forgot", c = at(120))
        assertTrue(clash!!.contains("overlaps"))
        assertEquals("Say why the times changed — it's kept with the original.", repo.timer.editSession(id, first.startedAt, t0.plusSeconds(20 * 60), null, "", c = at(120)))
        assertNull(repo.timer.editSession(id, first.startedAt, t0.plusSeconds(20 * 60), "draft", "stopped early", c = at(120)))
        val fix = db.focusDao().observeCorrections(id).first().single()
        assertEquals(t0.plusSeconds(30 * 60), fix.oldEndedAt)
        assertEquals("stopped early", fix.reason)
    }
}
