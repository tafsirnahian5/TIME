package com.example.timeapp

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timeapp.data.Direction
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.WalletKind
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.repository.MoneyRepository
import com.example.timeapp.data.netSpendMinor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Release 1.2's exit check from the PDF: "partial payments, refunds and month boundaries
 * reconcile exactly" — the Tk 3,000 grocery plan from the MVP checkpoint, carried through a
 * refund both ways. Runs the real repository against an in-memory Room database; needs a
 * device (`./gradlew connectedDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class MoneyReconciliationTest {
    private lateinit var db: TimeDatabase
    private lateinit var repo: MoneyRepository
    private var walletId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TimeDatabase::class.java).build()
        repo = MoneyRepository(db)
        repo.addWallet(Wallet(0, "Cash", WalletKind.entries.first(), 2_400_000, LocalDate.now()))
        walletId = repo.observeWallets().first().single().id
    }

    @After
    fun tearDown() = db.close()

    private suspend fun plan(id: Long) = db.moneyRecordDao().getById(id)!!
    private suspend fun balance() = repo.walletBalance(walletId).first()
    private suspend fun ledger() = repo.observeHistory().first()

    private suspend fun newPlan(estimate: Long): Long = repo.addPlan(
        MoneyRecord(0, Direction.COST, "Groceries", amountMinor = estimate, dueOn = LocalDate.now(), status = PlanStatus.PLANNED),
    )

    @Test
    fun partialPaymentLeavesExactOutstandingAndMovesTheWallet() = runBlocking {
        val id = newPlan(300_000)
        repo.recordPayment(id, 120_000, walletId, settle = false)
        assertEquals(PlanStatus.PARTLY_PAID, plan(id).status)
        assertEquals(180_000L, plan(id).amountMinor - plan(id).paidMinor)
        assertEquals(2_400_000L - 120_000L, balance())
    }

    @Test
    fun refundOnAnOpenPlanRaisesOutstandingAndReturnsMoney() = runBlocking {
        val id = newPlan(300_000)
        repo.recordPayment(id, 120_000, walletId, settle = false)
        val payment = ledger().single()
        assert(repo.refund(payment.id, 20_000, walletId, reopenPlan = false))
        assertEquals(100_000L, plan(id).paidMinor)
        assertEquals(PlanStatus.PARTLY_PAID, plan(id).status)
        assertEquals(2_400_000L - 100_000L, balance())
        // Can't refund more than is left: 1,000 of 1,200 refundable remains.
        assertEquals(100_000L, repo.refundableMinor(payment.id))
        assertFalse(repo.refund(payment.id, 100_001, walletId, reopenPlan = false))
    }

    @Test
    fun refundOnASettledPlanOnlyReopensWhenAsked() = runBlocking {
        val kept = newPlan(300_000)
        repo.recordPayment(kept, 300_000, walletId, settle = true)
        repo.refund(ledger().single { it.linkedPlanId == kept }.id, 50_000, walletId, reopenPlan = false)
        assertEquals(PlanStatus.SETTLED, plan(kept).status)
        assertEquals(250_000L, plan(kept).paidMinor) // shows as Tk 500 under in the variance report

        val reopened = newPlan(300_000)
        repo.recordPayment(reopened, 300_000, walletId, settle = true)
        repo.refund(ledger().single { it.linkedPlanId == reopened && it.direction == Direction.COST }.id, 50_000, walletId, reopenPlan = true)
        assertEquals(PlanStatus.PARTLY_PAID, plan(reopened).status)
        assertEquals(50_000L, plan(reopened).amountMinor - plan(reopened).paidMinor)
    }

    @Test
    fun deletingAnExpenseTakesItsRefundsAndRestoresThePlan() = runBlocking {
        val id = newPlan(300_000)
        repo.recordPayment(id, 120_000, walletId, settle = false)
        val payment = ledger().single()
        repo.refund(payment.id, 20_000, walletId, reopenPlan = false)
        repo.deleteHistoryRecord(payment.id)
        assertEquals(emptyList<MoneyRecord>(), ledger())
        assertEquals(0L, plan(id).paidMinor)
        assertEquals(2_400_000L, balance())
    }

    @Test
    fun refundsAreNotIncome() = runBlocking {
        val id = newPlan(300_000)
        repo.recordPayment(id, 120_000, walletId, settle = false)
        repo.refund(ledger().single().id, 20_000, walletId, reopenPlan = false)
        val rows = ledger()
        assertEquals(0, rows.count { it.direction == Direction.INCOME })
        assertEquals(100_000L, rows.sumOf { it.netSpendMinor })
    }

    /** The PDF: cancelling a part-paid plan releases only what was still owed, keeps the
     * payment, and a later refund of that payment doesn't bring the plan back. */
    @Test
    fun cancellingKeepsPaymentsAndStaysCancelled() = runBlocking {
        val id = newPlan(300_000)
        repo.recordPayment(id, 120_000, walletId, settle = false)
        repo.cancelPlan(id)
        assertEquals(PlanStatus.CANCELLED, plan(id).status)
        assertEquals(1, ledger().size)
        assertEquals(2_400_000L - 120_000L, balance())
        assert(repo.observePlans().first().none { it.id == id })
        assert(repo.refund(ledger().single().id, 20_000, walletId, reopenPlan = false))
        assertEquals(PlanStatus.CANCELLED, plan(id).status)
        assertEquals(100_000L, plan(id).paidMinor)
    }

    /** A plan with payments can't be deleted — its ledger rows would point at nothing. */
    @Test
    fun aPaidPlanIsNotDeleted() = runBlocking {
        val id = newPlan(300_000)
        repo.recordPayment(id, 120_000, walletId, settle = false)
        assertEquals(emptyList<Long>(), repo.deletePlanInSeries(id, com.example.timeapp.data.SeriesScope.ONLY_THIS))
        assertEquals(PlanStatus.PARTLY_PAID, plan(id).status)
    }

    /** Undo puts a deleted payment and its plan back exactly. */
    @Test
    fun undoingADeleteRestoresThePaymentAndThePlan() = runBlocking {
        val id = newPlan(300_000)
        repo.recordPayment(id, 120_000, walletId, settle = false)
        val payment = ledger().single()
        val snap = repo.deleteHistoryRecordUndoable(payment.id)!!
        assertEquals(0L, plan(id).paidMinor)
        repo.restore(snap)
        assertEquals(120_000L, plan(id).paidMinor)
        assertEquals(PlanStatus.PARTLY_PAID, plan(id).status)
        assertEquals(payment.id, ledger().single().id)
        assertEquals(2_400_000L - 120_000L, balance())
    }

    /** A transfer fee is its own expense; the transfer stays neutral. */
    @Test
    fun aTransferFeeIsSpendingTheTransferIsNot() = runBlocking {
        repo.addWallet(Wallet(0, "Bank", WalletKind.entries.first(), 0, LocalDate.now()))
        val bank = repo.observeWallets().first().first { it.name == "Bank" }.id
        repo.transfer(walletId, bank, 100_000, feeMinor = 1_500)
        assertEquals(2_400_000L - 101_500L, balance())
        assertEquals(1_500L, ledger().sumOf { it.netSpendMinor })
    }
}
