package com.example.timeapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.WalletKind
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.loanBalance
import com.example.timeapp.data.netSpendMinor
import com.example.timeapp.data.repository.LoanRepository
import com.example.timeapp.data.repository.MoneyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Loans move wallets exactly, never reports, and repayments can't exceed what's owed. */
@RunWith(AndroidJUnit4::class)
class LoanTest {
    private lateinit var db: TimeDatabase
    private lateinit var money: MoneyRepository
    private lateinit var loans: LoanRepository
    private var wallet = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TimeDatabase::class.java).build()
        money = MoneyRepository(db)
        loans = LoanRepository(db)
        money.addWallet(Wallet(0, "Cash", WalletKind.entries.first(), 1_000_000, LocalDate.now()))
        wallet = money.observeWallets().first().single().id
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun lendAndBeRepaid() = runBlocking {
        val id = loans.create("Rahim", lent = true, amountMinor = 500_000, walletId = wallet, dueOn = null, note = null)
        assertEquals(500_000L, money.walletBalance(wallet).first())
        assertFalse(loans.repay(id, 600_000, wallet))
        assertTrue(loans.repay(id, 200_000, wallet))
        val loan = db.loanDao().getById(id)!!.toDomain()
        assertEquals(300_000L, loanBalance(loan, db.moneyRecordDao().forLoan(id).map { it.toDomain() }).outstandingMinor)
        assertEquals(700_000L, money.walletBalance(wallet).first())
        assertEquals(0L, money.observeHistory().first().sumOf { it.netSpendMinor })
        assertTrue(loans.repay(id, 300_000, wallet))
        assertTrue(db.loanDao().getById(id)!!.closedAt != null) // settled in full closes it
    }

    @Test
    fun deletingALoanRestoresTheWallet() = runBlocking {
        val id = loans.create("Karim", lent = false, amountMinor = 300_000, walletId = wallet, dueOn = null, note = null)
        assertEquals(1_300_000L, money.walletBalance(wallet).first())
        loans.delete(id)
        assertEquals(1_000_000L, money.walletBalance(wallet).first())
    }
}
