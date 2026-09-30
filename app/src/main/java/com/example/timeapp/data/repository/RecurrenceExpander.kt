package com.example.timeapp.data.repository

import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.db.EntryEntity
import com.example.timeapp.data.db.MoneyRecordEntity
import com.example.timeapp.data.db.ReminderEntity
import com.example.timeapp.data.db.withTiming
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.notify.ReminderScheduler
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Materialises future occurrences of recurring tasks, events and bills, up to a bounded horizon.
 *
 * Each occurrence is its own record sharing the series' `seriesId`, so paying October's rent
 * or finishing Tuesday's task never touches the rest of the series. Nothing is generated past
 * [HORIZON_DAYS], per the PDF's "avoid infinite pre-generation: expand a bounded date window"
 * — running this again later simply extends the window.
 *
 * Idempotent: an occurrence is only created when the series has no record on that date and
 * the date is not in the rule's `skipped` set, so running it on every app start can never
 * duplicate a row or resurrect a deleted one.
 *
 * When the rule says `remind`, each new occurrence gets its own reminder row in the same
 * pass, armed immediately when a [scheduler] is supplied (otherwise the next reconcile arms
 * it). This is the second write path for reminders besides [ReminderRepository], and keeps
 * the same invariant: a row is never inserted without its alarm being armed or left for
 * reconcile. Only occurrences created *here* get one, so switching the reminder off on a
 * single occurrence is never undone by a later expansion.
 */
class RecurrenceExpander(
    private val db: TimeDatabase,
    private val scheduler: ReminderScheduler? = null,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun expand(today: LocalDate = LocalDate.now()) {
        val horizon = today.plusDays(HORIZON_DAYS)
        expandEntries(today, horizon)
        expandPlans(today, horizon)
    }

    private suspend fun expandEntries(today: LocalDate, horizon: LocalDate) {
        val dao = db.entryDao()
        dao.allInSeries().groupBy { it.seriesId }.forEach { (_, occurrences) ->
            val template = occurrences.minByOrNull { it.id } ?: return@forEach
            val rule = template.recurrence ?: return@forEach
            val templateDate = template.occurrenceDate(zone) ?: return@forEach
            val taken = occurrences.mapNotNull { it.occurrenceDate(zone) }.toMutableSet()
            var cursor = taken.maxOrNull() ?: return@forEach
            while (true) {
                val next = rule.nextAfter(cursor, anchor = templateDate) ?: break
                if (next.isAfter(horizon)) break
                cursor = next
                if (next in rule.skipped || !taken.add(next)) continue
                // A block keeps its time of day and length; a date-only task stays date-only.
                val start = template.startAt?.let { shiftToDate(it, templateDate, next) }
                val end = template.endAt?.let { e -> start?.plus(Duration.between(template.startAt, e)) }
                val id = dao.insert(
                    template.copy(
                        id = 0,
                        dueOn = template.dueOn?.let { next },
                        startAt = start,
                        endAt = end,
                        // A generated occurrence starts clean: not done, not carrying the
                        // template's tracked state or its paused resume note.
                        status = EntryStatus.PLANNED,
                        resumeNote = null,
                        meta = null,
                        // The calendar UID identifies the imported series, not each copy.
                        icsUid = null,
                    ),
                )
                // Tags live in a join table, so template.copy() alone would drop them.
                db.tagDao().copyLinks(template.id, id)
                if (rule.remind && !next.isBefore(today)) {
                    val isEvent = template.kind == EntryKind.EVENT
                    val kind = if (isEvent) ReminderOwnerKind.EVENT else ReminderOwnerKind.TASK
                    // Same timing as the series' first reminder when it had a chosen one.
                    val timing = db.reminderDao().getForOwner(kind, template.id)?.timing
                        ?: if (isEvent) com.example.timeapp.data.ReminderTiming.EVENT_DEFAULT else com.example.timeapp.data.ReminderTiming.TASK_DEFAULT
                    timing.at(next, start, zone)?.let { remind(kind, id, it, timing) }
                }
            }
        }
    }

    private suspend fun expandPlans(today: LocalDate, horizon: LocalDate) {
        val dao = db.moneyRecordDao()
        dao.allPlansInSeries().groupBy { it.seriesId }.forEach { (_, occurrences) ->
            val template = occurrences.minByOrNull { it.id } ?: return@forEach
            val rule = template.recurrence ?: return@forEach
            val taken = occurrences.mapNotNull { it.dueOn }.toMutableSet()
            var cursor = taken.maxOrNull() ?: return@forEach
            val anchor = template.dueOn
            while (true) {
                val next = rule.nextAfter(cursor, anchor) ?: break
                if (next.isAfter(horizon)) break
                cursor = next
                if (next in rule.skipped || !taken.add(next)) continue
                val id = dao.insert(
                    template.copy(
                        id = 0,
                        dueOn = next,
                        // Each occurrence carries its own payments — a settled October
                        // never makes November look paid.
                        paidMinor = 0,
                        settledAt = null,
                        status = PlanStatus.PLANNED,
                        linkedToPlan = false,
                        linkedPlanId = null,
                    ),
                )
                if (rule.remind && !next.isBefore(today)) {
                    val timing = db.reminderDao().getForOwner(ReminderOwnerKind.PLAN, template.id)?.timing
                        ?: com.example.timeapp.data.ReminderTiming.PLAN_DEFAULT
                    timing.at(next, null, zone)?.let { remind(ReminderOwnerKind.PLAN, id, it, timing) }
                }
            }
        }
    }

    private suspend fun remind(kind: ReminderOwnerKind, ownerId: Long, at: Instant, timing: com.example.timeapp.data.ReminderTiming? = null) {
        val dao = db.reminderDao()
        val row = ReminderEntity(ownerKind = kind, ownerId = ownerId, scheduledAt = at).withTiming(timing)
        val id = dao.insert(row)
        scheduler?.arm(row.copy(id = id).toDomain())
    }

    /** Same local time of day on [to] as [instant] had on [from] — DST-safe, unlike adding
     * a fixed number of hours. */
    private fun shiftToDate(instant: Instant, from: LocalDate, to: LocalDate): Instant {
        val local = instant.atZone(zone).toLocalDateTime()
        val days = java.time.temporal.ChronoUnit.DAYS.between(from, to)
        return local.plusDays(days).atZone(zone).toInstant()
    }

    private companion object {
        /** Two months of look-ahead: far enough to plan a month's bills, short enough that a
         * rule edit does not leave a long tail of stale rows behind it. */
        const val HORIZON_DAYS = 60L
    }
}

/** The day an entry "falls on" within its series: its due date, else a block's start day. */
internal fun EntryEntity.occurrenceDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    dueOn ?: startAt?.atZone(zone)?.toLocalDate()

/** Convenience for the two call sites that only have entities to hand. */
internal fun EntryEntity.isSeriesTemplate(): Boolean = seriesId != null && seriesId == id

internal fun MoneyRecordEntity.isSeriesTemplate(): Boolean = seriesId != null && seriesId == id
