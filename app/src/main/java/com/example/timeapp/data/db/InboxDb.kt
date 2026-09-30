package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.timeapp.data.InboxItem
import com.example.timeapp.data.InboxKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * One line in the app's own notification list (the bell). Written when T!ME decides to notify —
 * so a reminder is kept here even when Android's permission is off or the shade was cleared.
 * [title]/[body] are the words the notification used, in the language it was sent in: this is a
 * record of what was said, like the shade's own history, not a live view of the record.
 */
@Entity(tableName = "inbox_items", indices = [Index("createdAt")])
data class InboxItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val body: String,
    /** An in-app route to open on tap, or null when there's nothing to open. */
    val route: String?,
    val createdAt: Instant,
    val readAt: Instant?,
)

fun InboxItemEntity.toDomain() = InboxItem(
    id = id,
    kind = runCatching { InboxKind.valueOf(kind) }.getOrDefault(InboxKind.OTHER),
    title = title,
    body = body,
    route = route,
    createdAt = createdAt,
    read = readAt != null,
)

@Dao
interface InboxDao {
    @Insert
    suspend fun insert(item: InboxItemEntity): Long

    @Query("SELECT * FROM inbox_items ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<InboxItemEntity>>

    @Query("SELECT COUNT(*) FROM inbox_items WHERE readAt IS NULL")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE inbox_items SET readAt = :at WHERE id = :id AND readAt IS NULL")
    suspend fun markRead(id: Long, at: Instant)

    @Query("UPDATE inbox_items SET readAt = :at WHERE readAt IS NULL")
    suspend fun markAllRead(at: Instant)

    @Query("DELETE FROM inbox_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM inbox_items")
    suspend fun deleteAll()

    /** Keeps the list bounded: older than [before], or beyond the newest [keep], goes. */
    @Query(
        "DELETE FROM inbox_items WHERE createdAt < :before OR id NOT IN " +
            "(SELECT id FROM inbox_items ORDER BY createdAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun prune(before: Instant, keep: Int)
}
