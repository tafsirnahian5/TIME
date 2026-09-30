package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.WorkdaySegment
import com.example.timeapp.data.WorkdaySegmentKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "workday_segments")
data class WorkdaySegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val kind: WorkdaySegmentKind,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    /** Set on the first manual correction: the times as first recorded, and why they changed
     * (the PDF: "record manual corrections with their reason and original values"). */
    val originalStartedAt: Instant? = null,
    val originalEndedAt: Instant? = null,
    val correctionReason: String? = null,
)

fun WorkdaySegmentEntity.toDomain(): WorkdaySegment =
    WorkdaySegment(id, date, kind, startedAt, endedAt, originalStartedAt, originalEndedAt, correctionReason)

@Dao
interface WorkdaySegmentDao {
    @Insert
    suspend fun insert(segment: WorkdaySegmentEntity): Long

    @Update
    suspend fun update(segment: WorkdaySegmentEntity)

    @Query("SELECT * FROM workday_segments WHERE date = :date AND endedAt IS NULL LIMIT 1")
    suspend fun getActiveForDate(date: LocalDate): WorkdaySegmentEntity?

    @Query("SELECT * FROM workday_segments WHERE date = :date ORDER BY startedAt")
    suspend fun forDate(date: LocalDate): List<WorkdaySegmentEntity>

    @Query("SELECT * FROM workday_segments WHERE id = :id")
    suspend fun getById(id: Long): WorkdaySegmentEntity?

    /** A day left open before [date] — a forgotten End day. */
    @Query("SELECT * FROM workday_segments WHERE date < :date AND endedAt IS NULL ORDER BY startedAt LIMIT 1")
    fun observeOpenBefore(date: LocalDate): Flow<WorkdaySegmentEntity?>

    @Query("SELECT * FROM workday_segments WHERE date = :date ORDER BY startedAt")
    fun observeForDate(date: LocalDate): Flow<List<WorkdaySegmentEntity>>
}
