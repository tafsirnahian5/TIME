package com.example.timeapp.ui.habits

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.components.dashedCircle
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.formatAmount
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.timeapp.data.HabitScheduleKind
import com.example.timeapp.data.streakLabel
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.LocalDate

private val dayNameFormat get() = displayPattern("EEE d MMM")

/**
 * One day of a habit, as the refined canvas draws it: filled with a check when done (or its
 * number, with [numberWhenDone], in the month grid), a dashed ring on a rest day, a soft ring
 * for a missed due day, an accent ring for today, a muted number when it wasn't due. The
 * drawn circle is 34dp inside the full 48dp target (rule 6). Future days never respond.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HabitDayMark(
    date: LocalDate,
    mark: HabitMark?,
    due: Boolean,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    numberWhenDone: Boolean = false,
    /** A measurable habit's recorded amount, shown in the circle. */
    value: String? = null,
    hasNote: Boolean = false,
    /** Long-press: the day editor (amount, rest day, note). */
    onLongClick: (() -> Unit)? = null,
) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    val future = date.isAfter(today)
    val isToday = date == today
    val state = when (mark) {
        HabitMark.DONE -> "done"
        HabitMark.SKIPPED -> L("rest day")
        HabitMark.LOGGED -> L("logged %1\$s, below target", value.orEmpty())
        null -> if (due && !future) L("not done") else L("not due")
    }
    Column(
        modifier = modifier
            .heightIn(min = Dimens.touchMin)
            .combinedClickable(enabled = !future, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = "${date.format(dayNameFormat)}, $state" }
            .padding(vertical = Dimens.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday) androidx.compose.ui.text.font.FontWeight.Bold else null,
                color = if (isToday) colors.acc else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimens.s6),
            )
        }
        val circle = Modifier.size(34.dp)
        val number = date.dayOfMonth.toString()
        when {
            mark == HabitMark.DONE -> Box(circle.background(colors.acc, CircleShape), contentAlignment = Alignment.Center) {
                if (value != null) Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                else if (numberWhenDone) Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
            // Recorded but short of the target: kept, visibly, without counting.
            mark == HabitMark.LOGGED -> Box(circle.background(colors.accC, CircleShape).border(Dimens.s2, colors.ring2, CircleShape), contentAlignment = Alignment.Center) {
                Text(value ?: number, style = MaterialTheme.typography.labelSmall, color = colors.accInk, maxLines = 1)
            }
            mark == HabitMark.SKIPPED -> Box(circle.dashedCircle(status.controlOutline), contentAlignment = Alignment.Center) {
                Text(if (numberWhenDone) number else "–", style = MaterialTheme.typography.labelMedium, color = status.neutralInk)
            }
            isToday && due -> Box(circle.border(Dimens.s2, colors.acc, CircleShape), contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.labelMedium, color = colors.acc)
            }
            due && !future -> Box(circle.border(Dimens.s2, colors.ring2, CircleShape), contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Box(circle, contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.labelMedium, color = status.dim)
            }
        }
        // A day with a note carries a small dot under it.
        Box(Modifier.padding(top = Dimens.s2).size(Dimens.s4).background(if (hasNote) colors.acc else androidx.compose.ui.graphics.Color.Transparent, CircleShape))
    }
}

/** The last seven days ending today, oldest first — the habit list's back-fill strip. */
@Composable
fun HabitWeekStrip(row: HabitRow, today: LocalDate, onCycle: (LocalDate, HabitMark?) -> Unit, modifier: Modifier = Modifier, onEditDay: (LocalDate) -> Unit = {}) {
    Row(modifier = modifier.fillMaxWidth()) {
        (6L downTo 0L).map { today.minusDays(it) }.forEach { date ->
            val mark = row.marks[date]
            val check = row.checks[date]
            HabitDayMark(
                date = date,
                mark = mark,
                due = row.habit.schedule.isDueOn(date) && !date.isBefore(row.habit.startedOn),
                today = today,
                label = date.dayOfWeek.name.take(1),
                // A measurable habit's day takes an amount, so a tap opens the editor.
                onClick = { if (row.habit.target != null) onEditDay(date) else onCycle(date, mark) },
                modifier = Modifier.weight(1f),
                value = check?.value?.let(::formatAmount),
                hasNote = check?.note != null,
                onLongClick = { onEditDay(date) },
            )
        }
    }
}

/** The canvas's streak chip: a flame and the count on the amber container. Hidden at 0. */
@Composable
fun StreakChip(row: HabitRow, modifier: Modifier = Modifier, long: Boolean = false) {
    val stats = row.stats
    if (stats.currentStreak <= 0) return
    val status = TimeThemeTokens.status
    Row(
        modifier = modifier.background(status.warn, Shapes.pill).padding(horizontal = Dimens.s10, vertical = Dimens.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = status.warnInk, modifier = Modifier.size(14.dp))
        Text(
            if (long) stats.streakLabel() else stats.currentStreak.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = status.warnInk,
            modifier = Modifier.padding(start = Dimens.s4),
        )
    }
}

/** A weekly target as segments, one per check-in needed this week. */
@Composable
fun WeeklyTargetBar(done: Int, target: Int, modifier: Modifier = Modifier) {
    if (target <= 0) return
    val colors = TimeThemeTokens.colors
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.s6)) {
        repeat(target) { i ->
            Box(Modifier.weight(1f).height(Dimens.s8).background(if (i < done) colors.acc else colors.ring, Shapes.pill))
        }
    }
}

/** "12 days" with a flame, or "2 of 3 this week" for a weekly target that is under way. */
@Composable
fun HabitStreakText(row: HabitRow, modifier: Modifier = Modifier) {
    val stats = row.stats
    val text = when {
        row.habit.schedule.kind == HabitScheduleKind.TIMES_PER_WEEK && stats.targetThisWeek > 0 ->
            L("%1\$s of %2\$s this week", stats.doneThisWeek, stats.targetThisWeek)
        row.habit.schedule.kind == HabitScheduleKind.TIMES_PER_MONTH && stats.targetThisWeek > 0 ->
            L("%1\$s of %2\$s this month", stats.doneThisWeek, stats.targetThisWeek)
        row.habit.target != null && stats.currentStreak == 0 -> row.habit.target.label()
        stats.currentStreak > 0 -> L("%1\$s streak", stats.streakLabel())
        else -> row.habit.schedule.label()
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/**
 * One day of a habit, in full: the amount for a measurable habit (the mark follows its
 * target), done for a plain one, a rest day, and a note. Clearing the day removes its note too.
 */
@Composable
fun HabitDayDialog(row: HabitRow, date: LocalDate, onSave: (HabitMark?, Double?, String?) -> Unit, onDismiss: () -> Unit) {
    val check = row.checks[date]
    val target = row.habit.target
    var amount by remember { mutableStateOf(check?.value?.let(::formatAmount) ?: "") }
    var note by remember { mutableStateOf(check?.note.orEmpty()) }
    val parsed = amount.replace(',', '.').toDoubleOrNull()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.habit.name + " · " + date.format(displayPattern("EEE d MMM"))) },
        text = {
            Column {
                if (target != null) {
                    androidx.compose.material3.OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) },
                        label = { Text(L("How much") + if (target.unit.isBlank()) "" else " (${target.unit})") },
                        supportingText = { Text(L("Target: %1\$s", target.label())) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                androidx.compose.material3.OutlinedTextField(
                    value = note, onValueChange = { note = it.take(200) }, label = { Text(L("Note")) },
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
                )
                Row(modifier = Modifier.padding(top = Dimens.s8)) {
                    androidx.compose.material3.TextButton(onClick = { onSave(HabitMark.SKIPPED, null, note) }) { Text(L("Rest day")) }
                    androidx.compose.material3.TextButton(onClick = { onSave(null, null, null) }) { Text(L("Clear day")) }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    when {
                        target != null && parsed != null -> onSave(null, parsed, note)
                        target == null -> onSave(HabitMark.DONE, null, note)
                        // A measurable day with only a note keeps whatever mark it had.
                        else -> onSave(check?.mark, check?.value, note)
                    }
                },
            ) { Text(if (target == null) L("Done") else L("Save")) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}
