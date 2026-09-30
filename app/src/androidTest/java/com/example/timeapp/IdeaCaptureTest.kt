package com.example.timeapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timeapp.data.CollectionKind
import com.example.timeapp.data.db.IdeaCollectionEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.repository.IdeaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fresh-install bug: quick capture looked its collection up by name, the collections didn't
 * exist yet, and every idea was silently dropped. On an empty database an idea must now always
 * land — in Sparks by default, or in the collection chosen.
 */
@RunWith(AndroidJUnit4::class)
class IdeaCaptureTest {
    private lateinit var db: TimeDatabase
    private lateinit var repo: IdeaRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TimeDatabase::class.java).build()
        repo = IdeaRepository(db.ideaCollectionDao(), db.ideaDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun anIdeaOnAnEmptyDatabaseLandsInSparks() = runBlocking<Unit> {
        repo.addIdea("Learn to develop film", null)
        val sparks = db.ideaCollectionDao().firstOfKind(CollectionKind.SPARKS)
        assertNotNull("Sparks is created when missing", sparks)
        val ideas = db.ideaDao().observeAll().first()
        assertEquals(1, ideas.size)
        assertEquals(sparks!!.id, ideas.single().collectionId)
    }

    @Test
    fun defaultsAreCreatedOnceAndTheChosenCollectionIsUsed() = runBlocking<Unit> {
        repo.ensureDefaults(); repo.ensureDefaults()
        assertEquals(2, db.ideaCollectionDao().count())
        val mine = db.ideaCollectionDao().insert(IdeaCollectionEntity(name = "Recipes", kind = CollectionKind.LIST, icon = "menu_book", iconColorArgb = 0, count = 0))
        repo.addIdea("  Biryani  ", mine)
        val idea = db.ideaDao().observeAll().first().single()
        assertEquals(mine, idea.collectionId)
        assertEquals("Biryani", idea.title)
    }

    @Test
    fun aDeletedCollectionFallsBackToSparksRatherThanDropping() = runBlocking<Unit> {
        repo.addIdea("Somewhere", 9_999)
        assertEquals(1, db.ideaDao().observeAll().first().size)
    }
}
