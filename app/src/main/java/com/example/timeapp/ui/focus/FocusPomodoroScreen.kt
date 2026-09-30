package com.example.timeapp.ui.focus

import com.example.timeapp.data.L

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

private const val CYCLES_PER_LONG_BREAK = 4

/**
 * F2 — Focus · Pomodoro on the one work timer: a focus phase is a real work session, a break
 * is not work time, and phases end by themselves (the alarm covers the background).
 * Auto-start is opt-in; ending a cycle never marks the task Done.
 */
@Composable
fun FocusPomodoroScreen(
    onOpenStopwatch: () -> Unit,
    onOpenCountdown: () -> Unit,
    viewModel: FocusViewModel,
    planViewModel: PlanViewModel,
    onSwitchWork: () -> Unit,
    onOpenTimers: () -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val plan by planViewModel.uiState.collectAsState()
    val rt = ui.runtime
    val mine = rt.mode == FocusMode.POMODORO || !ui.inRun
    val onBreak = ui.inRun && rt.phase == FocusPhase.BREAK
    val phaseMs = if (ui.inRun) rt.targetMs ?: 0L else rt.workMinutes * 60_000L
    val elapsed = if (ui.inRun) ui.elapsedMs else 0L
    var showSettings by remember { mutableStateOf(false) }
    val posInSet = rt.cycles % CYCLES_PER_LONG_BREAK
    val askNotificationPermission = com.example.timeapp.ui.components.rememberNotificationPermissionRequest()

    Scaffold(
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s12), verticalAlignment = Alignment.CenterVertically) {
                com.example.timeapp.ui.components.TabTitle(L("Focus"), com.example.timeapp.ui.components.TabHelps.focus, Modifier.weight(1f))
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Tune, contentDescription = L("Settings")) }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.s16)) {
            FocusModeToggle(selectedIndex = 2, onOpenStopwatch = onOpenStopwatch, onOpenCountdown = onOpenCountdown, onOpenPomodoro = {}, onOpenTimers = onOpenTimers)
            if (!mine) {
                OtherModeCard(rt.mode, onOpen = if (rt.mode == FocusMode.STOPWATCH) onOpenStopwatch else onOpenCountdown, running = rt.running, onFinish = { viewModel.finish() })
                return@Column
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.accS, Shapes.xl).padding(Dimens.s16),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Dimens.s14)) {
                    for (i in 0 until CYCLES_PER_LONG_BREAK) {
                        CycleDot(filled = i <= posInSet, current = i == posInSet)
                    }
                    Text(L("cycle %1\$s of %2\$s", (posInSet + 1), CYCLES_PER_LONG_BREAK), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = Dimens.s6))
                }
                Row(
                    modifier = Modifier.background(colors.acc, Shapes.pill).padding(horizontal = Dimens.s14, vertical = Dimens.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val onAcc = MaterialTheme.colorScheme.onPrimary
                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = onAcc, modifier = Modifier.size(16.dp))
                    Text(
                        if (onBreak) L("BREAK PHASE") else L("FOCUS PHASE"),
                        style = MaterialTheme.typography.labelMedium, color = onAcc, modifier = Modifier.padding(start = Dimens.s6),
                    )
                }
                if (!onBreak) Box(Modifier.padding(top = Dimens.s12)) { FocusTaskChip(ui, plan, onChoose = viewModel::choose, onSwitchWork = onSwitchWork) }
                if (!ui.inRun) {
                    val presets by viewModel.presets.collectAsState()
                    PresetRow(
                        presets, FocusMode.POMODORO, rt.workMinutes, rt.breakMinutes,
                        onApply = viewModel::applyPreset,
                        onSave = { name -> viewModel.savePreset(name, FocusMode.POMODORO, rt.workMinutes, rt.breakMinutes) },
                        onDelete = { viewModel.deletePreset(it.id) },
                    )
                }
                Box(
                    modifier = Modifier.padding(top = Dimens.s16).size(158.dp)
                        .border(10.dp, colors.acc, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatElapsed((phaseMs - elapsed).coerceAtLeast(0)), style = MaterialTheme.typography.displaySmall)
                        Text(L("of %1\$s", formatElapsed(phaseMs)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.s4))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s14), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                    Button(
                        onClick = {
                            when {
                                ui.running -> viewModel.pause()
                                ui.inRun && (onBreak || rt.accumulatedMs > 0 || rt.entryId != null) -> viewModel.resume()
                                else -> {
                                    askNotificationPermission()
                                    viewModel.start(if (ui.inRun) rt.entryId else ui.pendingTaskId(plan), FocusMode.POMODORO)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(if (ui.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(if (ui.running) L("Pause") else if (ui.inRun) L("Resume") else L("Start"), modifier = Modifier.padding(start = Dimens.s8))
                    }
                    Button(
                        onClick = viewModel::skipPhase,
                        enabled = ui.inRun,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accC, contentColor = colors.accInk),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(if (onBreak) L("Skip to focus") else L("Skip to break"), modifier = Modifier.padding(start = Dimens.s8))
                    }
                }
                if (ui.inRun) {
                    TextButton(onClick = { viewModel.finish() }, modifier = Modifier.padding(top = Dimens.s4)) { Text(L("Finish this run")) }
                }
            }
            com.example.timeapp.ui.components.ExactAlarmNote(Modifier.padding(top = Dimens.s16))
            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg)) {
                SettingsRow(L("Focus / short break"), L("%1\$s / %2\$s min", rt.workMinutes, rt.breakMinutes), onClick = { showSettings = true })
                Row(modifier = Modifier.fillMaxWidth().padding(Dimens.s14), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Auto-start next phase"), style = MaterialTheme.typography.bodyLarge)
                        Text(L("Off by default — you choose when to continue"), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = rt.autoStart, onCheckedChange = { viewModel.setAutoStart(it) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s12)) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = colors.acc, modifier = Modifier.size(18.dp))
                Text(
                    L("Break phases are not active work, and ending a cycle never marks the task Done."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.s10),
                )
            }
        }
    }

    if (showSettings) {
        var workDraft by remember { mutableStateOf(rt.workMinutes.toString()) }
        var breakDraft by remember { mutableStateOf(rt.breakMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(L("Focus / break length")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = workDraft, onValueChange = { workDraft = it.filter(Char::isDigit) },
                        label = { Text(L("Focus minutes")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s10),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = breakDraft, onValueChange = { breakDraft = it.filter(Char::isDigit) },
                        label = { Text(L("Short break minutes")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDurations(workDraft.toIntOrNull() ?: rt.workMinutes, breakDraft.toIntOrNull() ?: rt.breakMinutes)
                    showSettings = false
                }) { Text(L("Save")) }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun CycleDot(filled: Boolean, current: Boolean = false) {
    val colors = TimeThemeTokens.colors
    Box(
        modifier = Modifier
            .padding(end = Dimens.s6)
            .size(if (current) 15.dp else 11.dp)
            .background(if (filled) colors.acc else colors.edge2, CircleShape),
    )
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Dimens.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
