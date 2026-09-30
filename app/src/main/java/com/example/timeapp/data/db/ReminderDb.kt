package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.Reminder
import com.example.timeapp.data.ReminderOwnerKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** One reminder per owner record — the unique index enforces the PDF's "one reminder per
 * task/event/plan" rule in the schema rather than in calling code. */
@Entity(
    tableName = "reminders",
    indices = [Index(value = ["ownerKind", "ownerId"], unique = true)],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerKind: ReminderOwnerKind,
    val ownerId: Long,
    val scheduledAt: Instant,
    val delivered: Boolean = false,
    /** How the user chose the time (see [com.example.timeapp.data.ReminderTiming]); all null
     * for a reminder set before timings existed, which falls back to the per-kind default. */
    val dayOffset: Int? = null,
    val localTime: String? = null,
    val minutesBefore: Int? = null,
) {
    val timing: com.example.timeapp.data.ReminderTiming? get() = com.example.timeapp.data.ReminderTiming.from(dayOffset, localTime, minutesBefore)
}

fun ReminderEntity.withTiming(t: com.example.timeapp.data.ReminderTiming?): ReminderEntity = when (t) {
    is com.example.timeapp.data.ReminderTiming.OnDay -> copy(dayOffset = t.dayOffset, localTime = t.time.toString(), minutesBefore = null)
    is com.example.timeapp.data.ReminderTiming.BeforeStart -> copy(dayOffset = null, localTime = null, minutesBefore = t.minutes)
    null -> copy(dayOffset = null, localTime = null, minutesBefore = null)
}

fun ReminderEntity.toDomain(): Reminder = Reminder(id, ownerKind, ownerId, scheduledAt, delivered)

fun Reminder.toEntity(): ReminderEntity = ReminderEntity(id, ownerKind, ownerId, scheduledAt, delivered)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY scheduledAt")
    fun observeAll(): Flow<List<ReminderEntity>>

    /** Everything the scheduler still owes an alarm for — the reconcile source of truth. */
    @Query("SELECT * FROM reminders WHERE delivered = 0 ORDER BY scheduledAt")
    suspend fun pending(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE ownerKind = :kind AND ownerId = :ownerId LIMIT 1")
    suspend fun getForOwner(kind: ReminderOwnerKind, ownerId: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE ownerKind = :kind AND ownerId = :ownerId LIMIT 1")
    fun observeForOwner(kind: ReminderOwnerKind, ownerId: Long): Flow<ReminderEntity?>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM reminders WHERE delivered = 0")
    suspend fun pendingCount(): Int
}
