package com.example.timeapp.data.repository

import com.example.timeapp.data.L

import androidx.room.withTransaction
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.SeriesOccurrence
import com.example.timeapp.data.SeriesScope
import com.example.timeapp.data.planSeriesSplit
import com.example.timeapp.data.db.EntryEntity
import com.example.timeapp.data.db.normaliseTag
import com.example.timeapp.data.Subtask
import com.example.timeapp.data.WorkSessionSpan
import com.example.timeapp.data.clippedTo
import com.example.timeapp.data.db.EntryDao
import com.example.timeapp.data.db.SubtaskEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class EntryRepository(private val db: TimeDatabase) {
    private val dao: EntryDao get() = db.entryDao()
    private val sessionDao get() = db.workSessionDao()
    private val subtaskDao get() = db.subtaskDao()

    /** The one work timer — every session change below goes through it. */
    val timer: WorkTimer by lazy { WorkTimer(db, this) }

    /** An explicit instant (tests, imports) is taken as wall time with no monotonic anchor. */
    private fun clocksAt(now: Instant?): com.example.timeapp.data.Clocks =
        now?.let { com.example.timeapp.data.Clocks(it, 0, null) } ?: TimerClock.now()

    fun observeSubtasks(entryId: Long): Flow<List<Subtask>> =
        subtaskDao.observeForEntry(entryId).map { list -> list.map { it.toDomain() } }

    suspend fun addSubtask(entryId: Long, title: String, sortOrder: Int) {
        subtaskDao.insert(SubtaskEntity(entryId = entryId, title = title, sortOrder = sortOrder))
    }

    /**
     * Turns the checklist in [entryId]'s description into Next steps, ticks kept, after any
     * existing steps; the lines leave the description in the same transaction. Returns the new
     * step ids and the description as it was, for Undo — or null when there's nothing to do.
     */
    suspend fun checklistToSubtasks(entryId: Long): Pair<List<Long>, String?>? = db.withTransaction {
        val row = dao.getById(entryId) ?: return@withTransaction null
        val (items, rest) = com.example.timeapp.ui.richtext.extractChecklist(row.description ?: return@withTransaction null)
        if (items.isEmpty()) return@withTransaction null
        val start = (subtaskDao.maxSortOrder(entryId) ?: -1) + 1
        val ids = items.mapIndexed { i, item ->
            subtaskDao.insert(SubtaskEntity(entryId = entryId, title = item.text, done = item.checked, sortOrder = start + i))
        }
        dao.update(row.copy(description = rest.ifBlank { null }))
        ids to row.description
    }

    /** Undo for [checklistToSubtasks]. */
    suspend fun undoChecklistToSubtasks(entryId: Long, stepIds: List<Long>, description: String?) = db.withTransaction {
        stepIds.forEach { subtaskDao.delete(it) }
        dao.getById(entryId)?.let { dao.update(it.copy(description = description)) }
    }

    suspend fun toggleSubtask(subtask: Subtask) = subtaskDao.update(subtask.toEntity().copy(done = !subtask.done))

    suspend fun deleteSubtask(id: Long) = subtaskDao.delete(id)

    /**
     * Entries with their category resolved from the category table, so a rename or recolour
     * shows up everywhere at once. The stored `categoryName`/`categoryColor` columns remain
     * as the fallback for a record whose category was never set or has since been removed.
     */
    fun observeEntries(): Flow<List<Entry>> =
        kotlinx.coroutines.flow.combine(observeEntriesWithoutBlocks(), db.scheduleBlockDao().observeAll()) { entries, blocks ->
            val byEntry = blocks.groupBy({ it.entryId }, { it.toDomain() })
            entries.map { e -> byEntry[e.id]?.let { e.copy(extraBlocks = it) } ?: e }
        }

    private fun observeEntriesWithoutBlocks(): Flow<List<Entry>> =
        kotlinx.coroutines.flow.combine(
            dao.observeAll(),
            db.categoryDao().observeAll(),
            db.tagDao().observeEntryTagNames(),
            db.dependencyDao().observeAll(),
            db.taskBookmarkDao().observeAll(),
        ) { entries, categories, tagLinks, dependencies, bookmarkLinks ->
            val bookmarksByEntry = bookmarkLinks.groupBy({ it.entryId }, { it.bookmarkId })
            val byId = categories.associateBy { it.id }
            val tagsByEntry = tagLinks.groupBy({ it.entryId }, { it.name })
            val prerequisitesByEntry = dependencies.groupBy({ it.taskId }, { it.prerequisiteId })
            entries.map { entity ->
                val resolved = entity.categoryId?.let { byId[it] }
                val project = entity.projectId?.let { byId[it] }
                val domain = entity.toDomain().copy(
                    project = project?.let { com.example.timeapp.data.WorkCategory(it.name, it.colorArgb) },
                    tags = tagsByEntry[entity.id].orEmpty(),
                    prerequisiteIds = prerequisitesByEntry[entity.id].orEmpty(),
                    bookmarkIds = bookmarksByEntry[entity.id].orEmpty(),
                )
                if (resolved == null) domain
                else domain.copy(category = com.example.timeapp.data.WorkCategory(resolved.name, resolved.colorArgb, resolved.icon))
            }
        }

    /**
     * Reserves another block of time for a task. With no first block yet, this becomes it (on
     * the entry itself); otherwise it's an extra row. The caller has already surfaced any
     * overlap (rule 4) — nothing here moves another block.
     */
    suspend fun addBlock(entryId: Long, start: Instant, end: Instant, isFixed: Boolean = false) = db.withTransaction {
        require(end.isAfter(start)) { L("Booked time must end after it starts") }
        val row = dao.getById(entryId) ?: return@withTransaction
        if (row.startAt == null || row.endAt == null) {
            val status = if (row.status == EntryStatus.UNSCHEDULED) EntryStatus.PLANNED else row.status
            update(row.toDomain().copy(startAt = start, endAt = end, isFixed = isFixed, status = status))
        } else {
            db.scheduleBlockDao().insert(com.example.timeapp.data.db.ScheduleBlockEntity(entryId = entryId, startAt = start, endAt = end, isFixed = isFixed))
        }
    }

    /** Changes one block's times: [blockId] null means the entry's own first block. */
    suspend fun updateBlock(entryId: Long, blockId: Long?, start: Instant, end: Instant) = db.withTransaction {
        require(end.isAfter(start)) { L("Booked time must end after it starts") }
        if (blockId == null) {
            val row = dao.getById(entryId) ?: return@withTransaction
            update(row.toDomain().copy(startAt = start, endAt = end))
        } else {
            val b = db.scheduleBlockDao().getById(blockId) ?: return@withTransaction
            db.scheduleBlockDao().update(b.copy(startAt = start, endAt = end))
        }
    }

    /**
     * Removes one block. Removing the first block promotes the earliest extra into its place,
     * so a task with blocks always has its first one on the entry.
     */
    suspend fun removeBlock(entryId: Long, blockId: Long?) = db.withTransaction {
        val blockDao = db.scheduleBlockDao()
        if (blockId != null) {
            blockDao.delete(blockId)
            return@withTransaction
        }
        val row = dao.getById(entryId) ?: return@withTransaction
        val next = blockDao.forEntry(entryId).firstOrNull()
        if (next != null) {
            blockDao.delete(next.id)
            update(row.toDomain().copy(startAt = next.startAt, endAt = next.endAt, isFixed = next.isFixed))
        } else {
            update(row.toDomain().copy(startAt = null, endAt = null))
        }
    }

    /**
     * Splits a block: it keeps its start and becomes [firstMs] long; the rest becomes a new
     * block at [secondStart] (a slot the user confirmed — nothing is placed without them).
     */
    suspend fun splitBlock(entryId: Long, blockId: Long?, firstMs: Long, secondStart: Instant) = db.withTransaction {
        val (start, end, fixed) = if (blockId == null) {
            val row = dao.getById(entryId) ?: return@withTransaction
            Triple(row.startAt ?: return@withTransaction, row.endAt ?: return@withTransaction, row.isFixed)
        } else {
            val b = db.scheduleBlockDao().getById(blockId) ?: return@withTransaction
            Triple(b.startAt, b.endAt, b.isFixed)
        }
        val total = java.time.Duration.between(start, end).toMillis()
        if (firstMs <= 0 || firstMs >= total) return@withTransaction
        updateBlock(entryId, blockId, start, start.plusMillis(firstMs))
        db.scheduleBlockDao().insert(
            com.example.timeapp.data.db.ScheduleBlockEntity(entryId = entryId, startAt = secondStart, endAt = secondStart.plusMillis(total - firstMs), isFixed = fixed),
        )
    }

    /** Reschedule's apply: unused future extras go, and the first block holds all remaining work. */
    suspend fun replaceFutureBlocks(entry: Entry, start: Instant, end: Instant, now: Instant = Instant.now()) = db.withTransaction {
        db.scheduleBlockDao().deleteFuture(entry.id, now)
        val row = dao.getById(entry.id) ?: return@withTransaction
        update(row.toDomain().copy(startAt = start, endAt = end, status = EntryStatus.PLANNED, isFixed = false))
    }

    /** Writes a column's new order: [idsInOrder] top to bottom. */
    suspend fun setBoardOrder(idsInOrder: List<Long>) = db.withTransaction {
        idsInOrder.forEachIndexed { index, id -> dao.setBoardOrder(id, index) }
    }

    /** Puts a card at the bottom of whatever column it is moving into. */
    suspend fun sendToBottom(id: Long) = dao.setBoardOrder(id, dao.maxBoardOrder() + 1)

    /** Every tag currently on some task, most-used first — the form's suggestions. */
    fun observeTagNames(): Flow<List<String>> = db.tagDao().observeNamesInUse()

    /**
     * Replaces an entry's tags. Names are normalised (see [normaliseTag]) and reused when they
     * already exist; tags no task uses any more are dropped so suggestions stay relevant.
     */
    suspend fun setTags(entryId: Long, names: List<String>) = db.withTransaction {
        val tagDao = db.tagDao()
        tagDao.clearLinks(entryId)
        names.map { normaliseTag(it) }.filter { it.isNotEmpty() }.distinct().forEach { name ->
            val tagId = tagDao.idFor(name) ?: tagDao.insertTag(com.example.timeapp.data.db.TagEntity(name = name))
            tagDao.insertLink(com.example.timeapp.data.db.EntryTagEntity(entryId, tagId))
        }
        tagDao.deleteUnused()
    }

    suspend fun insert(entry: Entry): Long {
        // New cards join the bottom of their column, never jump above ones already ordered.
        val id = dao.insert(entry.toEntity().copy(boardOrder = dao.maxBoardOrder() + 1))
        // The first occurrence is its own series template: seriesId == its own id, so the
        // expander can find the rule without a separate template table.
        if (entry.recurrence != null) {
            dao.getById(id)?.let { dao.update(it.copy(seriesId = id)) }
        }
        return id
    }
    /**
     * The one write path for an entry, and so the one place status changes are logged: when
     * [entry]'s status differs from what's stored, an [com.example.timeapp.data.db.EntryEventEntity]
     * records it — with [note] (a resume note) and [relatedEntryId] (the task switched to).
     */
    suspend fun update(entry: Entry, note: String? = null, relatedEntryId: Long? = null, at: Instant = Instant.now()) =
        db.withTransaction {
            val before = dao.getById(entry.id)
            // Compared as stored: screens hand back display entries, whose Overdue is derived and
            // never written — comparing the domain status logged a fake Planned → Overdue event
            // every time an overdue task was merely edited.
            val raw = entry.toEntity()
            val row = raw.copy(
                // Completion time follows the status change itself, whichever path made it.
                completedAt = when {
                    raw.status != EntryStatus.DONE -> null
                    before?.status == EntryStatus.DONE -> before.completedAt
                    else -> at
                },
                // Archive state is its own action; an edit from a stale screen can't undo it.
                archivedAt = before?.archivedAt ?: raw.archivedAt,
            )
            dao.update(row)
            if (before != null && before.status != row.status) {
                db.entryEventDao().insert(
                    com.example.timeapp.data.db.EntryEventEntity(
                        entryId = entry.id, at = at, fromStatus = before.status, toStatus = row.status,
                        note = note, relatedEntryId = relatedEntryId,
                    ),
                )
            }
        }

    /**
     * Stop and complete in one step (the PDF's "Stop and complete"): closes [entry]'s open
     * session, if any, and marks it Done at the same instant, in one transaction. Ticking a
     * tracked task done used to flip only its status, leaving the session open — so a
     * finished task kept accruing time forever.
     */
    suspend fun complete(entry: Entry, now: Instant? = null) = db.withTransaction {
        val c = clocksAt(now)
        val at = timer.stopIfOn(entry.id, c) ?: c.wall
        update(entry.copy(status = EntryStatus.DONE), at = at)
    }

    /** Reopens a done task where it would naturally sit: Planned when it has a date or a
     * block, otherwise the inbox. */
    suspend fun reopen(entry: Entry, now: Instant = Instant.now()) =
        update(entry.copy(status = com.example.timeapp.data.reopenedStatus(entry)), at = now)

    /** Takes a task out of Doing by some other route (a Board move): stops the timer if it's on
     * it, then saves the new status. */
    /**
     * A Board move into Doing is a start: the task's session opens through the one work timer,
     * which pauses whatever else was running. Before, the move only changed the status, so the
     * card said "Tracking" while nothing was recorded, and two cards could sit in Doing at once.
     * [entry] carries the target column.
     */
    suspend fun moveIntoDoing(entry: Entry) = db.withTransaction {
        timer.start(entry.id)
        // Re-read after the timer: it cleared the resume note and logged the start, and the
        // card's copy (from before) would write the stale note back. Only the column is ours.
        val fresh = dao.getById(entry.id)?.toDomain() ?: return@withTransaction
        update(fresh.copy(status = EntryStatus.DOING, columnId = entry.columnId))
    }

    suspend fun moveOutOfDoing(entry: Entry) = db.withTransaction {
        val at = timer.stopIfOn(entry.id) ?: Instant.now()
        update(entry, at = at)
    }

    /**
     * Interrupt [fromId] for [toId] at **one** instant: close its session, mark it Paused with
     * the resume note (logged with the task switched to), open [toId]'s session, mark it Doing.
     * One transaction, one timestamp — so the two segments meet exactly and never overlap
     * (the PDF's 1.3 exit check).
     */
    @Suppress("UNUSED_PARAMETER") // [from] is whatever is running; the timer knows that itself.
    suspend fun switchWork(from: Entry?, to: Entry, resumeNote: String?, now: Instant? = null) =
        timer.start(to.id, resumeNote = resumeNote, c = clocksAt(now))

    /** Makes [taskId] wait on [prerequisiteId]. Returns false, changing nothing, when that
     * would form a loop — checked inside the transaction against the stored links. */
    suspend fun addDependency(taskId: Long, prerequisiteId: Long): Boolean = db.withTransaction {
        val links = db.dependencyDao().all().map { it.taskId to it.prerequisiteId }
        if (com.example.timeapp.data.dependencyWouldCycle(links, taskId, prerequisiteId)) return@withTransaction false
        db.dependencyDao().insert(com.example.timeapp.data.db.TaskDependencyEntity(taskId, prerequisiteId))
        true
    }

    suspend fun removeDependency(taskId: Long, prerequisiteId: Long) = db.dependencyDao().delete(taskId, prerequisiteId)

    fun observeBoardColumns(): Flow<List<com.example.timeapp.data.BoardColumn>> =
        db.boardColumnDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addBoardColumn(name: String, status: com.example.timeapp.data.EntryStatus, wipLimit: Int?) {
        val order = (db.boardColumnDao().all().maxOfOrNull { it.sortOrder } ?: -1) + 1
        db.boardColumnDao().insert(com.example.timeapp.data.db.BoardColumnEntity(name = name.trim(), status = status, sortOrder = order, wipLimit = wipLimit))
    }

    suspend fun updateBoardColumn(column: com.example.timeapp.data.BoardColumn) = db.boardColumnDao().update(column.toEntity())

    /** Refuses to delete a status's last column (see `canDeleteColumn`). */
    suspend fun deleteBoardColumn(id: Long): Boolean = db.withTransaction {
        val columns = db.boardColumnDao().all().map { it.toDomain() }
        val column = columns.firstOrNull { it.id == id } ?: return@withTransaction false
        if (!com.example.timeapp.data.canDeleteColumn(column, columns)) return@withTransaction false
        db.boardColumnDao().releaseCards(id)
        db.boardColumnDao().delete(id)
        true
    }

    /** Writes a new left-to-right order of columns. */
    suspend fun reorderBoardColumns(idsInOrder: List<Long>) = db.withTransaction {
        val byId = db.boardColumnDao().all().associateBy { it.id }
        idsInOrder.forEachIndexed { i, id -> byId[id]?.let { db.boardColumnDao().update(it.copy(sortOrder = i)) } }
    }

    /**
     * Writes the chosen calendar items in one transaction. An item whose UID is already here
     * updates that entry (title, note, times) — the user ticked "update" for it; the rest are
     * new. A timed item is a schedule block (EVENT); an all-day one is a task due that day.
     * Returns how many rows were written. Callers expand recurrences afterwards.
     */
    suspend fun importCalendar(items: List<com.example.timeapp.data.Ics.ImportItem>): Int = db.withTransaction {
        var count = 0
        items.filter { it.importable }.forEach { item ->
            val existing = item.existingId?.let { dao.getById(it) }
            if (existing != null) {
                update(existing.toDomain().copy(title = item.title, description = item.note, startAt = item.start, endAt = item.end, dueOn = item.dueOn))
            } else {
                insert(
                    Entry(
                        id = 0,
                        kind = if (item.allDay) com.example.timeapp.data.EntryKind.TASK else com.example.timeapp.data.EntryKind.EVENT,
                        title = item.title,
                        status = com.example.timeapp.data.EntryStatus.PLANNED,
                        startAt = item.start,
                        endAt = item.end,
                        dueOn = item.dueOn,
                        description = item.note,
                        recurrence = item.recurrence,
                        icsUid = item.uid,
                    ),
                )
            }
            count++
        }
        count
    }

    suspend fun linkBookmark(entryId: Long, bookmarkId: Long) =
        db.taskBookmarkDao().insert(com.example.timeapp.data.db.TaskBookmarkEntity(entryId, bookmarkId))

    suspend fun unlinkBookmark(entryId: Long, bookmarkId: Long) = db.taskBookmarkDao().delete(entryId, bookmarkId)

    fun observeWorkTargets() = db.workTargetDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun setWorkTarget(weeklyMinutes: Int, effectiveFrom: java.time.LocalDate) =
        db.workTargetDao().upsert(com.example.timeapp.data.db.WorkTargetEntity(weeklyMinutes = weeklyMinutes, effectiveFrom = effectiveFrom))

    fun observeEvents(entryId: Long) = db.entryEventDao().observeForEntry(entryId)

    fun eventsInRange(from: Instant, to: Instant) = db.entryEventDao().observeInRange(from, to)
    suspend fun delete(id: Long) = db.withTransaction { deleteRow(id) }

    /** Copies [id] — its whole series when it has one, plus [alsoSeries] — for Undo. */
    suspend fun snapshotRows(id: Long, alsoSeries: Long? = null): com.example.timeapp.data.db.EntryRowsSnapshot? = db.withTransaction {
        val row = dao.getById(id) ?: return@withTransaction null
        val series = setOfNotNull(row.seriesId, alsoSeries)
        val raw = db.openHelper.writableDatabase
        val ids = setOf(id) + com.example.timeapp.data.db.RowCopy.ids(raw, "SELECT id FROM entries WHERE seriesId IN ${com.example.timeapp.data.db.RowCopy.inList(series)}")
        com.example.timeapp.data.db.EntryRowsSnapshot.capture(raw, ids, series)
    }

    /**
     * [deleteInSeries] with a copy taken first, in the same transaction. A running timer on
     * the task is stopped *before* the copy, so Undo brings back a closed session rather than
     * one left open with nothing timing it.
     */
    suspend fun deleteInSeriesUndoable(id: Long, scope: SeriesScope): Pair<List<Long>, com.example.timeapp.data.db.EntryRowsSnapshot>? = db.withTransaction {
        timer.stopIfOn(id)
        val snap = snapshotRows(id) ?: return@withTransaction null
        deleteInSeries(id, scope) to snap
    }

    suspend fun restoreRows(snap: com.example.timeapp.data.db.EntryRowsSnapshot) =
        db.withTransaction { snap.restore(db.openHelper.writableDatabase) }

    /**
     * Archives a task: closes a running session first (an archived task can't keep a timer
     * going) and moves Doing to Paused, then hides it from every default view. Its sessions,
     * history and links all stay, so reports don't change.
     */
    suspend fun archive(entry: Entry, now: Instant = Instant.now()) = db.withTransaction {
        val stored = dao.getById(entry.id) ?: return@withTransaction
        val at = timer.stopIfOn(entry.id) ?: now
        if (stored.status == EntryStatus.DOING) update(stored.toDomain().copy(status = EntryStatus.PAUSED), at = at)
        dao.setArchived(entry.id, at)
    }

    suspend fun restore(id: Long) = dao.setArchived(id, null)

    /** Archives every done task that isn't archived yet — the Board's "Archive done". */
    suspend fun archiveDone(now: Instant = Instant.now()): Int = db.withTransaction {
        val done = dao.all().filter { it.status == EntryStatus.DONE && it.archivedAt == null }
        done.forEach { dao.setArchived(it.id, now) }
        done.size
    }

    /**
     * Deletes [id], or it and every later occurrence of its series. Returns the ids actually
     * removed so the caller can retire their reminders.
     *
     * "Only this" records the date on the rule's `skipped` set — without it, deleting the
     * series' latest occurrence would simply be regenerated on the next app start.
     * "This and future" ends the series the day before; later occurrences with tracked or
     * finished work are kept, since deleting history is never part of a series edit.
     */
    suspend fun deleteInSeries(id: Long, scope: SeriesScope): List<Long> = db.withTransaction {
        val row = dao.getById(id) ?: return@withTransaction emptyList()
        val seriesId = row.seriesId
        if (seriesId == null || scope == SeriesScope.ONLY_THIS) {
            deleteRow(id)
            if (seriesId != null) row.occurrenceDate()?.let { skipInSeries(seriesId, it) }
            return@withTransaction listOf(id)
        }
        val split = planSeriesSplit(seriesOccurrences(seriesId), row.asOccurrence())
        val removed = split.delete + id
        removed.forEach { deleteRow(it) }
        endSeries(split.keepInOld + split.moveToNew, split.endOldOn)
        removed
    }

    /**
     * Saves an edit to one occurrence. [ONLY_THIS] keeps the row's rule and series (a single
     * occurrence cannot have its own rule) and, if its date moved, skips the old date so it is
     * not regenerated. [THIS_AND_FUTURE] splits the series at this row: earlier occurrences
     * keep the old rule and end the day before, and this row becomes the template of a new
     * series whose later occurrences are regenerated from it. Returns the ids removed.
     */
    suspend fun updateInSeries(edited: Entry, scope: SeriesScope): List<Long> = db.withTransaction {
        val original = dao.getById(edited.id) ?: return@withTransaction emptyList()
        val seriesId = original.seriesId
        if (seriesId == null) {
            // Not a series yet — adding a rule here starts one, as creating it would have.
            val rule = edited.recurrence
            dao.update(edited.toEntity().copy(seriesId = if (rule != null) edited.id else null))
            return@withTransaction emptyList()
        }
        if (scope == SeriesScope.ONLY_THIS) {
            dao.update(edited.toEntity().copy(recurrence = original.recurrence, seriesId = seriesId))
            val oldDate = original.occurrenceDate()
            if (oldDate != null && oldDate != edited.toEntity().occurrenceDate()) skipInSeries(seriesId, oldDate)
            return@withTransaction emptyList()
        }
        val split = planSeriesSplit(seriesOccurrences(seriesId), original.asOccurrence())
        split.delete.forEach { deleteRow(it) }
        endSeries(split.keepInOld, split.endOldOn)
        val newRule = edited.recurrence?.let { rule ->
            rule.copy(skipped = original.recurrence?.skipped.orEmpty().filter { d -> split.endOldOn == null || d.isAfter(split.endOldOn) }.toSet())
        }
        val newSeries = if (newRule != null) edited.id else null
        dao.update(edited.toEntity().copy(recurrence = newRule, seriesId = newSeries))
        split.moveToNew.forEach { movedId ->
            dao.getById(movedId)?.let { dao.update(it.copy(recurrence = newRule, seriesId = newSeries)) }
        }
        split.delete
    }

    /**
     * Removes a task with what only it owns. Sessions and subtasks have no foreign key, so a
     * deleted task used to leave them behind: invisible checklist rows, and time still counted
     * in totals but under no task or category. A delete of a task with tracked time is only
     * reached after the dialog has said so and offered Archive instead.
     */
    private suspend fun deleteRow(id: Long) {
        timer.stopIfOn(id)
        sessionDao.deleteForEntry(id)
        subtaskDao.deleteForEntry(id)
        dao.delete(id)
    }

    private suspend fun seriesOccurrences(seriesId: Long): List<SeriesOccurrence> =
        dao.inSeries(seriesId).map { it.asOccurrence() }

    private suspend fun EntryEntity.asOccurrence() = SeriesOccurrence(
        id = id,
        date = occurrenceDate(),
        locked = status == EntryStatus.DONE || status == EntryStatus.DOING || status == EntryStatus.PAUSED ||
            sessionDao.countForEntry(id) > 0,
    )

    private suspend fun skipInSeries(seriesId: Long, date: java.time.LocalDate) {
        dao.inSeries(seriesId).forEach { e ->
            e.recurrence?.let { rule -> dao.update(e.copy(recurrence = rule.copy(skipped = rule.skipped + date))) }
        }
    }

    private suspend fun endSeries(ids: List<Long>, endOn: java.time.LocalDate?) {
        if (endOn == null) return
        ids.forEach { rowId ->
            dao.getById(rowId)?.let { e ->
                e.recurrence?.let { rule ->
                    val end = rule.endOn?.takeIf { it.isBefore(endOn) } ?: endOn
                    dao.update(e.copy(recurrence = rule.copy(endOn = end)))
                }
            }
        }
    }

    fun sumTrackedMs(entryId: Long): Flow<Long> = sessionDao.sumDurationMsForEntry(entryId)

    /** Closed work inside [from, to): overlapping sessions, clipped to the range, so a session
     * across the boundary counts only its part inside. */
    fun sessionsInRange(from: Instant, to: Instant): Flow<List<WorkSessionSpan>> =
        sessionDao.sessionsInRange(from, to).map { list ->
            list.mapNotNull { s -> s.endedAt?.let { WorkSessionSpan(s.entryId, s.startedAt, it).clippedTo(from, to) } }
        }

    fun sumDurationMsInRange(from: Instant, to: Instant): Flow<Long> = sessionDao.sumDurationMsInRange(from, to)

    /** Starts work on [entryId] through the one timer (closing whatever else runs); a second
     * start of the running task does nothing. */
    suspend fun startSession(entryId: Long, now: Instant? = null) = timer.start(entryId, c = clocksAt(now))

    /** Stops the timer if it's running on [entryId]; the task's status is the caller's. */
    suspend fun endSession(entryId: Long, now: Instant? = null) = db.withTransaction { timer.stopIfOn(entryId, clocksAt(now)) }
}
