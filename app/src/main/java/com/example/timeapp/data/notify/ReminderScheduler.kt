package com.example.timeapp.data.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.timeapp.data.Reminder
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import java.time.Instant

/**
 * Arms one AlarmManager alarm per pending [Reminder]. Nothing here decides *whether* a
 * reminder deserves to fire — that is re-evaluated against live data at delivery time in
 * [ReminderReceiver], per the PDF's "re-read the owner and due state" rule, so a task
 * completed or deleted after its alarm was armed never notifies.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    /** True when the OS lets us fire at the exact minute; false means the fallback below,
     * which the UI labels as approximate rather than quietly promising precision. */
    fun canScheduleExact(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= 31) manager.canScheduleExactAlarms() else true
    }

    fun arm(reminder: Reminder) {
        val manager = alarmManager ?: return
        val triggerAt = reminder.scheduledAt.toEpochMilli()
        val intent = pendingIntentFor(reminder.id, create = true) ?: return
        // A past-due reminder still fires immediately rather than being dropped — recovering a
        // missed alarm after a reboot/force-stop is the whole point of reconcile().
        if (canScheduleExact()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    fun cancel(reminderId: Long) {
        val manager = alarmManager ?: return
        val intent = pendingIntentFor(reminderId, create = false) ?: return
        manager.cancel(intent)
        intent.cancel()
    }

    /**
     * Re-arms every still-pending reminder from the database. Alarms do not survive a reboot,
     * a force-stop, or an app update, so this runs on boot, on app start, and after a restore —
     * the database is the source of truth and pending alarms are treated as disposable.
     */
    suspend fun reconcile(database: TimeDatabase, now: Instant = Instant.now()) {
        val (onTime, missed) = com.example.timeapp.data.splitMissed(database.reminderDao().pending().map { it.toDomain() }, now)
        onTime.forEach { arm(it) }
        if (missed.isNotEmpty()) MissedReminderSummary.retireAndPost(context, database, missed)
    }

    private fun pendingIntentFor(reminderId: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            // A distinct data URI per reminder keeps PendingIntents from being treated as
            // equal (extras alone are not part of Intent equality, so they would collapse).
            data = android.net.Uri.parse("timeapp://reminder/$reminderId")
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, reminderId.toInt(), intent, flags)
    }

    companion object {
        const val ACTION_FIRE = "com.example.timeapp.REMINDER_FIRE"
        const val EXTRA_REMINDER_ID = "reminderId"

        /** The default reminder instant for a date-only due date: 09:00 local on that day. */
        fun defaultTimeFor(date: java.time.LocalDate): Instant =
            date.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()

        /** A bill's reminder: 09:00 the day *before* it falls due — a prompt to arrange
         * payment, never a payment itself. */
        fun planTimeFor(dueOn: java.time.LocalDate): Instant = defaultTimeFor(dueOn.minusDays(1))
    }
}
