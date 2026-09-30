package com.example.timeapp.ui.focus

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.FocusMode
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.ZoneId

/**
 * F1 — Focus · Stopwatch with laps. This *is* the work timer: Start opens a real work session
 * (on the chosen task, or none), Pause closes it and pauses the task, Switch hands the time to
 * another task at the same instant. It used to be a separate clock nothing recorded.
 */
@Composable
fun FocusStopwatchScreen(
    onOpenCountdown: () -> Unit,
    onOpenPomodoro: () -> Unit,
    onSwitchWork: () -> Unit,
    viewModel: FocusViewModel,
    onOpenTimers: () -> Unit = {},
    planViewModel: PlanViewModel,
    onOpenHistory: () -> Unit = {},
    breakReminderMinutes: Int = 0,
    onSetBreakReminder: (Int) -> Unit = {},
) {
    var choosingBreakReminder by remember { mutableStateOf(false) }
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val plan by planViewModel.uiState.collectAsState()
    val rt = ui.runtime
    val mine = rt.mode == FocusMode.STOPWATCH || !ui.inRun
    var menuExpanded by remember { mutableStateOf(false) }
    val askNotificationPermission = com.example.timeapp.ui.components.rememberNotificationPermissionRequest()

    Scaffold(
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s12), verticalAlignment = Alignment.CenterVertically) {
                com.example.timeapp.ui.components.TabTitle(L("Focus"), com.example.timeapp.ui.components.TabHelps.focus, Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = L("More")) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(L("Session history")) }, onClick = { menuExpanded = false; onOpenHistory() })
                        DropdownMenuItem(
                            text = { Text(L("Break reminder: ") + if (breakReminderMinutes > 0) L("%1\$s min", breakReminderMinutes) else "off") },
                            onClick = { menuExpanded = false; choosingBreakReminder = true },
                        )
                        DropdownMenuItem(text = { Text(L("Reset laps")) }, onClick = { menuExpanded = false; viewModel.clearLaps() })
                        if (ui.inRun && rt.entryId != null) {
                            DropdownMenuItem(text = { Text(L("Finish and mark task done")) }, onClick = { menuExpanded = false; viewModel.finish(completeTask = true) })
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            FocusModeToggle(selectedIndex = 0, onOpenStopwatch = {}, onOpenCountdown = onOpenCountdown, onOpenPomodoro = onOpenPomodoro, onOpenTimers = onOpenTimers)
            val review by viewModel.needsReview.collectAsState()
            ReviewBanner(review.size, onOpenHistory)
            if (!mine) {
                OtherModeCard(rt.mode, onOpen = if (rt.mode == FocusMode.COUNTDOWN) onOpenCountdown else onOpenPomodoro, running = rt.running, onFinish = { viewModel.finish() })
                return@Column
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.accS, Shapes.xl).padding(Dimens.s20),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FocusTaskChip(ui, plan, onChoose = viewModel::choose, onSwitchWork = onSwitchWork)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Dimens.s16)) {
                    Text(formatElapsed(ui.elapsedMs), style = MaterialTheme.typography.displaySmall)
                }
                Text(
                    rt.stretchStartedAt?.let {
                        L("Recording since %1\$s", it.atZone(ZoneId.systemDefault()).toLocalTime().format(displayPattern("HH:mm")))
                    } ?: if (ui.inRun) L("Paused — tap Resume to carry on") else L("Tap Start to record your time"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimens.s10),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s20), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                    Button(
                        onClick = {
                            when {
                                ui.running -> viewModel.pause()
                                ui.inRun -> viewModel.resume()
                                else -> {
                                    askNotificationPermission()
                                    viewModel.start(ui.pendingTaskId(plan), FocusMode.STOPWATCH)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(if (ui.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(if (ui.running) L("Pause") else if (ui.inRun) L("Resume") else L("Start"), modifier = Modifier.padding(start = Dimens.s8))
                    }
                    Button(
                        onClick = viewModel::lap,
                        enabled = ui.running,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.accC, contentColor = colors.accInk),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(L("Lap"), modifier = Modifier.padding(start = Dimens.s8))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s10), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                    OutlinedButton(onClick = onSwitchWork, enabled = ui.running, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(L("Switch task"), modifier = Modifier.padding(start = Dimens.s8))
                    }
                    // Finishing the session isn't finishing the task: it's paused, ready to pick
                    // up. "Finish and mark task done" is in the menu.
                    OutlinedButton(onClick = { viewModel.finish() }, enabled = ui.inRun, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(L("Finish"), modifier = Modifier.padding(start = Dimens.s8))
                    }
                }
            }
            // A "Laps · split · total" heading over nothing read like a list that failed to load.
            if (rt.laps.isEmpty()) {
                Text(
                    L("Tap Lap while it runs to mark a split — each one shows here."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s10),
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s10)) {
                    Text(L("Laps"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(L("split · total"), style = MaterialTheme.typography.bodySmall)
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val laps = rt.laps
                items(laps.indices.reversed().toList()) { i ->
                    val split = laps[i] - (laps.getOrNull(i - 1) ?: 0L)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s10), verticalAlignment = Alignment.CenterVertically) {
                        Text(com.example.timeapp.data.Lang.digits("%02d".format(i + 1)), style = MaterialTheme.typography.bodyMedium, color = colors.acc, modifier = Modifier.padding(end = Dimens.s10))
                        Text(L("Lap %1\$s", (i + 1)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${formatElapsed(split)} · ${formatElapsed(laps[i])}")
                    }
                }
            }
            // (The permanent "only one work session…" box now lives in Focus's ? sheet.)
        }
    }
    if (choosingBreakReminder) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { choosingBreakReminder = false },
            title = { Text(L("Break reminder")) },
            text = {
                Column {
                    Text(
                        L("A nudge after this much unbroken work. A pause, switch or break starts the count again."),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = Dimens.s8),
                    )
                    listOf(0, 25, 50, 90).forEach { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSetBreakReminder(m); choosingBreakReminder = false }.padding(vertical = Dimens.s4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = m == breakReminderMinutes, onClick = { onSetBreakReminder(m); choosingBreakReminder = false })
                            Text(if (m == 0) L("Off") else L("After %1\$s minutes", m))
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { choosingBreakReminder = false }) { Text(L("Close")) } },
        )
    }
}

@Composable
internal fun FocusModeToggle(
    selectedIndex: Int,
    onOpenStopwatch: () -> Unit,
    onOpenCountdown: () -> Unit,
    onOpenPomodoro: () -> Unit,
    onOpenTimers: () -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.s14)
            .background(colors.accS2, Shapes.md)
            .padding(Dimens.s4),
    ) {
        listOf(L("Stopwatch"), L("Countdown"), L("Pomodoro"), L("Timers")).forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) colors.accC else Color.Transparent, Shapes.sm)
                    .semantics { this.selected = selected }.clickable {
                        when (i) {
                            0 -> onOpenStopwatch()
                            1 -> onOpenCountdown()
                            2 -> onOpenPomodoro()
                            3 -> onOpenTimers()
                            else -> {}
                        }
                    }
                    .padding(vertical = Dimens.s10),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
