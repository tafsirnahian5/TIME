package com.example.timeapp.ui.money

import kotlinx.coroutines.flow.flowOn
import com.example.timeapp.data.L

import com.example.timeapp.data.totalEffect
import com.example.timeapp.data.netSpendMinor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.Direction
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.WalletKind
import com.example.timeapp.data.notify.ReminderScheduler
import com.example.timeapp.data.repository.MoneyRepository
import com.example.timeapp.data.repository.ReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The PDF's four numbers. Planned remaining is **windowed**: open commitments due from today
 * to the end of the month. Anything already past its date is the *overdue carryover*, shown
 * as its own labelled line and included by default ("allow that filter to be switched off").
 * Undated plans stay out of every forecast until dated, and plans due in a later month wait
 * for that month — this used to sum every dated plan, so December's bill cut September's
 * "after planned costs".
 */
data class MoneyOverviewNumbers(
    val availableBalance: Long,
    val paidThisMonth: Long,
    /** Open commitments due between today and the end of the window. */
    val plannedInWindow: Long,
    /** Open commitments already past their due date. */
    val overdueCarryover: Long,
    val includeCarryover: Boolean,
    /** The window's last day, for the "to 30 Sep" label every projection carries. */
    val windowEnd: LocalDate,
) {
    val plannedRemaining: Long get() = plannedInWindow + if (includeCarryover) overdueCarryover else 0L
    val afterPlannedCosts: Long get() = availableBalance - plannedRemaining
}

/** The Overview's numbers, lifted out so `WeeklyReviewScreen`'s Money section reuses them
 * instead of re-deriving the same math. Pure; tested in MoneyOverviewTest. */
fun computeMoneyOverview(
    ui: MoneyUiState,
    month: YearMonth = YearMonth.now(),
    today: LocalDate = LocalDate.now(),
    includeCarryover: Boolean = true,
): MoneyOverviewNumbers {
    val totalOpening = ui.wallets.sumOf { it.openingBalanceMinor }
    val totalDelta = ui.history.sumOf { it.totalEffect }
    val availableBalance = totalOpening + totalDelta
    // Net of refunds: the PDF's "net actual expense in the chosen month".
    val paidThisMonth = ui.history.filter {
        it.settledAt != null && YearMonth.from(it.settledAt.atZone(ZoneId.systemDefault())) == month
    }.sumOf { it.netSpendMinor }
    val windowEnd = month.atEndOfMonth()
    val open = ui.plans.filter { it.isOpen && it.dueOn != null }
    val carryover = open.filter { it.dueOn!!.isBefore(today) }.sumOf { it.remainingMinor }
    val inWindow = open.filter { !it.dueOn!!.isBefore(today) && !it.dueOn.isAfter(windowEnd) }.sumOf { it.remainingMinor }
    return MoneyOverviewNumbers(availableBalance, paidThisMonth, inWindow, carryover, includeCarryover, windowEnd)
}

data class MoneyUiState(
    val plans: List<MoneyRecord> = emptyList(),
    val history: List<MoneyRecord> = emptyList(),
    /** Every wallet, archived included — balances and history always count them. */
    val wallets: List<Wallet> = emptyList(),
) {
    val activeWallets: List<Wallet> get() = wallets.filter { !it.isArchived }

    /** What a wallet picker offers: the active wallets, plus an archived one the record being
     * edited already uses (so editing it can't silently move it to another wallet). */
    fun pickerWallets(vararg keep: Long?): List<Wallet> = wallets.filter { !it.isArchived || it.id in keep }
}

class MoneyViewModel(
    private val repository: MoneyRepository,
    private val reminders: ReminderRepository,
) : ViewModel() {
    /** Chosen reminder timings by plan id, for the cost form to prefill. */
    val reminderTimings: StateFlow<Map<Long, com.example.timeapp.data.ReminderTiming>> = reminders.observeTimings()
        .map { m -> m.filterKeys { it.first == ReminderOwnerKind.PLAN }.mapKeys { it.key.second } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Plan ids that currently have a reminder — see PlanViewModel.reminderOwnerIds. */
    val reminderOwnerIds: StateFlow<Set<Long>> = reminders.observeAll()
        .map { list -> list.filter { it.ownerKind == ReminderOwnerKind.PLAN }.map { it.ownerId }.toSet() }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val uiState: StateFlow<MoneyUiState> = combine(
        repository.observePlans(), repository.observeHistory(), repository.observeWallets(),
    ) { plans, history, wallets ->
        // Overdue is a live, date-derived badge, not a one-time classification made at
        // creation — a PLANNED plan whose due date has passed displays as OVERDUE without
        // mutating what's persisted (PARTLY_PAID stays PARTLY_PAID; only PLANNED escalates).
        val today = LocalDate.now()
        // Also puts right a status stored before it was derived from the date on write.
        val displayPlans = plans.map { p -> p.copy(status = p.displayPlanStatus(today)) }
        MoneyUiState(plans = displayPlans, history = history, wallets = wallets)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, MoneyUiState())

    fun walletBalance(walletId: Long) = repository.walletBalance(walletId)

    /** B3 — new planned cost. Status mirrors the existing PlanStatus semantics: overdue if the
     * due date is already past "today", planned if dated, unscheduled if not. */
    fun addPlan(
        title: String,
        amountMinor: Long,
        categoryId: Long?,
        dueOn: LocalDate?,
        walletId: Long? = null,
        note: String? = null,
        remind: Boolean = false,
        recurrence: com.example.timeapp.data.Recurrence? = null,
        timing: com.example.timeapp.data.ReminderTiming? = null,
        links: com.example.timeapp.data.MoneyLinks = com.example.timeapp.data.MoneyLinks(),
    ) {
        // Overdue is a badge worked out from the date when shown, never stored.
        val status = if (dueOn == null) PlanStatus.UNSCHEDULED else PlanStatus.PLANNED
        viewModelScope.launch {
            val id = repository.addPlan(
                MoneyRecord(
                    id = 0,
                    direction = Direction.COST,
                    title = title,
                    categoryId = categoryId,
                    amountMinor = amountMinor,
                    dueOn = dueOn,
                    status = status,
                    walletId = walletId,
                    note = note,
                    recurrence = recurrence?.copy(remind = remind && dueOn != null),
                    priority = links.priority,
                    taskId = links.taskId,
                    projectId = links.projectId,
                ),
            )
            if (remind && dueOn != null) {
                val t = timing ?: com.example.timeapp.data.ReminderTiming.PLAN_DEFAULT
                reminders.setReminder(ReminderOwnerKind.PLAN, id, t.at(dueOn, null) ?: planReminderInstant(dueOn), t)
            }
            // After the first bill's own reminder, so every generated month reminds too —
            // previously only the first occurrence ever did.
            if (recurrence != null) reminders.expandRecurrences()
        }
    }

    private fun planReminderInstant(dueOn: LocalDate) = ReminderScheduler.planTimeFor(dueOn)

    /** E3 — record payment: settle in full or part-pay and keep open, never collapsed. */
    fun recordPayment(planId: Long, amountMinor: Long, walletId: Long, settle: Boolean) {
        viewModelScope.launch { repository.recordPayment(planId, amountMinor, walletId, settle) }
    }

    fun addWallet(name: String, kind: WalletKind, openingBalanceMinor: Long, openingDate: LocalDate) {
        viewModelScope.launch {
            repository.addWallet(Wallet(id = 0, name = name, kind = kind, openingBalanceMinor = openingBalanceMinor, openingDate = openingDate))
        }
    }

    fun renameWallet(walletId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repository.renameWallet(walletId, newName) }
    }

    fun setWalletArchived(walletId: Long, archived: Boolean) {
        viewModelScope.launch { repository.setWalletArchived(walletId, archived) }
    }

    fun updatePlan(record: MoneyRecord) {
        viewModelScope.launch { repository.updatePlan(record) }
    }

    /** A recorded transaction is not immutable history — a typo must be fixable. Any linked
     * plan's paid total and settled status are reconciled in the same transaction. */
    fun updateHistoryRecord(record: MoneyRecord) {
        viewModelScope.launch {
            val snap = repository.updateHistoryRecordUndoable(record) ?: return@launch
            offerUndo(L("“%1\$s” updated", record.title), snap)
        }
    }

    /** Receipts by ledger row. */
    val attachments: StateFlow<Map<Long, List<com.example.timeapp.data.db.AttachmentEntity>>> = repository.observeAttachments()
        .map { list -> list.groupBy { it.recordId } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private fun offerUndo(message: String, snap: MoneyRepository.LedgerSnapshot) {
        com.example.timeapp.ui.plan.UndoBus.offer(com.example.timeapp.ui.plan.UndoAction(message) { viewModelScope.launch { repository.restore(snap) } })
    }

    /** Puts back a deleted or edited plan (and its series) exactly, reminders re-armed. */
    private fun offerPlanRestore(message: String, snap: com.example.timeapp.data.db.PlanRowsSnapshot) {
        com.example.timeapp.ui.plan.UndoBus.offer(com.example.timeapp.ui.plan.UndoAction(message) {
            viewModelScope.launch {
                repository.restorePlanRows(snap)
                reminders.reconcile()
            }
        })
    }

    suspend fun refundableMinor(originalId: Long): Long = repository.refundableMinor(originalId)

    suspend fun linkedPlanOf(originalId: Long): MoneyRecord? = repository.linkedPlanOf(originalId)

    /** Refunds [originalId]; [onDone] gets false if the amount was rejected. */
    fun refund(originalId: Long, amountMinor: Long, walletId: Long, reopenPlan: Boolean, note: String?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repository.refund(originalId, amountMinor, walletId, reopenPlan, note)) }
    }

    fun importTransactions(
        rows: List<com.example.timeapp.data.CsvImport.ImportRow>,
        walletId: Long,
        categoryIdsByName: Map<String, Long>,
        onDone: (Int) -> Unit,
    ) {
        viewModelScope.launch { onDone(repository.importTransactions(rows, walletId, categoryIdsByName)) }
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch {
            val snap = repository.deleteHistoryRecordUndoable(id) ?: return@launch
            offerUndo(L("“%1\$s” deleted", snap.rows.first().title), snap)
        }
    }

    fun deletePlan(id: Long, scope: com.example.timeapp.data.SeriesScope = com.example.timeapp.data.SeriesScope.ONLY_THIS) {
        viewModelScope.launch {
            val (removed, snap) = repository.deletePlanUndoable(id, scope) ?: return@launch
            removed.forEach { reminders.clearReminder(ReminderOwnerKind.PLAN, it) }
            val title = snap.titleOf(id)
            offerPlanRestore(if (removed.size > 1) L("“%1\$s” and %2\$s later deleted", title, (removed.size - 1)) else L("“%1\$s” deleted", title), snap)
        }
    }

    /** Cancelled plans — Money › Planned lists them apart, where one can be reopened. */
    val cancelledPlans: StateFlow<List<MoneyRecord>> = repository.observeCancelledPlans()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Cancels a plan (payments kept) and retires its reminder — a cancelled plan never notifies. */
    fun cancelPlan(id: Long) {
        viewModelScope.launch {
            repository.cancelPlan(id)
            reminders.clearReminder(ReminderOwnerKind.PLAN, id)
        }
    }

    fun reopenCancelledPlan(id: Long) {
        viewModelScope.launch { repository.reopenCancelledPlan(id) }
    }

    /** Saves an edit from the cost form — see [com.example.timeapp.ui.plan.PlanViewModel.saveTaskEdit]. */
    fun savePlanEdit(record: MoneyRecord, scope: com.example.timeapp.data.SeriesScope, remind: Boolean, timing: com.example.timeapp.data.ReminderTiming? = null) {
        viewModelScope.launch {
            val snap = repository.snapshotPlanRows(record.id, alsoSeries = record.id)
            val removeIds = repository.updatePlanInSeries(
                record.copy(recurrence = record.recurrence?.copy(remind = remind && record.dueOn != null)),
                scope,
            )
            removeIds.forEach { reminders.clearReminder(ReminderOwnerKind.PLAN, it) }
            val dueOn = record.dueOn
            if (remind && dueOn != null) {
                val t = timing ?: com.example.timeapp.data.ReminderTiming.PLAN_DEFAULT
                reminders.setReminder(ReminderOwnerKind.PLAN, record.id, t.at(dueOn, null) ?: planReminderInstant(dueOn), t)
            } else {
                reminders.clearReminder(ReminderOwnerKind.PLAN, record.id)
            }
            if (record.recurrence != null) reminders.expandRecurrences()
            if (snap != null) offerPlanRestore(L("“%1\$s” updated", record.title), snap)
        }
    }

    fun transfer(fromWalletId: Long, toWalletId: Long, amountMinor: Long, feeMinor: Long = 0) {
        viewModelScope.launch { repository.transfer(fromWalletId, toWalletId, amountMinor, feeMinor) }
    }

    fun addTransaction(direction: Direction, title: String, amountMinor: Long, walletId: Long, note: String? = null, links: com.example.timeapp.data.MoneyLinks = com.example.timeapp.data.MoneyLinks()) {
        if (title.isBlank() || amountMinor <= 0) return
        viewModelScope.launch { repository.addTransaction(direction, title, amountMinor, walletId, note, links) }
    }

    fun adjustBalance(walletId: Long, deltaMinor: Long) {
        viewModelScope.launch { repository.adjustBalance(walletId, deltaMinor) }
    }

    class Factory(
        private val repository: MoneyRepository,
        private val reminders: ReminderRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MoneyViewModel(repository, reminders) as T
    }
}

/** [wallets] with the preferred one first — the money forms preselect the first wallet. */
fun List<com.example.timeapp.data.Wallet>.preferring(id: Long): List<com.example.timeapp.data.Wallet> =
    sortedByDescending { it.id == id }
