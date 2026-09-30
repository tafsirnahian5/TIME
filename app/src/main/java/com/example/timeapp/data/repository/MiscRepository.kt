package com.example.timeapp.data.repository

import com.example.timeapp.data.Bookmark
import com.example.timeapp.data.db.AchievementDao
import com.example.timeapp.data.db.BookmarkDao
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction

/** Achievements stay read-only by design (G1). Bookmarks (Links) get add/update/delete below. */
class MiscRepository(
    private val achievementDao: AchievementDao,
    private val bookmarkDao: BookmarkDao,
    private val db: com.example.timeapp.data.db.TimeDatabase,
) {
    fun observeBookmarks(): Flow<List<Bookmark>> = bookmarkDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addBookmark(bookmark: Bookmark) = bookmarkDao.insert(bookmark.toEntity())
    suspend fun updateBookmark(bookmark: Bookmark) = bookmarkDao.update(bookmark.toEntity())
    suspend fun deleteBookmark(id: Long) = bookmarkDao.delete(id)
    suspend fun setBookmarkArchived(id: Long, at: java.time.Instant?) = bookmarkDao.setArchived(id, at)
    suspend fun setBookmarkFolder(id: Long, folder: String) = bookmarkDao.setFolder(id, folder)

    /** Copies a link (and its task links) for Undo. */
    suspend fun snapshotBookmark(id: Long) = db.withTransaction {
        com.example.timeapp.data.db.BookmarkRowsSnapshot.capture(db.openHelper.writableDatabase, id)
    }

    suspend fun restoreBookmark(snap: com.example.timeapp.data.db.BookmarkRowsSnapshot) =
        db.withTransaction { snap.restore(db.openHelper.writableDatabase) }

    /** All-or-nothing: a failure part-way leaves no half-imported folder behind. */
    suspend fun importBookmarks(bookmarks: List<Bookmark>) = bookmarkDao.insertAll(bookmarks.map { it.toEntity().copy(id = 0) })
}
