package com.example.timeapp.data.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/**
 * Actions on a reminder notification (Phase 11, the PDF's "notification actions"): Snooze
 * (10 min, 1 h, tomorrow 09:00 — the reminder moves, never the deadline), Mark done on a task,
 * Remind later on a bill (it never records a payment), +1 min on a finished utility timer.
 * Everything re-reads the database, so a stale tap on something already done changes nothing.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        val ownerId = intent.getLongExtra(EXTRA_OWNER_ID, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        // Every action here answers the alarm, if one is ringing.
        AlarmService.stopRinging()
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = TimeDatabase.getInstance(app)
                val reminders = ReminderRepository(db, ReminderScheduler(app))
                when (action) {
                    SNOOZE_10 -> reminders.snooze(reminderId, Instant.now().plusSeconds(10 * 60))
                    SNOOZE_60 -> reminders.snooze(reminderId, Instant.now().plusSeconds(60 * 60))
                    SNOOZE_TOMORROW -> reminders.snooze(reminderId, ReminderScheduler.defaultTimeFor(LocalDate.now().plusDays(1)))
                    DONE_TASK -> db.entryDao().getById(ownerId)
                        ?.takeIf { it.status != EntryStatus.DONE }
                        ?.let { EntryRepository(db).complete(it.toDomain()) }
                    TIMER_PLUS_MINUTE, TIMER_PLUS_5 -> db.utilityTimerDao().getById(ownerId)?.let { t ->
                        val seconds = if (action == TIMER_PLUS_5) 300L else 60L
                        val ends = Instant.now().plusSeconds(seconds)
                        db.utilityTimerDao().update(t.copy(endsAt = ends, remainingMs = seconds * 1000))
                        reminders.setReminder(ReminderOwnerKind.TIMER, t.id, ends)
                    }
                    DISMISS -> Unit
                }
                NotificationManagerCompat.from(app).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val SNOOZE_10 = "com.example.timeapp.reminder.SNOOZE_10"
        const val SNOOZE_60 = "com.example.timeapp.reminder.SNOOZE_60"
        const val SNOOZE_TOMORROW = "com.example.timeapp.reminder.SNOOZE_TOMORROW"
        const val DONE_TASK = "com.example.timeapp.reminder.DONE_TASK"
        const val TIMER_PLUS_MINUTE = "com.example.timeapp.reminder.TIMER_PLUS_MINUTE"
        const val TIMER_PLUS_5 = "com.example.timeapp.reminder.TIMER_PLUS_5"
        /** Just answers the notification — for an alarm, stops the ringing. */
        const val DISMISS = "com.example.timeapp.reminder.DISMISS"

        /** The raw intent behind an action, for the alarm page to send too. */
        fun intent(context: Context, action: String, reminderId: Long, ownerId: Long, notificationId: Int): Intent =
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(action)
                .setData(android.net.Uri.parse("timeapp://reminder-action/$reminderId/$action"))
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_OWNER_ID, ownerId)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        private const val EXTRA_REMINDER_ID = "reminderId"
        private const val EXTRA_OWNER_ID = "ownerId"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"

        /** A notification action; [unlock] asks for the device to be unlocked first (API 31+). */
        fun action(context: Context, label: String, action: String, reminderId: Long, ownerId: Long, notificationId: Int, unlock: Boolean): NotificationCompat.Action {
            val intent = Intent(context, ReminderActionReceiver::class.java)
                .setAction(action)
                .setData(android.net.Uri.parse("timeapp://reminder-action/$reminderId/$action"))
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_OWNER_ID, ownerId)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            val pi = PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val b = NotificationCompat.Action.Builder(0, label, pi)
            if (unlock && Build.VERSION.SDK_INT >= 31) b.setAuthenticationRequired(true)
            return b.build()
        }
    }
}
