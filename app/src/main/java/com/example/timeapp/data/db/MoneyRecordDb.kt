package com.example.timeapp.data.db

import com.example.timeapp.data.clampedTitle
import com.example.timeapp.data.clampedNote
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.Direction
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

// Indexes for the queries that run on every Money screen and at 10k rows (Phase 16):
// plans/history by status (history ordered by settledAt), series, loans and refunds.
@Entity(
    tableName = "money_records",
    indices = [
        androidx.room.Index("status", "settledAt"),
        androidx.room.Index("seriesId"),
        androidx.room.Index("loanId"),
        androidx.room.Index("refundOfId"),
    ],
)
data class MoneyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: Direction,
    val title: String,
    val categoryColor: Long? = null,
    val amountMinor: Long,
    val paidMinor: Long = 0,
    val dueOn: LocalDate? = null,
    val settledAt: Instant? = null,
    val walletId: Long? = null,
    val transferToWalletId: Long? = null,
    val status: PlanStatus? = null,
    val linkedToPlan: Boolean = false,
    val linkedPlanId: Long? = null,
    val note: String? = null,
    val recurrence: com.example.timeapp.data.Recurrence? = null,
    val seriesId: Long? = null,
    val categoryId: Long? = null,
    val refundOfId: Long? = null,
    val loanId: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "NORMAL") val priority: com.example.timeapp.data.Priority = com.example.timeapp.data.Priority.NORMAL,
    val taskId: Long? = null,
    val projectId: Long? = null,
)

fun MoneyRecordEntity.toDomain(): MoneyRecord = MoneyRecord(
    id = id, direction = direction, title = title.clampedTitle(), categoryColor = categoryColor,
    amountMinor = amountMinor, paidMinor = paidMinor, dueOn = dueOn, settledAt = settledAt,
    walletId = walletId, transferToWalletId = transferToWalletId, status = status, linkedToPlan = linkedToPlan,
    linkedPlanId = linkedPlanId, note = note?.clampedNote(), recurrence = recurrence, seriesId = seriesId, categoryId = categoryId,
    refundOfId = refundOfId,
    loanId = loanId,
    priority = priority,
    taskId = taskId,
    projectId = projectId,
)

fun MoneyRecord.toEntity(): MoneyRecordEntity = MoneyRecordEntity(
    id = id, direction = direction, title = title, categoryColor = categoryColor,
    amountMinor = amountMinor, paidMinor = paidMinor, dueOn = dueOn, settledAt = settledAt,
    // Never Overdue, and Planned/Unscheduled always agree with the due date.
    walletId = walletId, transferToWalletId = transferToWalletId, status = com.example.timeapp.data.storedPlanStatus(status, dueOn), linkedToPlan = linkedToPlan,
    linkedPlanId = linkedPlanId, note = note, recurrence = recurrence, seriesId = seriesId, categoryId = categoryId,
    refundOfId = refundOfId,
    loanId = loanId,
    priority = priority,
    taskId = taskId,
    projectId = projectId,
)

@Dao
interface MoneyRecordDao {
    /** Open plans (E1) — excludes SETTLED, which stays in the table (not the open list) so its
     * original estimate survives for variance reporting instead of being deleted. */
    @Query("SELECT * FROM money_records WHERE status IS NOT NULL AND status NOT IN ('SETTLED', 'CANCELLED') ORDER BY id")
    fun observePlans(): Flow<List<MoneyRecordEntity>>

    /** Cancelled plans, most recent first — listed apart so one can be reopened. */
    @Query("SELECT * FROM money_records WHERE status = 'CANCELLED' ORDER BY id DESC")
    fun observeCancelledPlans(): Flow<List<MoneyRecordEntity>>

    /** Settled ledger history (E2), reverse chronological. */
    @Query("SELECT * FROM money_records WHERE status IS NULL ORDER BY settledAt DESC")
    fun observeHistory(): Flow<List<MoneyRecordEntity>>

    /** Plans that transitioned to SETTLED within [from, to) — Tier 10's Money Stats
     * planned-vs-actual variance, reusing observePlans()'s raw-string status idiom. */
    @Query("SELECT * FROM money_records WHERE status = 'SETTLED' AND settledAt >= :from AND settledAt < :to ORDER BY settledAt")
    fun observeSettledPlansInRange(from: Instant, to: Instant): Flow<List<MoneyRecordEntity>>

    @Insert
    suspend fun insert(record: MoneyRecordEntity): Long

    @Update
    suspend fun update(record: MoneyRecordEntity)

    @Query("DELETE FROM money_records WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM money_records WHERE id = :id")
    suspend fun getById(id: Long): MoneyRecordEntity?

    @Query("SELECT COUNT(*) FROM money_records")
    suspend fun count(): Int

    /** Every plan row belonging to a repeat series, for the bounded expander. */
    @Query("SELECT * FROM money_records WHERE seriesId IS NOT NULL AND status IS NOT NULL")
    suspend fun allPlansInSeries(): List<MoneyRecordEntity>

    /** Every refund already made against [originalId], to cap the next one. */
    @androidx.room.Upsert
    suspend fun upsert(record: MoneyRecordEntity)

    @Query("SELECT * FROM money_records WHERE loanId = :loanId")
    suspend fun forLoan(loanId: Long): List<MoneyRecordEntity>

    @Query("DELETE FROM money_records WHERE loanId = :loanId")
    suspend fun deleteForLoan(loanId: Long)

    @Query("SELECT * FROM money_records WHERE refundOfId = :originalId")
    suspend fun refundsOf(originalId: Long): List<MoneyRecordEntity>

    /** One series' plan rows — ledger rows (status NULL) never belong to a series. */
    @Query("SELECT * FROM money_records WHERE seriesId = :seriesId AND status IS NOT NULL")
    suspend fun plansInSeries(seriesId: Long): List<MoneyRecordEntity>
}
