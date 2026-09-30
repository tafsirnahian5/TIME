package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.Category
import com.example.timeapp.data.CategoryKind
import kotlinx.coroutines.flow.Flow

/**
 * User-editable categories for both work and money — `kind` discriminates, matching the
 * one-table-two-purposes pattern `MoneyRecordEntity` and `ReviewEntity` already use.
 *
 * Records reference a category by id and resolve name/colour at read time, so renaming or
 * recolouring one updates every view at once without touching a single stored amount or
 * duration. Categories are archived, never deleted, so historical records keep their label.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long,
    val kind: CategoryKind,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val budgetMinor: Long? = null,
    /** A key into the app's own icon set (`CategoryIcons`); null shows the colour dot. */
    val icon: String? = null,
)

fun CategoryEntity.toDomain(): Category = Category(id, name, colorArgb, kind, archived, sortOrder, budgetMinor, icon)

fun Category.toEntity(): CategoryEntity = CategoryEntity(id, name, colorArgb, kind, archived, sortOrder, budgetMinor, icon)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    // Merge: every record pointing at [from] points at [into] instead.
    @Query("UPDATE entries SET categoryId = :into WHERE categoryId = :from")
    suspend fun remapEntries(from: Long, into: Long)

    @Query("UPDATE entries SET projectId = :into WHERE projectId = :from")
    suspend fun remapProjects(from: Long, into: Long)

    @Query("UPDATE money_records SET categoryId = :into WHERE categoryId = :from")
    suspend fun remapMoney(from: Long, into: Long)

    @Query("UPDATE habits SET categoryId = :into WHERE categoryId = :from")
    suspend fun remapHabits(from: Long, into: Long)

    @Query("SELECT * FROM categories WHERE kind = :kind AND archived = 0 ORDER BY sortOrder, id")
    fun observeActive(kind: CategoryKind): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    suspend fun all(): List<CategoryEntity>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}
