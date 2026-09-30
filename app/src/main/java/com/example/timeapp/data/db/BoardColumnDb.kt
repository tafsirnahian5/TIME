package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.BoardColumn
import com.example.timeapp.data.EntryStatus
import kotlinx.coroutines.flow.Flow

/** A Board column: a user-named lane that always stands for one [status], so timers and
 * reports keep their meaning however the board is arranged (the PDF's custom columns). */
@Entity(tableName = "board_columns")
data class BoardColumnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val status: EntryStatus,
    val sortOrder: Int,
    val wipLimit: Int? = null,
)

fun BoardColumnEntity.toDomain() = BoardColumn(id, name, status, sortOrder, wipLimit)
fun BoardColumn.toEntity() = BoardColumnEntity(id, name, status, sortOrder, wipLimit)

@Dao
interface BoardColumnDao {
    @Query("SELECT * FROM board_columns ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<BoardColumnEntity>>

    @Query("SELECT * FROM board_columns ORDER BY sortOrder, id")
    suspend fun all(): List<BoardColumnEntity>

    @Insert
    suspend fun insert(column: BoardColumnEntity): Long

    @Update
    suspend fun update(column: BoardColumnEntity)

    @Query("DELETE FROM board_columns WHERE id = :id")
    suspend fun delete(id: Long)

    /** Cards that sat in a deleted column fall back to their status's first column. */
    @Query("UPDATE entries SET columnId = NULL WHERE columnId = :columnId")
    suspend fun releaseCards(columnId: Long)
}
