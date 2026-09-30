package com.example.timeapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.SeriesScope
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.repository.EntryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Phase 14 Undo: a deleted or edited task comes back exactly — ids, links and time. Needs a device. */
@RunWith(AndroidJUnit4::class)
class UndoRestoreTest {
    private lateinit var db: TimeDatabase
    private lateinit var repo: EntryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TimeDatabase::class.java).build()
        repo = EntryRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun task(title: String) = repo.insert(Entry(0, EntryKind.TASK, title, status = EntryStatus.PLANNED))

    @Test
    fun deletedTaskComesBackWithEverythingAttached() = runBlocking {
        val a = task("Portfolio")
        val b = task("Prerequisite")
        repo.setTags(a, listOf("android", "Client work"))
        repo.addSubtask(a, "Draft", 0)
        repo.addDependency(a, b)
        repo.timer.start(a, FocusMode.STOPWATCH)
        repo.timer.pause()
        val sessionsBefore = db.workSessionDao().countForEntry(a)

        val (removed, snap) = repo.deleteInSeriesUndoable(a, SeriesScope.ONLY_THIS)!!
        assertEquals(listOf(a), removed)
        assertNull(db.entryDao().getById(a))
        // Tags nobody uses any more are pruned on the next setTags — make that happen.
        repo.setTags(b, emptyList())

        repo.restoreRows(snap)
        val back = db.entryDao().getById(a)
        assertNotNull(back)
        assertEquals("Portfolio", back!!.title)
        assertEquals(setOf("android", "client-work"), repo.observeEntries().first().first { it.id == a }.tags.toSet())
        assertEquals(1, repo.observeSubtasks(a).first().size)
        assertEquals(sessionsBefore, db.workSessionDao().countForEntry(a))
        assertEquals(1, db.dependencyDao().all().count { it.taskId == a && it.prerequisiteId == b })
    }

    @Test
    fun runningTaskIsStoppedBeforeTheCopy() = runBlocking {
        val a = task("Running")
        repo.timer.start(a, FocusMode.STOPWATCH, c = com.example.timeapp.data.Clocks(Instant.now().minusSeconds(600), 1_000_000, 1))
        val (_, snap) = repo.deleteInSeriesUndoable(a, SeriesScope.ONLY_THIS)!!
        repo.restoreRows(snap)
        // Back, but not timing: every restored session is closed.
        assertEquals(1, db.workSessionDao().countForEntry(a))
        assertNull(db.workSessionDao().getActiveForEntry(a))
    }

    @Test
    fun editUndoPutsTheOldValuesBack() = runBlocking {
        val a = task("Old title")
        val snap = repo.snapshotRows(a, alsoSeries = a)!!
        repo.updateInSeries(db.entryDao().getById(a)!!.toDomain().copy(title = "New title"), SeriesScope.ONLY_THIS)
        assertEquals("New title", db.entryDao().getById(a)!!.title)
        repo.restoreRows(snap)
        assertEquals("Old title", db.entryDao().getById(a)!!.title)
    }
}
