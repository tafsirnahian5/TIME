package com.example.timeapp

import com.example.timeapp.data.Direction
import com.example.timeapp.data.Loan
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.WalletKind
import com.example.timeapp.data.moneyByDay
import com.example.timeapp.data.netWorthByMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MoneyTrendsTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant()
    private fun row(id: Long, dir: Direction, minor: Long, on: LocalDate, loan: Long? = null) =
        MoneyRecord(id, dir, "r$id", amountMinor = minor, paidMinor = minor, walletId = 1, settledAt = at(on), loanId = loan)
    private val aug = LocalDate.of(2026, 8, 20)
    private val sep = LocalDate.of(2026, 9, 10)
    private val history = listOf(
        row(1, Direction.INCOME, 1_000_000, aug),
        row(2, Direction.COST, 300_000, sep),
        row(3, Direction.REFUND, 50_000, sep),
        row(4, Direction.LOAN_OUT, 200_000, sep, loan = 7),
    )

    @Test
    fun `days show net spending and income, not transfers or loans`() {
        val day = moneyByDay(history, YearMonth.of(2026, 9), zone)[sep]!!
        assertEquals(250_000L, day.spentMinor)
        assertEquals(0L, day.incomeMinor)
        assertEquals(3, day.rows.size)
    }

    /** Lending moves cash out, but what's owed back keeps net worth where it was. */
    @Test
    fun `net worth counts what people owe you`() {
        val wallets = listOf(Wallet(1, "Cash", WalletKind.CASH, 500_000, LocalDate.of(2026, 8, 1)))
        val loans = listOf(Loan(7, "Rahim", lent = true, startedOn = sep))
        val trend = netWorthByMonth(wallets, history, loans, LocalDate.of(2026, 9, 30), zone, months = 2)
        assertEquals(YearMonth.of(2026, 8) to 1_500_000L, trend[0])
        // 15,000 − 3,000 + 500 − 2,000 lent + 2,000 owed back
        assertEquals(1_250_000L, trend[1].second)
    }
}
