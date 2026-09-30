package com.example.timeapp.ui.habits

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import com.example.timeapp.ui.components.dashedCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import com.example.timeapp.data.HabitScheduleKind
import com.example.timeapp.data.StreakUnit
import com.example.timeapp.data.streakLabel
import com.example.timeapp.ui.components.ConfirmDeleteDialog
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.YearMonth
import kotlin.math.roundToInt

private val monthFormat get() = displayPattern("MMMM yyyy")

/**
 * One habit's history: current and best streak, the recent completion rate, and a month grid
 * that can be browsed and back-filled with the same tap cycle as the list's day strip.
 */
@Composable
fun HabitDetailScreen(
    habitId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: HabitsViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    val row = (state.active + state.archived).firstOrNull { it.habit.id == habitId }
    var month by remember { mutableStateOf(YearMonth.from(state.today)) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var editingDay by remember { mutableStateOf<java.time.LocalDate?>(null) }

    Scaffold(
        topBar = {
            TimeTopBar(
                title = row?.habit?.name ?: L("Habit"),
                onBack = onBack,
                actions = {
                    if (row != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = L("More")) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text(L("Edit")) }, onClick = { menuOpen = false; onEdit(habitId) })
                                DropdownMenuItem(
                                    text = { Text(if (row.habit.archived) L("Restore") else L("Archive")) },
                                    onClick = { menuOpen = false; viewModel.setArchived(row.habit, !row.habit.archived) },
                                )
                                DropdownMenuItem(text = { Text(L("Delete")) }, onClick = { menuOpen = false; confirmingDelete = true })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        // Deleted from here (or not loaded yet): nothing to show, and the back stack pops.
        if (row == null) return@Scaffold
        val stats = row.stats
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.s16),
        ) {
            Text(
                row.habit.schedule.label() + (row.habit.target?.let { " · " + it.label() } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s4, bottom = Dimens.s16),
            )
            if (row.habit.archived) {
                Text(
                    L("Archived — kept with its history, off Today and without reminders. Restore it from the menu."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.s16)
                        .background(TimeThemeTokens.status.neutral, Shapes.md)
                        .padding(Dimens.s12),
                    color = TimeThemeTokens.status.neutralInk,
                )
            }
            // The canvas's hero: the recent rate as a ring, the running streak beside it.
            val colors = TimeThemeTokens.colors
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.accC2, Shapes.xl).padding(Dimens.s20),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.example.timeapp.ui.components.ProgressRing(
                    fraction = (stats.recentRate ?: 0.0).toFloat(),
                    color = colors.acc, track = colors.accDim, size = 96.dp, stroke = 8.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stats.recentRate?.let { "${(it * 100).roundToInt()}%" } ?: "—", style = MaterialTheme.typography.headlineSmall, color = colors.accInk)
                        Text(if (stats.unit == StreakUnit.WEEKS) L("8 weeks") else L("30 days"), style = MaterialTheme.typography.labelSmall, color = colors.accInk)
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = Dimens.s20)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (stats.currentStreak > 0) {
                            Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = colors.acc, modifier = Modifier.size(22.dp))
                        }
                        Text(stats.streakLabel(), style = MaterialTheme.typography.headlineSmall, color = colors.accInk)
                    }
                    Text(L("current streak"), style = MaterialTheme.typography.bodySmall, color = colors.accInk)
                    Text(
                        L("Best: %1\$s", stats.streakLabel(stats.bestStreak)) + (row.habit.remindAt?.let { L(" · reminds at %1\$s", it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accInk,
                        modifier = Modifier.padding(top = Dimens.s8),
                    )
                    if (stats.recentRate == null) {
                        Text(L("Not enough data for a rate yet"), style = MaterialTheme.typography.bodySmall, color = colors.accInk)
                    }
                }
            }
            if (row.habit.schedule.isPeriodTarget) {
                Text(
                    (if (row.habit.schedule.kind == HabitScheduleKind.TIMES_PER_MONTH) L("This month: ") else L("This week: ")) +
                        L("%1\$s of %2\$s", stats.doneThisWeek, stats.targetThisWeek),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Dimens.s12),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s20, bottom = Dimens.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = L("Previous month"))
                }
                Text(month.format(monthFormat), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.from(state.today)) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = L("Next month"))
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.xl)
                    .border(Dimens.hairline, TimeThemeTokens.colors.line, Shapes.xl).padding(Dimens.s10),
            ) {
                MonthGrid(month, row, state.today, onEditDay = { editingDay = it }) { date, mark -> viewModel.cycle(habitId, date, mark) }
            }
            Row(modifier = Modifier.padding(top = Dimens.s14), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.s16)) {
                LegendDot(L("Done"), Modifier.background(TimeThemeTokens.colors.acc, androidx.compose.foundation.shape.CircleShape))
                LegendDot(L("Rest day"), Modifier.dashedCircle(TimeThemeTokens.status.controlOutline))
                LegendDot(L("Missed"), Modifier.border(Dimens.s2, TimeThemeTokens.colors.ring2, androidx.compose.foundation.shape.CircleShape))
            }
            Text(
                (if (row.habit.target != null) L("Tap a day to record how much.") else L("Tap a day: done, then rest day, then clear.")) +
                    L(" Hold a day to add a note. Rest days are left out of streaks and the rate."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s16),
            )
            HabitHistoryCharts(row, state.today)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Dimens.s24))
        }
    }

    if (row != null) {
        editingDay?.let { d ->
            HabitDayDialog(row, d, onSave = { m, v, n -> viewModel.setDay(habitId, d, m, v, n); editingDay = null }, onDismiss = { editingDay = null })
        }
    }
    if (confirmingDelete && row != null) {
        ConfirmDeleteDialog(
            itemLabel = row.habit.name,
            onConfirm = { confirmingDelete = false; viewModel.delete(habitId); onBack() },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(TimeThemeTokens.colors.card, Shapes.lg)
            .padding(Dimens.s12),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Monday-first weeks, blank cells before the 1st — the same week start the stats use. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    row: HabitRow,
    today: java.time.LocalDate,
    onEditDay: (java.time.LocalDate) -> Unit,
    onCycle: (java.time.LocalDate, com.example.timeapp.data.HabitMark?) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        com.example.timeapp.data.weekdayInitials().forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    val first = month.atDay(1)
    val cells: List<java.time.LocalDate?> =
        List(com.example.timeapp.data.leadingBlanks(first)) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    cells.chunked(7).forEach { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { date ->
                if (date == null) {
                    Box(Modifier.weight(1f))
                } else {
                    val mark = row.marks[date]
                    HabitDayMark(
                        date = date,
                        mark = mark,
                        due = row.habit.schedule.isDueOn(date) && !date.isBefore(row.habit.startedOn),
                        today = today,
                        onClick = { if (row.habit.target != null) onEditDay(date) else onCycle(date, mark) },
                        modifier = Modifier.weight(1f),
                        numberWhenDone = true,
                        // Month cells keep the date; the amount shows in the week strip and the day editor.
                        hasNote = row.checks[date]?.note != null,
                        onLongClick = { onEditDay(date) },
                    )
                }
            }
            repeat(7 - week.size) { Box(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun LegendDot(label: String, shape: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(Dimens.s12).then(shape))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Dimens.s6))
    }
}
