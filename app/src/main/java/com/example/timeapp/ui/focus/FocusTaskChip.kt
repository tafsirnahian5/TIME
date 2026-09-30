package com.example.timeapp.ui.focus

import com.example.timeapp.data.L

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.data.label
import com.example.timeapp.ui.plan.PlanUiState
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** The task the next run starts on (null: no task). Unchosen, it offers what's already
 * running or paused — the work most likely to be picked up again. */
internal fun FocusUiState.pendingTaskId(plan: PlanUiState): Long? = when (val c = choice) {
    TaskChoice.NoTask -> null
    is TaskChoice.Task -> c.id.takeIf { id -> plan.entries.any { it.id == id } }
    TaskChoice.Unset -> plan.entries.firstOrNull { it.status == EntryStatus.DOING }?.id
        ?: plan.entries.firstOrNull { it.status == EntryStatus.PAUSED }?.id
}

/** The task this Focus run is on, or would start on. */
internal fun FocusUiState.shownTask(plan: PlanUiState): Entry? =
    if (inRun) runtime.entryId?.let { plan.find(it) } else pendingTaskId(plan)?.let { plan.find(it) }

/**
 * Which task Focus is on. During a work phase it names the running task and opens Switch work
 * (with its resume note); before a run it opens a picker — Focus can also run with no task,
 * which still counts as work time, under no category.
 */
@Composable
internal fun FocusTaskChip(state: FocusUiState, plan: PlanUiState, onChoose: (TaskChoice) -> Unit, onSwitchWork: () -> Unit) {
    val colors = TimeThemeTokens.colors
    val task = state.shownTask(plan)
    var picking by remember { mutableStateOf(false) }
    val working = state.inRun && state.runtime.phase == FocusPhase.WORK
    Row(
        modifier = Modifier
            .background(TimeThemeTokens.colors.card, Shapes.pill)
            .clickable { if (working && state.running) onSwitchWork() else picking = true }
            .padding(horizontal = Dimens.s14, vertical = Dimens.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.example.timeapp.ui.components.CategoryMark(task?.category?.color, task?.category?.icon, size = 18.dp)
        Text(
            task?.let { "${it.category?.name ?: L("Task")} · ${it.title}" } ?: L("No task — just focus"),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = Dimens.s8),
        )
        Icon(Icons.Rounded.ExpandMore, contentDescription = if (working && state.running) L("Switch task") else L("Choose a task"), modifier = Modifier.size(17.dp))
    }
    if (picking) {
        TaskPickerDialog(
            plan = plan,
            selectedId = task?.id,
            onPick = { picked ->
                picking = false
                onChoose(picked)
            },
            onDismiss = { picking = false },
        )
    }
}

/** Open tasks, paused work first — what can be picked up. */
@Composable
private fun TaskPickerDialog(plan: PlanUiState, selectedId: Long?, onPick: (TaskChoice) -> Unit, onDismiss: () -> Unit) {
    val order = listOf(EntryStatus.PAUSED, EntryStatus.DOING, EntryStatus.OVERDUE, EntryStatus.PLANNED, EntryStatus.UNSCHEDULED, EntryStatus.BLOCKED)
    val candidates = plan.entries
        .filter { it.kind == EntryKind.TASK && it.status != EntryStatus.DONE }
        .sortedBy { order.indexOf(it.status).let { i -> if (i < 0) order.size else i } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Work on…")) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                item {
                    PickRow(L("No task — just focus"), L("Counts as work time, under no category"), selectedId == null) { onPick(TaskChoice.NoTask) }
                }
                items(candidates, key = { it.id }) { e ->
                    PickRow(e.title, e.status.label(), e.id == selectedId) { onPick(TaskChoice.Task(e.id)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}

@Composable
private fun PickRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) TimeThemeTokens.colors.accC else Color.Transparent, Shapes.md)
            .clickable(onClick = onClick)
            .heightIn(min = Dimens.touchMin)
            .padding(horizontal = Dimens.s12, vertical = Dimens.s10),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shown on a mode's screen while the one timer is running in another mode: one timer at a
 * time, so this points at it rather than starting a second. */
@Composable
internal fun OtherModeCard(mode: FocusMode, onOpen: () -> Unit, running: Boolean = true, onFinish: (() -> Unit)? = null) {
    // Whole sentences per mode and state: this slotted untranslated "stopwatch"/"countdown" into
    // one sentence, and said "is going" for a run that was paused.
    val headline = when (mode) {
        FocusMode.STOPWATCH -> if (running) L("A stopwatch is running") else L("A stopwatch run is paused")
        FocusMode.COUNTDOWN -> if (running) L("A countdown is running") else L("A countdown is paused")
        FocusMode.POMODORO -> if (running) L("A Pomodoro is running") else L("A Pomodoro is paused")
    }
    val open = when (mode) {
        FocusMode.STOPWATCH -> L("Open the stopwatch")
        FocusMode.COUNTDOWN -> L("Open the countdown")
        FocusMode.POMODORO -> L("Open the Pomodoro")
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.accS, Shapes.xl).padding(Dimens.s20),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(headline, style = MaterialTheme.typography.titleMedium)
        Text(
            L("One work timer at a time. Finish that run to start this kind — the time it recorded is kept."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.s6, bottom = Dimens.s14),
        )
        OutlinedButton(onClick = onOpen) { Text(open) }
        if (onFinish != null) {
            androidx.compose.material3.TextButton(onClick = onFinish) { Text(L("Finish it and start here")) }
        }
    }
}
