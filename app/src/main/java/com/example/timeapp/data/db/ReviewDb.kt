package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.Review
import com.example.timeapp.data.ReviewPeriod
import java.time.Instant

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val period: ReviewPeriod,
    val periodKey: String,
    val note: String,
    val savedAt: Instant,
)

fun ReviewEntity.toDomain(): Review = Review(id, period, periodKey, note, savedAt)

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<ReviewEntity>>

    @Query("SELECT * FROM reviews WHERE period = :period AND periodKey = :periodKey LIMIT 1")
    suspend fun getForPeriod(period: ReviewPeriod, periodKey: String): ReviewEntity?

    @Insert
    suspend fun insert(review: ReviewEntity): Long

    @Update
    suspend fun update(review: ReviewEntity)
}
