package com.example.timeapp.data.db

import com.example.timeapp.data.clampedTitle
import com.example.timeapp.data.clampedNote
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.Bookmark
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folder: String,
    val favourite: Boolean = false,
    val note: String? = null,
    val addedAt: java.time.Instant? = null,
    val archivedAt: java.time.Instant? = null,
)

fun BookmarkEntity.toDomain(): Bookmark = Bookmark(id, title, url, folder, favourite, note, addedAt, archivedAt)

fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(id, title.clampedTitle(), url.clampedNote(), folder, favourite, note?.clampedNote(), addedAt, archivedAt)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY id")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    /** One statement for a whole import — Room runs a list insert as one transaction. */
    @Insert
    suspend fun insertAll(bookmarks: List<BookmarkEntity>)

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun count(): Int

    @Query("UPDATE bookmarks SET archivedAt = :at WHERE id = :id")
    suspend fun setArchived(id: Long, at: java.time.Instant?)

    @Query("UPDATE bookmarks SET folder = :folder WHERE id = :id")
    suspend fun setFolder(id: Long, folder: String)
}

/** The PDF's TaskBookmark: a unique task↔bookmark pair. Deleting either side removes it. */
@androidx.room.Entity(
    tableName = "task_bookmarks",
    primaryKeys = ["entryId", "bookmarkId"],
    indices = [androidx.room.Index("bookmarkId")],
    foreignKeys = [
        androidx.room.ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = androidx.room.ForeignKey.CASCADE),
        androidx.room.ForeignKey(entity = BookmarkEntity::class, parentColumns = ["id"], childColumns = ["bookmarkId"], onDelete = androidx.room.ForeignKey.CASCADE),
    ],
)
data class TaskBookmarkEntity(val entryId: Long, val bookmarkId: Long)

@Dao
interface TaskBookmarkDao {
    @Query("SELECT * FROM task_bookmarks")
    fun observeAll(): Flow<List<TaskBookmarkEntity>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insert(link: TaskBookmarkEntity)

    @Query("DELETE FROM task_bookmarks WHERE entryId = :entryId AND bookmarkId = :bookmarkId")
    suspend fun delete(entryId: Long, bookmarkId: Long)
}

