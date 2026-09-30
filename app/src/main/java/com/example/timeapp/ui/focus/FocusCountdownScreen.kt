package com.example.timeapp.ui.focus

import com.example.timeapp.data.L

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.ui.components.TimerRing
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * F1b — Focus · Countdown: the same one work timer, with a target, shown as time remaining.
 * When it runs out, work stops at that moment and the screen asks — the PDF's "countdown
 * expiry asks whether to continue, take a break or finish; it does not mark the task Done".
 */
@Composable
fun FocusCountdownScreen(
    onOpenStopwatch: () -> Unit,
    onOpenPomodoro: () -> Unit,
    viewModel: FocusViewModel,
    planViewModel: PlanViewModel,
    onSwitchWork: () -> Unit,
    onOpenTimers: () -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val plan by planViewModel.uiState.collectAsState()
    val rt = ui.runtime
    val mine = rt.mode == FocusMode.COUNTDOWN || !ui.inRun
    val onBreak = ui.inRun && rt.phase == FocusPhase.BREAK
    val targetMs = if (ui.inRun) rt.targetMs ?: 0L else rt.countdownMinutes * 60_000L
    val elapsed = if (ui.inRun) ui.elapsedMs else 0L
    val remaining = (targetMs - elapsed).coerceAtLeast(0)
    var showTargetPicker by remember { mutableStateOf(false) }
    val askNotificationPermission = com.example.timeapp.ui.components.rememberNotificationPermissionRequest()

    Scaffold(
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s12), verticalAlignment = Alignment.CenterVertically) {
                com.example.timeapp.ui.components.TabTitle(L("Focus"), com.example.timeapp.ui.components.TabHelps.focus, Modifier.weight(1f))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.s16)) {
            FocusModeToggle(selectedIndex = 1, onOpenStopwatch = onOpenStopwatch, onOpenCountdown = {}, onOpenPomodoro = onOpenPomodoro, onOpenTimers = onOpenTimers)
            if (!mine) {
                OtherModeCard(rt.mode, onOpen = if (rt.mode == FocusMode.STOPWATCH) onOpenStopwatch else onOpenPomodoro, running = rt.running, onFinish = { viewModel.finish() })
                return@Column
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.accS, Shapes.xl).padding(Dimens.s20),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!onBreak) FocusTaskChip(ui, plan, onChoose = viewModel::choose, onSwitchWork = onSwitchWork)
                if (!ui.inRun) {
                    val presets by viewModel.presets.collectAsState()
                    PresetRow(
                        presets, FocusMode.COUNTDOWN, rt.countdownMinutes, null,
                        onApply = viewModel::applyPreset,
                        onSave = { name -> viewModel.savePreset(name, FocusMode.COUNTDOWN, rt.countdownMinutes) },
                        onDelete = { viewModel.deletePreset(it.id) },
                    )
                }
                if (onBreak) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Coffee, contentDescription = null, tint = colors.acc, modifier = Modifier.size(18.dp))
                        Text(L("On a break — not work time"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Dimens.s6))
                    }
                }
                TimerRing(
                    progress = if (targetMs > 0) elapsed.toFloat() / targetMs else 0f,
                    label = formatElapsed(remaining),
                    modifier = Modifier.padding(top = Dimens.s16),
                )
                Text(
                    L("of %1\$s", formatElapsed(targetMs)) + if (!ui.inRun) L(" · tap to change") else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimens.s4).clickable(enabled = !ui.inRun) { showTargetPicker = true }.padding(Dimens.s8),
                )
                when {
                    rt.awaitingChoice -> ExpiredChoices(
                        minutes = rt.countdownMinutes,
                        breakMinutes = rt.breakMinutes,
                        onExtend = { viewModel.extend(5) },
                        onContinue = viewModel::continueRun,
                        onBreak = viewModel::takeBreak,
                        onFinish = { viewModel.finish() },
                    )
                    onBreak -> Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s20), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                        Button(onClick = { if (ui.running) viewModel.pause() else viewModel.resume() }, modifier = Modifier.weight(1f)) {
                            Text(if (ui.running) L("Pause break") else L("Resume break"))
                        }
                        OutlinedButton(onClick = { viewModel.finish() }, modifier = Modifier.weight(1f)) { Text(L("End break")) }
                    }
                    else -> Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s20), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                        Button(
                            onClick = {
                                when {
                                    ui.running -> viewModel.pause()
                                    ui.inRun -> viewModel.resume()
                                    else -> {
                                        askNotificationPermission()
                                        viewModel.start(ui.pendingTaskId(plan), FocusMode.COUNTDOWN)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(if (ui.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(if (ui.running) L("Pause") else if (ui.inRun) L("Resume") else L("Start"), modifier = Modifier.padding(start = Dimens.s8))
                        }
                        OutlinedButton(onClick = { viewModel.finish() }, enabled = ui.inRun, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(L("Finish"), modifier = Modifier.padding(start = Dimens.s8))
                        }
                    }
                }
            }
            Text(
                L("When it runs out, work stops at that moment and T!ME asks what's next — it never marks the task done by itself."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Dimens.s14),
            )
            com.example.timeapp.ui.components.ExactAlarmNote()
        }
    }

    if (showTargetPicker) {
        var minutes by remember { mutableStateOf(rt.countdownMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showTargetPicker = false },
            title = { Text(L("Countdown length")) },
            text = {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text(L("Minutes")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    minutes.toIntOrNull()?.takeIf { it > 0 }?.let { viewModel.setCountdownMinutes(it) }
                    showTargetPicker = false
                }) { Text(L("OK")) }
            },
            dismissButton = { TextButton(onClick = { showTargetPicker = false }) { Text(L("Cancel")) } },
        )
    }
}

/** Time's up: the four honest next steps. */
@Composable
private fun ExpiredChoices(
    minutes: Int,
    breakMinutes: Int,
    onExtend: () -> Unit,
    onContinue: () -> Unit,
    onBreak: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(L("Time's up"), style = MaterialTheme.typography.titleMedium, color = TimeThemeTokens.colors.acc)
        Text(
            L("Work stopped when it ran out. What next?"),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Dimens.s4, bottom = Dimens.s12),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
            Button(onClick = onContinue, modifier = Modifier.weight(1f)) { Text(L("Another %1\$s min", minutes)) }
            OutlinedButton(onClick = onExtend, modifier = Modifier.weight(1f)) { Text(L("+5 min")) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s10), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
            OutlinedButton(onClick = onBreak, modifier = Modifier.weight(1f)) { Text(L("%1\$s min break", breakMinutes)) }
            OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) { Text(L("Finish")) }
        }
    }
}
