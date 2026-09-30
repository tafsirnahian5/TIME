package com.example.timeapp.data.repository

import com.example.timeapp.data.L

import androidx.room.withTransaction
import com.example.timeapp.data.Direction
import com.example.timeapp.data.Loan
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.db.LoanEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.db.toEntity
import com.example.timeapp.data.loanBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * Loans & dues. Every money movement is a ledger row written in the same transaction as the
 * loan change, so a wallet balance and a loan balance can't disagree. Repayments never exceed
 * what's outstanding (refused, not rounded down).
 */
class LoanRepository(private val db: TimeDatabase) {
    private val dao get() = db.loanDao()
    private val money get() = db.moneyRecordDao()

    fun observeLoans(): Flow<List<Loan>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Every ledger row that belongs to a loan. */
    fun observeLoanRows(): Flow<List<MoneyRecord>> =
        money.observeHistory().map { list -> list.map { it.toDomain() }.filter { it.loanId != null } }

    /** A new loan with its first movement: lending takes money out of [walletId], borrowing puts it in. */
    suspend fun create(person: String, lent: Boolean, amountMinor: Long, walletId: Long, dueOn: LocalDate?, note: String?, now: Instant = Instant.now()): Long =
        db.withTransaction {
            val id = dao.insert(LoanEntity(person = person.trim(), lent = lent, startedOn = LocalDate.now(), dueOn = dueOn, note = note?.trim()?.takeIf { it.isNotEmpty() }))
            post(id, if (lent) Direction.LOAN_OUT else Direction.LOAN_IN, amountMinor, walletId, if (lent) L("Lent to %1\$s", person.trim()) else L("Borrowed from %1\$s", person.trim()), now)
            id
        }

    /** More lent (or borrowed) on the same loan. */
    suspend fun addMore(loanId: Long, amountMinor: Long, walletId: Long, now: Instant = Instant.now()) = db.withTransaction {
        val loan = dao.getById(loanId)?.toDomain() ?: return@withTransaction
        post(loanId, if (loan.lent) Direction.LOAN_OUT else Direction.LOAN_IN, amountMinor, walletId, if (loan.lent) L("Lent to %1\$s", loan.person) else L("Borrowed from %1\$s", loan.person), now)
    }

    /**
     * A repayment — to you on a loan you gave, by you on one you took. Refuses (false) more
     * than is outstanding. Paying it all closes nothing by itself: the loan closes when the
     * user says so, or here when the balance reaches zero.
     */
    suspend fun repay(loanId: Long, amountMinor: Long, walletId: Long, now: Instant = Instant.now()): Boolean = db.withTransaction {
        val loan = dao.getById(loanId)?.toDomain() ?: return@withTransaction false
        val rows = money.observeForLoanOnce(loanId)
        val outstanding = loanBalance(loan, rows).outstandingMinor
        if (amountMinor <= 0 || amountMinor > outstanding) return@withTransaction false
        post(loanId, if (loan.lent) Direction.LOAN_IN else Direction.LOAN_OUT, amountMinor, walletId, if (loan.lent) L("Repaid by %1\$s", loan.person) else L("Repaid %1\$s", loan.person), now)
        // Settled in full: closed, with its history kept.
        if (amountMinor == outstanding) dao.update(loan.copy(closedAt = now).toEntity())
        true
    }

    suspend fun setClosed(loanId: Long, closed: Boolean) {
        dao.getById(loanId)?.let { dao.update(it.copy(closedAt = if (closed) Instant.now() else null)) }
    }

    suspend fun update(loan: Loan) = dao.update(loan.toEntity())

    /** Deletes a loan and its ledger rows — wallet balances go back to as if it never happened. */
    suspend fun delete(loanId: Long) = db.withTransaction {
        money.deleteForLoan(loanId)
        dao.delete(loanId)
    }

    private suspend fun post(loanId: Long, direction: Direction, amountMinor: Long, walletId: Long, title: String, at: Instant) {
        money.insert(
            com.example.timeapp.data.db.MoneyRecordEntity(
                direction = direction, title = title, amountMinor = amountMinor, paidMinor = amountMinor,
                walletId = walletId, settledAt = at, loanId = loanId,
            ),
        )
    }

    private suspend fun com.example.timeapp.data.db.MoneyRecordDao.observeForLoanOnce(loanId: Long): List<MoneyRecord> =
        forLoan(loanId).map { it.toDomain() }
}
