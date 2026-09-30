package com.example.timeapp.ui.components

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** A labelled card of settings rows, the shape every settings page uses (canvas). */
@Composable
fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Dimens.s20, bottom = Dimens.s8, start = Dimens.s4).semantics { heading() })
    Column(Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg).border(Dimens.hairline, TimeThemeTokens.colors.line, Shapes.lg)) { content() }
}

@Composable
fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = Dimens.s14).height(Dimens.hairline).background(TimeThemeTokens.colors.line2))
}

/** A row that shows its current value in the accent and opens an editor on tap. */
@Composable
fun SettingsValueRow(title: String, value: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin + Dimens.s8).clickable(onClick = onClick).padding(horizontal = Dimens.s16, vertical = Dimens.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(value, style = MaterialTheme.typography.titleSmall, color = TimeThemeTokens.colors.acc)
    }
}

/** A switch row; the whole row toggles, and it reads as one switch to TalkBack. */
@Composable
fun SettingsSwitchRow(title: String, checked: Boolean, subtitle: String? = null, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin + Dimens.s8)
            .clickable(enabled = enabled, role = Role.Switch) { onChecked(!checked) }
            .padding(horizontal = Dimens.s16, vertical = Dimens.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Asks for a whole number of minutes in [range]; [allowZero] lets 0 mean "off". */
@Composable
fun MinutesDialog(title: String, initial: Int, range: IntRange, onDismiss: () -> Unit, onSave: (Int) -> Unit, caption: String? = null) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()?.takeIf { it in range }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter(Char::isDigit).take(4) },
                    label = { Text(L("Minutes")) }, singleLine = true, isError = value == null,
                    supportingText = { Text(caption ?: "${range.first}–${range.last}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(enabled = value != null, onClick = { value?.let(onSave) }) { Text(L("Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}
