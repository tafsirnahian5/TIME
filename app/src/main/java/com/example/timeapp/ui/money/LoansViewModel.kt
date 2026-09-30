package com.example.timeapp.ui.money

import kotlinx.coroutines.flow.flowOn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.Loan
import com.example.timeapp.data.LoanBalance
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.ReminderTiming
import com.example.timeapp.data.loanBalance
import com.example.timeapp.data.repository.LoanRepository
import com.example.timeapp.data.repository.ReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LoanRow(val loan: Loan, val balance: LoanBalance, val rows: List<MoneyRecord>)

data class LoansUiState(val open: List<LoanRow> = emptyList(), val closed: List<LoanRow> = emptyList()) {
    val owedToYouMinor: Long get() = open.filter { it.loan.lent }.sumOf { it.balance.outstandingMinor }
    val youOweMinor: Long get() = open.filterNot { it.loan.lent }.sumOf { it.balance.outstandingMinor }
    fun find(id: Long) = (open + closed).firstOrNull { it.loan.id == id }
}

/** Money › Loans & dues. */
class LoansViewModel(private val repo: LoanRepository, private val reminders: ReminderRepository) : ViewModel() {
    val uiState: StateFlow<LoansUiState> = combine(repo.observeLoans(), repo.observeLoanRows()) { loans, rows ->
        val all = loans.map { l -> LoanRow(l, loanBalance(l, rows), rows.filter { it.loanId == l.id }.sortedByDescending { it.settledAt }) }
        LoansUiState(all.filter { it.loan.isOpen }, all.filterNot { it.loan.isOpen })
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, LoansUiState())

    /** Loans with a reminder set, for the form. */
    val remindedIds: StateFlow<Set<Long>> = reminders.observeAll()
        .map { list -> list.filter { it.ownerKind == ReminderOwnerKind.LOAN }.map { it.ownerId }.toSet() }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun create(person: String, lent: Boolean, amountMinor: Long, walletId: Long, dueOn: LocalDate?, note: String?, remind: Boolean) {
        viewModelScope.launch {
            val id = repo.create(person, lent, amountMinor, walletId, dueOn, note)
            syncReminder(id, dueOn, remind)
        }
    }

    fun addMore(loanId: Long, amountMinor: Long, walletId: Long) = viewModelScope.launch { repo.addMore(loanId, amountMinor, walletId) }

    fun repay(loanId: Long, amountMinor: Long, walletId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.repay(loanId, amountMinor, walletId)
            if (ok && uiState.value.find(loanId)?.loan?.isOpen == false) reminders.clearReminder(ReminderOwnerKind.LOAN, loanId)
            onResult(ok)
        }
    }

    fun setClosed(loan: Loan, closed: Boolean) {
        viewModelScope.launch {
            repo.setClosed(loan.id, closed)
            if (closed) reminders.clearReminder(ReminderOwnerKind.LOAN, loan.id)
        }
    }

    fun saveDetails(loan: Loan, remind: Boolean) {
        viewModelScope.launch {
            repo.update(loan)
            syncReminder(loan.id, loan.dueOn, remind && loan.isOpen)
        }
    }

    fun delete(loanId: Long) {
        viewModelScope.launch {
            reminders.clearReminder(ReminderOwnerKind.LOAN, loanId)
            repo.delete(loanId)
        }
    }

    /** A due loan reminds at 09:00 on its day; it only ever prompts. */
    private suspend fun syncReminder(loanId: Long, dueOn: LocalDate?, remind: Boolean) {
        val at = dueOn?.let { ReminderTiming.TASK_DEFAULT.at(it, null) }
        if (remind && at != null) reminders.setReminder(ReminderOwnerKind.LOAN, loanId, at, ReminderTiming.TASK_DEFAULT)
        else reminders.clearReminder(ReminderOwnerKind.LOAN, loanId)
    }

    class Factory(private val repo: LoanRepository, private val reminders: ReminderRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LoansViewModel(repo, reminders) as T
    }
}
