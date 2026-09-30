package com.example.timeapp.data

import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** How late a reminder may still arrive as itself when alarms are rebuilt. */
val MISSED_GRACE: Duration = Duration.ofHours(1)

/**
 * Splits pending reminders when alarms are rebuilt (boot, app start, a clock change): those
 * due in the future, or late by less than [grace], fire as themselves; older ones were
 * *missed* — the phone was off, or the app force-stopped — and are summarised in one
 * notification instead (the PDF: "recovery should surface missed items without a burst of
 * stale alarms"). Habit reminders are never missed: each walks forward to its next due day
 * by itself. Pure; tested in ReminderRecoveryTest.
 */
fun splitMissed(pending: List<Reminder>, now: Instant, grace: Duration = MISSED_GRACE): Pair<List<Reminder>, List<Reminder>> =
    pending.partition {
        // A Focus phase's alarm always goes through: the timer ends the phase at its due moment.
        it.ownerKind == ReminderOwnerKind.HABIT || it.ownerKind == ReminderOwnerKind.FOCUS || !it.scheduledAt.isBefore(now.minus(grace))
    }

/**
 * The quiet window (Settings › Notifications, default 22:00–07:00): notifications in it arrive
 * silently, never dropped. A window may wrap past midnight; [start] == [end] or [enabled] off
 * means no quiet hours.
 */
fun isWithinQuietHours(
    time: LocalTime = LocalTime.now(),
    start: LocalTime = LocalTime.of(22, 0),
    end: LocalTime = LocalTime.of(7, 0),
    enabled: Boolean = true,
): Boolean = when {
    !enabled || start == end -> false
    start < end -> !time.isBefore(start) && time.isBefore(end)
    else -> !time.isBefore(start) || time.isBefore(end)
}
