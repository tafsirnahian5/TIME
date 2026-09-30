package com.example.timeapp.ui.focus

import com.example.timeapp.data.L

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.TimerPreset
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Named presets for one mode (the PDF's TimerPreset): tap to use one for the next run,
 * long-press to delete, *Save current* to keep what's set now. Only offered before a run —
 * a started phase keeps its own length.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PresetRow(
    presets: List<TimerPreset>,
    mode: FocusMode,
    currentMinutes: Int,
    currentBreakMinutes: Int?,
    onApply: (TimerPreset) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (TimerPreset) -> Unit,
) {
    val colors = TimeThemeTokens.colors
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TimerPreset?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Dimens.s12),
        horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.filter { it.mode == mode }.forEach { p ->
            val selected = p.minutes == currentMinutes && (mode != FocusMode.POMODORO || p.breakMinutes == currentBreakMinutes)
            Text(
                p.name + " · " + if (mode == FocusMode.POMODORO) "${p.minutes}/${p.breakMinutes}" else L("%1\$s min", p.minutes),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(if (selected) colors.accC else colors.card, Shapes.pill)
                    .border(Dimens.hairline, colors.line, Shapes.pill)
                    .combinedClickable(onClick = { onApply(p) }, onLongClick = { deleting = p })
                    .heightIn(min = Dimens.touchMin)
                    .padding(horizontal = Dimens.s14, vertical = Dimens.s14),
            )
        }
        TextButton(onClick = { saving = true }) { Text(L("Save as preset")) }
    }
    if (saving) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text(L("Name this preset")) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                    placeholder = { Text(L("Deep work")) },
                    supportingText = { Text(if (mode == FocusMode.POMODORO) L("%1\$s min focus, %2\$s min break", currentMinutes, (currentBreakMinutes ?: 0)) else L("%1\$s minutes", currentMinutes)) },
                )
            },
            confirmButton = { TextButton(onClick = { onSave(name); saving = false }, enabled = name.isNotBlank()) { Text(L("Save")) } },
            dismissButton = { TextButton(onClick = { saving = false }) { Text(L("Cancel")) } },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(L("Delete “%1\$s”?", p.name)) },
            text = { Text(L("Only the preset goes; nothing you've tracked changes.")) },
            confirmButton = { TextButton(onClick = { onDelete(p); deleting = null }) { Text(L("Delete")) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(L("Cancel")) } },
        )
    }
}

/** A banner linking to sessions that need a look. */
@Composable
internal fun ReviewBanner(count: Int, onOpen: () -> Unit) {
    if (count == 0) return
    TextButton(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s8).background(TimeThemeTokens.status.warn, Shapes.md),
    ) {
        Text(
            if (count == 1) L("1 session needs review — check its times") else L("%1\$s sessions need review — check their times", count),
            color = TimeThemeTokens.status.warnInk,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
