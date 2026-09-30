package com.example.timeapp.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Raw row copies for Undo: the exact column values of rows about to be deleted or rewritten,
 * so putting them back restores them byte for byte, ids included (every link to them by id
 * keeps working). Used only inside a Room transaction, on the transaction's own connection.
 */
object RowCopy {
    /** Ids are longs, so inlining them is safe; SQLite's variable limit is not a concern. */
    fun inList(ids: Collection<Long>): String = ids.joinToString(",", "(", ")").let { if (ids.isEmpty()) "(NULL)" else it }

    fun read(db: SupportSQLiteDatabase, sql: String): List<ContentValues> =
        db.query(sql).use { c -> buildList { while (c.moveToNext()) add(values(c)) } }

    fun ids(db: SupportSQLiteDatabase, sql: String): Set<Long> =
        db.query(sql).use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }

    fun exists(db: SupportSQLiteDatabase, table: String, id: Long): Boolean =
        db.query("SELECT 1 FROM $table WHERE id = $id").use { it.moveToFirst() }

    fun insert(db: SupportSQLiteDatabase, table: String, rows: List<ContentValues>, ignoreExisting: Boolean = false) {
        val conflict = if (ignoreExisting) SQLiteDatabase.CONFLICT_IGNORE else SQLiteDatabase.CONFLICT_ABORT
        rows.forEach { db.insert(table, conflict, ContentValues(it)) }
    }

    private fun values(c: Cursor) = ContentValues().apply {
        for (i in 0 until c.columnCount) {
            val name = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> putNull(name)
                Cursor.FIELD_TYPE_INTEGER -> put(name, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> put(name, c.getDouble(i))
                Cursor.FIELD_TYPE_BLOB -> put(name, c.getBlob(i))
                else -> put(name, c.getString(i))
            }
        }
    }
}

/**
 * A task (with its series siblings) as it was: the rows, and everything that hangs off them.
 * [seriesIds] lets a restore also remove occurrences the change generated afterwards.
 */
class EntryRowsSnapshot internal constructor(
    val ids: Set<Long>,
    val seriesIds: Set<Long>,
    internal val entries: List<ContentValues>,
    internal val tags: List<ContentValues>,
    internal val entryTags: List<ContentValues>,
    internal val events: List<ContentValues>,
    internal val dependencies: List<ContentValues>,
    internal val commitments: List<ContentValues>,
    internal val taskBookmarks: List<ContentValues>,
    internal val reminders: List<ContentValues>,
    internal val sessions: List<ContentValues>,
    internal val corrections: List<ContentValues>,
    internal val subtasks: List<ContentValues>,
    internal val blocks: List<ContentValues> = emptyList(),
) {
    companion object {
        private const val ENTRY_OWNERS = "('TASK','EVENT')"

        fun capture(db: SupportSQLiteDatabase, ids: Set<Long>, seriesIds: Set<Long>): EntryRowsSnapshot {
            val list = RowCopy.inList(ids)
            val sessions = RowCopy.read(db, "SELECT * FROM work_sessions WHERE entryId IN $list")
            val sessionIds = sessions.mapNotNull { it.getAsLong("id") }
            return EntryRowsSnapshot(
                ids = ids,
                seriesIds = seriesIds,
                entries = RowCopy.read(db, "SELECT * FROM entries WHERE id IN $list"),
                tags = RowCopy.read(db, "SELECT * FROM tags WHERE id IN (SELECT tagId FROM entry_tags WHERE entryId IN $list)"),
                entryTags = RowCopy.read(db, "SELECT * FROM entry_tags WHERE entryId IN $list"),
                events = RowCopy.read(db, "SELECT * FROM entry_events WHERE entryId IN $list"),
                dependencies = RowCopy.read(db, "SELECT * FROM task_dependencies WHERE taskId IN $list OR prerequisiteId IN $list"),
                commitments = RowCopy.read(db, "SELECT * FROM commitments WHERE entryId IN $list"),
                taskBookmarks = RowCopy.read(db, "SELECT * FROM task_bookmarks WHERE entryId IN $list"),
                reminders = RowCopy.read(db, "SELECT * FROM reminders WHERE ownerKind IN $ENTRY_OWNERS AND ownerId IN $list"),
                sessions = sessions,
                corrections = RowCopy.read(db, "SELECT * FROM session_corrections WHERE sessionId IN ${RowCopy.inList(sessionIds)}"),
                subtasks = RowCopy.read(db, "SELECT * FROM subtasks WHERE entryId IN $list"),
                blocks = RowCopy.read(db, "SELECT * FROM schedule_blocks WHERE entryId IN $list"),
            )
        }
    }

    fun titleOf(id: Long): String = entries.firstOrNull { it.getAsLong("id") == id }?.getAsString("title").orEmpty()

    /**
     * Puts every row back as it was. Rows the change created (a regenerated occurrence) are
     * removed; rows it deleted return with their ids. Links to things deleted since (another
     * task, a bookmark) are left out rather than failing the whole undo. Work sessions and
     * subtasks are only re-added, never overwritten, so time tracked since is kept.
     */
    fun restore(db: SupportSQLiteDatabase) {
        val current = ids + RowCopy.ids(db, "SELECT id FROM entries WHERE seriesId IN ${RowCopy.inList(seriesIds)}")
        val created = current - ids
        val all = RowCopy.inList(current)
        db.execSQL("DELETE FROM reminders WHERE ownerKind IN $ENTRY_OWNERS AND ownerId IN $all")
        if (created.isNotEmpty()) {
            val createdList = RowCopy.inList(created)
            db.execSQL("DELETE FROM work_sessions WHERE entryId IN $createdList")
            db.execSQL("DELETE FROM subtasks WHERE entryId IN $createdList")
        }
        // Cascades clear tags, events, dependencies, commitments and link rows; the copies
        // below replace them.
        db.execSQL("DELETE FROM entries WHERE id IN $all")
        RowCopy.insert(db, "entries", entries)

        val tagIdFor = mutableMapOf<Long, Long>()
        tags.forEach { tag ->
            val id = tag.getAsLong("id")
            val name = tag.getAsString("name")
            val existing = RowCopy.ids(db, "SELECT id FROM tags WHERE name = ${android.database.DatabaseUtils.sqlEscapeString(name)}").firstOrNull()
            tagIdFor[id] = existing ?: if (RowCopy.exists(db, "tags", id)) {
                db.insert("tags", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply { put("name", name) })
            } else {
                RowCopy.insert(db, "tags", listOf(tag)); id
            }
        }
        RowCopy.insert(db, "entry_tags", entryTags.mapNotNull { row ->
            val mapped = tagIdFor[row.getAsLong("tagId")] ?: return@mapNotNull null
            ContentValues(row).apply { put("tagId", mapped) }
        }, ignoreExisting = true)
        RowCopy.insert(db, "entry_events", events, ignoreExisting = true)
        RowCopy.insert(db, "task_dependencies", dependencies.filter {
            RowCopy.exists(db, "entries", it.getAsLong("taskId")) && RowCopy.exists(db, "entries", it.getAsLong("prerequisiteId"))
        }, ignoreExisting = true)
        RowCopy.insert(db, "commitments", commitments, ignoreExisting = true)
        RowCopy.insert(db, "task_bookmarks", taskBookmarks.filter { RowCopy.exists(db, "bookmarks", it.getAsLong("bookmarkId")) }, ignoreExisting = true)
        RowCopy.insert(db, "reminders", reminders, ignoreExisting = true)
        RowCopy.insert(db, "work_sessions", sessions, ignoreExisting = true)
        RowCopy.insert(db, "session_corrections", corrections, ignoreExisting = true)
        RowCopy.insert(db, "subtasks", subtasks, ignoreExisting = true)
        // Extra blocks cascade with their entry, so the copies replace them outright.
        RowCopy.insert(db, "schedule_blocks", blocks, ignoreExisting = true)
    }
}

/** A planned cost (with its series siblings) as it was — see [EntryRowsSnapshot]. */
class PlanRowsSnapshot internal constructor(
    val ids: Set<Long>,
    val seriesIds: Set<Long>,
    internal val records: List<ContentValues>,
    internal val attachments: List<ContentValues>,
    internal val reminders: List<ContentValues>,
) {
    companion object {
        fun capture(db: SupportSQLiteDatabase, ids: Set<Long>, seriesIds: Set<Long>): PlanRowsSnapshot {
            val list = RowCopy.inList(ids)
            return PlanRowsSnapshot(
                ids, seriesIds,
                records = RowCopy.read(db, "SELECT * FROM money_records WHERE id IN $list"),
                attachments = RowCopy.read(db, "SELECT * FROM attachments WHERE recordId IN $list"),
                reminders = RowCopy.read(db, "SELECT * FROM reminders WHERE ownerKind = 'PLAN' AND ownerId IN $list"),
            )
        }
    }

    fun titleOf(id: Long): String = records.firstOrNull { it.getAsLong("id") == id }?.getAsString("title").orEmpty()

    /** Only open plans are ever in the series set (payments are separate rows with their own
     * ids), so removing and re-adding these rows never touches the ledger. */
    fun restore(db: SupportSQLiteDatabase) {
        val current = ids + RowCopy.ids(db, "SELECT id FROM money_records WHERE status IS NOT NULL AND seriesId IN ${RowCopy.inList(seriesIds)}")
        val all = RowCopy.inList(current)
        db.execSQL("DELETE FROM reminders WHERE ownerKind = 'PLAN' AND ownerId IN $all")
        db.execSQL("DELETE FROM money_records WHERE id IN $all")
        RowCopy.insert(db, "money_records", records)
        RowCopy.insert(db, "attachments", attachments, ignoreExisting = true)
        RowCopy.insert(db, "reminders", reminders, ignoreExisting = true)
    }
}

/** A saved link as it was, with its task links. */
class BookmarkRowsSnapshot internal constructor(
    val id: Long,
    internal val bookmark: List<ContentValues>,
    internal val taskLinks: List<ContentValues>,
) {
    val title: String get() = bookmark.firstOrNull()?.getAsString("title").orEmpty()

    companion object {
        fun capture(db: SupportSQLiteDatabase, id: Long) = BookmarkRowsSnapshot(
            id,
            RowCopy.read(db, "SELECT * FROM bookmarks WHERE id = $id"),
            RowCopy.read(db, "SELECT * FROM task_bookmarks WHERE bookmarkId = $id"),
        )
    }

    fun restore(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM bookmarks WHERE id = $id")
        RowCopy.insert(db, "bookmarks", bookmark)
        RowCopy.insert(db, "task_bookmarks", taskLinks.filter { RowCopy.exists(db, "entries", it.getAsLong("entryId")) }, ignoreExisting = true)
    }
}
