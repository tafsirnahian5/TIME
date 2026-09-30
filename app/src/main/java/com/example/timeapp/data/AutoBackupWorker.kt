package com.example.timeapp.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.timeapp.MainActivity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.notify.NotificationChannels
import com.example.timeapp.ui.AppSettingsUiState
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The daily maintenance job (Phase 17). If automatic backups are on and one is due, it writes a
 * new backup into the folder the user granted, **verifies it**, and only then deletes automatic
 * copies beyond the newest [AutoBackup.KEEP]. A failure — the folder's permission revoked, the
 * disk full, verification failing — keeps every older copy, records why, and posts one
 * notification that opens Data & backup to repair it. With automatic backups off, it may
 * instead remind someone who backs up by hand (every [AppSettingsUiState.backupReminderDays]).
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val store = AppSettingsDataStore(app)
        val s = store.settings.first()
        val now = Instant.now()
        val force = inputData.getBoolean(KEY_FORCE, false)
        val frequency = AutoBackup.Frequency.entries.firstOrNull { it.name == s.autoBackupFrequency } ?: AutoBackup.Frequency.DAILY
        val lastAuto = s.lastAutoBackupAt.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        if (s.autoBackupEnabled && (force || AutoBackup.isDue(lastAuto, frequency, now))) {
            val error = runBackup(app, s, now)
            store.recordAutoBackup(if (error == null) now.toEpochMilli() else null, error)
            if (error != null) notify(app, s, L("Automatic backup didn't run"), L("%1\$s Tap to fix it in Data & backup.", error), FAILED_ID)
            return Result.success()
        }
        if (!s.autoBackupEnabled) {
            val lastBackup = s.lastBackupEpochMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            val lastReminder = s.lastBackupReminderAt.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            val installed = s.installedAt.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            if (AutoBackup.reminderDue(lastBackup, lastReminder, s.backupReminderDays, now, installed)) {
                notify(
                    app, s, L("Time for a backup"),
                    if (lastBackup == null) L("You haven't backed up T!ME yet. A backup is the only way to move or recover your records.")
                    else L("Your last backup is over %1\$s days old.", s.backupReminderDays),
                    REMINDER_ID,
                )
                store.setLastBackupReminderAt(now.toEpochMilli())
            }
        }
        return Result.success()
    }

    /** Null on success, else a sentence saying what went wrong. */
    private suspend fun runBackup(app: Context, s: AppSettingsUiState, now: Instant): String? {
        val tree = s.autoBackupTree?.let(Uri::parse) ?: return L("No folder is chosen.")
        val resolver = app.contentResolver
        if (resolver.persistedUriPermissions.none { it.uri == tree && it.isWritePermission }) {
            return L("T!ME can no longer write to the backup folder.")
        }
        val password = s.autoBackupPasswordSealed?.let { SecretBox.open(it) ?: return L("The backup password couldn't be unlocked on this phone — set it again.") }
        val name = AutoBackup.fileName(now, ZoneId.systemDefault(), encrypted = password != null)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = try {
            DocumentsContract.createDocument(resolver, parent, if (password != null) "application/octet-stream" else "application/zip", name)
        } catch (e: Exception) {
            null
        } ?: return L("The backup folder couldn't be written to.")
        val manager = BackupManager(app, TimeDatabase.getInstance(app), AppSettingsDataStore(app))
        val ok = manager.createBackup(doc, password) && manager.verifyBackup(doc, password)
        password?.fill('\u0000')
        if (!ok) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            return L("The new backup couldn't be written or checked — the phone may be out of space. Older backups were kept.")
        }
        // Only now that the new copy is known good do the oldest automatic ones go.
        runCatching {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val found = mutableMapOf<String, String>()
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) found[c.getString(1)] = c.getString(0)
            }
            AutoBackup.toDelete(found.keys.toList()).forEach { old ->
                found[old]?.let { id -> DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
            }
        }
        NotificationManagerCompat.from(app).cancel(FAILED_ID)
        return null
    }

    private suspend fun notify(app: Context, s: AppSettingsUiState, title: String, text: String, id: Int) {
        val manager = NotificationManagerCompat.from(app)
        if (!s.notifyMaintenanceChannel) return
        com.example.timeapp.data.repository.InboxRepository(TimeDatabase.getInstance(app))
            .record(com.example.timeapp.data.InboxKind.BACKUP, title, text, "settings/data-backup")
        if (!manager.areNotificationsEnabled()) return
        NotificationChannels.ensureCreated(app)
        val open = PendingIntent.getActivity(
            app, id,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(MainActivity.EXTRA_OPEN_ROUTE, "settings/data-backup"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(app, NotificationChannels.MAINTENANCE)
            .setSmallIcon(com.example.timeapp.R.drawable.ic_stat_time)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { manager.notify(id, n) } catch (e: SecurityException) { /* not permitted — Data & backup shows the status */ }
    }

    companion object {
        private const val UNIQUE = "time_maintenance"
        private const val KEY_FORCE = "force"
        private const val FAILED_ID = -3001
        private const val REMINDER_ID = -3002

        /** Once a day, not on a low battery. Idempotent: an existing schedule is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Data & backup's *Back up now*: runs the automatic backup straight away. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>().setInputData(workDataOf(KEY_FORCE to true)).build()
            WorkManager.getInstance(context).enqueueUniqueWork("$UNIQUE-now", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
