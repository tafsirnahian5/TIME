package com.example.timeapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.repository.EntryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Several blocks per task (Phase 14): first block on the entry, extras in schedule_blocks. Needs a device. */
@RunWith(AndroidJUnit4::class)
class ScheduleBlockRepoTest {
    private lateinit var db: TimeDatabase
    private lateinit var repo: EntryRepository
    private val t0 = Instant.parse("2026-10-01T09:00:00Z")
    private fun at(min: Long) = t0.plusSeconds(min * 60)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TimeDatabase::class.java).build()
        repo = EntryRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun firstBlockGoesOnTheEntryThenExtrasAndRemovalPromotes() = runBlocking {
        val id = repo.insert(Entry(0, EntryKind.TASK, "Split me", status = EntryStatus.UNSCHEDULED))
        repo.addBlock(id, at(0), at(45))
        assertEquals(at(0), db.entryDao().getById(id)!!.startAt)
        assertEquals(EntryStatus.PLANNED, db.entryDao().getById(id)!!.status)
        repo.addBlock(id, at(120), at(165))
        assertEquals(1, db.scheduleBlockDao().forEntry(id).size)

        repo.removeBlock(id, null)
        val row = db.entryDao().getById(id)!!
        assertEquals(at(120), row.startAt)
        assertEquals(0, db.scheduleBlockDao().forEntry(id).size)
        repo.removeBlock(id, null)
        assertNull(db.entryDao().getById(id)!!.startAt)
    }

    @Test
    fun splitKeepsTheTotalAndRescheduleDropsOnlyFutureExtras() = runBlocking {
        val id = repo.insert(Entry(0, EntryKind.TASK, "Split me", status = EntryStatus.PLANNED, startAt = at(0), endAt = at(90)))
        repo.splitBlock(id, null, firstMs = 45 * 60_000L, secondStart = at(240))
        assertEquals(at(45), db.entryDao().getById(id)!!.endAt)
        val extra = db.scheduleBlockDao().forEntry(id).single()
        assertEquals(at(240) to at(285), extra.startAt to extra.endAt)

        val entry = db.entryDao().getById(id)!!.toDomain()
        repo.replaceFutureBlocks(entry, at(600), at(645), now = at(100))
        assertEquals(0, db.scheduleBlockDao().forEntry(id).size)
        assertEquals(at(600), db.entryDao().getById(id)!!.startAt)
    }

    @Test
    fun deletingTheTaskCascadesItsBlocksAndUndoBringsThemBack() = runBlocking {
        val id = repo.insert(Entry(0, EntryKind.TASK, "Split me", status = EntryStatus.PLANNED, startAt = at(0), endAt = at(45)))
        repo.addBlock(id, at(120), at(165))
        val (_, snap) = repo.deleteInSeriesUndoable(id, com.example.timeapp.data.SeriesScope.ONLY_THIS)!!
        assertEquals(0, db.scheduleBlockDao().forEntry(id).size)
        repo.restoreRows(snap)
        assertEquals(1, db.scheduleBlockDao().forEntry(id).size)
    }
}
