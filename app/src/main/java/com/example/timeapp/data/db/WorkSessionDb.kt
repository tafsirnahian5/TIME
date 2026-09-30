package com.example.timeapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.SessionMode
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * One contiguous span of active work — the only place work time is recorded. At most one row
 * is open (`endedAt` null) at any moment: the one-work-timer rule, kept by `WorkTimer`, which
 * opens and closes rows in the same transaction as the timer's runtime row.
 *
 * [entryId] is null for a Focus run with no task. [anchorElapsed] + [bootCount] make the
 * length monotonic within one boot; [checkpointAt] is the last moment T!ME saw it running —
 * where a session cut off by a reboot is closed, flagged [needsReview], never guessed longer.
 */
@Entity(tableName = "work_sessions", indices = [Index("entryId"), Index("startedAt")])
data class WorkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long?,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    @ColumnInfo(defaultValue = "TASK") val mode: SessionMode = SessionMode.TASK,
    /** Markdown-lite session note (the PDF's "session notes"). */
    val note: String? = null,
    @ColumnInfo(defaultValue = "0") val needsReview: Boolean = false,
    val anchorElapsed: Long? = null,
    val bootCount: Int? = null,
    val checkpointAt: Instant? = null,
)

@Dao
interface WorkSessionDao {
    @Insert
    suspend fun insert(session: WorkSessionEntity): Long

    @Update
    suspend fun update(session: WorkSessionEntity)

    /** Whether any work was ever recorded — ticks "Start a timer" on Today's getting-started card. */
    @Query("SELECT EXISTS(SELECT 1 FROM work_sessions)")
    fun observeAny(): kotlinx.coroutines.flow.Flow<Boolean>

    @Query("SELECT * FROM work_sessions WHERE id = :id")
    suspend fun getById(id: Long): WorkSessionEntity?

    /** The latest moment any session reaches — see WorkTimer.ordered. */
    @Query("SELECT MAX(COALESCE(endedAt, startedAt)) FROM work_sessions")
    suspend fun latestEdge(): Instant?

    @Query("DELETE FROM work_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM work_sessions WHERE endedAt IS NULL LIMIT 1")
    suspend fun getActive(): WorkSessionEntity?

    @Query("SELECT * FROM work_sessions WHERE endedAt IS NULL LIMIT 1")
    fun observeActive(): Flow<WorkSessionEntity?>

    @Query("SELECT * FROM work_sessions WHERE entryId = :entryId AND endedAt IS NULL LIMIT 1")
    suspend fun getActiveForEntry(entryId: Long): WorkSessionEntity?

    @Query("SELECT COUNT(*) FROM work_sessions WHERE entryId = :entryId")
    suspend fun countForEntry(entryId: Long): Int

    @Query("DELETE FROM work_sessions WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: Long)

    @Query("SELECT * FROM work_sessions WHERE entryId = :entryId ORDER BY startedAt DESC")
    fun observeForEntry(entryId: Long): Flow<List<WorkSessionEntity>>

    /** Sum of every closed session's duration for this entry, in milliseconds. Instant columns
     * are stored as epoch-millis longs (see Converters), so this is plain integer arithmetic. */
    @Query(
        "SELECT COALESCE(SUM(endedAt - startedAt), 0) " +
            "FROM work_sessions WHERE entryId = :entryId AND endedAt IS NOT NULL",
    )
    fun sumDurationMsForEntry(entryId: Long): Flow<Long>

    /** Every closed session that *overlaps* [from, to) — callers clip them to the period (see
     * `clippedTo`). Filtering on the start alone gave a period work it didn't contain. */
    @Query("SELECT * FROM work_sessions WHERE endedAt IS NOT NULL AND startedAt < :to AND endedAt > :from ORDER BY startedAt")
    fun sessionsInRange(from: Instant, to: Instant): Flow<List<WorkSessionEntity>>

    /** Closed work inside [from, to) only: each overlapping session contributes the part of it
     * that falls in the range. */
    @Query(
        "SELECT COALESCE(SUM(MIN(endedAt, :to) - MAX(startedAt, :from)), 0) FROM work_sessions " +
            "WHERE endedAt IS NOT NULL AND startedAt < :to AND endedAt > :from",
    )
    fun sumDurationMsInRange(from: Instant, to: Instant): Flow<Long>

    /** Sessions (open or closed) started from [from] on, newest first — Focus's history. */
    @Query("SELECT * FROM work_sessions WHERE startedAt >= :from OR endedAt IS NULL ORDER BY startedAt DESC")
    fun observeSince(from: Instant): Flow<List<WorkSessionEntity>>

    /** The session that ended most recently, at or before [at]. */
    @Query("SELECT * FROM work_sessions WHERE endedAt IS NOT NULL AND endedAt <= :at ORDER BY endedAt DESC LIMIT 1")
    suspend fun lastEndedBy(at: Instant): WorkSessionEntity?

    @Query("SELECT * FROM work_sessions WHERE needsReview = 1 ORDER BY startedAt DESC")
    fun observeNeedingReview(): Flow<List<WorkSessionEntity>>
}
