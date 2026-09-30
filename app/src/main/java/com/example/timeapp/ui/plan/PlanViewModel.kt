package com.example.timeapp.ui.plan

import kotlinx.coroutines.flow.flowOn
import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import com.example.timeapp.data.withBlocksExpanded
import com.example.timeapp.data.displayStatus
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.notify.ReminderScheduler
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class PlanUiState(
    /** Everything not archived — what every default view (List, Board, calendar, Today,
     * pickers) works from. */
    val entries: List<Entry> = emptyList(),
    val pendingConflict: ConflictProposal? = null,
    val scope: PlanScope = PlanScope(),
    /** Archived tasks, newest first: out of the default views, still in reports and Search. */
    val archived: List<Entry> = emptyList(),
) {
    /** Archived included — for reports, Search and opening a task by id. */
    val allEntries: List<Entry> get() = entries + archived

    fun find(id: Long): Entry? = entries.firstOrNull { it.id == id } ?: archived.firstOrNull { it.id == id }

    /** [entries] narrowed to the chosen project/tag — what the List and Board show. Today,
     * Switch and Focus keep using the unfiltered [entries]. */
    val scopedEntries: List<Entry>
        get() = entries.filter { e ->
            (scope.projectId == null || e.projectId == scope.projectId) && (scope.tag == null || scope.tag in e.tags)
        }
}

/** Prerequisites of [entry] that aren't done yet — what it is actually waiting on. */
fun PlanUiState.unfinishedPrerequisites(entry: Entry): List<Entry> {
    if (entry.prerequisiteIds.isEmpty()) return emptyList()
    val byId = allEntries.associateBy { it.id }
    return entry.prerequisiteIds.mapNotNull { byId[it] }.filter { it.status != EntryStatus.DONE }
}

/** The List/Board project and tag filter. Held here, not per screen, so switching between
 * the two views keeps it. */
data class PlanScope(val projectId: Long? = null, val tag: String? = null) {
    val isActive: Boolean get() = projectId != null || tag != null
}

/** A just-applied change and how to reverse it. */
class UndoAction(val message: String, val undo: () -> Unit)

/**
 * The one change that can currently be undone, shown app-wide as a snackbar by the root
 * Scaffold. Every screen offers through here, so a second change simply replaces the first
 * (only the latest is undoable) instead of two snackbars competing.
 */
object UndoBus {
    private val pending = MutableStateFlow<UndoAction?>(null)
    val current: StateFlow<UndoAction?> = pending
    fun offer(action: UndoAction) { pending.value = action }
    fun consume(action: UndoAction) { pending.compareAndSet(action, null) }
}

/** A new/edited entry whose time range overlaps an existing one — surfaced, never auto-resolved. */
data class ConflictProposal(
    val candidateTitle: String,
    val candidateStart: Instant,
    val candidateEnd: Instant,
    val existing: Entry,
    val candidateCategoryId: Long? = null,
    val candidateDueOn: LocalDate? = null,
    val candidateEstimateMs: Long? = null,
    val candidateDescription: String? = null,
    val candidateRemind: Boolean = false,
    val candidateRecurrence: com.example.timeapp.data.Recurrence? = null,
    val candidateKind: EntryKind = EntryKind.TASK,
    val candidateProjectId: Long? = null,
    val candidateTags: List<String> = emptyList(),
    val candidateFixed: Boolean = false,
    val candidateTiming: com.example.timeapp.data.ReminderTiming? = null,
    val candidatePriority: com.example.timeapp.data.Priority = com.example.timeapp.data.Priority.NORMAL,
)

enum class ConflictChoice { MOVE_NEW, KEEP_BOTH, CANCEL }

/** Shared between C1 (List), C2 (Calendar) and the C3 conflict dialog. */
class PlanViewModel(
    private val repository: EntryRepository,
    private val reminders: ReminderRepository,
    private val commitments: com.example.timeapp.data.repository.CommitmentRepository? = null,
) : ViewModel() {
    /** Today's chosen commitments, taken-off ones included (they stay on the record). */
    val todayCommitments: StateFlow<List<com.example.timeapp.data.Commitment>> =
        (commitments?.observeForDate(LocalDate.now()) ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun commitToday(entryId: Long) {
        viewModelScope.launch { commitments?.commit(LocalDate.now(), entryId) }
    }

    fun uncommitToday(entryId: Long, note: String?) {
        viewModelScope.launch { commitments?.remove(LocalDate.now(), entryId, note) }
    }

    private val pendingConflict = MutableStateFlow<ConflictProposal?>(null)
    private val scope = MutableStateFlow(PlanScope())

    fun setScope(value: PlanScope) = scope.update { value }

    private val calendarFilterState = MutableStateFlow(com.example.timeapp.data.CalendarFilter())

    /** The calendar's filter — here, not on the screen, so Month/Week/Day/Agenda share it. */
    val calendarFilter: StateFlow<com.example.timeapp.data.CalendarFilter> = calendarFilterState

    fun setCalendarFilter(value: com.example.timeapp.data.CalendarFilter) = calendarFilterState.update { value }

    /** Tags in use, most-used first — the task form's suggestions and the tag filter. */
    val tagNames: StateFlow<List<String>> = repository.observeTagNames()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Chosen reminder timings by task/event id, for the forms to prefill when editing. */
    val reminderTimings: StateFlow<Map<Long, com.example.timeapp.data.ReminderTiming>> = reminders.observeTimings()
        .map { m -> m.filterKeys { it.first == ReminderOwnerKind.TASK || it.first == ReminderOwnerKind.EVENT }.mapKeys { it.key.second } }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Owner ids that currently have a reminder — lets a form prefill its "Remind me" switch
     * without a per-screen suspend lookup. */
    val reminderOwnerIds: StateFlow<Set<Long>> = reminders.observeAll()
        .map { list ->
            list.filter { it.ownerKind == ReminderOwnerKind.TASK || it.ownerKind == ReminderOwnerKind.EVENT }
                .map { it.ownerId }
                .toSet()
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val uiState: StateFlow<PlanUiState> = combine(repository.observeEntries(), pendingConflict, scope) { entries, conflict, planScope ->
        // Overdue is a live, date-derived badge, not a stored one-time classification —
        // a PLANNED entry whose due date has passed is displayed as OVERDUE without
        // mutating what's persisted.
        val today = LocalDate.now()
        val displayEntries = entries.map { e -> e.copy(status = e.displayStatus(today)) }
        val (archived, active) = displayEntries.partition { it.isArchived }
        PlanUiState(
            entries = active,
            pendingConflict = conflict,
            scope = planScope,
            archived = archived.sortedByDescending { it.archivedAt },
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, PlanUiState())

    /** C3's required behavior: overlapping time is surfaced and confirmed, never silently moved. */
    fun proposeSchedule(
        title: String,
        start: Instant,
        end: Instant,
        categoryId: Long? = null,
        dueOn: LocalDate? = null,
        estimateMs: Long? = null,
        kind: EntryKind = EntryKind.TASK,
        description: String? = null,
        remind: Boolean = false,
        recurrence: com.example.timeapp.data.Recurrence? = null,
        projectId: Long? = null,
        tags: List<String> = emptyList(),
        isFixed: Boolean = false,
        timing: com.example.timeapp.data.ReminderTiming? = null,
        priority: com.example.timeapp.data.Priority = com.example.timeapp.data.Priority.NORMAL,
    ) {
        // Only the first occurrence is conflict-checked: later ones are generated ahead of time
        // by the expander, and a repeat never moves anything — an overlap there is visible on
        // the calendar, never resolved silently.
        val collision = uiState.value.entries.withBlocksExpanded().firstOrNull { e ->
            val s = e.startAt ?: return@firstOrNull false
            val en = e.endAt ?: s
            s.isBefore(end) && en.isAfter(start)
        }
        if (collision == null) {
            commit(title, start, end, categoryId, dueOn, estimateMs, kind, description, remind, recurrence, projectId, tags, isFixed, timing, priority)
        } else {
            pendingConflict.update { ConflictProposal(title, start, end, collision, categoryId, dueOn, estimateMs, description, remind, recurrence, kind, projectId, tags, isFixed, timing, priority) }
        }
    }

    fun resolveConflict(choice: ConflictChoice) {
        val proposal = pendingConflict.value ?: return
        when (choice) {
            ConflictChoice.KEEP_BOTH -> commit(proposal.candidateTitle, proposal.candidateStart, proposal.candidateEnd, proposal.candidateCategoryId, proposal.candidateDueOn, proposal.candidateEstimateMs, proposal.candidateKind, proposal.candidateDescription, proposal.candidateRemind, proposal.candidateRecurrence, proposal.candidateProjectId, proposal.candidateTags, proposal.candidateFixed, proposal.candidateTiming, proposal.candidatePriority)
            ConflictChoice.MOVE_NEW -> {
                val duration = proposal.candidateEnd.toEpochMilli() - proposal.candidateStart.toEpochMilli()
                val newStart = proposal.existing.endAt ?: proposal.existing.startAt!!
                commit(proposal.candidateTitle, newStart, newStart.plusMillis(duration), proposal.candidateCategoryId, proposal.candidateDueOn, proposal.candidateEstimateMs, proposal.candidateKind, proposal.candidateDescription, proposal.candidateRemind, proposal.candidateRecurrence, proposal.candidateProjectId, proposal.candidateTags, proposal.candidateFixed, proposal.candidateTiming, proposal.candidatePriority)
            }
            ConflictChoice.CANCEL -> Unit
        }
        pendingConflict.update { null }
    }

    private fun commit(
        title: String,
        start: Instant,
        end: Instant,
        categoryId: Long? = null,
        dueOn: LocalDate? = null,
        estimateMs: Long? = null,
        kind: EntryKind = EntryKind.TASK,
        description: String? = null,
        remind: Boolean = false,
        recurrence: com.example.timeapp.data.Recurrence? = null,
        projectId: Long? = null,
        tags: List<String> = emptyList(),
        isFixed: Boolean = false,
        timing: com.example.timeapp.data.ReminderTiming? = null,
        priority: com.example.timeapp.data.Priority = com.example.timeapp.data.Priority.NORMAL,
    ) {
        viewModelScope.launch {
            val id = repository.insert(
                Entry(
                    id = 0,
                    kind = kind,
                    title = title,
                    categoryId = categoryId,
                    status = EntryStatus.PLANNED,
                    startAt = start,
                    endAt = end,
                    isFixed = isFixed,
                    dueOn = dueOn,
                    estimateMs = estimateMs,
                    description = description,
                    recurrence = recurrence?.copy(remind = remind),
                    projectId = projectId,
                    priority = priority,
                ),
            )
            if (tags.isNotEmpty()) repository.setTags(id, tags)
            if (remind) {
                // A block already knows its own instant, so it reminds at the start itself
                // rather than at a due date's 09:00 default.
                val kindForReminder = if (kind == EntryKind.EVENT) ReminderOwnerKind.EVENT else ReminderOwnerKind.TASK
                val t = timing ?: com.example.timeapp.data.ReminderTiming.EVENT_DEFAULT
                reminders.setReminder(kindForReminder, id, t.at(dueOn, start) ?: start, t)
            }
            // After the first occurrence's own reminder, so the whole series is armed together.
            if (recurrence != null) reminders.expandRecurrences()
        }
    }

    /** No-time-range path — used when "Reserve a calendar block" is off. */
    fun addTask(
        title: String,
        categoryId: Long?,
        dueOn: LocalDate?,
        estimateMs: Long? = null,
        description: String? = null,
        remind: Boolean = false,
        recurrence: com.example.timeapp.data.Recurrence? = null,
        priority: com.example.timeapp.data.Priority = com.example.timeapp.data.Priority.NORMAL,
        projectId: Long? = null,
        tags: List<String> = emptyList(),
        timing: com.example.timeapp.data.ReminderTiming? = null,
    ) {
        viewModelScope.launch {
            val id = repository.insert(
                Entry(
                    id = 0,
                    kind = EntryKind.TASK,
                    title = title,
                    categoryId = categoryId,
                    status = if (dueOn == null) EntryStatus.UNSCHEDULED else EntryStatus.PLANNED,
                    dueOn = dueOn,
                    estimateMs = estimateMs,
                    description = description,
                    recurrence = recurrence?.copy(remind = remind && dueOn != null),
                    priority = priority,
                    projectId = projectId,
                ),
            )
            // Before expanding, so generated occurrences copy the tags from the first one.
            if (tags.isNotEmpty()) repository.setTags(id, tags)
            if (remind && dueOn != null) {
                val t = timing ?: com.example.timeapp.data.ReminderTiming.TASK_DEFAULT
                reminders.setReminder(ReminderOwnerKind.TASK, id, t.at(dueOn, null) ?: ReminderScheduler.defaultTimeFor(dueOn), t)
            }
            if (recurrence != null) reminders.expandRecurrences()
        }
    }

    /** Direct update — no conflict re-check against the entry's own prior slot. */
    fun updateTask(entry: Entry) {
        viewModelScope.launch { repository.update(entry) }
    }

    /**
     * Saves an edit from the task form. For a repeating task [scope] says whether it applies
     * to this occurrence only or splits the series here (see [EntryRepository.updateInSeries]);
     * occurrences that split removes have their reminders retired, and the new series is
     * regenerated — with reminders, when [remind] is on — before this returns.
     */
    fun saveTaskEdit(entry: Entry, scope: com.example.timeapp.data.SeriesScope, remind: Boolean, tags: List<String>, timing: com.example.timeapp.data.ReminderTiming? = null) {
        viewModelScope.launch {
            // A split starts a new series under this row's id, so that series is copied too.
            val snap = repository.snapshotRows(entry.id, alsoSeries = entry.id)
            val removeIds = repository.updateInSeries(
                entry.copy(recurrence = entry.recurrence?.copy(remind = remind && entry.dueOn != null)),
                scope,
            )
            removeIds.forEach { retireReminders(it) }
            repository.setTags(entry.id, tags)
            val dueOn = entry.dueOn
            if (remind && dueOn != null) {
                val t = timing ?: com.example.timeapp.data.ReminderTiming.TASK_DEFAULT
                reminders.setReminder(ReminderOwnerKind.TASK, entry.id, t.at(dueOn, entry.startAt) ?: ReminderScheduler.defaultTimeFor(dueOn), t)
            } else {
                reminders.clearReminder(ReminderOwnerKind.TASK, entry.id)
            }
            if (entry.recurrence != null) reminders.expandRecurrences()
            if (snap != null) offerRestore(L("“%1\$s” updated", entry.title), snap)
        }
    }

    /**
     * Saves edited booked time (the Book time form's edit mode). Its reminder hangs off the
     * start, not a due date, so it moves with the booking — [saveTaskEdit] would clear it.
     */
    fun saveBlockEdit(entry: Entry, scope: com.example.timeapp.data.SeriesScope, remind: Boolean, timing: com.example.timeapp.data.ReminderTiming) {
        viewModelScope.launch {
            val snap = repository.snapshotRows(entry.id, alsoSeries = entry.id)
            val removeIds = repository.updateInSeries(entry.copy(recurrence = entry.recurrence?.copy(remind = remind)), scope)
            removeIds.forEach { retireReminders(it) }
            val start = entry.startAt
            if (remind && start != null) {
                reminders.setReminder(ReminderOwnerKind.EVENT, entry.id, timing.at(null, start) ?: start, timing)
            } else {
                reminders.clearReminder(ReminderOwnerKind.EVENT, entry.id)
            }
            if (entry.recurrence != null) reminders.expandRecurrences()
            if (snap != null) offerRestore(L("“%1\$s” updated", entry.title), snap)
        }
    }

    fun deleteTask(id: Long, scope: com.example.timeapp.data.SeriesScope = com.example.timeapp.data.SeriesScope.ONLY_THIS) {
        viewModelScope.launch {
            val (removed, snap) = repository.deleteInSeriesUndoable(id, scope) ?: return@launch
            removed.forEach { retireReminders(it) }
            val title = snap.titleOf(id)
            offerRestore(if (removed.size > 1) L("“%1\$s” and %2\$s later deleted", title, (removed.size - 1)) else L("“%1\$s” deleted", title), snap)
        }
    }

    /** Hides a task from every default view, keeping its time, history and links (see
     * [EntryRepository.archive]). Its reminders go: an archived task never notifies. */
    fun archive(entry: Entry) {
        viewModelScope.launch {
            repository.archive(entry)
            retireReminders(entry.id)
        }
    }

    fun restore(entry: Entry) {
        viewModelScope.launch { repository.restore(entry.id) }
    }

    /** The Board's "Archive done": every finished task at once, with Undo. */
    fun archiveDone() {
        val done = uiState.value.entries.filter { it.status == EntryStatus.DONE }
        if (done.isEmpty()) return
        viewModelScope.launch {
            repository.archiveDone()
            UndoBus.offer(
                UndoAction(if (done.size == 1) L("Archived 1 done task") else L("Archived %1\$s done tasks", done.size)) {
                    viewModelScope.launch { done.forEach { repository.restore(it.id) } }
                },
            )
        }
    }

    /** Cancel the alarm too — a deleted owner must never notify, and leaving the row would
     * make every reconcile re-arm an alarm for a record that no longer exists. */
    private suspend fun retireReminders(id: Long) {
        reminders.clearReminder(ReminderOwnerKind.TASK, id)
        reminders.clearReminder(ReminderOwnerKind.EVENT, id)
    }

    /**
     * A Board move. Leaving Doing closes the open work session — before this, moving a
     * tracking card to Done (or anywhere) changed its column but left the session open, so
     * the task kept accruing time indefinitely. Moving *into* Doing does not start a timer
     * (the PDF: "moving a card to Doing alone does not automatically start a timer").
     */
    fun moveTo(entry: Entry, target: EntryStatus, columnId: Long? = null) {
        viewModelScope.launch {
            val moved = entry.copy(status = target, columnId = columnId)
            when {
                entry.status == EntryStatus.DOING && target != EntryStatus.DOING -> repository.moveOutOfDoing(moved)
                entry.status != EntryStatus.DOING && target == EntryStatus.DOING -> repository.moveIntoDoing(moved)
                else -> repository.update(moved)
            }
            repository.sendToBottom(entry.id)
        }
    }

    /** The Board's columns, left to right. */
    val boardColumns: StateFlow<List<com.example.timeapp.data.BoardColumn>> = repository.observeBoardColumns()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addBoardColumn(name: String, status: EntryStatus, wipLimit: Int?) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addBoardColumn(name, status, wipLimit) }
    }

    fun updateBoardColumn(column: com.example.timeapp.data.BoardColumn) {
        if (column.name.isBlank()) return
        viewModelScope.launch { repository.updateBoardColumn(column.copy(name = column.name.trim())) }
    }

    fun deleteBoardColumn(id: Long) {
        viewModelScope.launch { repository.deleteBoardColumn(id) }
    }

    fun moveBoardColumn(columns: List<com.example.timeapp.data.BoardColumn>, column: com.example.timeapp.data.BoardColumn, offset: Int) {
        val ids = columns.map { it.id }.toMutableList()
        val from = ids.indexOf(column.id)
        val to = (from + offset).coerceIn(0, ids.lastIndex)
        if (from < 0 || from == to) return
        ids.removeAt(from); ids.add(to, column.id)
        viewModelScope.launch { repository.reorderBoardColumns(ids) }
    }

    /**
     * Reorders one Board column: [column] is its current top-to-bottom order, [entry] moves to
     * [newIndex]. The whole column is renumbered, so earlier ties can never make a move a no-op.
     */
    fun reorder(column: List<Entry>, entry: Entry, newIndex: Int) {
        val ids = column.map { it.id }.toMutableList()
        if (!ids.remove(entry.id)) return
        ids.add(newIndex.coerceIn(0, ids.size), entry.id)
        viewModelScope.launch { repository.setBoardOrder(ids) }
    }

    /** Blocked ↔ back to where it would naturally sit: planned if it has a date, else inbox. */
    fun setBlocked(entry: Entry, blocked: Boolean) {
        moveTo(entry, if (blocked) EntryStatus.BLOCKED else com.example.timeapp.data.reopenedStatus(entry))
    }

    /** Completing closes a running session in the same step; reopening puts the task back
     * where it would naturally sit (it used to land in the inbox even when it had a date). */
    fun toggleDone(entry: Entry) {
        viewModelScope.launch {
            if (entry.status == EntryStatus.DONE) repository.reopen(entry) else repository.complete(entry)
        }
    }

    /** D2 — applies a reschedule preview: closes nothing silently, only called from an explicit
     * Apply, and offers Undo right after (the PDF: "apply only after the user accepts; keep an
     * Undo action" — the help text promised it long before it existed). */
    fun reschedule(id: Long, newStart: Instant, newEnd: Instant) {
        val before = uiState.value.entries.firstOrNull { it.id == id } ?: return
        val at = newStart.atZone(java.time.ZoneId.systemDefault()).format(displayPattern("EEE HH:mm"))
        viewModelScope.launch {
            // Only reached from an explicit Apply; a fixed block's screen says "Unlock and move",
            // so moving it also makes it flexible. Unused future blocks go (the PDF: reschedule
            // replaces only unused future allocation); blocks already begun stay as they were.
            val snap = repository.snapshotRows(id)
            repository.replaceFutureBlocks(before, newStart, newEnd)
            if (snap != null) offerRestore(L("“%1\$s” moved to %2\$s", before.title, at), snap)
        }
    }

    /** Task Detail's blocks card. Overlaps are surfaced by the dialog before this is called. */
    fun addBlock(entryId: Long, start: Instant, end: Instant) {
        viewModelScope.launch { repository.addBlock(entryId, start, end) }
    }

    fun removeBlock(entryId: Long, blockId: Long?) {
        viewModelScope.launch {
            val snap = repository.snapshotRows(entryId)
            repository.removeBlock(entryId, blockId)
            if (snap != null) offerRestore(L("Booked time removed"), snap)
        }
    }

    fun splitBlock(entryId: Long, blockId: Long?, firstMs: Long, secondStart: Instant) {
        viewModelScope.launch {
            val snap = repository.snapshotRows(entryId)
            repository.splitBlock(entryId, blockId, firstMs, secondStart)
            if (snap != null) offerRestore(L("Booked time split in two"), snap)
        }
    }

    /** What a new block from [start] to [end] would overlap, if anything (never [exceptId]'s own). */
    fun overlapFor(start: Instant, end: Instant, exceptEntryId: Long? = null): Entry? =
        uiState.value.entries.withBlocksExpanded().firstOrNull { e ->
            val s = e.startAt ?: return@firstOrNull false
            val en = e.endAt ?: s
            e.id != exceptEntryId && s.isBefore(end) && en.isAfter(start)
        }

    /** Puts back a deleted or edited task exactly (rows, links, history, reminders). */
    private fun offerRestore(message: String, snap: com.example.timeapp.data.db.EntryRowsSnapshot) {
        UndoBus.offer(UndoAction(message) {
            viewModelScope.launch {
                repository.restoreRows(snap)
                reminders.reconcile()
            }
        })
    }

    /** Real tracked-time total for one entry, in milliseconds — the sum of every closed
     * [com.example.timeapp.data.db.WorkSessionEntity] for it. */
    fun trackedMs(entryId: Long) = repository.sumTrackedMs(entryId)

    /** Starts work on [entryId] through the one work timer — the same timer Focus shows, so
     * whatever else was running pauses at the same instant (see [com.example.timeapp.data.repository.WorkTimer]). */
    fun startTask(entryId: Long) {
        viewModelScope.launch { repository.timer.start(entryId) }
    }

    /** Pauses the running work (this task), optionally with a resume note. */
    @Suppress("UNUSED_PARAMETER")
    fun pauseTask(entryId: Long, resumeNote: String? = null) {
        viewModelScope.launch { repository.timer.pause(resumeNote) }
    }

    /** Resumes a paused task: back on the timer, whatever else was running pauses. */
    fun resumeTask(entryId: Long) {
        viewModelScope.launch { repository.timer.start(entryId) }
    }

    /** The PDF's flagship interrupt/switch/resume example: one effective timestamp closes the
     * running segment and opens [toId]'s; the task left keeps [resumeNote]. */
    @Suppress("UNUSED_PARAMETER")
    fun switchTask(fromId: Long?, toId: Long, resumeNote: String?) {
        viewModelScope.launch { repository.timer.start(toId, resumeNote = resumeNote) }
    }

    fun importCalendar(items: List<com.example.timeapp.data.Ics.ImportItem>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val n = repository.importCalendar(items)
            // Imported repeats materialise like any other series (with no reminders — none
            // were asked for).
            if (items.any { it.recurrence != null }) reminders.expandRecurrences()
            onDone(n)
        }
    }

    fun linkBookmark(entryId: Long, bookmarkId: Long) {
        viewModelScope.launch { repository.linkBookmark(entryId, bookmarkId) }
    }

    fun unlinkBookmark(entryId: Long, bookmarkId: Long) {
        viewModelScope.launch { repository.unlinkBookmark(entryId, bookmarkId) }
    }

    fun addDependency(taskId: Long, prerequisiteId: Long) {
        viewModelScope.launch { repository.addDependency(taskId, prerequisiteId) }
    }

    fun removeDependency(taskId: Long, prerequisiteId: Long) {
        viewModelScope.launch { repository.removeDependency(taskId, prerequisiteId) }
    }

    /** A task's work sessions, newest first — Task Detail's Sessions card. */
    fun sessions(entryId: Long) = repository.timer.observeForEntry(entryId)

    /** Any work ever recorded, by any route (a task, Focus, a widget). */
    val hasTrackedTime = repository.timer.observeAnyEver()

    val sessionActions = com.example.timeapp.ui.focus.sessionActionsFor(repository)

    /** A task's state transitions, newest first — Task Detail's History. */
    fun events(entryId: Long) = repository.observeEvents(entryId)

    /** Task Detail's "Next steps" checklist — see [EntryRepository.observeSubtasks]. */
    fun subtasks(entryId: Long) = repository.observeSubtasks(entryId)

    fun addSubtask(entryId: Long, title: String, sortOrder: Int) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.addSubtask(entryId, title, sortOrder) }
    }

    /** Task Detail's "Turn into steps", with Undo. */
    fun checklistToSubtasks(entryId: Long) {
        viewModelScope.launch {
            val (ids, before) = repository.checklistToSubtasks(entryId) ?: return@launch
            UndoBus.offer(UndoAction(if (ids.size == 1) L("1 item moved to Next steps") else L("%1\$s items moved to Next steps", ids.size)) {
                viewModelScope.launch { repository.undoChecklistToSubtasks(entryId, ids, before) }
            })
        }
    }

    fun toggleSubtask(subtask: com.example.timeapp.data.Subtask) {
        viewModelScope.launch { repository.toggleSubtask(subtask) }
    }

    fun deleteSubtask(id: Long) {
        viewModelScope.launch { repository.deleteSubtask(id) }
    }

    /** Stop-and-complete in one step: closes the session (if any) and marks the task Done. */
    fun finishTask(entryId: Long) {
        val entry = uiState.value.find(entryId) ?: return
        viewModelScope.launch { repository.complete(entry) }
    }

    class Factory(
        private val repository: EntryRepository,
        private val reminders: ReminderRepository,
        private val commitments: com.example.timeapp.data.repository.CommitmentRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PlanViewModel(repository, reminders, commitments) as T
    }
}
