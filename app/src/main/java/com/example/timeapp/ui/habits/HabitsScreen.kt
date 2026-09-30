package com.example.timeapp.ui.habits

import com.example.timeapp.data.L

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Every habit with its last seven days. No design canvas covers habits (they post-date both
 * the PDF and the real canvas), so like Links this is built from the shared tokens and the
 * app's existing list/card patterns — lower fidelity until a real design exists.
 *
 * The day strip back-fills: yesterday's forgotten check-in is one tap, and a second tap turns
 * it into a rest day, which never breaks a streak.
 */
@Composable
fun HabitsScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: HabitsViewModel,
) {
    val colors = TimeThemeTokens.colors
    val state by viewModel.uiState.collectAsState()
    var showArchived by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TimeTopBar(title = L("Habits"), onBack = onBack) {
                com.example.timeapp.ui.components.TabHelpButton(com.example.timeapp.ui.components.TabHelps.habits)
                if (state.active.size > 1) {
                    TextButton(onClick = { reordering = !reordering }) { Text(if (reordering) L("Done") else L("Reorder")) }
                }
            }
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = colors.acc,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Shapes.lg,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(L("Habit")) },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            if (state.active.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.EventRepeat,
                        illustration = Illustration.HABITS,
                        headline = L("No habits yet"),
                        supporting = L("Pick something small you want to do regularly — every day, on set days, or a few times a week. Check it off here or on Today."),
                        actionLabel = L("New habit"),
                        onAction = onAdd,
                        modifier = Modifier.padding(vertical = Dimens.s24),
                    )
                }
            } else {
                item { HabitsSummary(state) }
                item {
                    Text(
                        L("Tap a day to check it off. Tap again for a rest day — rest days never break a streak."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.s8, bottom = Dimens.s12),
                    )
                }
            }
            items(state.active, key = { it.habit.id }) { row ->
                if (reordering) {
                    // Arrows, not dragging: the accessible way to order (the PDF's rule for the Board too).
                    val index = state.active.indexOf(row)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.cardGap).background(colors.card, Shapes.lg).padding(start = Dimens.s16),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.habit.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        androidx.compose.material3.IconButton(onClick = { viewModel.move(row.habit.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = L("Move %1\$s up", row.habit.name))
                        }
                        androidx.compose.material3.IconButton(onClick = { viewModel.move(row.habit.id, 1) }, enabled = index < state.active.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = L("Move %1\$s down", row.habit.name))
                        }
                    }
                    return@items
                }
                HabitCard(
                    row, state.today, onOpen = { onOpen(row.habit.id) },
                    onCycle = { date, mark ->
                        com.example.timeapp.data.Haptics.perform(com.example.timeapp.data.HapticEvent.TICK)
                        viewModel.cycle(row.habit.id, date, mark)
                    },
                    onSetDay = { date, mark, value, note -> viewModel.setDay(row.habit.id, date, mark, value, note) },
                )
            }
            if (state.archived.isNotEmpty()) {
                item {
                    TextButton(onClick = { showArchived = !showArchived }, modifier = Modifier.padding(top = Dimens.s8)) {
                        Text(if (showArchived) L("Hide archived (%1\$s)", state.archived.size) else L("Archived (%1\$s)", state.archived.size))
                    }
                }
                if (showArchived) {
                    items(state.archived, key = { it.habit.id }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(row.habit.id) }
                                .padding(vertical = Dimens.s14),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(row.habit.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(row.habit.schedule.label(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            // Room under the last card for the FAB.
            item { Box(Modifier.padding(bottom = Dimens.s34 * 2)) }
        }
    }
}

/** The canvas's summary strip: done today, the average recent rate, the best running streak. */
@Composable
private fun HabitsSummary(state: HabitsUiState) {
    val colors = TimeThemeTokens.colors
    val due = state.dueToday
    val done = due.count { it.marks[state.today] == com.example.timeapp.data.HabitMark.DONE }
    val rates = state.active.mapNotNull { it.stats.recentRate }
    val best = state.active.maxOfOrNull { it.stats.currentStreak } ?: 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8).background(colors.accS, Shapes.lg).padding(Dimens.s14),
    ) {
        SummaryCell("$done / ${due.size}", L("done today"), Modifier.weight(1f))
        Box(Modifier.width(Dimens.hairline).height(Dimens.s34).background(colors.ring))
        SummaryCell(if (rates.isEmpty()) "—" else "${(rates.average() * 100).toInt()}%", L("recent rate"), Modifier.weight(1f).padding(start = Dimens.s14))
        Box(Modifier.width(Dimens.hairline).height(Dimens.s34).background(colors.ring))
        SummaryCell(best.toString(), L("best streak now"), Modifier.weight(1f).padding(start = Dimens.s14))
    }
}

@Composable
private fun SummaryCell(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HabitCard(
    row: HabitRow,
    today: java.time.LocalDate,
    onOpen: () -> Unit,
    onCycle: (java.time.LocalDate, com.example.timeapp.data.HabitMark?) -> Unit,
    onSetDay: (java.time.LocalDate, com.example.timeapp.data.HabitMark?, Double?, String?) -> Unit = { _, _, _, _ -> },
) {
    val colors = TimeThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.cardGap)
            .background(colors.card, Shapes.xl)
            .border(Dimens.hairline, colors.line, Shapes.xl)
            .padding(start = Dimens.s16, end = Dimens.s12, top = Dimens.s12, bottom = Dimens.s8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = Dimens.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.example.timeapp.ui.components.CategoryMark(row.habit.category?.color, row.habit.category?.icon, size = Dimens.s20)
            Column(modifier = Modifier.weight(1f).padding(start = Dimens.s10)) {
                Text(row.habit.name, style = MaterialTheme.typography.titleSmall)
                Text(row.habit.schedule.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.habit.schedule.isPeriodTarget) {
                Text(
                    // Whole sentences, so each language can order its own words.
                    if (row.habit.schedule.kind == com.example.timeapp.data.HabitScheduleKind.TIMES_PER_MONTH) {
                        L("%1\$s of %2\$s this month", row.stats.doneThisWeek, row.stats.targetThisWeek)
                    } else {
                        L("%1\$s of %2\$s this week", row.stats.doneThisWeek, row.stats.targetThisWeek)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                StreakChip(row, long = true)
            }
        }
        if (row.habit.schedule.isPeriodTarget) {
            WeeklyTargetBar(row.stats.doneThisWeek, row.stats.targetThisWeek, Modifier.padding(top = Dimens.s8, bottom = Dimens.s4))
        }
        var editingDay by remember { mutableStateOf<java.time.LocalDate?>(null) }
        HabitWeekStrip(row, today, onCycle, Modifier.padding(top = Dimens.s6), onEditDay = { editingDay = it })
        editingDay?.let { d ->
            HabitDayDialog(row, d, onSave = { m, v, n -> onSetDay(d, m, v, n); editingDay = null }, onDismiss = { editingDay = null })
        }
    }
}
