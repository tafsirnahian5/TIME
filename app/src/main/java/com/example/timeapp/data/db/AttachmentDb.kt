package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** A receipt: an app-managed copy of a file, attached to a ledger row (see `Attachments`). */
@Entity(
    tableName = "attachments",
    indices = [Index("recordId")],
    foreignKeys = [ForeignKey(entity = MoneyRecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    /** File name inside the app's attachments folder — generated, never the user's. */
    val fileName: String,
    val mime: String,
    val sizeBytes: Long,
    val sha256: String,
    val addedAt: Instant,
    /** The name it had when picked, for display only. */
    val displayName: String?,
)

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments ORDER BY addedAt")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE recordId = :recordId ORDER BY addedAt")
    suspend fun forRecord(recordId: Long): List<AttachmentEntity>

    @Query("SELECT fileName FROM attachments")
    suspend fun allFileNames(): List<String>

    @Insert
    suspend fun insert(a: AttachmentEntity): Long

    @Upsert
    suspend fun upsert(a: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)
}
