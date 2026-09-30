package com.example.timeapp

import com.example.timeapp.data.BoardSort
import com.example.timeapp.data.CalendarFilter
import com.example.timeapp.data.Direction
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.HabitSchedule
import com.example.timeapp.data.HabitScheduleKind
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.WalletKind
import com.example.timeapp.data.WorkSessionSpan
import com.example.timeapp.data.buildAgenda
import com.example.timeapp.data.habitStats
import com.example.timeapp.data.moneyByDay
import com.example.timeapp.data.netWorthByMonth
import com.example.timeapp.data.projectSummary
import com.example.timeapp.data.sortBoardColumn
import com.example.timeapp.data.withBlocksExpanded
import com.example.timeapp.ui.money.LedgerFilter
import com.example.timeapp.ui.money.applyLedgerFilter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * The PDF's 10k-record check, for the pure code every screen runs on each change: none of it
 * may be quadratic. Bounds are generous (a slow CI box must still pass); a regression to O(n²)
 * at 10k rows blows well past them.
 */
class PerformanceTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 28)
    private val n = 10_000

    private val ledger = (1..n).map { i ->
        MoneyRecord(
            i.toLong(), if (i % 5 == 0) Direction.INCOME else Direction.COST, "Row $i", amountMinor = 100L * i, paidMinor = 100L * i,
            settledAt = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 2_400L), walletId = (i % 3).toLong() + 1,
        )
    }
    private val plans = (1..n).map { i ->
        MoneyRecord(n + i.toLong(), Direction.COST, "Plan $i", amountMinor = 5_000, dueOn = today.plusDays((i % 60).toLong()), status = PlanStatus.PLANNED)
    }
    private val entries = (1..n).map { i ->
        Entry(
            i.toLong(), EntryKind.TASK, "Task $i", status = EntryStatus.PLANNED, dueOn = today.plusDays((i % 90).toLong()),
            boardOrder = n - i, projectId = (i % 20).toLong(),
            startAt = if (i % 2 == 0) today.plusDays((i % 30).toLong()).atTime(9, 0).toInstant(zone) else null,
            endAt = if (i % 2 == 0) today.plusDays((i % 30).toLong()).atTime(10, 0).toInstant(zone) else null,
        )
    }
    private val sessions = (1..n).map { i ->
        val s = Instant.parse("2026-01-01T09:00:00Z").plusSeconds(i * 3_600L)
        WorkSessionSpan(i.toLong(), s, s.plusSeconds(1_800))
    }

    private fun timed(label: String, limitMs: Long = 1_500, block: () -> Unit) {
        block() // warm up
        val start = System.nanoTime()
        block()
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("$label took $ms ms at $n rows (limit $limitMs)", ms < limitMs)
    }

    @Test fun ledgerSearchAndFilters() = timed("ledger search") {
        ledger.applyLedgerFilter(LedgerFilter(query = "row 99"))
        ledger.applyLedgerFilter(LedgerFilter(query = "1,200.00", walletId = 2))
    }

    @Test fun moneyCalendarAndNetWorth() = timed("money calendar + net worth") {
        moneyByDay(ledger, YearMonth.of(2026, 6), zone)
        netWorthByMonth(listOf(1L, 2L, 3L).map { Wallet(it, "W$it", WalletKind.BANK, 100_000, today.minusYears(1)) }, ledger, emptyList(), today, zone)
    }

    @Test fun agendaAndBlocks() = timed("agenda") {
        buildAgenda(entries.withBlocksExpanded(), plans, today, 30, CalendarFilter(), zone)
    }

    @Test fun boardSortOfALargeColumn() = timed("board sort") {
        BoardSort.entries.forEach { sortBoardColumn(entries, it) }
    }

    @Test fun projectSummary() = timed("project summary") {
        projectSummary(3, entries, sessions, ledger, plans)
    }

    @Test fun habitWithYearsOfCheckIns() = timed("habit stats") {
        val habit = Habit(1, "Read", HabitSchedule(HabitScheduleKind.DAILY), startedOn = today.minusYears(3))
        val marks = (0 until 3 * 365).associate { today.minusDays(it.toLong()) to if (it % 7 == 0) HabitMark.SKIPPED else HabitMark.DONE }
        habitStats(habit, marks, today)
    }
}
