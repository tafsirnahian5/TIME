package com.example.timeapp.ui.security

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.timeapp.ui.components.SegmentedToggle
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

// -1 means "never while foregrounded"; the labels line up 1:1 with these minute values.
private val autoLockOptions = listOf(L("Now") to 0, "30 s" to 30, L("1 min") to 60, L("5 min") to 300, L("15 min") to 900, L("Never") to -1)

/** Settings › Security & app lock. */
@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    onSetupPin: () -> Unit,
    onChangePin: () -> Unit,
    viewModel: SecurityViewModel,
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    var showDisableConfirm by remember { mutableStateOf(false) }

    Scaffold(topBar = { TimeTopBar(title = L("Security & app lock"), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.s16)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Enable app PIN"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (ui.hasPin) L("T!ME asks for your PIN when you open it.") else L("Protect T!ME with a PIN of %1\$s–%2\$s digits.", PIN_MIN_LENGTH, PIN_MAX_LENGTH),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = ui.hasPin,
                        onCheckedChange = { checked -> if (checked) onSetupPin() else showDisableConfirm = true },
                    )
                }
            }

            if (ui.hasPin) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg),
                ) {
                    SettingsActionRow(L("Change PIN"), onChangePin)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Unlock with biometrics"), style = MaterialTheme.typography.bodyLarge)
                        Text(L("A shortcut past PIN entry — your PIN still works if biometrics fail."), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = ui.biometricEnabled, onCheckedChange = { viewModel.setBiometricEnabled(it) })
                }

                Text(L("AUTO-LOCK AFTER"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s20, bottom = Dimens.s8))
                SegmentedToggle(
                    options = autoLockOptions.map { it.first },
                    selectedIndex = autoLockOptions.indexOfFirst { it.second == ui.autoLockSeconds }.coerceAtLeast(0),
                    onSelect = { index -> viewModel.setAutoLockSeconds(autoLockOptions[index].second) },
                    modifier = Modifier.fillMaxWidth(),
                )

                com.example.timeapp.ui.components.SettingsSwitchRow(
                    title = L("Hide T!ME in recent apps"),
                    subtitle = L("While the lock is on, the recent-apps screen shows a blank card instead of your last page."),
                    checked = ui.hideInRecents,
                    onChecked = { viewModel.setHideInRecents(it) },
                )
                Text(
                    L("T!ME also locks as soon as the phone's screen turns off, unless auto-lock is set to Never."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.s8),
                )
                OutlinedButton(onClick = { viewModel.lockNow() }, modifier = Modifier.fillMaxWidth().padding(top = Dimens.s20)) {
                    Text(L("Lock now"))
                }
            }
        }
    }

    if (showDisableConfirm) {
        // Fresh authentication (Phase 16): the current PIN, not just a tap in an unlocked app.
        var pin by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDisableConfirm = false },
            title = { Text(L("Turn off app lock?")) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(L("Anyone with access to this phone will be able to open T!ME without a PIN."))
                    androidx.compose.material3.OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(PIN_MAX_LENGTH); wrong = false },
                        label = { Text(L("Current PIN")) },
                        singleLine = true,
                        isError = wrong,
                        supportingText = if (wrong) { { Text(L("That's not the PIN.")) } } else null,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        modifier = Modifier.padding(top = Dimens.s12),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pin.length >= PIN_MIN_LENGTH,
                    onClick = { viewModel.disableLock(pin) { ok -> if (ok) showDisableConfirm = false else wrong = true } },
                ) { Text(L("Turn off")) }
            },
            dismissButton = { TextButton(onClick = { showDisableConfirm = false }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun SettingsActionRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(Dimens.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
