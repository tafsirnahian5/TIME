package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.GoalAllocation
import com.example.timeapp.data.SavingsGoal
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val targetDate: LocalDate? = null,
    val archived: Boolean = false,
)

/** A set-aside or take-back; deleting the goal deletes its history. Wallets are never deleted. */
@Entity(
    tableName = "goal_allocations",
    indices = [Index("goalId"), Index("walletId")],
    foreignKeys = [ForeignKey(entity = SavingsGoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE)],
)
data class GoalAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val walletId: Long,
    val amountMinor: Long,
    val at: Instant,
)

fun SavingsGoalEntity.toDomain() = SavingsGoal(id, name, targetMinor, targetDate, archived)
fun SavingsGoal.toEntity() = SavingsGoalEntity(id, name, targetMinor, targetDate, archived)
fun GoalAllocationEntity.toDomain() = GoalAllocation(id, goalId, walletId, amountMinor, at)

@Dao
interface SavingsDao {
    @Query("SELECT * FROM savings_goals ORDER BY archived, id")
    fun observeGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM goal_allocations ORDER BY at")
    fun observeAllocations(): Flow<List<GoalAllocationEntity>>

    @Query("SELECT * FROM goal_allocations WHERE goalId = :goalId")
    suspend fun allocationsFor(goalId: Long): List<GoalAllocationEntity>

    @Query("SELECT * FROM goal_allocations WHERE walletId = :walletId")
    suspend fun allocationsInWallet(walletId: Long): List<GoalAllocationEntity>

    @Insert
    suspend fun insertGoal(goal: SavingsGoalEntity): Long

    @Update
    suspend fun updateGoal(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Insert
    suspend fun insertAllocation(allocation: GoalAllocationEntity): Long
}
