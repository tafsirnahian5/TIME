package com.example.timeapp.data.repository

import com.example.timeapp.data.clampedNote
import kotlinx.coroutines.flow.map

import com.example.timeapp.data.Review
import com.example.timeapp.data.ReviewPeriod
import com.example.timeapp.data.db.ReviewEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import java.time.Instant

/** The only stored state Daily/Weekly Review need — every number on those screens is a
 * live view over other repositories; this just holds the free-text reflection. */
class ReviewRepository(private val db: TimeDatabase) {
    private val dao get() = db.reviewDao()

    fun observeAll(): kotlinx.coroutines.flow.Flow<List<Review>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getReview(period: ReviewPeriod, periodKey: String): Review? = dao.getForPeriod(period, periodKey)?.toDomain()

    suspend fun saveReview(period: ReviewPeriod, periodKey: String, note: String) {
        val existing = dao.getForPeriod(period, periodKey)
        if (existing != null) {
            dao.update(existing.copy(note = note.clampedNote(), savedAt = Instant.now()))
        } else {
            dao.insert(ReviewEntity(period = period, periodKey = periodKey, note = note.clampedNote(), savedAt = Instant.now()))
        }
    }
}
