package com.example.timeapp.data.notify

import com.example.timeapp.data.L

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timeapp.MainActivity
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.NotifyChannel
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.Reminder
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toEntity
import com.example.timeapp.data.isQuiet
import kotlinx.coroutines.flow.first

/**
 * Stands in for reminders missed while the phone was off: each is retired (delivered, alarm
 * cancelled) and whatever is still actionable is listed in **one** notification per channel,
 * instead of every stale alarm going off at once. Muted channels and the master switch are
 * respected, the quiet window silences it, and the lock-screen version names nothing when
 * content redaction is on.
 */
internal object MissedReminderSummary {
    suspend fun retireAndPost(context: Context, database: TimeDatabase, missed: List<Reminder>) {
        val scheduler = ReminderScheduler(context)
        val titlesByChannel = linkedMapOf<NotifyChannel, MutableList<String>>()
        missed.forEach { reminder ->
            scheduler.cancel(reminder.id)
            database.reminderDao().update(reminder.copy(delivered = true).toEntity())
            describe(database, reminder)?.let { (channel, title) -> titlesByChannel.getOrPut(channel) { mutableListOf() } += title }
        }
        if (titlesByChannel.isEmpty()) return
        val settings = AppSettingsDataStore(context).settings.first()
        val manager = NotificationManagerCompat.from(context)
        if (!settings.notifyRemindersEnabled) return
        // The bell's list records the summary even when Android won't show it.
        val canPost = manager.areNotificationsEnabled()
        if (canPost) NotificationChannels.ensureCreated(context)
        titlesByChannel.forEach { (channel, titles) ->
            val enabled = when (channel) {
                NotifyChannel.SCHEDULE -> settings.notifyScheduleChannel
                NotifyChannel.MONEY -> settings.notifyMoneyChannel
                NotifyChannel.WORK -> settings.notifyWorkChannel
                NotifyChannel.MAINTENANCE -> settings.notifyMaintenanceChannel
            }
            if (!enabled) return@forEach
            val heading = if (titles.size == 1) L("You missed a reminder") else L("You missed %1\$s reminders", titles.size)
            com.example.timeapp.data.repository.InboxRepository(database).record(
                com.example.timeapp.data.InboxKind.MISSED, heading, titles.joinToString(", "),
                when (channel) {
                    NotifyChannel.MONEY -> "money"
                    NotifyChannel.WORK -> "focus"
                    else -> "plan"
                },
            )
            if (!canPost) return@forEach
            val open = android.app.PendingIntent.getActivity(
                context, SUMMARY_ID_BASE - channel.ordinal,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val style = NotificationCompat.InboxStyle().setSummaryText(L("While your phone was off"))
            titles.take(6).forEach { style.addLine(it) }
            val builder = NotificationCompat.Builder(context, NotificationChannels.idFor(channel))
                .setSmallIcon(com.example.timeapp.R.drawable.ic_stat_time)
                .setContentTitle(heading)
                .setContentText(titles.joinToString(", "))
                .setStyle(style)
                .setContentIntent(open)
                .setAutoCancel(true)
            if (settings.isQuiet()) builder.setSilent(true)
            if (settings.notifyRedactContent) {
                builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                builder.setPublicVersion(
                    NotificationCompat.Builder(context, NotificationChannels.idFor(channel))
                        .setSmallIcon(com.example.timeapp.R.drawable.ic_stat_time)
                        .setContentTitle("T!ME")
                        .setContentText(heading)
                        .build(),
                )
            }
            try {
                // A fixed id per channel: a later recovery replaces this one instead of stacking.
                manager.notify(SUMMARY_ID_BASE - channel.ordinal, builder.build())
            } catch (e: SecurityException) {
                // Permission revoked in the meantime — nothing to post.
            }
        }
    }

    /** The channel and name of a missed reminder's owner, or null when nothing is left to do
     * (done, archived, deleted, settled or cancelled). A timer that ran out is marked finished. */
    private suspend fun describe(database: TimeDatabase, reminder: Reminder): Pair<NotifyChannel, String>? =
        when (reminder.ownerKind) {
            ReminderOwnerKind.TASK, ReminderOwnerKind.EVENT -> database.entryDao().getById(reminder.ownerId)
                ?.takeIf { it.status != EntryStatus.DONE && it.archivedAt == null }
                ?.let { NotifyChannel.SCHEDULE to it.title }
            ReminderOwnerKind.PLAN -> database.moneyRecordDao().getById(reminder.ownerId)
                ?.takeIf { it.status != null && it.status != PlanStatus.SETTLED && it.status != PlanStatus.CANCELLED }
                ?.let { NotifyChannel.MONEY to it.title }
            ReminderOwnerKind.TIMER -> database.utilityTimerDao().getById(reminder.ownerId)
                ?.takeIf { it.endsAt != null }
                ?.let { timer ->
                    database.utilityTimerDao().update(timer.copy(endsAt = null, remainingMs = 0))
                    NotifyChannel.WORK to L("“%1\$s” timer finished", timer.label)
                }
            ReminderOwnerKind.HABIT, ReminderOwnerKind.FOCUS, ReminderOwnerKind.HABIT_SNOOZE -> null
            ReminderOwnerKind.LOAN -> database.loanDao().getById(reminder.ownerId)?.takeIf { it.closedAt == null }
                ?.let { NotifyChannel.MONEY to L("Loan: %1\$s", it.person) }
        }

    /** Negative, so it can never collide with a reminder's own notification id. */
    private const val SUMMARY_ID_BASE = -1000
}
