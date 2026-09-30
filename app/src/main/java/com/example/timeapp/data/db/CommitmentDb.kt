package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Today's commitments: the few tasks chosen for a day (the PDF's CommitmentSnapshot). Each is
 * kept with when it was committed; taking one back keeps the row with [removedAt] and an
 * optional note, so the day's record is honest ("allowing additions with timestamps and
 * explicitly noted cancellations").
 */
@Entity(
    tableName = "commitments",
    indices = [Index(value = ["date", "entryId"], unique = true), Index("entryId")],
    foreignKeys = [ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
)
data class CommitmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val entryId: Long,
    val committedAt: Instant,
    val removedAt: Instant? = null,
    val removeNote: String? = null,
    val position: Int = 0,
)

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM commitments WHERE date = :date ORDER BY position, id")
    fun observeForDate(date: LocalDate): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE date >= :from AND date < :to ORDER BY date, position")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE date = :date AND entryId = :entryId")
    suspend fun get(date: LocalDate, entryId: Long): CommitmentEntity?

    @Query("SELECT COUNT(*) FROM commitments WHERE date = :date AND removedAt IS NULL")
    suspend fun activeCount(date: LocalDate): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(c: CommitmentEntity): Long

    @Update
    suspend fun update(c: CommitmentEntity)
}
