package com.example.timeapp.data.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.repository.EntryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The work timer's notification actions — Pause, Resume, Finish, +5 min, Break, Skip — run
 * through the same `WorkTimer` calls as the Focus screen, so a tap in the shade and a tap in
 * the app can't disagree. Each is idempotent there (a second Pause changes nothing). On a
 * locked phone Android asks for unlock first (the PDF: "pause/stop from the lock screen
 * requires unlock").
 */
class FocusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        // +5 min, Break, Finish… from a ringing phase end answer the alarm.
        AlarmService.stopRinging()
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // The unlock gate for widget buttons (Phase 16): the widget hides its controls
                // while the lock is on, and an old copy still on a home screen is refused here.
                if (intent.getBooleanExtra(EXTRA_FROM_WIDGET, false) &&
                    com.example.timeapp.data.AppSettingsDataStore(appContext).settings.first().lockEnabled
                ) return@launch
                val timer = EntryRepository(TimeDatabase.getInstance(appContext)).timer
                when (action) {
                    PAUSE -> timer.pause()
                    RESUME -> timer.resume()
                    FINISH -> timer.finish()
                    EXTEND -> timer.extend(5 * 60_000L)
                    BREAK -> timer.takeBreak()
                    SKIP -> timer.skipPhase()
                }
                // A "time's up" alert is answered once acted on; the ongoing one redraws itself.
                if (notificationId != FocusSurface.ONGOING_ID && notificationId != 0) NotificationManagerCompat.from(appContext).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val PAUSE = "com.example.timeapp.focus.PAUSE"
        const val RESUME = "com.example.timeapp.focus.RESUME"
        const val FINISH = "com.example.timeapp.focus.FINISH"
        const val EXTEND = "com.example.timeapp.focus.EXTEND"
        const val BREAK = "com.example.timeapp.focus.BREAK"
        const val SKIP = "com.example.timeapp.focus.SKIP"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val EXTRA_FROM_WIDGET = "fromWidget"

        fun intent(context: Context, action: String, notificationId: Int): Intent =
            Intent(context, FocusActionReceiver::class.java).setAction(action).putExtra(EXTRA_NOTIFICATION_ID, notificationId)

        fun action(context: Context, label: String, action: String, notificationId: Int): NotificationCompat.Action {
            val intent = Intent(context, FocusActionReceiver::class.java).setAction(action).putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            val pi = PendingIntent.getBroadcast(
                context, (action.hashCode() * 31) + notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Action.Builder(0, label, pi)
            if (Build.VERSION.SDK_INT >= 31) builder.setAuthenticationRequired(true)
            return builder.build()
        }
    }
}
