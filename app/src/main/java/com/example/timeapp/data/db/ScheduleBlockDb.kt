package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.ScheduleBlock
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * A task's *additional* blocks of reserved time (the PDF's "a 90-minute task can be split into
 * two 45-minute blocks"). The first block stays on the entry itself (`entries.startAt/endAt`),
 * so everything that reads one block keeps working; a task has rows here only while it also
 * has that first block (removing it promotes the earliest row — see EntryRepository.removeBlock).
 */
@Entity(
    tableName = "schedule_blocks",
    foreignKeys = [ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")],
)
data class ScheduleBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val startAt: Instant,
    val endAt: Instant,
    val isFixed: Boolean = false,
)

fun ScheduleBlockEntity.toDomain() = ScheduleBlock(id, entryId, startAt, endAt, isFixed)

@Dao
interface ScheduleBlockDao {
    @Query("SELECT * FROM schedule_blocks ORDER BY startAt")
    fun observeAll(): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks WHERE entryId = :entryId ORDER BY startAt")
    suspend fun forEntry(entryId: Long): List<ScheduleBlockEntity>

    @Query("SELECT * FROM schedule_blocks WHERE id = :id")
    suspend fun getById(id: Long): ScheduleBlockEntity?

    @Insert
    suspend fun insert(block: ScheduleBlockEntity): Long

    @Update
    suspend fun update(block: ScheduleBlockEntity)

    @Query("DELETE FROM schedule_blocks WHERE id = :id")
    suspend fun delete(id: Long)

    /** Reschedule replaces only *unused* future allocation (the PDF): blocks already begun stay. */
    @Query("DELETE FROM schedule_blocks WHERE entryId = :entryId AND startAt > :now")
    suspend fun deleteFuture(entryId: Long, now: Instant)
}
