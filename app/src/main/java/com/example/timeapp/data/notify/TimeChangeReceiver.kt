package com.example.timeapp.data.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The PDF: "timezone changes trigger reminder recalculation without rewriting historical
 * timestamps". A new timezone moves date-anchored reminders (09:00 on a due date) to the same
 * wall-clock time in the new zone; a manual clock change only rebuilds the alarms, which also
 * turns anything the jump skipped past into the missed summary rather than a burst.
 * Both broadcasts are protected (only the system sends them), and exempt from Android 8's
 * implicit-broadcast limits.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_TIMEZONE_CHANGED && action != Intent.ACTION_TIME_CHANGED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ReminderRepository(TimeDatabase.getInstance(appContext), ReminderScheduler(appContext))
                if (action == Intent.ACTION_TIMEZONE_CHANGED) repository.reanchorForTimezone()
                repository.reconcile()
            } finally {
                pending.finish()
            }
        }
    }
}
