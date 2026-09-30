package com.example.timeapp.data.repository

import com.example.timeapp.data.INBOX_KEEP_DAYS
import com.example.timeapp.data.INBOX_KEEP_MAX
import com.example.timeapp.data.InboxItem
import com.example.timeapp.data.InboxKind
import com.example.timeapp.data.db.InboxItemEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The bell's list. Every sender records here at the moment it decides to notify — before
 * asking Android whether it may post — so a reminder isn't lost when the permission is off or
 * the shade was cleared. Muted channels and the master switch still keep things out: muting is
 * the user saying "don't tell me".
 */
class InboxRepository(private val database: TimeDatabase) {
    private val dao get() = database.inboxDao()

    suspend fun record(kind: InboxKind, title: String, body: String, route: String?, at: Instant = Instant.now()) {
        runCatching {
            dao.insert(InboxItemEntity(kind = kind.name, title = title, body = body, route = route, createdAt = at, readAt = null))
            dao.prune(at.minus(INBOX_KEEP_DAYS, ChronoUnit.DAYS), INBOX_KEEP_MAX)
        } // never let the list stop a reminder from being posted
    }

    fun observeRecent(): Flow<List<InboxItem>> = dao.observeRecent(INBOX_KEEP_MAX).map { rows -> rows.map { it.toDomain() } }
    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()
    suspend fun markRead(id: Long) = dao.markRead(id, Instant.now())
    suspend fun markAllRead() = dao.markAllRead(Instant.now())
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.deleteAll()
}
