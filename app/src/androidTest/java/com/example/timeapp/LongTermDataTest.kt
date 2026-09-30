package com.example.timeapp

import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timeapp.data.Direction
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.HabitForToday
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.WalletKind
import com.example.timeapp.data.carryover
import com.example.timeapp.data.commitmentSuggestions
import com.example.timeapp.data.db.EntryEventEntity
import com.example.timeapp.data.db.HabitCheckEntity
import com.example.timeapp.data.db.HabitEntity
import com.example.timeapp.data.db.ReminderEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.WorkSessionEntity
import com.example.timeapp.data.db.toEntity
import com.example.timeapp.data.netSpendMinor
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.HabitRepository
import com.example.timeapp.data.repository.MoneyRepository
import com.example.timeapp.data.restOfToday
import com.example.timeapp.data.startNext
import com.example.timeapp.data.tasksToday
import com.example.timeapp.data.totalEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * "Does it still work after a year or two, and lose nothing?" Two years of a heavy user's data
 * in a throw-away database (never the real one): the real repositories must load it quickly and
 * completely, every total must reconcile with what was put in, the work Today redoes once a
 * second must stay cheap, and a backup snapshot taken while writes are happening must be sound.
 * Timings are logged under the tag `LongTermDataTest`.
 */
@RunWith(AndroidJUnit4::class)
class LongTermDataTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: TimeDatabase
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()
    private val days = 730
    private val rnd = Random(42)

    // What was seeded, to reconcile against.
    private var tasks = 0
    private var events = 0
    private var sessions = 0
    private var ledgerRows = 0
    private var plans = 0
    private var checks = 0
    private var openingTotal = 0L
    private var ledgerEffect = 0L
    private var spendTotal = 0L

    @Before
    fun setUp() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(context, TimeDatabase::class.java).build()
        seed(db)
    }

    @After
    fun tearDown() = db.close()

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant = day.atTime(hour, minute).atZone(zone).toInstant()

    private suspend fun seed(db: TimeDatabase) = db.withTransaction {
        val money = MoneyRepository(db)
        val walletIds = listOf("Cash" to 400_000L, "Bank" to 1_500_000L, "bKash" to 500_000L).map { (name, opening) ->
            openingTotal += opening
            db.walletDao().insert(Wallet(0, name, WalletKind.entries.first(), opening, today.minusDays(days.toLong())).toEntity())
        }
        for (d in days downTo 0) {
            val day = today.minusDays(d.toLong())
            // ~5 tasks a day, most done with one or two sessions each, plus an appointment.
            repeat(5) { i ->
                val done = d > 0 && rnd.nextInt(100) < 85
                val start = at(day, 9 + i * 2)
                val id = db.entryDao().insert(
                    Entry(
                        id = 0, kind = EntryKind.TASK, title = "Task $d-$i",
                        status = if (done) EntryStatus.DONE else EntryStatus.PLANNED,
                        dueOn = day, estimateMs = 45 * 60_000L,
                        description = "- [x] step one\n- [ ] step **two**",
                        completedAt = if (done) start.plusSeconds(3600) else null,
                    ).toEntity(),
                )
                tasks++
                if (done) {
                    repeat(1 + rnd.nextInt(2)) { k ->
                        val s = start.plusSeconds(k * 1800L)
                        db.workSessionDao().insert(WorkSessionEntity(entryId = id, startedAt = s, endedAt = s.plusSeconds(1500)))
                        sessions++
                    }
                    db.entryEventDao().insert(EntryEventEntity(entryId = id, at = start, fromStatus = EntryStatus.PLANNED, toStatus = EntryStatus.DOING))
                    db.entryEventDao().insert(EntryEventEntity(entryId = id, at = start.plusSeconds(3600), fromStatus = EntryStatus.DOING, toStatus = EntryStatus.DONE))
                    db.reminderDao().insert(ReminderEntity(ownerKind = com.example.timeapp.data.ReminderOwnerKind.entries.first(), ownerId = id, scheduledAt = at(day, 9), delivered = true))
                }
            }
            db.entryDao().insert(
                Entry(0, EntryKind.EVENT, "Meeting $d", status = EntryStatus.PLANNED, startAt = at(day, 15), endAt = at(day, 16)).toEntity(),
            )
            events++
            // ~6 money rows a day across the wallets.
            repeat(6) { i ->
                val income = i == 0 && d % 30 == 0
                val amount = if (income) 5_000_000L else (5_000L + rnd.nextInt(200_000))
                val r = MoneyRecord(
                    id = 0, direction = if (income) Direction.INCOME else Direction.COST,
                    title = if (income) "Salary" else "Spend $d-$i", amountMinor = amount, paidMinor = amount,
                    settledAt = at(day, 10 + i), walletId = walletIds[i % walletIds.size],
                )
                db.moneyRecordDao().insert(r.toEntity())
                ledgerRows++
                ledgerEffect += r.totalEffect
                spendTotal += r.netSpendMinor
            }
            if (d % 2 == 0) {
                money.addPlan(MoneyRecord(0, Direction.COST, "Bill $d", amountMinor = 150_000, dueOn = day.plusDays(20), status = PlanStatus.PLANNED))
                plans++
            }
        }
        repeat(6) { h ->
            val habitId = db.habitDao().insert(HabitEntity(name = "Habit $h", schedule = "DAILY", startedOn = today.minusDays(days.toLong())))
            for (d in days downTo 0) {
                if (rnd.nextInt(100) < 80) {
                    db.habitDao().upsertCheck(HabitCheckEntity(habitId, today.minusDays(d.toLong()), HabitMark.DONE))
                    checks++
                }
            }
        }
    }

    private inline fun <T> timed(label: String, block: () -> T): Pair<T, Long> {
        val t0 = System.nanoTime()
        val r = block()
        val ms = (System.nanoTime() - t0) / 1_000_000
        Log.i("LongTermDataTest", "$label: $ms ms")
        return r to ms
    }

    @Test
    fun twoYearsLoadCompletelyAndReconcile() = runBlocking<Unit> {
        val entries = EntryRepository(db)
        val money = MoneyRepository(db)
        val habits = HabitRepository(db)

        val (all, entriesMs) = timed("entries (${tasks + events} rows, with tags/deps/blocks)") { entries.observeEntries().first() }
        val (history, historyMs) = timed("money history ($ledgerRows rows)") { money.observeHistory().first() }
        val (openPlans, plansMs) = timed("open plans ($plans rows)") { money.observePlans().first() }
        val (habitChecks, checksMs) = timed("habit check-ins ($checks rows)") { habits.observeChecks().first() }
        val (wallets, _) = timed("wallets") { money.observeWallets().first() }

        // Nothing lost.
        assertEquals(tasks + events, all.size)
        assertEquals(tasks, all.count { it.kind == EntryKind.TASK })
        assertEquals(ledgerRows, history.size)
        assertEquals(plans, openPlans.size)
        assertEquals(checks, habitChecks.size)
        assertEquals(sessions, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM work_sessions").use { it.moveToFirst(); it.getInt(0) })

        // Money reconciles exactly after two years: opening balances plus every row's effect.
        val balance = wallets.sumOf { it.openingBalanceMinor } + history.sumOf { it.totalEffect }
        assertEquals(openingTotal + ledgerEffect, balance)
        assertEquals(spendTotal, history.sumOf { it.netSpendMinor })

        // Generous bounds — a guard against anything turning quadratic, not a benchmark.
        assertTrue("entries took $entriesMs ms", entriesMs < 3_000)
        assertTrue("history took $historyMs ms", historyMs < 2_000)
        assertTrue("plans took $plansMs ms", plansMs < 1_000)
        assertTrue("checks took $checksMs ms", checksMs < 2_000)
    }

    @Test
    fun todaysOncePerSecondWorkStaysCheap() = runBlocking<Unit> {
        val all = EntryRepository(db).observeEntries().first()
        val money = MoneyRepository(db)
        val plansList = money.observePlans().first()
        val history = money.observeHistory().first()
        val now = Instant.now()
        val habitRows = (0 until 6).map { HabitForToday(it.toLong(), "Habit $it", null, false) }
        // What TodayScreen recomputes on every tick of its clock.
        val runs = 20
        val (_, totalMs) = timed("Today tick x$runs") {
            repeat(runs) {
                carryover(all, plansList, today)
                restOfToday(all, plansList, habitRows, now, zone)
                commitmentSuggestions(all, today, emptySet(), zone)
                startNext(all, emptyList(), today, zone)
                tasksToday(all, today, zone)
                history.filter { it.settledAt?.atZone(zone)?.toLocalDate() == today }.sumOf { it.netSpendMinor }
            }
        }
        val perTick = totalMs / runs
        Log.i("LongTermDataTest", "Today per tick: $perTick ms")
        assertTrue("a Today tick took $perTick ms", perTick < 100)
    }

    @Test
    fun backupSnapshotIsSoundWhileWritesHappen() = runBlocking<Unit> {
        // A file-backed copy (VACUUM INTO needs a real database), in the test's own cache.
        val file = File(context.cacheDir, "longterm_source.db").apply { delete() }
        val snap = File(context.cacheDir, "longterm_snapshot.db").apply { delete() }
        val fileDb = Room.databaseBuilder(context, TimeDatabase::class.java, file.path).build()
        try {
            seed(fileDb)
            val before = fileDb.entryDao().count()
            // Keep writing while the snapshot is taken, the way a timer checkpoint or a widget might.
            val writer = async(Dispatchers.IO) {
                repeat(200) {
                    fileDb.entryDao().insert(Entry(0, EntryKind.TASK, "During backup $it", status = EntryStatus.UNSCHEDULED).toEntity())
                    delay(1)
                }
            }
            val (_, ms) = timed("VACUUM INTO snapshot") {
                fileDb.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf<Any>(snap.path))
            }
            writer.await()
            android.database.sqlite.SQLiteDatabase.openDatabase(snap.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { s ->
                s.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst(); assertEquals("ok", c.getString(0)) }
                s.rawQuery("PRAGMA user_version", null).use { c -> c.moveToFirst(); assertEquals(com.example.timeapp.data.db.DB_VERSION, c.getInt(0)) }
                s.rawQuery("SELECT COUNT(*) FROM entries", null).use { c ->
                    c.moveToFirst()
                    assertTrue("snapshot lost rows", c.getInt(0) >= before)
                }
            }
            Log.i("LongTermDataTest", "database size ${file.length() / 1024} KB, snapshot ${snap.length() / 1024} KB in $ms ms")
        } finally {
            fileDb.close()
            file.delete(); File(file.path + "-wal").delete(); File(file.path + "-shm").delete()
            snap.delete()
        }
    }
}
