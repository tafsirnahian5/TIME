package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * A utility countdown — tea, laundry, a parking meter. Kept apart from work entirely: no
 * session is ever opened for one, so they can't inflate work totals (the PDF's 1.6 exit
 * check). Running ⇔ [endsAt] set; otherwise [remainingMs] is what's left (0 = finished).
 */
@Entity(tableName = "utility_timers")
data class UtilityTimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val durationMs: Long,
    val endsAt: Instant? = null,
    val remainingMs: Long,
    /** A timed habit's timer: when it rings, that habit's day is checked off. */
    val habitId: Long? = null,
)

/** What's left on [timer] at [now]. */
fun UtilityTimerEntity.remainingAt(now: Instant): Long =
    endsAt?.let { (it.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0) } ?: remainingMs

@Dao
interface UtilityTimerDao {
    @Query("SELECT * FROM utility_timers ORDER BY id")
    fun observeAll(): Flow<List<UtilityTimerEntity>>

    @Query("SELECT * FROM utility_timers WHERE id = :id")
    suspend fun getById(id: Long): UtilityTimerEntity?

    @Insert
    suspend fun insert(timer: UtilityTimerEntity): Long

    @Update
    suspend fun update(timer: UtilityTimerEntity)

    @Query("SELECT * FROM utility_timers WHERE habitId = :habitId LIMIT 1")
    suspend fun forHabit(habitId: Long): UtilityTimerEntity?

    @Query("DELETE FROM utility_timers WHERE id = :id")
    suspend fun delete(id: Long)
}
