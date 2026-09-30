package com.example.timeapp.data.repository

import com.example.timeapp.data.Reminder
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.db.toEntity
import com.example.timeapp.data.db.withTiming
import com.example.timeapp.data.notify.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * The single write path for reminders — every change to a reminder row also arms or cancels
 * its alarm here, so the database and the pending-alarm set can never silently diverge.
 */
class ReminderRepository(
    private val db: TimeDatabase,
    private val scheduler: ReminderScheduler,
) {
    private val dao get() = db.reminderDao()

    fun observeAll(): Flow<List<Reminder>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForOwner(kind: ReminderOwnerKind, ownerId: Long): Flow<Reminder?> =
        dao.observeForOwner(kind, ownerId).map { it?.toDomain() }

    suspend fun getForOwner(kind: ReminderOwnerKind, ownerId: Long): Reminder? =
        dao.getForOwner(kind, ownerId)?.toDomain()

    suspend fun pendingCount(): Int = dao.pendingCount()

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    /** Sets (or moves) the one reminder this owner is allowed, replacing any existing one. */
    suspend fun setReminder(kind: ReminderOwnerKind, ownerId: Long, at: Instant, timing: com.example.timeapp.data.ReminderTiming? = null): Reminder {
        clearReminder(kind, ownerId)
        val id = dao.insert(Reminder(0, kind, ownerId, at).toEntity().withTiming(timing))
        val saved = Reminder(id, kind, ownerId, at)
        scheduler.arm(saved)
        return saved
    }

    /** The timings owners' reminders were set with — forms prefill from these when editing. */
    fun observeTimings(): Flow<Map<Pair<ReminderOwnerKind, Long>, com.example.timeapp.data.ReminderTiming>> =
        dao.observeAll().map { list -> list.mapNotNull { r -> r.timing?.let { (r.ownerKind to r.ownerId) to it } }.toMap() }

    /**
     * Snooze: the reminder comes back at [at]. Only the reminder moves — the task's due date,
     * the bill's due date and the block's time stay as they are (the PDF: snooze moves the
     * reminder, never the deadline). A habit's own reminder walks day to day, so snoozing one
     * adds a one-off [ReminderOwnerKind.HABIT_SNOOZE] instead.
     */
    suspend fun snooze(reminderId: Long, at: Instant) {
        val row = dao.getById(reminderId) ?: return
        if (row.ownerKind == ReminderOwnerKind.HABIT) {
            setReminder(ReminderOwnerKind.HABIT_SNOOZE, row.ownerId, at)
            return
        }
        scheduler.cancel(row.id)
        val moved = row.copy(scheduledAt = at, delivered = false)
        dao.update(moved)
        scheduler.arm(moved.toDomain())
    }

    suspend fun clearReminder(kind: ReminderOwnerKind, ownerId: Long) {
        dao.getForOwner(kind, ownerId)?.let {
            scheduler.cancel(it.id)
            dao.delete(it.id)
        }
    }

    /** Materialises repeat occurrences up to the horizon, arming each new occurrence's
     * reminder in the same pass (see [RecurrenceExpander]). Callers run it *after* setting the
     * template's own reminder, so a new series is fully armed before the form closes. */
    suspend fun expandRecurrences() = RecurrenceExpander(db, scheduler).expand()

    /** Re-arms everything still pending — app start, and after a backup restore. Reminders
     * missed by more than an hour are summarised instead (see `splitMissed`). */
    suspend fun reconcile() = scheduler.reconcile(db)

    /**
     * After a timezone change, moves date-anchored reminders to the same wall-clock time in the
     * new zone — a task's 09:00 on its due day, a bill's 09:00 the day before, a habit's own
     * time — and leaves instant-anchored ones alone: a block's start and a timer's end are
     * moments, not local times. Callers reconcile afterwards.
     */
    suspend fun reanchorForTimezone() {
        val zone = java.time.ZoneId.systemDefault()
        dao.pending().forEach { r ->
            val at: Instant? = when (r.ownerKind) {
                // A task reminding at its block's start keeps that instant; one on its due date
                // follows the date.
                ReminderOwnerKind.TASK -> db.entryDao().getById(r.ownerId)?.let { e ->
                    // A chosen timing is re-read in the new zone; an old one without a timing
                    // falls back to the default when it wasn't the block's start.
                    r.timing?.at(e.dueOn, e.startAt, zone)
                        ?: e.takeIf { it.startAt != r.scheduledAt }?.dueOn?.let { ReminderScheduler.defaultTimeFor(it) }
                }
                ReminderOwnerKind.PLAN -> db.moneyRecordDao().getById(r.ownerId)?.dueOn?.let { due ->
                    r.timing?.at(due, null, zone) ?: ReminderScheduler.planTimeFor(due)
                }
                ReminderOwnerKind.HABIT -> db.habitDao().getById(r.ownerId)?.toDomain()?.let { habit ->
                    habit.remindAt?.let { time ->
                        com.example.timeapp.data.nextHabitReminder(habit.schedule, time, java.time.LocalDateTime.now(zone))
                            .atZone(zone).toInstant()
                    }
                }
                ReminderOwnerKind.LOAN -> db.loanDao().getById(r.ownerId)?.dueOn?.let { due ->
                    (r.timing ?: com.example.timeapp.data.ReminderTiming.TASK_DEFAULT).at(due, null, zone)
                }
                ReminderOwnerKind.EVENT, ReminderOwnerKind.TIMER, ReminderOwnerKind.FOCUS, ReminderOwnerKind.HABIT_SNOOZE -> null
            }
            if (at != null && at != r.scheduledAt) {
                scheduler.cancel(r.id)
                dao.update(r.copy(scheduledAt = at))
            }
        }
    }
}
