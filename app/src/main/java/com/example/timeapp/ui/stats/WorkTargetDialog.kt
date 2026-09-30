package com.example.timeapp.ui.stats

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.timeapp.data.WorkTarget
import com.example.timeapp.ui.theme.Dimens

/**
 * Sets the weekly work target from this week on, and shows the versions before it — the
 * PDF's "target-history preview", so it's clear an earlier week is still judged against the
 * target it had.
 */
@Composable
fun WorkTargetDialog(
    current: Int?,
    history: List<WorkTarget>,
    onSave: (minutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var hours by remember { mutableStateOf(current?.let { hoursInput(it) } ?: "") }
    val minutes = hours.replace(',', '.').toDoubleOrNull()?.let { (it * 60).toInt() }
    val fmt = displayPattern("d MMM yyyy")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Weekly work target")) },
        text = {
            Column {
                OutlinedTextField(
                    value = hours,
                    onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() || it == '.' }) hours = v },
                    label = { Text(L("Hours a week")) },
                    placeholder = { Text("20") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    L("Applies from this week on. Earlier weeks keep the target they had."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.s8),
                )
                if (history.isNotEmpty()) {
                    Text(L("History"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Dimens.s16, bottom = Dimens.s4))
                    history.sortedByDescending { it.effectiveFrom }.take(5).forEach { t ->
                        Text(
                            (if (t.weeklyMinutes > 0) L("%1\$s h", formatHours(t.weeklyMinutes)) else L("No target")) + L(" from ") + t.effectiveFrom.format(fmt),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(minutes ?: 0) }, enabled = minutes != null && minutes in 0..(168 * 60)) { Text(L("Save")) }
        },
        dismissButton = {
            if (current != null) TextButton(onClick = { onSave(0) }) { Text(L("No target")) }
            else TextButton(onClick = onDismiss) { Text(L("Cancel")) }
        },
    )
}

/** 1200 → "20", 750 → "12.5", in Bangla digits when the app is in Bangla. */
fun formatHours(minutes: Int): String = com.example.timeapp.data.Lang.digits(hoursInput(minutes))

/** For the input field, which parses Latin digits. */
private fun hoursInput(minutes: Int): String =
    if (minutes % 60 == 0) (minutes / 60).toString() else "%.1f".format(java.util.Locale.ROOT, minutes / 60.0)
