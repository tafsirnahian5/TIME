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
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.WalletKind
import com.example.timeapp.data.carryover
import com.example.timeapp.data.commitmentSuggestions
import com.example.timeapp.data.db.EntryEventEntity
import com.example.timeapp.data.db.HabitCheckEntity
import com.example.timeapp.data.db.HabitEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.WorkSessionEntity
import com.example.timeapp.data.db.toEntity
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.HabitRepository
import com.example.timeapp.data.repository.MoneyRepository
import com.example.timeapp.data.restOfToday
import com.example.timeapp.data.startNext
import com.example.timeapp.data.tasksToday
import com.example.timeapp.data.netSpendMinor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * "Test it until it breaks": the same heavy-user day as [LongTermDataTest] (5 tasks with
 * sessions and status events, an appointment, 6 money rows, 6 habits), repeated for more and
 * more years, measuring what the app does with it at each size. Doesn't fail on slowness — it
 * logs (tag `StressTest`) where each part stops being comfortable, and where it breaks outright.
 * Run alone: `-e class com.example.timeapp.StressTest`.
 */
@RunWith(AndroidJUnit4::class)
class StressTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    private fun log(msg: String) = Log.i("StressTest", msg)
    private fun usedMb(): Long {
        val rt = Runtime.getRuntime()
        rt.gc()
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
    }

    private suspend fun seedDays(db: TimeDatabase, fromDay: Int, toDay: Int, rnd: Random) = db.withTransaction {
        val wallet = db.walletDao().observeAll().first().firstOrNull()?.id
            ?: db.walletDao().insert(Wallet(0, "Cash", WalletKind.entries.first(), 1_000_000, today.minusYears(40)).toEntity())
        for (d in toDay downTo fromDay) {
            val day = today.minusDays(d.toLong())
            repeat(5) { i ->
                val start = day.atTime(9 + i * 2, 0).atZone(zone).toInstant()
                val done = d > 0 && rnd.nextInt(100) < 85
                val id = db.entryDao().insert(
                    Entry(0, EntryKind.TASK, "Task $d-$i", status = if (done) EntryStatus.DONE else EntryStatus.PLANNED,
                        dueOn = day, completedAt = if (done) start.plusSeconds(3600) else null,
                        description = "- [x] one\n- [ ] **two**").toEntity(),
                )
                if (done) {
                    db.workSessionDao().insert(WorkSessionEntity(entryId = id, startedAt = start, endedAt = start.plusSeconds(1500)))
                    db.entryEventDao().insert(EntryEventEntity(entryId = id, at = start, fromStatus = EntryStatus.PLANNED, toStatus = EntryStatus.DONE))
                }
            }
            db.entryDao().insert(Entry(0, EntryKind.EVENT, "Meeting $d", status = EntryStatus.PLANNED,
                startAt = day.atTime(15, 0).atZone(zone).toInstant(), endAt = day.atTime(16, 0).atZone(zone).toInstant()).toEntity())
            repeat(6) { i ->
                val amount = 5_000L + rnd.nextInt(200_000)
                db.moneyRecordDao().insert(MoneyRecord(0, Direction.COST, "Spend $d-$i", amountMinor = amount, paidMinor = amount,
                    settledAt = day.atTime(10 + i, 0).atZone(zone).toInstant(), walletId = wallet).toEntity())
            }
        }
    }

    private suspend fun seedHabits(db: TimeDatabase, days: Int) = db.withTransaction {
        repeat(6) { h ->
            val id = db.habitDao().insert(HabitEntity(name = "Habit $h", schedule = "DAILY", startedOn = today.minusDays(days.toLong())))
            for (d in days downTo 0) db.habitDao().upsertCheck(HabitCheckEntity(id, today.minusDays(d.toLong()), HabitMark.DONE))
        }
    }

    private inline fun <T> ms(block: () -> T): Pair<T, Long> {
        val t0 = System.nanoTime(); val r = block(); return r to (System.nanoTime() - t0) / 1_000_000
    }

    @Test
    fun growUntilItBreaks() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, TimeDatabase::class.java).build()
        val rnd = Random(7)
        val maxHeap = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        log("heap limit $maxHeap MB")
        var seeded = -1
        try {
            for (years in listOf(1, 2, 5, 10, 20, 40)) {
                val days = years * 365
                seedDays(db, seeded + 1, days, rnd)
                seeded = days
                val entryRepo = EntryRepository(db)
                val money = MoneyRepository(db)
                val (all, entriesMs) = ms { entryRepo.observeEntries().first() }
                val (history, historyMs) = ms { money.observeHistory().first() }
                val plans = money.observePlans().first()
                val habitRows = (0 until 6).map { HabitForToday(it.toLong(), "Habit $it", null, false) }
                val (_, tickMs) = ms {
                    carryover(all, plans, today)
                    restOfToday(all, plans, habitRows, Instant.now(), zone)
                    commitmentSuggestions(all, today, emptySet(), zone)
                    startNext(all, emptyList(), today, zone)
                    tasksToday(all, today, zone)
                    history.filter { it.settledAt?.atZone(zone)?.toLocalDate() == today }.sumOf { it.netSpendMinor }
                }
                log("$years y: ${all.size} entries in $entriesMs ms, ${history.size} ledger rows in $historyMs ms, Today recompute $tickMs ms, heap ${usedMb()} of $maxHeap MB")
            }
            seedHabits(db, 20 * 365)
            val (checks, checksMs) = ms { HabitRepository(db).observeChecks().first() }
            log("habits: ${checks.size} check-ins in $checksMs ms, heap ${usedMb()} MB")
        } catch (t: Throwable) {
            log("BROKE after $seeded days: ${t::class.java.simpleName}: ${t.message}")
            throw t
        } finally {
            db.close()
        }
    }
}
