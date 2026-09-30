package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A reusable tag (the PDF's Tag). Names are stored normalised — trimmed, lower-case, no
 * leading `#` — and unique, so "Android", "#android" and "android " are one tag. */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** The PDF's TaskTag: a unique task↔tag link. Deleting either side removes the link. */
@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entryId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class EntryTagEntity(val entryId: Long, val tagId: Long)

/** One row of the entry→tag-name join, for resolving tags onto entries in a single query. */
data class EntryTagName(val entryId: Long, val name: String)

@Dao
interface TagDao {
    @Query("SELECT entry_tags.entryId AS entryId, tags.name AS name FROM entry_tags JOIN tags ON tags.id = entry_tags.tagId ORDER BY tags.name")
    fun observeEntryTagNames(): Flow<List<EntryTagName>>

    /** Every tag in use, most-used first — the suggestion list on the task form. */
    @Query("SELECT tags.name FROM tags JOIN entry_tags ON entry_tags.tagId = tags.id GROUP BY tags.id ORDER BY COUNT(*) DESC, tags.name")
    fun observeNamesInUse(): Flow<List<String>>

    @Query("SELECT id FROM tags WHERE name = :name")
    suspend fun idFor(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: EntryTagEntity)

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    suspend fun clearLinks(entryId: Long)

    /** A generated repeat occurrence inherits its template's tags. */
    @Query("INSERT OR IGNORE INTO entry_tags (entryId, tagId) SELECT :toEntryId, tagId FROM entry_tags WHERE entryId = :fromEntryId")
    suspend fun copyLinks(fromEntryId: Long, toEntryId: Long)

    /** Tags nothing uses any more — kept out of suggestions, removed so they don't pile up. */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM entry_tags)")
    suspend fun deleteUnused()
}

/** "#Android " → "android". Blank after normalising means "not a tag". */
fun normaliseTag(raw: String): String = raw.trim().trimStart('#').trim().lowercase().replace(Regex("\\s+"), "-")
