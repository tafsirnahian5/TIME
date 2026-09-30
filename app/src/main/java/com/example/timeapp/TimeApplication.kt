package com.example.timeapp

import kotlinx.coroutines.flow.first

import android.app.Application
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.notify.NotificationChannels
import com.example.timeapp.data.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimeApplication : Application() {
    val database: TimeDatabase by lazy { TimeDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        // The app's language, before anything builds text (Phase 17). One small read, once.
        com.example.timeapp.data.Lang.bangla = kotlinx.coroutines.runBlocking {
            com.example.timeapp.data.AppSettingsDataStore(this@TimeApplication).settings.first().appLanguage == "bn"
        }
        // Deliberately NOT seeded here. A fresh install starts empty so the first thing
        // you see is your own data, not a stranger's demo life you have to delete by hand.
        // The sample content is still available on demand from Settings › Data & backup.
        NotificationChannels.ensureCreated(this)
        // Which boot this is, so a work session knows whether its monotonic anchor still holds.
        com.example.timeapp.data.repository.TimerClock.bootCount =
            android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }
        // Alarms are disposable; the database is authoritative. Re-arming on every start
        // covers a force-stop (which clears alarms silently) and a restored backup.
        CoroutineScope(Dispatchers.IO).launch {
            // Starter categories are scaffolding, not demo data — nothing can be categorised
            // without them, so they exist even on a deliberately empty first launch.
            com.example.timeapp.data.repository.CategoryRepository(database).ensureDefaults()
            com.example.timeapp.data.repository.IdeaRepository(database.ideaCollectionDao(), database.ideaDao()).ensureDefaults()
            database.ensureBoardColumns()
            val timer = com.example.timeapp.data.repository.EntryRepository(database).timer
            // First run on the new timer: carry the Focus lengths the old in-memory one kept.
            if (database.focusDao().runtime() == null) {
                val old = com.example.timeapp.data.FocusStateStore(this@TimeApplication).load()
                timer.setLengths(old.countdownMinutes, old.workMinutes, old.breakMinutes, old.autoStart)
            }
            // A session left running across a reboot is closed at its last checkpoint and
            // flagged for review — before reminders are rebuilt below.
            com.example.timeapp.data.AttachmentStore(this@TimeApplication, database).cleanOrphans()
            if (timer.recoverAfterReboot()) com.example.timeapp.data.notify.FocusSurface.postRecovered(this@TimeApplication)
            // Recurring tasks and bills materialise their next occurrences up to a bounded
            // horizon; running this on each start simply extends the window forward.
            com.example.timeapp.data.repository.RecurrenceExpander(database, ReminderScheduler(this@TimeApplication)).expand()
            ReminderScheduler(this@TimeApplication).reconcile(database)
        }
        // Home-screen widget follows the data for as long as the process lives.
        com.example.timeapp.data.widget.TodayWidgetUpdater.start(this, database, CoroutineScope(Dispatchers.IO))
        // The work timer's ongoing notification and phase alarm follow its runtime row.
        com.example.timeapp.data.notify.FocusSurface.start(this, database, CoroutineScope(Dispatchers.IO))
        com.example.timeapp.data.notify.BudgetWatcher.start(this, database, CoroutineScope(Dispatchers.IO))
        // Phase 17: the daily maintenance job (automatic backups, backup reminders), and the
        // install date the reminder counts from.
        com.example.timeapp.data.AutoBackupWorker.schedule(this)
        CoroutineScope(Dispatchers.IO).launch {
            com.example.timeapp.data.AppSettingsDataStore(this@TimeApplication).ensureInstalledAt(System.currentTimeMillis())
        }
        com.example.timeapp.data.Haptics.init(this)
        // Every week calculation reads the user's first day of the week from here.
        CoroutineScope(Dispatchers.IO).launch {
            com.example.timeapp.data.AppSettingsDataStore(this@TimeApplication).settings.collect { s ->
                com.example.timeapp.data.WeekConfig.firstDay =
                    runCatching { java.time.DayOfWeek.valueOf(s.weekStartsOn) }.getOrDefault(java.time.DayOfWeek.MONDAY)
                // Language & region: how dates, times and amounts look everywhere.
                com.example.timeapp.data.DisplayFormats.use24h = when (s.clockFormat) {
                    "24" -> true
                    "12" -> false
                    else -> android.text.format.DateFormat.is24HourFormat(this@TimeApplication)
                }
                com.example.timeapp.data.DisplayFormats.monthFirst = s.dateOrder == "MONTH_FIRST"
                com.example.timeapp.data.DisplayFormats.currencyLabel = if (s.currencyLabel == "৳") "৳" else "Tk"
                com.example.timeapp.data.Haptics.level = s.hapticLevel
            }
        }
    }
}
