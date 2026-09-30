package com.example.timeapp.data.notify

import com.example.timeapp.data.L

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.timeapp.data.NotifyChannel

/**
 * The four real system channels behind Notifications settings' four toggles. The in-app
 * toggle and the system channel are deliberately separate gates: the app checks its own
 * setting before posting at all, and the system channel still governs sound/importance —
 * so turning a channel off in Android settings silences it even if the app setting is on.
 */
object NotificationChannels {
    const val WORK = "time_work_timers"
    const val SCHEDULE = "time_schedule"
    const val MONEY = "time_money"
    const val MAINTENANCE = "time_maintenance"
    /** The running work timer's ongoing notification — quiet, so its updates never sound. */
    const val RUNNING = "time_timer_running"
    /** Timers set to ring like an alarm: alarm volume, vibration, full screen when locked. */
    const val ALARMS = "time_timer_alarms"
    /** The same alarms when T!ME plays the sound itself (AlarmService: the user's tone, volume,
     * fade-in, speech) — silent here so the two never sound together. */
    const val ALARMS_APP = "time_timer_alarms_app"

    fun idFor(channel: NotifyChannel): String = when (channel) {
        NotifyChannel.WORK -> WORK
        NotifyChannel.SCHEDULE -> SCHEDULE
        NotifyChannel.MONEY -> MONEY
        NotifyChannel.MAINTENANCE -> MAINTENANCE
    }

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            Triple(WORK, L("Work & timers"), NotificationManager.IMPORTANCE_DEFAULT),
            Triple(SCHEDULE, L("Schedule"), NotificationManager.IMPORTANCE_DEFAULT),
            Triple(MONEY, L("Money"), NotificationManager.IMPORTANCE_DEFAULT),
            Triple(MAINTENANCE, L("Maintenance"), NotificationManager.IMPORTANCE_LOW),
            Triple(RUNNING, L("Running timer"), NotificationManager.IMPORTANCE_LOW),
        ).forEach { (id, name, importance) ->
            val existing = manager.getNotificationChannel(id)
            if (existing == null) {
                manager.createNotificationChannel(NotificationChannel(id, name, importance))
            } else if (existing.name != name) {
                // The app's language changed: Android lets an app rename its own channels
                // (the user's choices for them — importance, sound — are kept).
                existing.name = name
                manager.createNotificationChannel(existing)
            }
        }
        manager.getNotificationChannel(ALARMS)?.let { existing ->
            if (existing.name != L("Timer alarms")) {
                existing.name = L("Timer alarms")
                existing.description = L("Timers and Focus countdowns you've set to ring like an alarm")
                manager.createNotificationChannel(existing)
            }
        }
        val appName = L("Timer alarms (T!ME's sound)")
        val appChannel = manager.getNotificationChannel(ALARMS_APP)
        if (appChannel == null) {
            manager.createNotificationChannel(
                NotificationChannel(ALARMS_APP, appName, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = L("Alarm-style timers, sounded with the tone and volume you chose in Timers & focus")
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        } else if (appChannel.name != appName) {
            appChannel.name = appName
            manager.createNotificationChannel(appChannel)
        }
        if (manager.getNotificationChannel(ALARMS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ALARMS, L("Timer alarms"), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = L("Timers and Focus countdowns you've set to ring like an alarm")
                    // Alarm usage: plays at alarm volume, and Do Not Disturb's own "alarms"
                    // setting decides whether it sounds — T!ME doesn't override it.
                    setSound(
                        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                }
            )
        }
    }
}
