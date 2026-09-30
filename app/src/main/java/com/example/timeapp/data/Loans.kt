package com.example.timeapp.data

import java.time.Instant
import java.time.LocalDate

/**
 * Loans & dues (Phase 13, after XPENC and MoneyWallet): money lent to or borrowed from a
 * person. The money itself moves as ledger rows — [Direction.LOAN_OUT] leaves a wallet (you
 * lend, or you pay back), [Direction.LOAN_IN] arrives (you borrow, or you're paid back) — each
 * linked by `loanId`. They move wallet balances exactly and are **never income or spending**,
 * so reports stay clean (the same footing as transfers). Pure; tested in LoansTest.
 */
data class Loan(
    val id: Long,
    val person: String,
    /** True: you lent (they owe you). False: you borrowed (you owe them). */
    val lent: Boolean,
    val startedOn: LocalDate,
    val dueOn: LocalDate? = null,
    val note: String? = null,
    val closedAt: Instant? = null,
) {
    val isOpen: Boolean get() = closedAt == null
}

/** Where a loan stands, from its ledger rows. */
data class LoanBalance(
    /** Lent, or borrowed, in total. */
    val principalMinor: Long,
    /** Repaid so far. */
    val repaidMinor: Long,
) {
    /** Still owed (to you when lent, by you when borrowed); never negative. */
    val outstandingMinor: Long get() = (principalMinor - repaidMinor).coerceAtLeast(0)

    /** Repaid more than was lent — shown, not hidden. */
    val overpaidMinor: Long get() = (repaidMinor - principalMinor).coerceAtLeast(0)
}

/** A lent loan's principal is money out and repayments money in; a borrowed one the reverse. */
fun loanBalance(loan: Loan, rows: List<MoneyRecord>): LoanBalance {
    val mine = rows.filter { it.loanId == loan.id }
    val outMinor = mine.filter { it.direction == Direction.LOAN_OUT }.sumOf { it.paidMinor }
    val inMinor = mine.filter { it.direction == Direction.LOAN_IN }.sumOf { it.paidMinor }
    return if (loan.lent) LoanBalance(outMinor, inMinor) else LoanBalance(inMinor, outMinor)
}

/** Owed to you and owed by you, over open loans. */
fun loanTotals(loans: List<Loan>, rows: List<MoneyRecord>): Pair<Long, Long> {
    val open = loans.filter { it.isOpen }
    val owedToYou = open.filter { it.lent }.sumOf { loanBalance(it, rows).outstandingMinor }
    val youOwe = open.filterNot { it.lent }.sumOf { loanBalance(it, rows).outstandingMinor }
    return owedToYou to youOwe
}
