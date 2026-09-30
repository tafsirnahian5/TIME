package com.example.timeapp.data

import java.time.Instant
import java.time.LocalDate

enum class EntryKind { TASK, EVENT }

/** Task priority. Ordered low→urgent so sorting and the "High" filter can compare directly. */
enum class Priority { LOW, NORMAL, HIGH, URGENT }
/** [PAUSED] is an intentional interruption; [BLOCKED] means progress depends on something
 * else (the PDF's release-1.1 column). Stored by name, so adding a value needs no migration. */
enum class EntryStatus { OVERDUE, DOING, PAUSED, BLOCKED, PLANNED, UNSCHEDULED, DONE }

/** Where a task goes when it stops being Done or Blocked: Planned if it has a date or a
 * block, otherwise the inbox. */
fun reopenedStatus(entry: Entry): EntryStatus =
    if (entry.dueOn != null || entry.startAt != null) EntryStatus.PLANNED else EntryStatus.UNSCHEDULED

/**
 * What a task shows as today: a Planned task past its due date reads Overdue. Overdue is
 * **display-only** — derived here, never stored (see [com.example.timeapp.data.db.toEntity]),
 * so moving the due date clears it by itself.
 */
fun Entry.displayStatus(today: LocalDate): EntryStatus =
    if (status == EntryStatus.PLANNED && dueOn != null && dueOn.isBefore(today)) EntryStatus.OVERDUE else status

/**
 * One line of a task's History: "Switched to Client call", "Resumed", "Marked blocked".
 * [relatedTitle] names the task switched to, when known.
 */
fun transitionLabel(from: EntryStatus?, to: EntryStatus, relatedTitle: String?, switched: Boolean): String = when {
    to == EntryStatus.PAUSED && switched -> L("Switched to %1\$s", (relatedTitle ?: L("another task")))
    to == EntryStatus.PAUSED -> L("Paused")
    to == EntryStatus.DOING && from == EntryStatus.PAUSED -> L("Resumed")
    to == EntryStatus.DOING && from == EntryStatus.BLOCKED -> L("Started while blocked")
    to == EntryStatus.DOING -> L("Started")
    to == EntryStatus.BLOCKED -> L("Marked blocked")
    to == EntryStatus.DONE -> L("Completed")
    from == EntryStatus.DONE -> L("Reopened")
    from == EntryStatus.BLOCKED -> L("Unblocked")
    else -> L("Moved to %1\$s", to.label())
}

/**
 * Whether linking [taskId] to wait on [prerequisiteId] would close a loop, given the existing
 * [links] (task → prerequisite). A loop is a task that could never start, so it's refused.
 */
fun dependencyWouldCycle(links: List<Pair<Long, Long>>, taskId: Long, prerequisiteId: Long): Boolean {
    if (taskId == prerequisiteId) return true
    val waitsOn = links.groupBy({ it.first }, { it.second })
    // Does the prerequisite (transitively) already wait on the task?
    val seen = mutableSetOf<Long>()
    val stack = ArrayDeque(listOf(prerequisiteId))
    while (stack.isNotEmpty()) {
        val next = stack.removeLast()
        if (next == taskId) return true
        if (seen.add(next)) waitsOn[next]?.let { stack.addAll(it) }
    }
    return false
}

/** A Board column; see [com.example.timeapp.data.db.BoardColumnEntity]. [wipLimit] is the
 * PDF's optional soft limit — exceeding it warns, never blocks. */
data class BoardColumn(val id: Long, val name: String, val status: EntryStatus, val sortOrder: Int, val wipLimit: Int? = null)

/** The starting board — the PDF's default columns. Scaffolding, recreated after a wipe. */
val DefaultBoardColumns = listOf(
    L("Inbox") to EntryStatus.UNSCHEDULED,
    L("Ready") to EntryStatus.PLANNED,
    L("Doing") to EntryStatus.DOING,
    L("Paused") to EntryStatus.PAUSED,
    L("Blocked") to EntryStatus.BLOCKED,
    L("Done") to EntryStatus.DONE,
)

/** Statuses a column can stand for. Overdue is display-only; it lives in a Planned column. */
val BoardStatuses = DefaultBoardColumns.map { it.second }

/**
 * The column a card shows in: the one it was put in, while that column still stands for the
 * card's status; otherwise the first column of that status. So a card started from "Review"
 * lands in Doing, and returns to "Review" if moved back to that status later.
 */
fun boardColumnFor(entry: Entry, columns: List<BoardColumn>): BoardColumn? {
    val status = if (entry.status == EntryStatus.OVERDUE) EntryStatus.PLANNED else entry.status
    columns.firstOrNull { it.id == entry.columnId && it.status == status }?.let { return it }
    return columns.filter { it.status == status }.minByOrNull { it.sortOrder }
}

/** Every status keeps at least one column, so no card can be left with nowhere to show. */
fun canDeleteColumn(column: BoardColumn, columns: List<BoardColumn>): Boolean =
    columns.count { it.status == column.status } > 1

/** Display copy for a status; the enum name is storage. */
fun EntryStatus.label(): String = when (this) {
    EntryStatus.OVERDUE -> L("Overdue")
    EntryStatus.DOING -> L("Doing")
    EntryStatus.PAUSED -> L("Paused")
    EntryStatus.BLOCKED -> L("Blocked")
    EntryStatus.PLANNED -> L("Planned")
    EntryStatus.UNSCHEDULED -> L("No date")
    EntryStatus.DONE -> L("Done")
}

/** A category (or project) as a record shows it: resolved at read time. [icon] is a [com.example.timeapp.ui.components.CategoryIcons] key. */
data class WorkCategory(val name: String, val color: Long, val icon: String? = null)

/**
 * Work categories, money categories and projects share one table; [kind] separates the lists.
 * A project is exactly a category's shape and lifecycle — name, colour, archived-never-deleted,
 * resolved at read time — so it reuses all of that rather than duplicating it. The difference
 * is only what it answers: a category says what *kind* of work, a project says what it's *for*.
 */
enum class CategoryKind { WORK, MONEY, PROJECT }

data class Category(
    val id: Long,
    val name: String,
    val colorArgb: Long,
    val kind: CategoryKind,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    /** Monthly spending limit in poisha, money categories only. Null means no limit —
     * distinct from a limit of zero, so "unset" never reads as "already over budget". */
    val budgetMinor: Long? = null,
    /** Icon key (see `CategoryIcons`), or null for the colour dot. */
    val icon: String? = null,
)

/** The starter set created on first launch — scaffolding, not demo data: an app with no
 * categories at all cannot categorise anything. All of them are editable and archivable. */
val DefaultWorkCategories = listOf(
    L("Coding") to 0xFF6C4ED9,
    L("Study") to 0xFF2E7DE8,
    L("Job") to 0xFF12A05C,
    L("Personal") to 0xFFE5A32B,
)

val DefaultMoneyCategories = listOf(
    L("Food") to 0xFF12A05C,
    L("Transport") to 0xFF2E7DE8,
    L("Bills") to 0xFFE5A32B,
    L("Learning") to 0xFF6C4ED9,
)

val CategoryCoding = WorkCategory(L("Coding"), 0xFF6C4ED9)
val CategoryStudy = WorkCategory(L("Study"), 0xFF2E7DE8)
val CategoryJob = WorkCategory(L("Job"), 0xFF12A05C)
val CategoryPersonal = WorkCategory(L("Personal"), 0xFFE5A32B)

/** One table for tasks and events; `kind` discriminates. */
data class Entry(
    val id: Long,
    val kind: EntryKind,
    val title: String,
    val category: WorkCategory? = null,
    val status: EntryStatus,
    val meta: String? = null,          // free-form supporting line, e.g. "42 min left"
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val isFixed: Boolean = false,
    val dueOn: LocalDate? = null,      // structured due date — re-parseable, unlike meta's free text
    val estimateMs: Long? = null,      // effort estimate, used by Reschedule's remaining-effort math
    val resumeNote: String? = null,    // set when Switch Work pauses this task, shown on resume
    val description: String? = null,   // markdown-lite text, see ui/richtext/
    val recurrence: Recurrence? = null,  // repeat rule; occurrences are separate rows, see Recurrence
    val seriesId: Long? = null,          // groups the occurrences generated from one rule
    val priority: Priority = Priority.NORMAL,
    val categoryId: Long? = null,        // resolved to [category] at read time, see EntryRepository
    val projectId: Long? = null,         // a PROJECT-kind category, resolved to [project] at read time
    val project: WorkCategory? = null,
    /** Lower-case tag names, read from the entry_tags join. Written only through
     * EntryRepository.setTags — a plain update never touches them. */
    val tags: List<String> = emptyList(),
    val boardOrder: Int = 0,
    /** Tasks this one waits on (the PDF's dependencies), resolved from task_dependencies. */
    val prerequisiteIds: List<Long> = emptyList(),
    /** The Board column it was put in, if any — see [boardColumnFor]. */
    val columnId: Long? = null,
    /** Bookmarks linked to this task (task_bookmarks). */
    val bookmarkIds: List<Long> = emptyList(),
    /** The calendar UID an imported event came with — how re-importing the same .ics offers
     * "update" instead of making a second copy. Null for anything made in T!ME. */
    val icsUid: String? = null,
    /** When it last became Done (null while not done, or done before this was recorded). */
    val completedAt: Instant? = null,
    /** Blocks beyond the first ([startAt]/[endAt]), read from schedule_blocks. */
    val extraBlocks: List<ScheduleBlock> = emptyList(),
    /** Set while archived: out of every default view, still in reports and search. */
    val archivedAt: Instant? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
}

/** One closed work session, exposed only for Tier 10/11 stats aggregation — everywhere else
 * only the aggregate durations from [com.example.timeapp.data.repository.EntryRepository]
 * are needed, not individual spans. */
data class WorkSessionSpan(val entryId: Long?, val startedAt: Instant, val endedAt: Instant)

/** Tier 11 — one calendar day's Start-day/break/End-day timeline, distinct from
 * [WorkSessionSpan] (which tracks task-level active work). */
enum class WorkdaySegmentKind { ON_DUTY, BREAK }

data class WorkdaySegment(
    val id: Long,
    val date: LocalDate,
    val kind: WorkdaySegmentKind,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val originalStartedAt: Instant? = null,
    val originalEndedAt: Instant? = null,
    val correctionReason: String? = null,
)

/** Tier 11 — one table for both Daily and Weekly reviews; `period`+`periodKey` discriminate
 * (an ISO date for DAILY, an ISO week string like "2026-W37" for WEEKLY), matching
 * `MoneyRecordEntity`'s existing one-table-two-purposes pattern. */
enum class ReviewPeriod { DAILY, WEEKLY, MONTHLY }

data class Review(
    val id: Long,
    val period: ReviewPeriod,
    val periodKey: String,
    val note: String,
    val savedAt: Instant,
)

/** One scheduled reminder for exactly one owner record. `delivered` enforces the PDF's
 * "deliver at most once for that occurrence" rule across duplicate alarm deliveries; the
 * owner is always re-read at delivery time, so a completed/deleted owner never notifies. */
/** [HABIT_SNOOZE] is a one-off "remind me again" for a habit, beside the habit's own row that
 * walks day to day (owner id = the habit). Stored by name. */
enum class ReminderOwnerKind { TASK, EVENT, PLAN, HABIT, TIMER, FOCUS, HABIT_SNOOZE, LOAN }

data class Reminder(
    val id: Long,
    val ownerKind: ReminderOwnerKind,
    val ownerId: Long,
    val scheduledAt: Instant,
    val delivered: Boolean = false,
)

enum class WalletKind { CASH, BANK, MOBILE }

data class Wallet(
    val id: Long,
    val name: String,
    val kind: WalletKind,
    val openingBalanceMinor: Long,
    val openingDate: LocalDate,
    /** Archived wallets leave the pickers and the wallet list; their balance and history stay
     * (the PDF: "archive wallets with history rather than deleting their records"). */
    val archivedAt: Instant? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
}

/** [REFUND] is money coming back for an earlier expense (`refundOfId`). It adds to a wallet
 * but is **not income**: reports count it as negative spending in the original's category,
 * per the PDF ("refunds reduce the original expense category"). Stored by name. */
/** [LOAN_OUT] / [LOAN_IN]: money lent or repaid out of a wallet / borrowed or repaid into one
 * (see `data/Loans.kt`) — like a transfer, never income or spending. */
enum class Direction { COST, INCOME, TRANSFER, REFUND, LOAN_OUT, LOAN_IN }

/**
 * The PDF's plan statuses are Planned, Partly paid, Settled and Cancelled; Unscheduled is
 * Planned without a date. [OVERDUE] is a **display badge only** (see [displayPlanStatus]) —
 * never stored, because a stored Overdue outlived a due date that was moved later.
 * [CANCELLED] releases what was still owed but keeps every payment. Stored by name.
 */
enum class PlanStatus { OVERDUE, PARTLY_PAID, PLANNED, UNSCHEDULED, SETTLED, CANCELLED }

/**
 * The status to store for a plan: an unpaid open plan is Planned with a due date and
 * Unscheduled without one — derived from the date on every write, so adding a date to an
 * undated plan (which used to leave it Unscheduled and out of every forecast) or moving an
 * overdue one later can't leave a stale status behind.
 */
fun storedPlanStatus(status: PlanStatus?, dueOn: LocalDate?): PlanStatus? = when (status) {
    PlanStatus.OVERDUE, PlanStatus.PLANNED, PlanStatus.UNSCHEDULED ->
        if (dueOn == null) PlanStatus.UNSCHEDULED else PlanStatus.PLANNED
    else -> status
}

/** Planned and actual money in one table; `settledAt`/status distinguishes them. */
data class MoneyRecord(
    val id: Long,
    val direction: Direction,
    val title: String,
    val categoryColor: Long? = null,
    /** Resolved with [categoryColor] at read time (Phase 20C). */
    val categoryIcon: String? = null,
    val amountMinor: Long,
    val paidMinor: Long = 0,
    val dueOn: LocalDate? = null,
    val settledAt: Instant? = null,
    val walletId: Long? = null,
    val transferToWalletId: Long? = null,
    val status: PlanStatus? = null,    // null for already-settled ledger-only rows
    val linkedToPlan: Boolean = false,
    val linkedPlanId: Long? = null,    // set on a payment row so deleting it can un-pay the plan
    val note: String? = null,          // markdown-lite text, see ui/richtext/
    val categoryId: Long? = null,      // resolved to [categoryColor] at read time
    val recurrence: Recurrence? = null,  // repeat rule; occurrences are separate rows, see Recurrence
    val seriesId: Long? = null,          // groups the occurrences generated from one rule
    val refundOfId: Long? = null,        // a REFUND row's original expense
    val loanId: Long? = null,            // a LOAN_IN / LOAN_OUT row's loan
    val priority: Priority = Priority.NORMAL, // a plan's priority (the PDF's plan fields)
    val taskId: Long? = null,            // the task it's for, if any
    val projectId: Long? = null,         // the project it's for, if any
) {
    val isOpen: Boolean get() = status != null && status != PlanStatus.SETTLED && status != PlanStatus.CANCELLED
    val remainingMinor: Long get() = (amountMinor - paidMinor).coerceAtLeast(0)

    /** Past its due date with money still owed — partly paid or not (a date badge, not a status). */
    fun isOverdue(today: LocalDate): Boolean = isOpen && dueOn != null && dueOn.isBefore(today) && remainingMinor > 0

    /** What a plan shows: an unpaid plan past its date reads Overdue; the rest as stored. */
    fun displayPlanStatus(today: LocalDate): PlanStatus? = when {
        status == PlanStatus.PARTLY_PAID || !isOpen -> status
        dueOn == null -> PlanStatus.UNSCHEDULED
        dueOn.isBefore(today) -> PlanStatus.OVERDUE
        else -> PlanStatus.PLANNED
    }
}

/**
 * What one settled ledger row does to [walletId]'s balance, signed. The single definition —
 * it used to be re-derived in four places, which is exactly how a new row type (refunds)
 * would have been counted in some views and not others.
 */
fun MoneyRecord.walletEffect(walletId: Long): Long = when (direction) {
    Direction.INCOME, Direction.REFUND, Direction.LOAN_IN -> if (this.walletId == walletId) paidMinor else 0L
    Direction.COST, Direction.LOAN_OUT -> if (this.walletId == walletId) -paidMinor else 0L
    Direction.TRANSFER ->
        (if (transferToWalletId == walletId) paidMinor else 0L) - (if (this.walletId == walletId) paidMinor else 0L)
}

/** The same across every wallet at once: transfers net to zero. */
val MoneyRecord.totalEffect: Long
    get() = when (direction) {
        Direction.INCOME, Direction.REFUND, Direction.LOAN_IN -> paidMinor
        Direction.COST, Direction.LOAN_OUT -> -paidMinor
        Direction.TRANSFER -> 0L
    }

/** Net spending this row represents: an expense adds, a refund takes back, others are 0. */
val MoneyRecord.netSpendMinor: Long
    get() = when (direction) {
        Direction.COST -> paidMinor
        Direction.REFUND -> -paidMinor
        else -> 0L
    }

/**
 * The "outstanding this month" figure both Today and Money › Planned show.
 *
 * Month-scoped on purpose, which is what the "Overdue carryover — included above" card
 * sitting beside it already claimed: everything still owed from earlier months plus
 * everything falling due before this one ends. Both screens previously summed *every*
 * open plan while labelling the result "this month", so a bill due in December inflated
 * September's figure. A plan with no due date counts as owed now rather than being
 * deferred forever; a plan due in a later month is left for that month.
 */
fun List<MoneyRecord>.outstandingThisMonth(today: LocalDate = LocalDate.now()): Long {
    val endOfMonth = today.withDayOfMonth(today.lengthOfMonth())
    return filter { it.isOpen && (it.dueOn == null || !it.dueOn!!.isAfter(endOfMonth)) }
        .sumOf { it.remainingMinor }
}

enum class CollectionKind { BUCKET, SPARKS, LIST }

/** Sparks and the Bucket list are the app's own and stored in English, so they're translated
 * where shown; a collection the user named is shown exactly as typed. */
val IdeaCollection.displayName: String get() = if (kind == CollectionKind.LIST) name else L(name)

data class IdeaCollection(
    val id: Long,
    val name: String,
    val kind: CollectionKind,
    val icon: String,          // Material Symbols name
    val iconColorArgb: Long,
    val count: Int,
    val doneCount: Int = 0,
)

data class Idea(
    val id: Long,
    val collectionId: Long,
    val title: String,
    val body: String? = null,
    val isDone: Boolean = false,
    val costsMoneyEstimateMinor: Long? = null,
    val promotedToPlan: Boolean = false,
    val touchedLabel: String? = null, // "2d", "5d", "3w" for the recently-touched list
)

data class Lap(val index: Int, val label: String, val splitLabel: String, val totalLabel: String)

data class Achievement(
    val title: String,
    val icon: String,
    val earnedLabel: String? = null,   // e.g. "Earned 28 Aug"
    val progressLabel: String? = null, // e.g. "3 of 5 daily reviews written"
    val progressFraction: Float? = null,
    /** The collection it belongs to (Work, Habits, Money, Reviews, Safety). */
    val collection: String = "",
)

/** Formats integer minor units (poisha) as "Tk 1,200" or "-Tk 1,200" (or "৳1,200", per Language &
 * region) — poisha only when they exist. */
fun Long.toTaka(): String {
    val sign = if (this < 0) "-" else ""
    val magnitude = kotlin.math.abs(this)
    val whole = magnitude / 100
    val fraction = (magnitude % 100).toInt()
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    // Poisha are shown only when they exist, so whole amounts stay uncluttered but a
    // Tk 1,200.50 bill never silently displays as Tk 1,200.
    val decimals = if (fraction == 0) "" else ".${fraction.toString().padStart(2, '0')}"
    return Lang.digits("$sign${DisplayFormats.currencyPrefix}$grouped$decimals")
}

/** "Next steps" checklist item on Task Detail — no PDF spec governs this; see EntryRepository. */
data class Subtask(
    val id: Long,
    val entryId: Long,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int = 0,
)

data class Bookmark(
    val id: Long,
    val title: String,
    val url: String,
    val folder: String,
    val favourite: Boolean = false,
    val note: String? = null,          // markdown-lite text, see ui/richtext/
    /** When it was saved; null for links saved before this was recorded (never guessed). */
    val addedAt: java.time.Instant? = null,
    /** Archived links leave the list but keep their task links and can come back. */
    val archivedAt: java.time.Instant? = null,
)

/** One of a day's chosen commitments (see `commitments`). [removedAt] set = taken off that day. */
data class Commitment(
    val date: LocalDate,
    val entryId: Long,
    val committedAt: Instant,
    val removedAt: Instant? = null,
    val removeNote: String? = null,
) {
    val active: Boolean get() = removedAt == null
}

/** What a money row is for: its category and, optionally, a task or project (Phase 13). */
data class MoneyLinks(val categoryId: Long? = null, val taskId: Long? = null, val projectId: Long? = null, val priority: Priority = Priority.NORMAL)

/** At most this many commitments a day — few enough to mean something. */
const val MAX_COMMITMENTS = 3

/** Whether [time] falls in the user's quiet hours, from settings. */
fun com.example.timeapp.ui.AppSettingsUiState.isQuiet(time: java.time.LocalTime = java.time.LocalTime.now()): Boolean =
    isWithinQuietHours(
        time,
        runCatching { java.time.LocalTime.parse(quietStart) }.getOrDefault(java.time.LocalTime.of(22, 0)),
        runCatching { java.time.LocalTime.parse(quietEnd) }.getOrDefault(java.time.LocalTime.of(7, 0)),
        quietHoursEnabled,
    )

/** One additional block of time reserved for a task (see ScheduleBlockEntity). */
data class ScheduleBlock(val id: Long, val entryId: Long, val startAt: Instant, val endAt: Instant, val isFixed: Boolean = false)

/** Every block of time reserved for this entry — its own, then the extras — earliest first. */
/** "Thu 1 Oct · 14:00–15:00", or both dates when a booking crosses midnight. */
fun bookingLabel(start: Instant, end: Instant, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    val s = start.atZone(zone)
    val e = end.atZone(zone)
    val day = displayPattern("EEE d MMM")
    val time = displayPattern("HH:mm")
    return if (s.toLocalDate() == e.toLocalDate()) "${s.format(day)} · ${s.format(time)}–${e.format(time)}"
    else "${s.format(day)} ${s.format(time)} – ${e.format(day)} ${e.format(time)}"
}

/**
 * A Plan/Board row's detail line — the due date, else the first booked time — built from live
 * fields each time it's shown. It used to be text stored at creation ("Due Wed 30 Sept"), which
 * went stale the moment the due date changed, and kept the language and date format it was
 * written in.
 */
fun Entry.whenLine(zone: java.time.ZoneId = java.time.ZoneId.systemDefault(), now: Instant = Instant.now()): String? {
    // Booked time first — it's the more exact fact; a row used to say only "Due Tue 29 Sept" for
    // a task booked 9:15–11:45 pm that day. The next booking not yet over, else the last one.
    val spans = blockSpans()
    val booking = spans.firstOrNull { it.second.isAfter(now) } ?: spans.lastOrNull()
    val due = dueOn
    if (booking == null) return due?.let { L("Due %1\$s", it.format(displayPattern("EEE d MMM"))) }
    val label = bookingLabel(booking.first, booking.second, zone)
    return if (due == null || due == booking.first.atZone(zone).toLocalDate()) label
    else L("%1\$s · due %2\$s", label, due.format(displayPattern("EEE d MMM")))
}

fun Entry.blockSpans(): List<Pair<Instant, Instant>> {
    val own = if (startAt != null && endAt != null) listOf(startAt to endAt) else emptyList()
    return (own + extraBlocks.map { it.startAt to it.endAt }).sortedBy { it.first }
}

/**
 * One entry per reserved block: the entry itself, plus a copy carrying each extra block's times
 * (same id, so tapping any of them opens the task). Everything that reads `startAt` — the
 * calendar, conflict checks, Workload, Today, the widget — goes through this, so a task split
 * into two blocks shows and counts both.
 */
fun List<Entry>.withBlocksExpanded(): List<Entry> = flatMap { e ->
    if (e.extraBlocks.isEmpty()) listOf(e)
    else listOf(e.copy(extraBlocks = emptyList())) +
        e.extraBlocks.map { b -> e.copy(startAt = b.startAt, endAt = b.endAt, isFixed = b.isFixed, extraBlocks = emptyList()) }
}
