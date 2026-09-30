package com.example.timeapp.data.db

import com.example.timeapp.data.clampedTitle
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.Subtask
import kotlinx.coroutines.flow.Flow

/** "Next steps" checklist item on a Task Detail screen — not in the PDF's data model (which has
 * no subtask concept), added for Task Detail's checklist per the T!ME Board/Task-Detail design. */
@Entity(tableName = "subtasks")
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int = 0,
)

fun SubtaskEntity.toDomain(): Subtask = Subtask(id, entryId, title, done, sortOrder)
fun Subtask.toEntity(): SubtaskEntity = SubtaskEntity(id, entryId, title.clampedTitle(), done, sortOrder)

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtasks WHERE entryId = :entryId ORDER BY sortOrder, id")
    fun observeForEntry(entryId: Long): Flow<List<SubtaskEntity>>

    @Insert
    suspend fun insert(subtask: SubtaskEntity): Long

    @Update
    suspend fun update(subtask: SubtaskEntity)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun delete(id: Long)

    @androidx.room.Query("SELECT MAX(sortOrder) FROM subtasks WHERE entryId = :entryId")
    suspend fun maxSortOrder(entryId: Long): Int?

    @Query("DELETE FROM subtasks WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: Long)
}
