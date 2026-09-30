package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.timeapp.data.WorkTarget
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** One version of the weekly work target; unique per effective week, so a second change in
 * the same week replaces that week's version instead of stacking. */
@Entity(tableName = "work_targets", indices = [Index(value = ["effectiveFrom"], unique = true)])
data class WorkTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weeklyMinutes: Int,
    val effectiveFrom: LocalDate,
)

fun WorkTargetEntity.toDomain() = WorkTarget(id, weeklyMinutes, effectiveFrom)

@Dao
interface WorkTargetDao {
    @Query("SELECT * FROM work_targets ORDER BY effectiveFrom")
    fun observeAll(): Flow<List<WorkTargetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: WorkTargetEntity)
}
