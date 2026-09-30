package com.example.timeapp.ui.components

import com.example.timeapp.data.L

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * The locale the date pickers draw weeks with: the app's own, with T!ME's week start as its
 * first weekday (the Unicode "fw" extension). Material's picker took the week start from the
 * phone's language, so it began on Sunday while every T!ME calendar began on the day set in
 * Settings.
 */
fun pickerLocale(): java.util.Locale {
    val fw = com.example.timeapp.data.WeekConfig.firstDay.name.take(3).lowercase()
    return runCatching {
        java.util.Locale.Builder().setLocale(com.example.timeapp.data.Lang.locale).setUnicodeLocaleKeyword("fw", fw).build()
    }.getOrDefault(com.example.timeapp.data.Lang.locale)
}

/** Material date and time pickers returning java.time values; null means cancelled. Shared by
 * the schedule-block form and the session editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerPrompt(initial: LocalDate, onResult: (LocalDate?) -> Unit) {
    val state = androidx.compose.runtime.remember { androidx.compose.material3.DatePickerState(
        locale = com.example.timeapp.ui.components.pickerLocale(),
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    ) }
    DatePickerDialog(
        onDismissRequest = { onResult(null) },
        confirmButton = {
            TextButton(onClick = {
                onResult(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text(L("OK")) }
        },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text(L("Cancel")) } },
    ) { DatePicker(state = state) }
}

/**
 * The app's one time picker: Material's time-picker dialog, with a title, the clock dial and a
 * keyboard toggle for typing the time instead. It used to be a bare dial squeezed into an
 * AlertDialog's text slot — no title, no way to type, and cramped on small screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerPrompt(initial: LocalTime, title: String = L("Pick a time"), onResult: (LocalTime?) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = com.example.timeapp.data.DisplayFormats.use24h)
    var mode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.material3.TimePickerDisplayMode.Picker) }
    androidx.compose.material3.TimePickerDialog(
        onDismissRequest = { onResult(null) },
        title = { Text(title, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, modifier = androidx.compose.ui.Modifier.padding(bottom = com.example.timeapp.ui.theme.Dimens.s16)) },
        modeToggleButton = {
            androidx.compose.material3.TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    mode = if (mode == androidx.compose.material3.TimePickerDisplayMode.Picker) androidx.compose.material3.TimePickerDisplayMode.Input
                    else androidx.compose.material3.TimePickerDisplayMode.Picker
                },
                displayMode = mode,
            )
        },
        confirmButton = { TextButton(onClick = { onResult(LocalTime.of(state.hour, state.minute)) }) { Text(L("OK")) } },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text(L("Cancel")) } },
    ) {
        // The app's accent throughout: Material's default AM/PM uses the tertiary colour, which
        // came out maroon beside the violet (or any colourway's) selection.
        val c = com.example.timeapp.ui.theme.TimeThemeTokens.colors
        val pickerColors = androidx.compose.material3.TimePickerDefaults.colors(
            periodSelectorSelectedContainerColor = c.accC,
            periodSelectorSelectedContentColor = c.accInk,
            periodSelectorBorderColor = com.example.timeapp.ui.theme.TimeThemeTokens.status.controlOutline,
            timeSelectorSelectedContainerColor = c.accC,
            timeSelectorSelectedContentColor = c.accInk,
            selectorColor = c.acc,
        )
        if (mode == androidx.compose.material3.TimePickerDisplayMode.Picker) TimePicker(state = state, colors = pickerColors)
        else androidx.compose.material3.TimeInput(state = state, colors = pickerColors)
    }
}

/** "45 min", "1 h", "1 h 30 min" — how long something takes, in whole minutes. */
fun durationLabel(minutes: Int): String = com.example.timeapp.data.durationText(minutes.toLong())

/**
 * How long something takes, in hours and minutes. The task form's estimate used to be a single
 * "Minutes" box, so two and a half hours had to be typed as 150. Quick lengths are one tap;
 * the two fields set anything else. Returns whole minutes, or 0 to clear.
 */
@Composable
fun DurationPickerPrompt(initialMinutes: Int?, title: String = L("How long will it take?"), onResult: (Int?) -> Unit) {
    var hours by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialMinutes?.let { (it / 60).takeIf { h -> h > 0 }?.toString() } ?: "") }
    var mins by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialMinutes?.let { (it % 60).takeIf { m -> m > 0 }?.toString() } ?: "") }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (mins.toIntOrNull() ?: 0)
    val numberKeys = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
    AlertDialog(
        onDismissRequest = { onResult(null) },
        // Typed hours and minutes would be lost to a stray tap outside (the keyboard moves the
        // dialog) — it closes only through its buttons or Back.
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(com.example.timeapp.ui.theme.Dimens.s8),
                ) {
                    listOf(15, 30, 45, 60, 90, 120, 180, 240).forEach { preset ->
                        // Outlined explicitly: on a dialog's surface the default outline vanished and
                        // the chips read as plain text.
                        androidx.compose.material3.FilterChip(
                            selected = total == preset,
                            onClick = { onResult(preset) },
                            label = { Text(durationLabel(preset)) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = com.example.timeapp.ui.theme.TimeThemeTokens.colors.accC,
                                selectedLabelColor = com.example.timeapp.ui.theme.TimeThemeTokens.colors.accInk,
                            ),
                            border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = total == preset,
                                borderColor = com.example.timeapp.ui.theme.TimeThemeTokens.status.controlOutline,
                                selectedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                    }
                }
                Text(
                    L("Or set it exactly:"),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = androidx.compose.ui.Modifier.padding(top = com.example.timeapp.ui.theme.Dimens.s12, bottom = com.example.timeapp.ui.theme.Dimens.s6),
                )
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(com.example.timeapp.ui.theme.Dimens.s10)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = hours,
                        onValueChange = { v -> hours = v.filter(Char::isDigit).take(2) },
                        label = { Text(L("Hours")) },
                        singleLine = true,
                        keyboardOptions = numberKeys,
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = mins,
                        onValueChange = { v -> mins = v.filter(Char::isDigit).take(3) },
                        label = { Text(L("Minutes")) },
                        singleLine = true,
                        keyboardOptions = numberKeys,
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                    )
                }
                if (total > 0) {
                    Text(
                        L("That's %1\$s.", durationLabel(total)),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = androidx.compose.ui.Modifier.padding(top = com.example.timeapp.ui.theme.Dimens.s8),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onResult(total) }, enabled = total in 1..(24 * 60)) { Text(L("OK")) } },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (initialMinutes != null && initialMinutes > 0) TextButton(onClick = { onResult(0) }) { Text(L("Clear")) }
                TextButton(onClick = { onResult(null) }) { Text(L("Cancel")) }
            }
        },
    )
}
