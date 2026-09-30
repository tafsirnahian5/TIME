package com.example.timeapp.data.repository

import androidx.room.withTransaction
import com.example.timeapp.data.Commitment
import com.example.timeapp.data.MAX_COMMITMENTS
import com.example.timeapp.data.db.CommitmentEntity
import com.example.timeapp.data.db.TimeDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * Today's commitments — the PDF's "choose up to three commitments" at the start of the day,
 * kept as a record: adding stamps the time, taking one off keeps the row with when and why
 * ("allowing additions with timestamps and explicitly noted cancellations").
 */
class CommitmentRepository(private val db: TimeDatabase) {
    private val dao get() = db.commitmentDao()

    private fun CommitmentEntity.toDomain() = Commitment(date, entryId, committedAt, removedAt, removeNote)

    fun observeForDate(date: LocalDate): Flow<List<Commitment>> = dao.observeForDate(date).map { list -> list.map { it.toDomain() } }

    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Commitment>> = dao.observeBetween(from, to).map { list -> list.map { it.toDomain() } }

    /** Commits [entryId] for [date]; false when the day already has [MAX_COMMITMENTS]. */
    suspend fun commit(date: LocalDate, entryId: Long, now: Instant = Instant.now()): Boolean = db.withTransaction {
        val existing = dao.get(date, entryId)
        if (existing?.removedAt == null && existing != null) return@withTransaction true
        if (dao.activeCount(date) >= MAX_COMMITMENTS) return@withTransaction false
        if (existing != null) dao.update(existing.copy(removedAt = null, removeNote = null, committedAt = now))
        else dao.insert(CommitmentEntity(date = date, entryId = entryId, committedAt = now, position = dao.activeCount(date)))
        true
    }

    /** Takes a commitment off the day, keeping when and why. */
    suspend fun remove(date: LocalDate, entryId: Long, note: String?, now: Instant = Instant.now()) = db.withTransaction {
        dao.get(date, entryId)?.let { dao.update(it.copy(removedAt = now, removeNote = note?.trim()?.takeIf { n -> n.isNotEmpty() })) }
    }
}
