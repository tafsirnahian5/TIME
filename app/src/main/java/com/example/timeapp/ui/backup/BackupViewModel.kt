package com.example.timeapp.ui.backup

import kotlinx.coroutines.flow.flowOn
import com.example.timeapp.data.L

import com.example.timeapp.data.db.toDomain
import kotlinx.coroutines.flow.first
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.BackupManager
import com.example.timeapp.data.RestorePreview
import com.example.timeapp.data.RestoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val lastBackupEpochMs: Long = 0L,
    val inProgress: Boolean = false,
    val creationResult: Boolean? = null,
    val restorePreview: RestorePreview? = null,
    val restoreResult: RestoreResult? = null,
    val pendingRestoreUri: Uri? = null,
    // True while waiting for the user to pass TimeApp's lock gate again before a destructive
    // restore commits — see RestorePreviewScreen's LaunchedEffect for how this resolves.
    val awaitingReauth: Boolean = false,
    /** The picked backup is password-protected and needs its password to be read. */
    val restoreNeedsPassword: Boolean = false,
    /** The password given didn't open it. */
    val restoreWrongPassword: Boolean = false,
    val restoreEncrypted: Boolean = false,
)

class BackupViewModel(
    private val manager: BackupManager,
    private val settingsStore: AppSettingsDataStore,
    private val database: com.example.timeapp.data.db.TimeDatabase,
    private val reminders: com.example.timeapp.data.repository.ReminderRepository,
) : ViewModel() {
    private val local = MutableStateFlow(BackupUiState())

    val uiState: StateFlow<BackupUiState> = combine(local, settingsStore.settings) { state, settings ->
        state.copy(lastBackupEpochMs = settings.lastBackupEpochMs)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, BackupUiState())

    // Passwords live only in memory, only until they're used, and are zeroed after.
    private var backupPassword: CharArray? = null
    private var restorePassword: CharArray? = null

    /** The password for the next backup (null = none), chosen before the file picker opens. */
    fun setBackupPassword(password: CharArray?) {
        backupPassword?.fill('\u0000')
        backupPassword = password
    }

    fun createBackup(destUri: Uri) {
        val password = backupPassword
        backupPassword = null
        viewModelScope.launch {
            local.update { it.copy(inProgress = true, creationResult = null) }
            val ok = manager.createBackup(destUri, password)
            password?.fill('\u0000')
            local.update { it.copy(inProgress = false, creationResult = ok) }
        }
    }

    fun previewRestore(sourceUri: Uri, password: CharArray? = null) {
        restorePassword?.fill('\u0000')
        restorePassword = password
        viewModelScope.launch {
            local.update {
                it.copy(inProgress = true, pendingRestoreUri = sourceUri, restoreResult = null, restoreWrongPassword = false)
            }
            val outcome = manager.previewRestore(sourceUri, password)
            local.update {
                when (outcome) {
                    is com.example.timeapp.data.PreviewOutcome.Ok -> it.copy(
                        inProgress = false, restorePreview = outcome.preview, restoreNeedsPassword = false,
                        restoreEncrypted = outcome.encrypted,
                    )
                    com.example.timeapp.data.PreviewOutcome.NeedsPassword -> it.copy(inProgress = false, restorePreview = null, restoreNeedsPassword = true)
                    com.example.timeapp.data.PreviewOutcome.WrongPassword -> it.copy(
                        inProgress = false, restorePreview = null, restoreNeedsPassword = true, restoreWrongPassword = true,
                    )
                    com.example.timeapp.data.PreviewOutcome.Corrupt -> it.copy(
                        inProgress = false, restorePreview = null, restoreNeedsPassword = false, restoreResult = RestoreResult.CorruptZip,
                    )
                }
            }
        }
    }

    /** Adds the sample content, but only to an empty app — it can never duplicate or
     * overwrite real records. */
    fun loadDemoData(onDone: () -> Unit) {
        viewModelScope.launch {
            database.seedIfEmpty()
            onDone()
        }
    }

    /** Wipes every record, keeping preferences and the app lock. Pending reminder alarms are
     * cancelled first, since those live in AlarmManager rather than in the database. */
    fun deleteAllRecords(onDone: () -> Unit) {
        viewModelScope.launch {
            database.reminderDao().pending().forEach { reminders.clearReminder(it.ownerKind, it.ownerId) }
            // Off the main thread: Room's clearAllTables is blocking and refuses the main thread,
            // so this crashed the app every time (the wipe never happened).
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { database.clearAllRecords() }
            onDone()
        }
    }

    /** CSV is a convenience export, not a backup — see CsvExport's kdoc. */
    fun exportTransactionsCsv(uri: Uri, onDone: (Boolean) -> Unit) = exportCsv(uri, onDone) { out ->
        com.example.timeapp.data.CsvExport.writeTransactions(database, out)
    }

    /** Every link, archived ones too, as JSON — see BookmarkJson. */
    fun exportBookmarksJson(uri: Uri, onDone: (Boolean) -> Unit) = exportCsv(uri, onDone) { out ->
        val bookmarks = database.bookmarkDao().observeAll().first().map { it.toDomain() }
        out.bufferedWriter().use { it.write(com.example.timeapp.data.BookmarkJson.export(bookmarks, java.time.Instant.now())) }
    }

    /** Links as a browser-readable bookmarks file — see BookmarkHtml. */
    fun exportBookmarksHtml(uri: Uri, onDone: (Boolean) -> Unit) = exportCsv(uri, onDone) { out ->
        val bookmarks = database.bookmarkDao().observeAll().first().map { it.toDomain() }
        out.bufferedWriter().use { it.write(com.example.timeapp.data.BookmarkHtml.export(bookmarks)) }
    }

    /** Scheduled blocks as a calendar file — see [com.example.timeapp.data.Ics]. */
    fun exportCalendar(uri: Uri, onDone: (Boolean) -> Unit) = exportCsv(uri, onDone) { out ->
        val entries = database.entryDao().observeAll().first().map { it.toDomain() }
        out.bufferedWriter().use { it.write(com.example.timeapp.data.Ics.export(entries)) }
    }

    fun exportTimeCsv(uri: Uri, onDone: (Boolean) -> Unit) = exportCsv(uri, onDone) { out ->
        com.example.timeapp.data.CsvExport.writeTimeSessions(database, out)
    }

    private fun exportCsv(uri: Uri, onDone: (Boolean) -> Unit, write: suspend (java.io.OutputStream) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                manager.openOutputStream(uri)?.use { write(it) } ?: error(L("no stream"))
            }.isSuccess
            onDone(ok)
        }
    }

    /** Every setting, for the Automatic backups card. */
    val settings: StateFlow<com.example.timeapp.ui.AppSettingsUiState> = settingsStore.settings
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, com.example.timeapp.ui.AppSettingsUiState())

    fun setAutoBackupEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setAutoBackupEnabled(enabled) }
    fun setAutoBackupFrequency(f: com.example.timeapp.data.AutoBackup.Frequency) = viewModelScope.launch { settingsStore.setAutoBackupFrequency(f.name) }
    fun setAutoBackupFolder(uri: Uri) = viewModelScope.launch { settingsStore.setAutoBackupTree(uri.toString()) }
    fun setBackupReminderDays(days: Int) = viewModelScope.launch { settingsStore.setBackupReminderDays(days) }

    /** Seals [password] with the Keystore (SecretBox) for the background job; null removes it.
     * [onDone] gets false when this phone's Keystore couldn't seal it. */
    fun setAutoBackupPassword(password: CharArray?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val sealed = password?.let { com.example.timeapp.data.SecretBox.seal(it) }
            password?.fill('\u0000')
            if (password != null && sealed == null) { onDone(false); return@launch }
            settingsStore.setAutoBackupPasswordSealed(sealed)
            onDone(true)
        }
    }

    fun armReauth() = local.update { it.copy(awaitingReauth = true) }
    fun clearReauthArm() = local.update { it.copy(awaitingReauth = false) }

    fun confirmRestore(onRestored: () -> Unit) {
        val uri = uiState.value.pendingRestoreUri ?: return
        viewModelScope.launch {
            local.update { it.copy(inProgress = true) }
            val result = manager.restoreBackup(uri, restorePassword)
            if (result is RestoreResult.Success) { restorePassword?.fill('\u0000'); restorePassword = null }
            local.update { it.copy(inProgress = false, restoreResult = result) }
            if (result is RestoreResult.Success) onRestored()
        }
    }

    class Factory(
        private val manager: BackupManager,
        private val settingsStore: AppSettingsDataStore,
        private val database: com.example.timeapp.data.db.TimeDatabase,
        private val reminders: com.example.timeapp.data.repository.ReminderRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BackupViewModel(manager, settingsStore, database, reminders) as T
    }
}
