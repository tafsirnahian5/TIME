package com.example.timeapp.ui.backup

import com.example.timeapp.data.L

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.timeapp.data.RestoreResult
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens
import com.example.timeapp.ui.theme.Shapes
import java.time.Instant
import java.time.ZoneId

/**
 * Restore preview/confirm — real Restore requires re-passing [com.example.timeapp.ui.TimeApp]'s
 * own lock gate first when one is enabled, driven by [awaitingReauth]/[locked] rather than a
 * second in-screen prompt (see [BackupViewModel.armReauth]'s call sites for the flow).
 */
@Composable
fun RestorePreviewScreen(
    onBack: () -> Unit,
    lockEnabled: Boolean,
    locked: Boolean,
    onLockNow: () -> Unit,
    viewModel: BackupViewModel,
) {
    val ui by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(locked, ui.awaitingReauth) {
        if (ui.awaitingReauth && !locked) {
            viewModel.clearReauthArm()
            viewModel.confirmRestore(onRestored = { com.example.timeapp.ui.components.restartApp(context) })
        }
    }

    Scaffold(topBar = { TimeTopBar(title = L("Restore backup"), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            val preview = ui.restorePreview
            if (preview == null && ui.restoreNeedsPassword) {
                // A protected backup: nothing is read, and nothing can change, until it opens.
                var password by remember { mutableStateOf("") }
                Text(
                    L("This backup is protected with a password."),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Dimens.s24, bottom = Dimens.s12),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(L("Backup password")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = ui.restoreWrongPassword,
                    supportingText = if (ui.restoreWrongPassword) {
                        { Text(L("Wrong password, or the file is damaged. Nothing was changed.")) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { ui.pendingRestoreUri?.let { viewModel.previewRestore(it, password.toCharArray()) } },
                    enabled = password.isNotEmpty() && !ui.inProgress,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s12),
                ) { Text(if (ui.inProgress) L("Opening…") else L("Open backup")) }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(L("Cancel")) }
            } else if (preview == null) {
                Text(
                    L("This file couldn't be read as a T!ME backup."),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Dimens.s24),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                    Text(L("BACKUP FILE"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s10))
                    StatRow(L("Created"), Instant.ofEpochMilli(preview.createdAtEpochMs).atZone(ZoneId.systemDefault()).format(displayPattern("d MMM yyyy, HH:mm")))
                    StatRow(L("App version"), preview.appVersionName)
                    StatRow(L("Format version"), preview.formatVersion.toString())
                    StatRow(L("Password"), if (ui.restoreEncrypted) L("Protected") else L("None"))
                }
                Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                    Text(L("WHAT'S INSIDE"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s10))
                    Text(
                        preview.recordCounts.entries.joinToString(" · ") { "${it.value} ${it.key}" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.status.danger, Shapes.lg).padding(Dimens.s14)) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = TimeThemeTokens.status.dangerInk)
                    Text(
                        L("This replaces everything currently on this phone. A safety copy of your current data is taken automatically first."),
                        style = MaterialTheme.typography.bodySmall,
                        color = TimeThemeTokens.status.dangerInk,
                        modifier = Modifier.padding(start = Dimens.s10),
                    )
                }

                when (val result = ui.restoreResult) {
                    RestoreResult.CorruptZip -> ErrorText(L("That file looks corrupted — try a different backup."))
                    RestoreResult.MissingManifest -> ErrorText(L("This zip isn't a T!ME backup."))
                    RestoreResult.ChecksumMismatch -> ErrorText(L("The backup's data didn't match its checksum — it may be damaged."))
                    is RestoreResult.UnsupportedFormatVersion -> ErrorText(L("This backup was made by a newer version of T!ME and can't be read here."))
                    is RestoreResult.UnsupportedSchema -> ErrorText(
                        if (result.newer) L("This backup was made by a newer version of T!ME — update the app, then restore it. Nothing was changed.")
                        else L("This backup is from an early test version of T!ME that this one can't convert. Nothing was changed."),
                    )
                    RestoreResult.NeedsPassword, RestoreResult.WrongPassword ->
                        ErrorText(L("Wrong password, or the file is damaged. Nothing was changed."))
                    RestoreResult.Success, null -> Unit
                }
                }
                Button(
                    onClick = {
                        if (lockEnabled) {
                            viewModel.armReauth()
                            onLockNow()
                        } else {
                            viewModel.confirmRestore(onRestored = { com.example.timeapp.ui.components.restartApp(context) })
                        }
                    },
                    enabled = !ui.inProgress,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16),
                ) { Text(L("Confirm restore")) }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(L("Cancel")) }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s6)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.s10))
}


