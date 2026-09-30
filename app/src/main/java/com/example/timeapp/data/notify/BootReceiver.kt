package com.example.timeapp.data.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.timeapp.data.db.TimeDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms do not survive a reboot or an app update, so every pending reminder is re-armed
 * from the database here — the same reconcile the app runs at startup. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler(appContext).reconcile(TimeDatabase.getInstance(appContext))
            } finally {
                pending.finish()
            }
        }
    }
}
