package com.example.timeapp.data.db

import com.example.timeapp.data.clampedTitle
import com.example.timeapp.data.clampedNote
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.CollectionKind
import com.example.timeapp.data.Idea
import com.example.timeapp.data.IdeaCollection
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "idea_collections")
data class IdeaCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CollectionKind,
    val icon: String,
    val iconColorArgb: Long,
    val count: Int,
    val doneCount: Int = 0,
)

fun IdeaCollectionEntity.toDomain(): IdeaCollection = IdeaCollection(id, name, kind, icon, iconColorArgb, count, doneCount)

fun IdeaCollection.toEntity(): IdeaCollectionEntity = IdeaCollectionEntity(id, name, kind, icon, iconColorArgb, count, doneCount)

@Dao
interface IdeaCollectionDao {
    @Query("SELECT * FROM idea_collections ORDER BY id")
    fun observeAll(): Flow<List<IdeaCollectionEntity>>

    @Query("SELECT * FROM idea_collections WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): IdeaCollectionEntity?

    @Insert
    suspend fun insert(collection: IdeaCollectionEntity): Long

    @Query("SELECT COUNT(*) FROM idea_collections")
    suspend fun count(): Int

    @Query("SELECT * FROM idea_collections WHERE id = :id")
    suspend fun getById(id: Long): IdeaCollectionEntity?

    @Query("SELECT * FROM idea_collections WHERE kind = :kind ORDER BY id LIMIT 1")
    suspend fun firstOfKind(kind: CollectionKind): IdeaCollectionEntity?

    @Query("UPDATE idea_collections SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE ideas SET collectionId = :to WHERE collectionId = :from")
    suspend fun moveIdeas(from: Long, to: Long)

    @Query("DELETE FROM idea_collections WHERE id = :id")
    suspend fun delete(id: Long)

    /** Deletes a collection after moving its ideas to [to], in one transaction — no idea is lost. */
    @androidx.room.Transaction
    suspend fun deleteMovingIdeas(id: Long, to: Long) {
        moveIdeas(id, to)
        delete(id)
    }
}

@Entity(tableName = "ideas")
data class IdeaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long,
    val title: String,
    val body: String? = null,
    val isDone: Boolean = false,
    val costsMoneyEstimateMinor: Long? = null,
    val promotedToPlan: Boolean = false,
    val touchedLabel: String? = null,
)

fun IdeaEntity.toDomain(): Idea = Idea(id, collectionId, title, body, isDone, costsMoneyEstimateMinor, promotedToPlan, touchedLabel)

fun Idea.toEntity(): IdeaEntity = IdeaEntity(id, collectionId, title.clampedTitle(), body?.clampedNote(), isDone, costsMoneyEstimateMinor, promotedToPlan, touchedLabel)

@Dao
interface IdeaDao {
    @Query("SELECT * FROM ideas ORDER BY id")
    fun observeAll(): Flow<List<IdeaEntity>>

    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun getById(id: Long): IdeaEntity?

    @Insert
    suspend fun insert(idea: IdeaEntity): Long

    @Update
    suspend fun update(idea: IdeaEntity)

    @Query("DELETE FROM ideas WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM ideas")
    suspend fun count(): Int
}
