package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** The PDF's TaskDependency: [taskId] waits on [prerequisiteId]. Unique pair; cycles are
 * refused before insert (see `dependencyWouldCycle`). Deleting either task removes the link. */
@Entity(
    tableName = "task_dependencies",
    primaryKeys = ["taskId", "prerequisiteId"],
    indices = [Index("prerequisiteId")],
    foreignKeys = [
        ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["prerequisiteId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class TaskDependencyEntity(val taskId: Long, val prerequisiteId: Long)

@Dao
interface DependencyDao {
    @Query("SELECT * FROM task_dependencies")
    fun observeAll(): Flow<List<TaskDependencyEntity>>

    @Query("SELECT * FROM task_dependencies")
    suspend fun all(): List<TaskDependencyEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: TaskDependencyEntity)

    @Query("DELETE FROM task_dependencies WHERE taskId = :taskId AND prerequisiteId = :prerequisiteId")
    suspend fun delete(taskId: Long, prerequisiteId: Long)
}
