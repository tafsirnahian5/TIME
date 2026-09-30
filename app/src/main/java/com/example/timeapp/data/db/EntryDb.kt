package com.example.timeapp.data.db

import com.example.timeapp.data.clampedTitle
import com.example.timeapp.data.clampedNote
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.WorkCategory
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

// Series lookups run on every expansion and series edit (Phase 16 index).
@Entity(tableName = "entries", indices = [androidx.room.Index("seriesId")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: EntryKind,
    val title: String,
    val categoryName: String? = null,
    val categoryColor: Long? = null,
    val status: EntryStatus,
    val meta: String? = null,
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val isFixed: Boolean = false,
    val dueOn: LocalDate? = null,
    val estimateMs: Long? = null,
    val resumeNote: String? = null,
    val description: String? = null,
    val recurrence: com.example.timeapp.data.Recurrence? = null,
    val seriesId: Long? = null,
    val priority: com.example.timeapp.data.Priority = com.example.timeapp.data.Priority.NORMAL,
    val categoryId: Long? = null,
    val projectId: Long? = null,
    /** Position within its Board column, lowest first; ties fall back to id. Only ever set by
     * an explicit move — the Board never reshuffles itself (PDF: board order is user choice). */
    @androidx.room.ColumnInfo(defaultValue = "0") val boardOrder: Int = 0,
    val columnId: Long? = null,
    val icsUid: String? = null,
    /** When it last became Done — set and cleared only by `EntryRepository.update`, so reports
     * count a completion in the period it happened rather than by its due date. */
    val completedAt: Instant? = null,
    /** Archived: hidden from the List, Board, calendar and pickers, kept in reports and search.
     * Written only through `EntryDao.setArchived` — a plain update never changes it. */
    val archivedAt: Instant? = null,
)

fun EntryEntity.toDomain(): Entry = Entry(
    id = id,
    kind = kind,
    title = title,
    category = if (categoryName != null && categoryColor != null) WorkCategory(categoryName, categoryColor) else null,
    status = status,
    meta = meta,
    startAt = startAt,
    endAt = endAt,
    isFixed = isFixed,
    dueOn = dueOn,
    estimateMs = estimateMs,
    resumeNote = resumeNote,
    description = description,
    recurrence = recurrence,
    seriesId = seriesId,
    priority = priority,
    categoryId = categoryId,
    projectId = projectId,
    boardOrder = boardOrder,
    columnId = columnId,
    icsUid = icsUid,
    completedAt = completedAt,
    archivedAt = archivedAt,
)

fun Entry.toEntity(): EntryEntity = EntryEntity(
    id = id,
    kind = kind,
    title = title.clampedTitle(),
    categoryName = category?.name,
    categoryColor = category?.color,
    // Overdue is derived from the due date on read. Screens hand back the entries they were
    // shown, so an edit used to write "OVERDUE" to disk — and a task whose due date was then
    // moved later stayed Overdue for good.
    status = if (status == EntryStatus.OVERDUE) EntryStatus.PLANNED else status,
    meta = meta,
    startAt = startAt,
    endAt = endAt,
    isFixed = isFixed,
    dueOn = dueOn,
    estimateMs = estimateMs,
    resumeNote = resumeNote?.clampedNote(),
    description = description?.clampedNote(),
    recurrence = recurrence,
    seriesId = seriesId,
    priority = priority,
    categoryId = categoryId,
    projectId = projectId,
    boardOrder = boardOrder,
    columnId = columnId,
    icsUid = icsUid,
    completedAt = completedAt,
    archivedAt = archivedAt,
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY id")
    fun observeAll(): Flow<List<EntryEntity>>

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): EntryEntity?

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int

    /** Every entry belonging to a repeat series, for the bounded expander. */
    @Query("SELECT * FROM entries WHERE seriesId IS NOT NULL")
    suspend fun allInSeries(): List<EntryEntity>

    @Query("SELECT COALESCE(MAX(boardOrder), 0) FROM entries")
    suspend fun maxBoardOrder(): Int

    @Query("UPDATE entries SET boardOrder = :order WHERE id = :id")
    suspend fun setBoardOrder(id: Long, order: Int)

    @Query("SELECT * FROM entries WHERE seriesId = :seriesId")
    suspend fun inSeries(seriesId: Long): List<EntryEntity>

    @Query("SELECT * FROM entries")
    suspend fun all(): List<EntryEntity>

        @Query("UPDATE entries SET archivedAt = :at WHERE id = :id")
    suspend fun setArchived(id: Long, at: Instant?)
}
