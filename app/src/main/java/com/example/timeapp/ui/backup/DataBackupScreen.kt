package com.example.timeapp.ui.backup

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.Instant
import java.time.ZoneId

/** Settings › Data & backup — real SAF-based ZIP export/import of the whole Room DB. */
@Composable
fun DataBackupScreen(
    onBack: () -> Unit,
    onRestorePreview: () -> Unit,
    lockEnabled: Boolean,
    viewModel: BackupViewModel,
    onImportCsv: () -> Unit = {},
    onImportBookmarks: () -> Unit = {},
    onImportCalendar: () -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var confirmingWipe by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.createBackup(uri) else viewModel.setBackupPassword(null)
    }
    // A protected backup isn't a zip anyone else can open, so it gets its own type and name.
    val createProtectedLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.createBackup(uri) else viewModel.setBackupPassword(null)
    }
    var choosingBackup by remember { mutableStateOf(false) }
    val exportTransactionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            viewModel.exportTransactionsCsv(uri) { ok ->
                Toast.makeText(context, if (ok) L("Transactions exported") else L("Couldn't export"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val exportTimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            viewModel.exportTimeCsv(uri) { ok ->
                Toast.makeText(context, if (ok) L("Time exported") else L("Couldn't export"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val exportLinksLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) {
            viewModel.exportBookmarksHtml(uri) { ok ->
                Toast.makeText(context, if (ok) L("Links exported") else L("Couldn't export"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val exportLinksJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            viewModel.exportBookmarksJson(uri) { ok ->
                Toast.makeText(context, if (ok) L("Links exported") else L("Couldn't export"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val exportCalendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) {
            viewModel.exportCalendar(uri) { ok ->
                Toast.makeText(context, if (ok) L("Calendar exported") else L("Couldn't export"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.previewRestore(uri)
            onRestorePreview()
        }
    }

    LaunchedEffect(ui.creationResult) {
        when (ui.creationResult) {
            true -> Toast.makeText(context, L("Backup saved"), Toast.LENGTH_SHORT).show()
            false -> Toast.makeText(context, L("Couldn't create the backup"), Toast.LENGTH_SHORT).show()
            null -> Unit
        }
    }

    Scaffold(topBar = { TimeTopBar(title = L("Data & backup"), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.s16)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(40.dp).background(colors.accC, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = colors.acc)
                }
                Column(modifier = Modifier.padding(start = Dimens.s12)) {
                    Text(L("Last backup"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (ui.lastBackupEpochMs > 0) {
                            Instant.ofEpochMilli(ui.lastBackupEpochMs).atZone(ZoneId.systemDefault())
                                .format(displayPattern("d MMM, HH:mm"))
                        } else {
                            L("Never backed up yet")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s20)) {
                Button(
                    onClick = { choosingBackup = true },
                    enabled = !ui.inProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(L("Create backup"), modifier = Modifier.padding(start = Dimens.s8))
                }
                Text(
                    L("Saves everything to a location you choose — with a password if you like, so it can only be restored by someone who knows it."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimens.s8, start = Dimens.s4),
                )

                OutlinedButton(
                    onClick = { openLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    enabled = !ui.inProgress,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s20),
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(L("Restore from backup"), modifier = Modifier.padding(start = Dimens.s8))
                }
                Text(
                    L("Pick a backup file you made earlier (.zip, or .timebackup if it has a password). It replaces what's on this phone — you'll see what's in it first."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimens.s8, start = Dimens.s4),
                )
            }

            AutoBackupCard(viewModel)

            // Only when it's true. This used to say "This file is not encrypted" on every visit —
            // written before backups could have a password, and about no file in particular.
            val autoSettings by viewModel.settings.collectAsState()
            if (autoSettings.autoBackupEnabled && autoSettings.autoBackupPasswordSealed == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s20).background(TimeThemeTokens.status.warn, Shapes.lg).padding(Dimens.s14),
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = TimeThemeTokens.status.warnInk, modifier = Modifier.size(18.dp))
                    Text(
                        L("Automatic backups have no password, so anyone who can open that folder can read them. Set a password above to lock them."),
                        style = MaterialTheme.typography.bodySmall,
                        color = TimeThemeTokens.status.warnInk,
                        modifier = Modifier.padding(start = Dimens.s10),
                    )
                }
            }

            Text(
                "EXPORT",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = Dimens.s20, bottom = Dimens.s8),
            )
            Column(modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportTransactionsLauncher.launch("time-transactions.csv") }
                        .padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Export transactions (CSV)"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L("Your money ledger as a spreadsheet: date, type, category, wallet, exact amount, note."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportTimeLauncher.launch("time-sessions.csv") }
                        .padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Export tracked time (CSV)"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L("Every completed work session: date, task, category, start, end, minutes."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportLinksLauncher.launch("time-links.html") }
                        .padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Export links (HTML)"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L("A bookmarks file any browser can import — folders, notes and favourites included."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportLinksJsonLauncher.launch("time-links.json") }
                        .padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Export links (JSON)"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L("Every link with its folder, note, favourite, saved date and archive state — for other tools."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportCalendarLauncher.launch("time-calendar.ics") }
                        .padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Export calendar (.ics)"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L("Your booked time, for any calendar app. Tasks with only a due date aren't calendar events."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Text(
                "IMPORT",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = Dimens.s20, bottom = Dimens.s8),
            )
            Column(modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onImportCsv).padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Import transactions (CSV)"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L("From a bank statement or spreadsheet. You check every row, and duplicates, before anything is saved."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onImportCalendar).padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Import calendar (.ics)"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L("Events from a calendar app, shown in your time zone before anything is saved."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onImportBookmarks).padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Import bookmarks (HTML)"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L("A bookmarks file exported from a browser, previewed before anything is saved."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            // Transactions can come in now, so the old "can't be imported back" was no longer
            // true — but a CSV still isn't a backup: no tasks, plans, habits or settings.
            Text(
                L("These move one kind of record each. To move everything — tasks, bills, habits, settings — use a backup."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s8),
            )

            Text(
                L("DEMO & RESET"),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = Dimens.s20, bottom = Dimens.s8),
            )
            Column(modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.loadDemoData { Toast.makeText(context, L("Demo data loaded"), Toast.LENGTH_SHORT).show() } }
                        .padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Load demo data"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L("Sample tasks, bills and wallets to try the app with. Only works while the app is empty, so it can never mix into your own records."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmingWipe = true }
                        .padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Delete all data"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        Text(
                            L("Removes every task, record, idea and link. Your settings and app lock stay. Back up first if you might want any of it."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (lockEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s10).background(colors.accS, Shapes.lg).padding(Dimens.s14),
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.acc, modifier = Modifier.size(18.dp))
                    Text(
                        L("Restore will ask you to unlock again before it replaces anything."),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = Dimens.s10),
                    )
                }
            }
        }
    }

    if (choosingBackup) {
        BackupPasswordDialog(
            onDismiss = { choosingBackup = false },
            onPlain = {
                choosingBackup = false
                viewModel.setBackupPassword(null)
                createLauncher.launch("time-backup-${System.currentTimeMillis()}.zip")
            },
            onProtected = { password ->
                choosingBackup = false
                viewModel.setBackupPassword(password)
                createProtectedLauncher.launch("time-backup-${System.currentTimeMillis()}.timebackup")
            },
        )
    }

    if (confirmingWipe) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingWipe = false },
            title = { Text(L("Delete all data?")) },
            text = {
                Text(
                    L("Every task, bill, payment, wallet, idea and link is removed from this phone. This cannot be undone, and a backup made earlier is the only way back."),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmingWipe = false
                    viewModel.deleteAllRecords { Toast.makeText(context, L("All data deleted"), Toast.LENGTH_SHORT).show() }
                }) { Text(L("Delete everything"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmingWipe = false }) { Text(L("Cancel")) }
            },
        )
    }
}

/**
 * Before a backup: protect it with a password, or not. The password is asked twice and never
 * stored — the copy says plainly that a forgotten one can't be recovered.
 */
@Composable
private fun BackupPasswordDialog(onDismiss: () -> Unit, onPlain: () -> Unit, onProtected: (CharArray) -> Unit) {
    var protect by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val min = com.example.timeapp.data.BackupCrypto.MIN_PASSWORD_LENGTH
    val problem = when {
        !protect -> null
        first.length < min -> L("At least %1\$s characters", min)
        second.isNotEmpty() && second != first -> L("The two don't match")
        else -> null
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Create backup")) },
        text = {
            Column {
                com.example.timeapp.ui.components.SettingsSwitchRow(
                    L("Protect with a password"), protect,
                    subtitle = L("Needed to restore it, here or on another phone"),
                ) { protect = it }
                if (protect) {
                    androidx.compose.material3.OutlinedTextField(
                        value = first, onValueChange = { first = it },
                        label = { Text(L("Password")) }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = second, onValueChange = { second = it },
                        label = { Text(L("Same password again")) }, singleLine = true,
                        isError = problem != null && (second.isNotEmpty() || first.length >= min),
                        supportingText = { Text(problem ?: L("T!ME can't recover a forgotten password — the backup would be unreadable.")) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !protect || (problem == null && second == first),
                onClick = { if (protect) onProtected(first.toCharArray()) else onPlain() },
            ) { Text(L("Choose where to save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}

/**
 * Automatic backups (Phase 17): a folder the user grants once, daily or weekly, an optional
 * password (sealed on this phone), the last result — and, when something broke, what and a way
 * to fix it. With them off, a reminder after N days without any backup.
 */
@Composable
private fun AutoBackupCard(viewModel: BackupViewModel) {
    val s by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var settingPassword by remember { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Keep the grant across restarts — without it the job would lose the folder.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setAutoBackupFolder(uri)
            viewModel.setAutoBackupEnabled(true)
        }
    }
    val folderName = s.autoBackupTree?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: L("Chosen folder") }
    com.example.timeapp.ui.components.SettingsGroup(L("AUTOMATIC BACKUPS")) {
        com.example.timeapp.ui.components.SettingsSwitchRow(
            L("Back up automatically"), s.autoBackupEnabled,
            subtitle = L("Keeps the newest %1\$s; older ones go only after a new one checks out", com.example.timeapp.data.AutoBackup.KEEP),
        ) { on -> if (on && s.autoBackupTree == null) folderLauncher.launch(null) else viewModel.setAutoBackupEnabled(on) }
        if (s.autoBackupEnabled) {
            com.example.timeapp.ui.components.SettingsDivider()
            Column(Modifier.padding(Dimens.s16)) {
                com.example.timeapp.ui.components.SegmentedToggle(
                    options = com.example.timeapp.data.AutoBackup.Frequency.entries.map { it.label },
                    selectedIndex = com.example.timeapp.data.AutoBackup.Frequency.entries.indexOfFirst { it.name == s.autoBackupFrequency }.coerceAtLeast(0),
                    onSelect = { viewModel.setAutoBackupFrequency(com.example.timeapp.data.AutoBackup.Frequency.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            com.example.timeapp.ui.components.SettingsDivider()
            com.example.timeapp.ui.components.SettingsValueRow(L("Folder"), folderName ?: L("Not chosen"), subtitle = L("Tap to choose another")) { folderLauncher.launch(null) }
            com.example.timeapp.ui.components.SettingsDivider()
            com.example.timeapp.ui.components.SettingsValueRow(
                L("Password"), if (s.autoBackupPasswordSealed != null) L("On") else L("Off"),
                subtitle = L("Kept on this phone only, sealed by Android's Keystore"),
            ) { settingPassword = true }
            com.example.timeapp.ui.components.SettingsDivider()
            Column(Modifier.padding(Dimens.s16)) {
                val last = s.lastAutoBackupAt.takeIf { it > 0 }?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(displayPattern("d MMM, HH:mm"))
                }
                val error = s.lastAutoBackupError
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = TimeThemeTokens.status.dangerInk)
                    TextButton(onClick = { folderLauncher.launch(null) }) { Text(L("Choose the folder again")) }
                } else {
                    Text(
                        last?.let { L("Last automatic backup: %1\$s", it) } ?: L("No automatic backup yet"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = {
                    com.example.timeapp.data.AutoBackupWorker.runNow(context)
                    Toast.makeText(context, L("Backing up in the background"), Toast.LENGTH_SHORT).show()
                }) { Text(L("Back up now")) }
            }
        } else {
            com.example.timeapp.ui.components.SettingsDivider()
            Column(Modifier.padding(Dimens.s16)) {
                Text(L("Remind me to back up after"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = Dimens.s10))
                val options = listOf(0 to L("Never"), 7 to L("7 days"), 14 to L("14 days"), 30 to L("30 days"))
                com.example.timeapp.ui.components.SegmentedToggle(
                    options = options.map { it.second },
                    selectedIndex = options.indexOfFirst { it.first == s.backupReminderDays }.coerceAtLeast(0),
                    onSelect = { viewModel.setBackupReminderDays(options[it].first) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (settingPassword) {
        AutoBackupPasswordDialog(
            hasPassword = s.autoBackupPasswordSealed != null,
            onDismiss = { settingPassword = false },
            onSave = { pw ->
                viewModel.setAutoBackupPassword(pw) { ok ->
                    if (!ok) Toast.makeText(context, L("This phone couldn't store the password safely"), Toast.LENGTH_LONG).show()
                }
                settingPassword = false
            },
        )
    }
}

@Composable
private fun AutoBackupPasswordDialog(hasPassword: Boolean, onDismiss: () -> Unit, onSave: (CharArray?) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val min = com.example.timeapp.data.BackupCrypto.MIN_PASSWORD_LENGTH
    val valid = first.length >= min && first == second
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Automatic backup password")) },
        text = {
            Column {
                Text(
                    L("New automatic backups are protected with it. T!ME can't recover a forgotten password — keep it somewhere safe."),
                    style = MaterialTheme.typography.bodySmall,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = first, onValueChange = { first = it }, label = { Text(L("Password")) }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = second, onValueChange = { second = it }, label = { Text(L("Same password again")) }, singleLine = true,
                    isError = second.isNotEmpty() && second != first,
                    supportingText = { Text(if (first.isNotEmpty() && first.length < min) L("At least %1\$s characters", min) else "") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(first.toCharArray()) }) { Text(L("Save")) } },
        dismissButton = {
            Row {
                if (hasPassword) TextButton(onClick = { onSave(null) }) { Text(L("Turn off")) }
                TextButton(onClick = onDismiss) { Text(L("Cancel")) }
            }
        },
    )
}
