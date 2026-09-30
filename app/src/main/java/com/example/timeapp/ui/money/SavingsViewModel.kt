package com.example.timeapp.ui.money

import kotlinx.coroutines.flow.flowOn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.GoalPace
import com.example.timeapp.data.SavingsGoal
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.earmarkedByWallet
import com.example.timeapp.data.goalPace
import com.example.timeapp.data.repository.MoneyRepository
import com.example.timeapp.data.repository.SavingsRepository
import com.example.timeapp.data.savedFor
import com.example.timeapp.data.unearmarkedMinor
import com.example.timeapp.data.walletEffect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One goal with what it holds, per wallet, and its pace. */
data class GoalRow(val goal: SavingsGoal, val pace: GoalPace, val heldByWallet: Map<Long, Long>)

/** A wallet's real balance, how much of it is set aside, and what's free. */
data class WalletSavings(val wallet: Wallet, val balanceMinor: Long, val earmarkedMinor: Long) {
    val freeMinor: Long get() = unearmarkedMinor(balanceMinor, earmarkedMinor)
    /** Set aside more than the wallet now holds — spending has overtaken the earmarks. */
    val overCommittedMinor: Long get() = (earmarkedMinor - balanceMinor).coerceAtLeast(0)
}

data class SavingsUiState(
    val active: List<GoalRow> = emptyList(),
    val archived: List<GoalRow> = emptyList(),
    val wallets: List<WalletSavings> = emptyList(),
) {
    val totalEarmarkedMinor: Long get() = wallets.sumOf { it.earmarkedMinor }
}

class SavingsViewModel(
    private val repository: SavingsRepository,
    moneyRepository: MoneyRepository,
) : ViewModel() {

    val uiState: StateFlow<SavingsUiState> = combine(
        repository.observeGoals(),
        repository.observeAllocations(),
        moneyRepository.observeWallets(),
        moneyRepository.observeHistory(),
    ) { goals, allocations, wallets, history ->
        val today = LocalDate.now()
        val earmarked = allocations.earmarkedByWallet()
        val rows = goals.map { g ->
            val mine = allocations.filter { it.goalId == g.id }
            GoalRow(
                g,
                goalPace(g, allocations.savedFor(g.id), today),
                mine.groupBy { it.walletId }.mapValues { (_, l) -> l.sumOf { it.amountMinor } }.filterValues { it > 0 },
            )
        }
        SavingsUiState(
            active = rows.filterNot { it.goal.archived },
            archived = rows.filter { it.goal.archived },
            wallets = wallets.map { w ->
                WalletSavings(w, w.openingBalanceMinor + history.sumOf { it.walletEffect(w.id) }, earmarked[w.id] ?: 0L)
            },
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, SavingsUiState())

    fun addGoal(name: String, targetMinor: Long, targetDate: LocalDate?) {
        if (name.isBlank() || targetMinor <= 0) return
        viewModelScope.launch { repository.addGoal(SavingsGoal(0, name.trim(), targetMinor, targetDate)) }
    }

    fun updateGoal(goal: SavingsGoal) {
        if (goal.name.isBlank() || goal.targetMinor <= 0) return
        viewModelScope.launch { repository.updateGoal(goal.copy(name = goal.name.trim())) }
    }

    fun deleteGoal(id: Long) = viewModelScope.launch { repository.deleteGoal(id) }
    fun archive(goal: SavingsGoal) = viewModelScope.launch { repository.archive(goal) }
    fun restore(goal: SavingsGoal) = viewModelScope.launch { repository.restore(goal) }

    fun setAside(goalId: Long, walletId: Long, amountMinor: Long, onDone: (Boolean) -> Unit) {
        val before = uiState.value.active.firstOrNull { it.goal.id == goalId }
        viewModelScope.launch {
            val ok = repository.setAside(goalId, walletId, amountMinor)
            onDone(ok)
            // Confetti only on the set-aside that crosses the line, not on every one after.
            if (ok && before != null && !before.pace.reached && before.pace.savedMinor + amountMinor >= before.goal.targetMinor) {
                com.example.timeapp.ui.components.CelebrationBus.emit(
                    com.example.timeapp.ui.components.Celebration.GoalReached(before.goal.name),
                )
            }
        }
    }

    fun takeBack(goalId: Long, walletId: Long, amountMinor: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repository.takeBack(goalId, walletId, amountMinor)) }
    }

    class Factory(private val repository: SavingsRepository, private val moneyRepository: MoneyRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SavingsViewModel(repository, moneyRepository) as T
    }
}
