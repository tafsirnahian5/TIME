package com.example.timeapp.data.repository

import androidx.room.withTransaction
import com.example.timeapp.data.GoalAllocation
import com.example.timeapp.data.SavingsGoal
import com.example.timeapp.data.db.GoalAllocationEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.db.toEntity
import com.example.timeapp.data.unearmarkedMinor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Savings goals and set-asides — see [com.example.timeapp.data.SavingsGoal]. Nothing here
 * touches the ledger: setting money aside is not a transaction. */
class SavingsRepository(private val db: TimeDatabase) {
    private val dao get() = db.savingsDao()
    private val money = MoneyRepository(db)

    fun observeGoals(): Flow<List<SavingsGoal>> = dao.observeGoals().map { list -> list.map { it.toDomain() } }
    fun observeAllocations(): Flow<List<GoalAllocation>> = dao.observeAllocations().map { list -> list.map { it.toDomain() } }

    suspend fun addGoal(goal: SavingsGoal): Long = dao.insertGoal(goal.toEntity().copy(id = 0))
    suspend fun updateGoal(goal: SavingsGoal) = dao.updateGoal(goal.toEntity())

    /** Deleting a goal deletes its set-asides, which frees that money in its wallets. */
    suspend fun deleteGoal(id: Long) = dao.deleteGoal(id)

    /**
     * Sets [amountMinor] aside for [goalId] from [walletId]. Refused (false, nothing written)
     * beyond the wallet's unearmarked money — earmarking what isn't there would be a promise
     * the wallet can't keep. Checked against live balances inside the transaction.
     */
    suspend fun setAside(goalId: Long, walletId: Long, amountMinor: Long): Boolean = db.withTransaction {
        if (amountMinor <= 0) return@withTransaction false
        val balance = money.walletBalance(walletId).first()
        val earmarked = dao.allocationsInWallet(walletId).sumOf { it.amountMinor }
        if (amountMinor > unearmarkedMinor(balance, earmarked)) return@withTransaction false
        dao.insertAllocation(GoalAllocationEntity(goalId = goalId, walletId = walletId, amountMinor = amountMinor, at = Instant.now()))
        true
    }

    /** Takes money back off a goal, into the free part of the wallet it came from. Capped at
     * what the goal holds from that wallet. */
    suspend fun takeBack(goalId: Long, walletId: Long, amountMinor: Long): Boolean = db.withTransaction {
        val held = dao.allocationsFor(goalId).filter { it.walletId == walletId }.sumOf { it.amountMinor }
        if (amountMinor <= 0 || amountMinor > held) return@withTransaction false
        dao.insertAllocation(GoalAllocationEntity(goalId = goalId, walletId = walletId, amountMinor = -amountMinor, at = Instant.now()))
        true
    }

    /**
     * Marks a goal reached (or given up) and releases everything set aside for it, so that
     * money is free again — spending it is then an ordinary expense. The history stays.
     */
    suspend fun archive(goal: SavingsGoal) = db.withTransaction {
        dao.allocationsFor(goal.id).groupBy { it.walletId }.forEach { (walletId, list) ->
            val held = list.sumOf { it.amountMinor }
            if (held > 0) dao.insertAllocation(GoalAllocationEntity(goalId = goal.id, walletId = walletId, amountMinor = -held, at = Instant.now()))
        }
        dao.updateGoal(goal.toEntity().copy(archived = true))
    }

    suspend fun restore(goal: SavingsGoal) = dao.updateGoal(goal.toEntity().copy(archived = false))
}
