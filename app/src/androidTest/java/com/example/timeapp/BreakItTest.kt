package com.example.timeapp

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timeapp.data.Direction
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.WalletKind
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.MoneyRepository
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.richtext.parseMarkdownLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.time.LocalDate
import kotlin.random.Random

/**
 * Trying to break the data layer on purpose, against throw-away in-memory databases (never the
 * real one): the timer hammered from many threads at once, payments raced, and absurd input.
 * Every test states the invariant that must survive. Findings are logged under `BreakItTest`.
 */
@RunWith(AndroidJUnit4::class)
class BreakItTest {
    private lateinit var db: TimeDatabase
    private lateinit var entries: EntryRepository
    private lateinit var money: MoneyRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TimeDatabase::class.java).build()
        entries = EntryRepository(db)
        money = MoneyRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun log(s: String) = Log.i("BreakItTest", s)

    /** 400 random timer commands from 40 threads at once: one timer, no overlapping work, ever. */
    @Test
    fun timerHammeredFromManyThreadsKeepsOneTimerAndNoOverlap() = runBlocking<Unit> {
        val ids = (1..6).map { entries.insert(Entry(0, EntryKind.TASK, "T$it", status = EntryStatus.PLANNED)) }
        val rnd = Random(3)
        val jobs = (1..40).map { worker ->
            async(Dispatchers.IO) {
                val r = Random(worker)
                repeat(10) {
                    runCatching {
                        when (r.nextInt(5)) {
                            0, 1 -> entries.timer.start(ids[r.nextInt(ids.size)], FocusMode.STOPWATCH)
                            2 -> entries.timer.pause()
                            3 -> entries.timer.resume()
                            else -> entries.timer.finish()
                        }
                    }.onFailure { log("command threw: $it") }
                }
            }
        }
        jobs.awaitAll()
        rnd.nextInt()
        val sessions = db.openHelper.readableDatabase.query("SELECT startedAt, endedAt FROM work_sessions ORDER BY startedAt, id").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to (if (c.isNull(1)) null else c.getLong(1))) }
        }
        val open = sessions.count { it.second == null }
        log("sessions: ${sessions.size}, open: $open")
        assertTrue("more than one open session: $open", open <= 1)
        sessions.zipWithNext().forEach { (a, b) ->
            val aEnd = a.second ?: Long.MAX_VALUE
            assertTrue("overlap: $a then $b", aEnd <= b.first)
        }
        val doing = ids.count { db.entryDao().getById(it)!!.status == EntryStatus.DOING }
        assertTrue("$doing tasks marked Doing at once", doing <= 1)
        assertEquals("runtime says running=${entries.timer.runtime().running} with $open open sessions", open == 1, entries.timer.runtime().running)
    }

    /** Twenty payments on one plan at the same moment: none lost, the plan's paid equals its rows. */
    @Test
    fun racedPaymentsAreNeitherLostNorDoubled() = runBlocking<Unit> {
        money.addWallet(Wallet(0, "Cash", WalletKind.entries.first(), 10_000_000, LocalDate.now()))
        val wallet = money.observeWallets().first().single().id
        val plan = money.addPlan(MoneyRecord(0, Direction.COST, "Rent", amountMinor = 2_000_000, dueOn = LocalDate.now(), status = PlanStatus.PLANNED))
        (1..20).map { async(Dispatchers.IO) { money.recordPayment(plan, 10_000, wallet, settle = false) } }.awaitAll()
        val rows = money.observeHistory().first().filter { it.linkedPlanId == plan }
        val stored = db.moneyRecordDao().getById(plan)!!
        log("rows ${rows.size}, plan paid ${stored.paidMinor}")
        assertEquals(20, rows.size)
        assertEquals(rows.sumOf { it.paidMinor }, stored.paidMinor)
        assertEquals(10_000_000L - 200_000L, money.walletBalance(wallet).first())
    }

    /** Amounts at the edge of a Long: parsing refuses rather than wrapping round. */
    @Test
    fun hugeAmountsAreRefusedNotWrapped() {
        assertEquals(92_233_720_368_547_758_07L, parseTakaToMinor("92233720368547758.07"))
        assertNull(parseTakaToMinor("92233720368547758.08"))
        assertNull(parseTakaToMinor("99999999999999999999"))
        assertNull(parseTakaToMinor("1.005"))
        assertNull(parseTakaToMinor("-"))
        assertNull(parseTakaToMinor(""))
        // Formatting the extremes must not crash.
        Long.MAX_VALUE.toTaka(); Long.MIN_VALUE.toTaka(); (-1L).toTaka()
    }

    /** Text that's huge or unusual goes in and comes back byte for byte, and renders quickly. */
    @Test
    fun extremeTextSurvivesAndRenders() = runBlocking<Unit> {
        val weird = "টাকা ১২০০ · שלום · مرحبا · 👩‍👩‍👧‍👦🎉 · \u0000nul · '\"; DROP TABLE entries; -- · **bold * [x](javascript:alert(1))"
        val huge = buildString { repeat(4_000) { append("- [ ] item $it with **bold** and *it* and `code` 🚀\n") } } // ~200 KB
        // Under the note limit: back byte for byte. Over it: cut to the limit (data/TextLimits.kt).
        val fits = huge.take(com.example.timeapp.data.MAX_NOTE_CHARS - 10)
        val id = entries.insert(Entry(0, EntryKind.TASK, weird, status = EntryStatus.PLANNED, description = fits))
        val back = db.entryDao().getById(id)!!
        assertEquals(weird, back.title)
        assertEquals(fits, back.description)
        val over = entries.insert(Entry(0, EntryKind.TASK, "over", status = EntryStatus.PLANNED, description = huge))
        assertEquals(com.example.timeapp.data.MAX_NOTE_CHARS, db.entryDao().getById(over)!!.description!!.length)
        val t0 = System.nanoTime()
        val blocks = parseMarkdownLite(huge)
        val ms = (System.nanoTime() - t0) / 1_000_000
        log("200 KB note parsed into ${blocks.size} blocks in $ms ms")
        assertTrue("parse took $ms ms", ms < 3_000)
        // A thousand tags on one task.
        entries.setTags(id, (1..1000).map { "tag$it" })
        assertEquals(1000, entries.observeEntries().first().single { it.id == id }.tags.size)
    }

    /** Dates far away and at awkward edges are stored and read back unchanged. */
    @Test
    fun awkwardDatesRoundTrip() = runBlocking<Unit> {
        val dates = listOf(LocalDate.of(2028, 2, 29), LocalDate.of(2099, 12, 31), LocalDate.of(2027, 1, 1), LocalDate.of(1970, 1, 1))
        val ids = dates.map { entries.insert(Entry(0, EntryKind.TASK, "d $it", status = EntryStatus.PLANNED, dueOn = it)) }
        ids.zip(dates).forEach { (id, d) -> assertEquals(d, db.entryDao().getById(id)!!.dueOn) }
        val far = Instant.parse("2099-12-31T23:30:00Z")
        val ev = entries.insert(Entry(0, EntryKind.EVENT, "Late", status = EntryStatus.PLANNED, startAt = far, endAt = far.plusSeconds(7200)))
        assertEquals(far, db.entryDao().getById(ev)!!.startAt)
    }

    /** A note bigger than Android's 2 MB cursor window: can the task still be read — and the lists? */
    @Test
    fun hugePasteDoesNotBrickTheTaskList() = runBlocking<Unit> {
        entries.insert(Entry(0, EntryKind.TASK, "Normal", status = EntryStatus.PLANNED))
        val mb3 = "x".repeat(3 * 1024 * 1024)
        val result = runCatching {
            val id = entries.insert(Entry(0, EntryKind.TASK, "Pasted a book", status = EntryStatus.PLANNED, description = mb3))
            entries.observeEntries().first().size to id
        }
        log("3 MB note: ${result.exceptionOrNull()?.let { "BROKE — ${it::class.java.simpleName}: ${it.message}" } ?: "read back fine, ${result.getOrNull()!!.first} entries"}")
        assertTrue("a 3 MB note breaks the task list: ${result.exceptionOrNull()}", result.isSuccess)
    }

    /** Ten times the load: 200 threads x 25 commands. */
    @Test
    fun timerUnderTenTimesTheLoad() = runBlocking<Unit> {
        val ids = (1..10).map { entries.insert(Entry(0, EntryKind.TASK, "T$it", status = EntryStatus.PLANNED)) }
        (1..200).map { worker ->
            async(Dispatchers.IO) {
                val r = Random(worker * 31)
                repeat(25) {
                    runCatching {
                        when (r.nextInt(5)) {
                            0, 1 -> entries.timer.start(ids[r.nextInt(ids.size)], FocusMode.STOPWATCH)
                            2 -> entries.timer.pause()
                            3 -> entries.timer.resume()
                            else -> entries.timer.finish()
                        }
                    }.onFailure { log("command threw: $it") }
                }
            }
        }.awaitAll()
        val sessions = db.openHelper.readableDatabase.query("SELECT startedAt, endedAt FROM work_sessions ORDER BY startedAt, id").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to (if (c.isNull(1)) null else c.getLong(1))) }
        }
        val open = sessions.count { it.second == null }
        val zero = sessions.count { it.second != null && it.second == it.first }
        val negative = sessions.count { it.second != null && it.second!! < it.first }
        log("x10: ${sessions.size} sessions, open $open, zero-length $zero, negative $negative")
        assertTrue(open <= 1)
        assertEquals(0, negative)
        sessions.zipWithNext().forEach { (a, b) -> assertTrue("overlap $a / $b", (a.second ?: Long.MAX_VALUE) <= b.first) }
        assertTrue(ids.count { db.entryDao().getById(it)!!.status == EntryStatus.DOING } <= 1)
    }
}
