package com.example.timeapp.data.notify

import com.example.timeapp.data.L

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timeapp.MainActivity
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.NotifyChannel
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.db.ReminderEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.showsToday
import com.example.timeapp.data.isQuiet
import com.example.timeapp.data.toTaka
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Delivers one reminder, re-deciding from live data whether it still deserves to fire.
 * Everything that could have changed since the alarm was armed is checked here: the owner
 * may be completed or deleted, the user may have switched the channel off, and a duplicate
 * delivery may already have been handled. A money reminder only ever prompts — it never
 * records a payment and dismissing it never cancels the plan.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_HABIT_DONE) {
            markHabitDone(context, intent)
            return
        }
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deliver(appContext, reminderId)
            } finally {
                pending.finish()
            }
        }
    }

    /** Delivers [reminderId] now, exactly as its alarm would — used when the app notices a
     * Focus phase ran out before the (possibly inexact) alarm arrived. */
    internal suspend fun deliverNow(context: Context, reminderId: Long) = deliver(context.applicationContext, reminderId)

    private suspend fun deliver(context: Context, reminderId: Long) {
        val database = TimeDatabase.getInstance(context)
        val reminderDao = database.reminderDao()
        val reminder = reminderDao.getById(reminderId) ?: return
        if (reminder.delivered) return // at most once per occurrence, even on duplicate delivery

        val settings = AppSettingsDataStore(context).settings.first()
        val content = buildContent(database, reminder) ?: run {
            // Owner is gone or no longer actionable — retire the reminder instead of
            // leaving it pending forever and re-arming it on every reconcile.
            reminderDao.update(reminder.copy(delivered = true))
            return
        }

        val channel = when (reminder.ownerKind) {
            ReminderOwnerKind.TASK, ReminderOwnerKind.EVENT, ReminderOwnerKind.HABIT, ReminderOwnerKind.HABIT_SNOOZE -> NotifyChannel.SCHEDULE
            ReminderOwnerKind.TIMER, ReminderOwnerKind.FOCUS -> NotifyChannel.WORK
            ReminderOwnerKind.PLAN, ReminderOwnerKind.LOAN -> NotifyChannel.MONEY
        }
        val channelEnabled = when (channel) {
            NotifyChannel.SCHEDULE -> settings.notifyScheduleChannel
            NotifyChannel.MONEY -> settings.notifyMoneyChannel
            NotifyChannel.WORK -> settings.notifyWorkChannel
            NotifyChannel.MAINTENANCE -> settings.notifyMaintenanceChannel
        }

        // Mark delivered regardless of whether it was posted: the moment has passed, and a
        // reminder the user muted should not resurface later as if it were still due.
        reminderDao.update(reminder.copy(delivered = true))
        // A habit reminder is one row that walks forward: re-arm the next due day before
        // anything below can return, so a muted channel or a day already checked off never
        // ends the habit's reminders for good.
        if (reminder.ownerKind == ReminderOwnerKind.HABIT) rearmHabit(context, database, reminder)
        // A timer that has rung is finished, whether or not the notification can show.
        if (reminder.ownerKind == ReminderOwnerKind.TIMER) {
            database.utilityTimerDao().getById(reminder.ownerId)?.let {
                database.utilityTimerDao().update(it.copy(endsAt = null, remainingMs = 0))
                // A timed habit's timer: ringing is the habit done, on the day it was started.
                val habitId = it.habitId
                val endsAt = it.endsAt
                if (habitId != null && endsAt != null && database.habitDao().getById(habitId) != null) {
                    val day = endsAt.minusMillis(it.durationMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    val existing = database.habitDao().checkOn(habitId, day)
                    database.habitDao().upsertCheck(
                        com.example.timeapp.data.db.HabitCheckEntity(habitId, day, com.example.timeapp.data.HabitMark.DONE, existing?.value, existing?.note),
                    )
                }
            }
        }
        if (content.suppress) return
        if (!settings.notifyRemindersEnabled || !channelEnabled) return

        // Tapping opens the thing itself — through the app lock, which MainActivity puts first.
        val route = when (reminder.ownerKind) {
            ReminderOwnerKind.TASK -> "plan/task/${reminder.ownerId}"
            ReminderOwnerKind.EVENT -> "plan/month"
            ReminderOwnerKind.PLAN -> "money"
            ReminderOwnerKind.HABIT, ReminderOwnerKind.HABIT_SNOOZE -> "habits/${reminder.ownerId}"
            ReminderOwnerKind.TIMER -> "focus/timers"
            ReminderOwnerKind.FOCUS -> "focus"
            ReminderOwnerKind.LOAN -> "money/loans/${reminder.ownerId}"
        }
        // The bell's list keeps it even when Android won't show it (permission off, shade cleared).
        com.example.timeapp.data.repository.InboxRepository(database).record(
            when (reminder.ownerKind) {
                ReminderOwnerKind.TASK -> com.example.timeapp.data.InboxKind.TASK
                ReminderOwnerKind.EVENT -> com.example.timeapp.data.InboxKind.BOOKING
                ReminderOwnerKind.PLAN -> com.example.timeapp.data.InboxKind.BILL
                ReminderOwnerKind.HABIT, ReminderOwnerKind.HABIT_SNOOZE -> com.example.timeapp.data.InboxKind.HABIT
                ReminderOwnerKind.TIMER -> com.example.timeapp.data.InboxKind.TIMER
                ReminderOwnerKind.FOCUS -> com.example.timeapp.data.InboxKind.FOCUS
                ReminderOwnerKind.LOAN -> com.example.timeapp.data.InboxKind.LOAN
            },
            content.title, content.body, route,
        )

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        NotificationChannels.ensureCreated(context)
        val openApp = android.app.PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(android.net.Uri.parse("timeapp://open/$reminderId"))
                .putExtra(MainActivity.EXTRA_OPEN_ROUTE, route),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        // Phase 11: a timer or Focus countdown set to ring like an alarm uses the alarm channel
        // (alarm volume, vibration) and a full-screen page, and repeats until answered.
        val alarmStyle = settings.timerAlarmStyle && (
            reminder.ownerKind == ReminderOwnerKind.TIMER ||
                (reminder.ownerKind == ReminderOwnerKind.FOCUS && reminder.ownerId != FocusSurface.BREAK_NUDGE_OWNER_ID)
            )
        // Alarm style is first built for the app's own player (a silent high-importance channel —
        // AlarmService makes the sound); if the service may not start, the channel with the
        // fixed alarm sound below is the fallback, so it rings either way.
        val builder = NotificationCompat.Builder(context, if (alarmStyle) NotificationChannels.ALARMS_APP else NotificationChannels.idFor(channel))
            .setSmallIcon(com.example.timeapp.R.drawable.ic_stat_time)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setContentIntent(openApp)
            .setAutoCancel(true)
        val nid = reminderId.toInt()
        fun act(label: String, action: String, unlock: Boolean = false) =
            builder.addAction(ReminderActionReceiver.action(context, label, action, reminderId, reminder.ownerId, nid, unlock))
        // Phase 11: act from the shade. Snoozing reveals and changes nothing but the reminder,
        // so it needs no unlock; marking a task done does.
        when (reminder.ownerKind) {
            ReminderOwnerKind.TASK -> {
                act(L("Done"), ReminderActionReceiver.DONE_TASK, unlock = true)
                act(L("1 hour"), ReminderActionReceiver.SNOOZE_60)
                act(L("Tomorrow"), ReminderActionReceiver.SNOOZE_TOMORROW)
            }
            ReminderOwnerKind.EVENT -> act(L("Snooze 10 min"), ReminderActionReceiver.SNOOZE_10)
            ReminderOwnerKind.PLAN, ReminderOwnerKind.LOAN -> act(L("Remind me tomorrow"), ReminderActionReceiver.SNOOZE_TOMORROW)
            ReminderOwnerKind.TIMER -> act(L("+1 min"), ReminderActionReceiver.TIMER_PLUS_MINUTE)
            else -> Unit
        }
        if (reminder.ownerKind == ReminderOwnerKind.HABIT || reminder.ownerKind == ReminderOwnerKind.HABIT_SNOOZE) {
            if (reminder.ownerKind == ReminderOwnerKind.HABIT) act(L("In 1 hour"), ReminderActionReceiver.SNOOZE_60)
            // Check it off without opening the app. Only records what the user tapped — it
            // reveals nothing, so it needs no unlock even with the app lock on.
            val done = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_HABIT_DONE
                data = android.net.Uri.parse("timeapp://habit-done/${reminder.ownerId}")
                putExtra(EXTRA_HABIT_ID, reminder.ownerId)
                putExtra(EXTRA_EPOCH_DAY, java.time.LocalDate.now().toEpochDay())
                putExtra(EXTRA_NOTIFICATION_ID, reminderId.toInt())
            }
            builder.addAction(
                0,
                L("Done"),
                android.app.PendingIntent.getBroadcast(
                    context, reminderId.toInt(), done,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        // A Focus phase ended: the next steps as actions (unlock required on a locked phone).
        content.actions.forEach { (label, action) ->
            builder.addAction(FocusActionReceiver.action(context, label, action, reminderId.toInt()))
        }
        // Quiet hours silence a reminder, they never drop it — the point of a reminder is
        // that it is still there in the morning, not that it was quietly discarded.
        // Timers are exempt: one the user started at 23:00 is meant to ring.
        if (settings.isQuiet() && reminder.ownerKind != ReminderOwnerKind.TIMER && reminder.ownerKind != ReminderOwnerKind.FOCUS) builder.setSilent(true)
        if (settings.notifyRedactContent) {
            // Details stay off the lock screen; the public version says only that something
            // is due, matching the "Redact content" switch's promise.
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            builder.setPublicVersion(
                NotificationCompat.Builder(context, NotificationChannels.idFor(channel))
                    .setSmallIcon(com.example.timeapp.R.drawable.ic_stat_time)
                    .setContentTitle("T!ME")
                    .setContentText(content.redactedBody)
                    .build(),
            )
        }
        if (alarmStyle) {
            val nid2 = reminderId.toInt()
            val snooze: Intent? = when {
                reminder.ownerKind == ReminderOwnerKind.TIMER ->
                    ReminderActionReceiver.intent(context, ReminderActionReceiver.TIMER_PLUS_5, reminderId, reminder.ownerId, nid2)
                content.actions.any { it.second == FocusActionReceiver.EXTEND } ->
                    FocusActionReceiver.intent(context, FocusActionReceiver.EXTEND, nid2)
                else -> null
            }
            val page = Intent(context, com.example.timeapp.AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(com.example.timeapp.AlarmActivity.EXTRA_TITLE, if (settings.notifyRedactContent) L("Time's up") else content.title)
                .putExtra(com.example.timeapp.AlarmActivity.EXTRA_BODY, if (settings.notifyRedactContent) "" else content.body)
                .putExtra(com.example.timeapp.AlarmActivity.EXTRA_NOTIFICATION_ID, nid2)
                .putExtra(com.example.timeapp.AlarmActivity.EXTRA_SNOOZE, snooze)
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(false)
                .setFullScreenIntent(
                    android.app.PendingIntent.getActivity(context, nid2, page, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE),
                    true,
                )
            act(L("Stop"), ReminderActionReceiver.DISMISS)
            if (reminder.ownerKind == ReminderOwnerKind.TIMER) act(L("Snooze 5 min"), ReminderActionReceiver.TIMER_PLUS_5)
        }
        if (alarmStyle) {
            // Swiping the notification away answers the alarm too.
            builder.setDeleteIntent(
                android.app.PendingIntent.getBroadcast(
                    context, reminderId.toInt(),
                    ReminderActionReceiver.intent(context, ReminderActionReceiver.DISMISS, reminderId, reminder.ownerId, reminderId.toInt()),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            val locked = context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked ?: true
            val speech = com.example.timeapp.data.spokenAlert(content.title, settings.notifyRedactContent && locked)
            if (AlarmService.start(context, reminderId.toInt(), builder.build(), speech)) return
            builder.setChannelId(NotificationChannels.ALARMS)
        }
        try {
            val notification = builder.build()
            // The fallback ring: the channel's alarm sound, repeating until answered.
            if (alarmStyle) notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
            manager.notify(reminderId.toInt(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check above and here — nothing to do.
        }
    }

    /** A habit notification's Done: marks the day it was sent for (not "now", so tapping it
     * just after midnight still credits the right day) and clears the notification. */
    private fun markHabitDone(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE)
        if (habitId < 0 || epochDay == Long.MIN_VALUE) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = TimeDatabase.getInstance(appContext).habitDao()
                if (dao.getById(habitId) != null) {
                    val day = java.time.LocalDate.ofEpochDay(epochDay)
                    // Keeps the day's amount and note, if it had any.
                    val existing = dao.checkOn(habitId, day)
                    dao.upsertCheck(
                        com.example.timeapp.data.db.HabitCheckEntity(
                            habitId, day, com.example.timeapp.data.HabitMark.DONE, existing?.value, existing?.note,
                        ),
                    )
                }
                NotificationManagerCompat.from(appContext).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            } finally {
                pending.finish()
            }
        }
    }

    /** [suppress]: still actionable, so the reminder lives on, but there is nothing to say
     * right now — a habit already marked today, or a stale one recovered after a reboot. */
    private data class Content(
        val title: String,
        val body: String,
        val redactedBody: String,
        val suppress: Boolean = false,
        /** Notification actions: label → [FocusActionReceiver] action. */
        val actions: List<Pair<String, String>> = emptyList(),
    )

    /**
     * A countdown or Pomodoro phase's alarm. The timer ends the phase at its due moment (not
     * now — delivery can be late) and reports what happened; a stale alarm (the phase was
     * paused or extended since) returns nothing and is retired.
     */
    private suspend fun focusContent(database: TimeDatabase, reminder: ReminderEntity): Content? {
        val repo = com.example.timeapp.data.repository.EntryRepository(database)
        val runtime = repo.timer.runtime()
        if (reminder.ownerId == FocusSurface.BREAK_NUDGE_OWNER_ID) {
            // Still the same unbroken stretch? Otherwise the nudge is stale.
            val same = runtime.running && runtime.phase == com.example.timeapp.data.FocusPhase.WORK &&
                runtime.stretchStartedAt != null && !runtime.stretchStartedAt.isAfter(reminder.scheduledAt)
            if (!same) return null
            val minutes = java.time.Duration.between(runtime.stretchStartedAt, reminder.scheduledAt).toMinutes()
            return Content(
                title = L("Time for a break?"),
                body = L("You've worked %1\$s minutes without a pause.", minutes),
                redactedBody = L("A break reminder"),
                actions = listOf(L("Take a break") to FocusActionReceiver.BREAK, L("Pause") to FocusActionReceiver.PAUSE),
            )
        }
        val end = repo.timer.onPhaseExpired(reminder.scheduledAt) ?: return null
        val task = runtime.entryId?.let { database.entryDao().getById(it)?.title }
        val on = task?.let { L(" on %1\$s", it) } ?: ""
        return when (end) {
            com.example.timeapp.data.repository.PhaseEnd.CountdownDone -> Content(
                title = L("Time's up%1\$s", on),
                body = L("Work stopped when your %1\$s-minute countdown ran out. What next?", (runtime.targetMs?.div(60_000) ?: 0)),
                redactedBody = L("Your countdown finished"),
                actions = listOf(L("+5 min") to FocusActionReceiver.EXTEND, L("Break") to FocusActionReceiver.BREAK, L("Finish") to FocusActionReceiver.FINISH),
            )
            is com.example.timeapp.data.repository.PhaseEnd.FocusDone -> Content(
                title = L("Focus phase done%1\$s", on),
                body = if (end.breakStarted) L("Your %1\$s-minute break has started.", end.breakMinutes) else L("Time for a %1\$s-minute break.", end.breakMinutes),
                redactedBody = L("A focus phase finished"),
                actions = if (end.breakStarted) listOf(L("Skip break") to FocusActionReceiver.SKIP) else listOf(L("Start break") to FocusActionReceiver.RESUME, L("Skip break") to FocusActionReceiver.SKIP),
            )
            is com.example.timeapp.data.repository.PhaseEnd.BreakDone -> Content(
                title = L("Break's over"),
                body = if (end.workStarted) L("The next focus phase has started%1\$s.", on) else L("Ready for the next focus phase%1\$s?", on),
                redactedBody = L("Your break finished"),
                actions = if (end.workStarted) listOf(L("Pause") to FocusActionReceiver.PAUSE) else listOf(L("Start focus") to FocusActionReceiver.RESUME),
            )
        }
    }

    private suspend fun rearmHabit(context: Context, database: TimeDatabase, reminder: ReminderEntity) {
        val habit = database.habitDao().getById(reminder.ownerId)?.toDomain() ?: return
        val remindAt = habit.remindAt ?: return
        val zone = java.time.ZoneId.systemDefault()
        // Strictly after both now and the instant just delivered, so a late delivery can
        // never re-arm the same moment and loop.
        val after = maxOf(java.time.Instant.now(), reminder.scheduledAt).atZone(zone).toLocalDateTime()
        val next = com.example.timeapp.data.nextHabitReminder(habit.schedule, remindAt, after).atZone(zone).toInstant()
        val rearmed = reminder.copy(scheduledAt = next, delivered = false)
        database.reminderDao().update(rearmed)
        ReminderScheduler(context).arm(rearmed.toDomain())
    }

    private suspend fun buildContent(database: TimeDatabase, reminder: ReminderEntity): Content? =
        when (reminder.ownerKind) {
            ReminderOwnerKind.TASK, ReminderOwnerKind.EVENT -> {
                val entry = database.entryDao().getById(reminder.ownerId)
                // Done, deleted or archived since it was armed: nothing to remind about.
                if (entry == null || entry.status == EntryStatus.DONE || entry.archivedAt != null) {
                    null
                } else {
                    Content(
                        title = entry.title,
                        body = if (reminder.ownerKind == ReminderOwnerKind.EVENT) L("Starting soon") else L("Due today"),
                        redactedBody = L("You have something scheduled"),
                    )
                }
            }
            ReminderOwnerKind.TIMER -> {
                val timer = database.utilityTimerDao().getById(reminder.ownerId)
                // Paused, reset or deleted since it was armed — nothing to ring for.
                if (timer?.endsAt == null) {
                    null
                } else {
                    Content(
                        title = L("“%1\$s” is done", timer.label),
                        body = L("Your %1\$s-minute timer finished.", (timer.durationMs / 60_000)),
                        redactedBody = L("A timer finished"),
                    )
                }
            }
            ReminderOwnerKind.HABIT -> {
                val habit = database.habitDao().getById(reminder.ownerId)
                if (habit == null || habit.archived || habit.remindMinuteOfDay == null) {
                    null
                } else {
                    val domain = habit.toDomain()
                    val today = java.time.LocalDate.now()
                    val firesFor = reminder.scheduledAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    val marks = database.habitDao().checksFor(habit.id).associate { it.date to it.mark }
                    Content(
                        title = habit.name,
                        body = L("Time for this habit — check it off in T!ME"),
                        redactedBody = L("You have a habit to check in"),
                        // Decided now, not when armed: stale (recovered after the phone was
                        // off), already marked today, or a weekly target already met.
                        suppress = firesFor != today || !domain.showsToday(marks, today) || marks[today] != null,
                    )
                }
            }
            ReminderOwnerKind.FOCUS -> focusContent(database, reminder)
            ReminderOwnerKind.LOAN -> {
                // Re-read at delivery: a closed or fully repaid loan never notifies.
                val loan = database.loanDao().getById(reminder.ownerId)?.toDomain()
                val left = loan?.let { com.example.timeapp.data.loanBalance(it, database.moneyRecordDao().forLoan(it.id).map { r -> r.toDomain() }).outstandingMinor } ?: 0
                if (loan == null || !loan.isOpen || left == 0L) null else Content(
                    title = if (loan.lent) L("%1\$s owes you %2\$s", loan.person, left.toTaka()) else L("You owe %1\$s %2\$s", loan.person, left.toTaka()),
                    body = L("Due today — this only reminds you; nothing is recorded."),
                    redactedBody = L("A loan is due"),
                )
            }
            ReminderOwnerKind.HABIT_SNOOZE -> {
                // One-off "remind me again": gone for good after this, whatever happens.
                database.reminderDao().delete(reminder.id)
                val habit = database.habitDao().getById(reminder.ownerId)
                val today = java.time.LocalDate.now()
                if (habit == null || habit.archived) null else Content(
                    title = habit.name,
                    body = L("You asked to be reminded again"),
                    redactedBody = L("You have a habit to check in"),
                    suppress = database.habitDao().checksFor(habit.id).any { it.date == today },
                )
            }
            ReminderOwnerKind.PLAN -> {
                val plan = database.moneyRecordDao().getById(reminder.ownerId)
                if (plan == null || plan.status == null || plan.status == PlanStatus.SETTLED || plan.status == PlanStatus.CANCELLED) {
                    null
                } else {
                    Content(
                        title = plan.title,
                        body = L("%1\$s due — this only reminds you, it pays nothing", ((plan.amountMinor - plan.paidMinor).coerceAtLeast(0).toTaka())),
                        redactedBody = L("A bill is due"),
                    )
                }
            }
        }

    companion object {
        const val ACTION_HABIT_DONE = "com.example.timeapp.HABIT_DONE"

        /** The Done broadcast for [habitId] on [day] — used by the notification and the widget. */
        fun habitDoneIntent(context: Context, habitId: Long, day: java.time.LocalDate): Intent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_HABIT_DONE
                data = android.net.Uri.parse("timeapp://habit-done/$habitId/${day.toEpochDay()}")
                putExtra(EXTRA_HABIT_ID, habitId)
                putExtra(EXTRA_EPOCH_DAY, day.toEpochDay())
                putExtra(EXTRA_NOTIFICATION_ID, 0)
            }
        private const val EXTRA_HABIT_ID = "habitId"
        private const val EXTRA_EPOCH_DAY = "epochDay"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
