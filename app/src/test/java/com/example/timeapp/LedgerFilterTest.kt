package com.example.timeapp

import com.example.timeapp.data.Direction
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.netSpendMinor
import com.example.timeapp.data.totalEffect
import com.example.timeapp.data.walletEffect
import com.example.timeapp.ui.money.LedgerFilter
import com.example.timeapp.ui.money.applyLedgerFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerFilterTest {
    private fun row(id: Long, direction: Direction, paid: Long, wallet: Long = 1, to: Long? = null, cat: Long? = null, title: String = "t$id", note: String? = null) =
        MoneyRecord(id, direction, title, amountMinor = paid, paidMinor = paid, walletId = wallet, transferToWalletId = to, categoryId = cat, note = note)

    private val rows = listOf(
        row(1, Direction.COST, 120_050, cat = 7, title = "Groceries", note = "Shwapno, weekly shop"),
        row(2, Direction.INCOME, 5_000_000, title = "Salary"),
        row(3, Direction.TRANSFER, 200_000, wallet = 1, to = 2, title = "Wallet transfer"),
        row(4, Direction.REFUND, 20_000, wallet = 2, cat = 7, title = "Refund: Groceries"),
    )

    @Test
    fun `search matches title, note and exact amount`() {
        assertEquals(listOf(1L, 4L), rows.applyLedgerFilter(LedgerFilter(query = "groceries")).map { it.id })
        assertEquals(listOf(1L), rows.applyLedgerFilter(LedgerFilter(query = "shwapno")).map { it.id })
        assertEquals(listOf(1L), rows.applyLedgerFilter(LedgerFilter(query = "1,200.50")).map { it.id })
        assertEquals(listOf(1L), rows.applyLedgerFilter(LedgerFilter(query = "Tk 1200.50")).map { it.id })
    }

    @Test
    fun `wallet filter includes transfers into the wallet`() {
        assertEquals(listOf(3L, 4L), rows.applyLedgerFilter(LedgerFilter(walletId = 2)).map { it.id })
    }

    @Test
    fun `type and category narrow together`() {
        assertEquals(listOf(4L), rows.applyLedgerFilter(LedgerFilter(type = Direction.REFUND, categoryId = 7)).map { it.id })
    }

    /** The PDF: refunds reverse spending and are not income; transfers are neither. */
    @Test
    fun `refunds reduce spending and add to their wallet without being income`() {
        assertEquals(120_050L - 20_000L, rows.sumOf { it.netSpendMinor })
        assertEquals(20_000L, rows[3].walletEffect(2))
        assertEquals(0L, rows[3].walletEffect(1))
        assertEquals(-200_000L, rows[2].walletEffect(1))
        assertEquals(200_000L, rows[2].walletEffect(2))
        assertEquals(0L, rows[2].totalEffect)
        // Across all wallets: -1200.50 + 50,000 + 0 + 200
        assertEquals(-120_050L + 5_000_000L + 20_000L, rows.sumOf { it.totalEffect })
    }
}
