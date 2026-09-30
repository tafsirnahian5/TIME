package com.example.timeapp

import com.example.timeapp.data.Direction
import com.example.timeapp.data.Loan
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.loanBalance
import com.example.timeapp.data.loanTotals
import com.example.timeapp.data.netSpendMinor
import com.example.timeapp.data.totalEffect
import com.example.timeapp.data.walletEffect
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LoansTest {
    private val day = LocalDate.of(2026, 9, 1)
    private fun row(id: Long, dir: Direction, minor: Long, loan: Long) =
        MoneyRecord(id, dir, "r$id", amountMinor = minor, paidMinor = minor, walletId = 1, settledAt = Instant.EPOCH, loanId = loan)
    private val toRahim = Loan(1, "Rahim", lent = true, startedOn = day)
    private val fromKarim = Loan(2, "Karim", lent = false, startedOn = day)
    private val rows = listOf(
        row(1, Direction.LOAN_OUT, 500_000, 1), // lent Tk 5,000
        row(2, Direction.LOAN_IN, 200_000, 1),  // Rahim paid back Tk 2,000
        row(3, Direction.LOAN_IN, 300_000, 2),  // borrowed Tk 3,000 from Karim
        row(4, Direction.LOAN_OUT, 100_000, 2), // paid Karim back Tk 1,000
    )

    @Test
    fun `partial repayments leave the exact balance each way`() {
        assertEquals(300_000L, loanBalance(toRahim, rows).outstandingMinor)
        assertEquals(200_000L, loanBalance(fromKarim, rows).outstandingMinor)
        assertEquals(300_000L to 200_000L, loanTotals(listOf(toRahim, fromKarim), rows))
    }

    /** Loans move wallets but are never income or spending. */
    @Test
    fun `loans move wallets, never reports`() {
        assertEquals(-500_000L, rows[0].walletEffect(1))
        assertEquals(200_000L, rows[1].walletEffect(1))
        assertEquals(-100_000L, rows.sumOf { it.totalEffect } - 0) // net cash out so far
        assertEquals(0L, rows.sumOf { it.netSpendMinor })
    }

    @Test
    fun `a closed loan leaves the totals and overpayment is shown`() {
        val closed = toRahim.copy(closedAt = Instant.EPOCH)
        assertEquals(0L to 200_000L, loanTotals(listOf(closed, fromKarim), rows))
        val over = rows + row(5, Direction.LOAN_IN, 400_000, 1)
        assertEquals(100_000L, loanBalance(toRahim, over).overpaidMinor)
    }
}
