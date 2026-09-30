package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.timeapp.data.Achievement
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val icon: String,
    val earnedLabel: String? = null,
    val progressLabel: String? = null,
    val progressFraction: Float? = null,
)

fun AchievementEntity.toDomain(): Achievement = Achievement(title, icon, earnedLabel, progressLabel, progressFraction)

fun Achievement.toEntity(): AchievementEntity = AchievementEntity(
    title = title, icon = icon, earnedLabel = earnedLabel, progressLabel = progressLabel, progressFraction = progressFraction,
)

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY id")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Insert
    suspend fun insert(achievement: AchievementEntity): Long

    @Query("SELECT COUNT(*) FROM achievements")
    suspend fun count(): Int
}
