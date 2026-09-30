package com.example.timeapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.data.FocusRuntime
import com.example.timeapp.data.parseLaps
import com.example.timeapp.data.serializeLaps
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The one work timer's runtime — a single row (id 1), the PDF's "singleton runtime record".
 * Kept in Room rather than preferences so a session and the timer that owns it always change
 * in one transaction. See [FocusRuntime].
 */
@Entity(tableName = "focus_runtime")
data class FocusRuntimeEntity(
    @PrimaryKey val id: Int = SINGLETON,
    val mode: FocusMode,
    val phase: FocusPhase,
    val running: Boolean,
    val entryId: Long?,
    val targetMs: Long?,
    val accumulatedMs: Long,
    val stretchStartedAt: Instant?,
    val stretchAnchor: Long?,
    val bootCount: Int?,
    val cycles: Int,
    val laps: String,
    val awaitingChoice: Boolean,
    val countdownMinutes: Int,
    val workMinutes: Int,
    val breakMinutes: Int,
    val longBreakMinutes: Int,
    val cyclesPerLongBreak: Int,
    val autoStart: Boolean,
) {
    companion object {
        const val SINGLETON = 1
    }
}

fun FocusRuntimeEntity.toDomain() = FocusRuntime(
    mode, phase, running, entryId, targetMs, accumulatedMs, stretchStartedAt, stretchAnchor, bootCount, cycles,
    parseLaps(laps), awaitingChoice, countdownMinutes, workMinutes, breakMinutes, longBreakMinutes, cyclesPerLongBreak, autoStart,
)

fun FocusRuntime.toEntity() = FocusRuntimeEntity(
    FocusRuntimeEntity.SINGLETON, mode, phase, running, entryId, targetMs, accumulatedMs, stretchStartedAt, stretchAnchor,
    bootCount, cycles, serializeLaps(), awaitingChoice, countdownMinutes, workMinutes, breakMinutes, longBreakMinutes,
    cyclesPerLongBreak, autoStart,
)

/**
 * A manual change to a session's times, kept with its reason and the values it replaced
 * (the PDF: "record manual corrections with their reason and original values").
 */
@Entity(
    tableName = "session_corrections",
    indices = [Index("sessionId")],
    foreignKeys = [ForeignKey(entity = WorkSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
)
data class SessionCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val at: Instant,
    val oldStartedAt: Instant,
    val oldEndedAt: Instant?,
    val newStartedAt: Instant,
    val newEndedAt: Instant?,
    val reason: String,
)

/** A named timer setup: a countdown length, or Pomodoro work/break lengths. */
@Entity(tableName = "timer_presets")
data class TimerPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: FocusMode,
    val minutes: Int,
    val breakMinutes: Int? = null,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_runtime WHERE id = 1")
    suspend fun runtime(): FocusRuntimeEntity?

    @Query("SELECT * FROM focus_runtime WHERE id = 1")
    fun observeRuntime(): Flow<FocusRuntimeEntity?>

    @Upsert
    suspend fun saveRuntime(runtime: FocusRuntimeEntity)

    @Insert
    suspend fun insertCorrection(correction: SessionCorrectionEntity): Long

    @Query("SELECT * FROM session_corrections WHERE sessionId = :sessionId ORDER BY at DESC")
    fun observeCorrections(sessionId: Long): Flow<List<SessionCorrectionEntity>>

    @Query("SELECT * FROM timer_presets ORDER BY sortOrder, id")
    fun observePresets(): Flow<List<TimerPresetEntity>>

    @Insert
    suspend fun insertPreset(preset: TimerPresetEntity): Long

    @Query("DELETE FROM timer_presets WHERE id = :id")
    suspend fun deletePreset(id: Long)
}
