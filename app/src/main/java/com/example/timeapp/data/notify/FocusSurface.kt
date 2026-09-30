package com.example.timeapp.data.notify

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timeapp.MainActivity
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.elapsedMs
import com.example.timeapp.data.finishAt
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.ReminderRepository
import com.example.timeapp.data.repository.TimerClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Everything the work timer shows outside the app, derived from its runtime row whenever it
 * changes — whoever changed it (the app, a notification action, an alarm):
 * - the **ongoing notification**, with the system chronometer counting up (stopwatch) or down
 *   (countdown, phase, break) and Pause/Resume/Finish actions — the PDF's "standard ongoing
 *   notification with system chronometer rendering and actions; a continuously running
 *   service is not required merely to count time";
 * - the **phase alarm** (`ReminderOwnerKind.FOCUS`), armed at the expected finish so a
 *   countdown ends on time with the app closed, and cleared when nothing is timed.
 * Nothing is written per second.
 */
object FocusSurface {
    const val ONGOING_ID = -2000
    private const val PHASE_OWNER_ID = 1L
    /** The break nudge's reminder: [ReminderOwnerKind.FOCUS] with this owner id. */
    const val BREAK_NUDGE_OWNER_ID = 2L
    private val timeFmt get() = displayPattern("HH:mm")

    fun start(context: Context, db: TimeDatabase, scope: CoroutineScope) {
        val app = context.applicationContext
        val timer = EntryRepository(db).timer
        val reminders = ReminderRepository(db, ReminderScheduler(app))
        scope.launch {
            combine(timer.observe(), AppSettingsDataStore(app).settings, db.entryDao().observeAll()) { rt, settings, entries ->
                Triple(rt, settings, rt.entryId?.let { id -> entries.firstOrNull { it.id == id }?.title })
            }.distinctUntilChanged().collectLatest { (rt, settings, title) ->
                val c = TimerClock.now()
                // The optional break nudge: after N minutes of unbroken work (a pause, switch or
                // break starts the count again).
                val nudgeAt = rt.stretchStartedAt?.takeIf { rt.running && rt.phase == FocusPhase.WORK && settings.focusBreakReminderMinutes > 0 }
                    ?.plusSeconds(settings.focusBreakReminderMinutes * 60L)
                val nudge = reminders.getForOwner(ReminderOwnerKind.FOCUS, BREAK_NUDGE_OWNER_ID)
                if (nudgeAt == null) {
                    if (nudge != null) reminders.clearReminder(ReminderOwnerKind.FOCUS, BREAK_NUDGE_OWNER_ID)
                } else if (nudge == null || nudge.scheduledAt != nudgeAt) {
                    reminders.setReminder(ReminderOwnerKind.FOCUS, BREAK_NUDGE_OWNER_ID, nudgeAt)
                }
                // The phase alarm follows the runtime: armed while something timed runs.
                val finish = rt.finishAt(c)
                val current = reminders.getForOwner(ReminderOwnerKind.FOCUS, PHASE_OWNER_ID)
                if (finish == null) {
                    if (current != null) reminders.clearReminder(ReminderOwnerKind.FOCUS, PHASE_OWNER_ID)
                } else if (current == null || current.delivered || kotlin.math.abs(current.scheduledAt.toEpochMilli() - finish.toEpochMilli()) > 1_000) {
                    reminders.setReminder(ReminderOwnerKind.FOCUS, PHASE_OWNER_ID, finish)
                }
                render(app, rt, title, settings.notifyWorkChannel, settings.notifyRedactContent)
                com.example.timeapp.data.widget.FocusWidget.refresh(app)
            }
        }
    }

    /**
     * The app noticed a countdown / phase ran out (its alarm may be inexact and minutes away).
     * Ends it through the phase reminder's own delivery, so the user gets the same "Time's up"
     * alert and actions as from the alarm — ending it quietly here used to cancel that alarm
     * and nothing ever told them. The alarm arriving later finds the row delivered.
     */
    suspend fun expireNow(context: Context, due: java.time.Instant) {
        val db = TimeDatabase.getInstance(context)
        val row = db.reminderDao().getForOwner(ReminderOwnerKind.FOCUS, PHASE_OWNER_ID)
        if (row == null) {
            EntryRepository(db).timer.onPhaseExpired(due)
            return
        }
        // The row may still hold an earlier finish (re-arming follows the runtime a moment later).
        if (row.delivered || kotlin.math.abs(row.scheduledAt.toEpochMilli() - due.toEpochMilli()) > 1_000) {
            db.reminderDao().update(row.copy(scheduledAt = due, delivered = false))
        }
        ReminderReceiver().deliverNow(context, row.id)
    }

    private fun render(
        context: Context,
        rt: com.example.timeapp.data.FocusRuntime,
        taskTitle: String?,
        enabled: Boolean,
        redact: Boolean,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!enabled || rt.phase == FocusPhase.IDLE || rt.awaitingChoice || !manager.areNotificationsEnabled()) {
            manager.cancel(ONGOING_ID)
            return
        }
        NotificationChannels.ensureCreated(context)
        val c = TimerClock.now()
        val onBreak = rt.phase == FocusPhase.BREAK
        val title = when {
            onBreak -> L("Break")
            else -> taskTitle ?: L("Focus")
        }
        val finish = rt.finishAt(c)
        val text = when {
            !rt.running -> if (onBreak) L("Paused · %1\$s of the break", formatMinutes(rt.elapsedMs(c))) else L("Paused · %1\$s so far", formatMinutes(rt.elapsedMs(c)))
            onBreak -> L("Not work time · ends %1\$s", finish?.atZone(ZoneId.systemDefault())?.format(timeFmt))
            rt.mode == FocusMode.STOPWATCH -> L("Stopwatch · counts as work")
            rt.mode == FocusMode.COUNTDOWN -> L("Countdown · ends %1\$s", finish?.atZone(ZoneId.systemDefault())?.format(timeFmt))
            else -> L("Pomodoro focus · cycle %1\$s of %2\$s", (rt.cycles % rt.cyclesPerLongBreak.coerceAtLeast(1) + 1), rt.cyclesPerLongBreak)
        }
        val open = android.app.PendingIntent.getActivity(
            context, ONGOING_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, NotificationChannels.RUNNING)
            .setSmallIcon(com.example.timeapp.R.drawable.ic_stat_time)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        if (rt.running) {
            builder.setUsesChronometer(true).setShowWhen(true)
            if (finish != null) {
                builder.setWhen(finish.toEpochMilli()).setChronometerCountDown(true)
            } else {
                builder.setWhen(c.wall.toEpochMilli() - rt.elapsedMs(c))
            }
            builder.addAction(FocusActionReceiver.action(context, L("Pause"), FocusActionReceiver.PAUSE, ONGOING_ID))
        } else {
            builder.setShowWhen(false)
            builder.addAction(FocusActionReceiver.action(context, L("Resume"), FocusActionReceiver.RESUME, ONGOING_ID))
        }
        if (onBreak && rt.mode == FocusMode.POMODORO) {
            builder.addAction(FocusActionReceiver.action(context, L("Skip break"), FocusActionReceiver.SKIP, ONGOING_ID))
        } else {
            builder.addAction(FocusActionReceiver.action(context, L("Finish"), FocusActionReceiver.FINISH, ONGOING_ID))
        }
        if (redact) {
            // The lock screen shows that a timer runs, never what it's for — and no controls.
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            builder.setPublicVersion(
                NotificationCompat.Builder(context, NotificationChannels.RUNNING)
                    .setSmallIcon(com.example.timeapp.R.drawable.ic_stat_time)
                    .setContentTitle("T!ME")
                    .setContentText(if (onBreak) L("Break running") else L("Timer running"))
                    .build(),
            )
        }
        try {
            manager.notify(ONGOING_ID, builder.build())
        } catch (e: SecurityException) {
            // Notifications not permitted — the timer still runs; Focus shows it.
        }
    }

    /** After [com.example.timeapp.data.repository.WorkTimer.recoverAfterReboot] closed a session:
     * say so, so the gap gets a look instead of silently shrinking the day. */
    suspend fun postRecovered(context: Context) {
        val settings = AppSettingsDataStore(context).settings.first()
        val manager = NotificationManagerCompat.from(context)
        if (!settings.notifyWorkChannel) return
        com.example.timeapp.data.repository.InboxRepository(TimeDatabase.getInstance(context)).record(
            com.example.timeapp.data.InboxKind.WORK,
            L("A work session was cut off by a restart"),
            L("T!ME counted it up to the last moment it saw it running. Check it in Focus › Session history."),
            "focus/history",
        )
        if (!manager.areNotificationsEnabled()) return
        NotificationChannels.ensureCreated(context)
        val open = android.app.PendingIntent.getActivity(
            context, RECOVERED_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, NotificationChannels.WORK)
            .setSmallIcon(com.example.timeapp.R.drawable.ic_stat_time)
            .setContentTitle(L("A work session was cut off by a restart"))
            .setContentText(L("T!ME counted it up to the last moment it saw it running. Check it in Focus › Session history."))
            .setContentIntent(open)
            .setAutoCancel(true)
        try {
            manager.notify(RECOVERED_ID, builder.build())
        } catch (e: SecurityException) {
            // Not permitted — Focus still shows the Needs review banner.
        }
    }

    private const val RECOVERED_ID = -2001

    private fun formatMinutes(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) L("%1\$s h %2\$s min", (m / 60), (m % 60)) else L("%1\$s min", m)
    }
}
