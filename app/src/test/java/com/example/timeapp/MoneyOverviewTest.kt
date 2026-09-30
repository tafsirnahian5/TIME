package com.example.timeapp

import com.example.timeapp.data.Direction
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.WalletKind
import com.example.timeapp.ui.money.MoneyUiState
import com.example.timeapp.ui.money.computeMoneyOverview
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The PDF's four numbers across a month boundary, with a refund in play. */
class MoneyOverviewTest {
    private val zone = ZoneId.systemDefault()
    private val sept = YearMonth.of(2026, 9)
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant()
    private fun row(id: Long, dir: Direction, paid: Long, on: LocalDate, refundOf: Long? = null) =
        MoneyRecord(id, dir, "r$id", amountMinor = paid, paidMinor = paid, walletId = 1, settledAt = at(on), refundOfId = refundOf)

    private val ui = MoneyUiState(
        wallets = listOf(Wallet(1, "Cash", WalletKind.entries.first(), 3_000_000, LocalDate.of(2026, 8, 1))),
        history = listOf(
            row(1, Direction.COST, 400_000, LocalDate.of(2026, 8, 31)), // last month: not "paid this month"
            row(2, Direction.COST, 600_000, LocalDate.of(2026, 9, 1)),
            row(3, Direction.REFUND, 100_000, LocalDate.of(2026, 9, 2), refundOf = 2),
            row(4, Direction.INCOME, 1_000_000, LocalDate.of(2026, 9, 3)),
        ),
        plans = listOf(
            MoneyRecord(9, Direction.COST, "Rent", amountMinor = 800_000, dueOn = LocalDate.of(2026, 9, 30), status = PlanStatus.PLANNED),
        ),
    )

    @Test
    fun `paid this month is net of refunds and ignores last month`() {
        assertEquals(500_000L, computeMoneyOverview(ui, sept).paidThisMonth)
    }

    @Test
    fun `available balance counts the refund back and is not double-counted`() {
        val n = computeMoneyOverview(ui, sept, LocalDate.of(2026, 9, 20))
        // 30,000 - 4,000 - 6,000 + 1,000 + 10,000
        assertEquals(3_100_000L, n.availableBalance)
        assertEquals(800_000L, n.plannedRemaining)
        assertEquals(2_300_000L, n.afterPlannedCosts)
    }

    /**
     * The forecast is windowed: a bill due next month waits for next month, and an overdue one
     * is its own carryover line that can be switched off. Undated plans stay out.
     */
    @Test
    fun `planned remaining covers this month with overdue as a separate carryover`() {
        val today = LocalDate.of(2026, 9, 20)
        val plans = listOf(
            MoneyRecord(10, Direction.COST, "Rent", amountMinor = 800_000, dueOn = LocalDate.of(2026, 9, 30), status = PlanStatus.PLANNED),
            MoneyRecord(11, Direction.COST, "Internet", amountMinor = 150_000, paidMinor = 50_000, dueOn = LocalDate.of(2026, 9, 5), status = PlanStatus.PARTLY_PAID),
            MoneyRecord(12, Direction.COST, "Insurance", amountMinor = 900_000, dueOn = LocalDate.of(2026, 12, 1), status = PlanStatus.PLANNED),
            MoneyRecord(13, Direction.COST, "Someday", amountMinor = 70_000, status = PlanStatus.UNSCHEDULED),
            MoneyRecord(14, Direction.COST, "Dropped", amountMinor = 60_000, dueOn = LocalDate.of(2026, 9, 25), status = PlanStatus.CANCELLED),
        )
        val with = computeMoneyOverview(ui.copy(plans = plans), sept, today)
        assertEquals(800_000L, with.plannedInWindow)
        assertEquals(100_000L, with.overdueCarryover)
        assertEquals(900_000L, with.plannedRemaining)
        assertEquals(LocalDate.of(2026, 9, 30), with.windowEnd)
        val without = computeMoneyOverview(ui.copy(plans = plans), sept, today, includeCarryover = false)
        assertEquals(800_000L, without.plannedRemaining)
        assertEquals(without.availableBalance - 800_000L, without.afterPlannedCosts)
    }
}
