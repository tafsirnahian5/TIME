package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.timeapp.data.EntryStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * One status change of a task — the PDF's "inspect state transitions and interruption
 * reasons". Written centrally by EntryRepository whenever a status actually changes, so no
 * screen can move a task without it being recorded. An interruption is simply a transition
 * out of Doing; a switch also names the task switched to ([relatedEntryId]) and carries the
 * resume note ([note]). Deleted with its task.
 */
@Entity(
    tableName = "entry_events",
    indices = [Index("entryId"), Index("at")],
    foreignKeys = [
        ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class EntryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val at: Instant,
    val fromStatus: EntryStatus?,
    val toStatus: EntryStatus,
    val note: String? = null,
    val relatedEntryId: Long? = null,
)

@Dao
interface EntryEventDao {
    @Insert
    suspend fun insert(event: EntryEventEntity): Long

    @Query("SELECT * FROM entry_events WHERE entryId = :entryId ORDER BY at DESC, id DESC")
    fun observeForEntry(entryId: Long): Flow<List<EntryEventEntity>>

    @Query("SELECT * FROM entry_events WHERE at >= :from AND at < :to ORDER BY at")
    fun observeInRange(from: Instant, to: Instant): Flow<List<EntryEventEntity>>
}
