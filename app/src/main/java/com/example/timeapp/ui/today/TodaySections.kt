package com.example.timeapp.ui.today

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.Commitment
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.MAX_COMMITMENTS
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.AnimatedCheckCircle
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.ui.stats.formatHm
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today's commitments: up to three tasks chosen for the day (the PDF's "choose up to three
 * commitments"). This heading used to sit over every unscheduled task. Ticking one completes
 * it; taking one off keeps it on the record with the time and an optional reason.
 */
@Composable
internal fun CommitmentsCard(
    commitments: List<Commitment>,
    entries: List<Entry>,
    archived: List<Entry>,
    viewModel: PlanViewModel,
    onOpenTask: (Long) -> Unit,
    /** Open tasks worth committing to (overdue, due today, urgent…) — one tap each when none chosen. */
    suggestions: List<Entry> = emptyList(),
) {
    val colors = TimeThemeTokens.colors
    var picking by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Entry?>(null) }
    val byId = (entries + archived).associateBy { it.id }
    val active = commitments.filter { it.active }
    val hm = displayPattern("HH:mm")
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.card, Shapes.lg).padding(Dimens.s16),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L("Today's 3"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (active.isNotEmpty()) Text(L("%1\$s of %2\$s done", (active.count { byId[it.entryId]?.status == EntryStatus.DONE }), active.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (commitments.isEmpty()) {
            Text(
                L("Pick up to %1\$s things that would make today count.", MAX_COMMITMENTS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s6),
            )
            if (suggestions.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.padding(top = Dimens.s10),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.s8),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.s8),
                ) {
                    suggestions.forEach { e ->
                        androidx.compose.material3.AssistChip(
                            onClick = { viewModel.commitToday(e.id) },
                            label = { Text(e.title, maxLines = 1) },
                            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(0.dp)) },
                            modifier = Modifier.heightIn(min = Dimens.touchMin),
                        )
                    }
                }
            }
        }
        commitments.forEach { c ->
            val e = byId[c.entryId] ?: return@forEach
            val done = e.status == EntryStatus.DONE
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin), verticalAlignment = Alignment.CenterVertically) {
                if (c.active) {
                    AnimatedCheckCircle(
                        checked = done,
                        onClick = { viewModel.toggleDone(e) },
                        contentDescription = if (done) L("Mark %1\$s not done", e.title) else L("Mark %1\$s done", e.title),
                        size = 24.dp,
                    )
                }
                Column(modifier = Modifier.weight(1f).clickable { onOpenTask(e.id) }.padding(start = Dimens.s6, top = Dimens.s6, bottom = Dimens.s6)) {
                    Text(
                        e.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (!c.active || done) TextDecoration.LineThrough else null,
                        color = if (c.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!c.active) {
                        Text(
                            L("Taken off at %1\$s", c.removedAt!!.atZone(ZoneId.systemDefault()).format(hm)) + (c.removeNote?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (c.active && !done) {
                    IconButton(onClick = { removing = e }) { Icon(Icons.Rounded.Close, contentDescription = L("Take %1\$s off today", e.title)) }
                }
            }
        }
        if (active.size < MAX_COMMITMENTS) {
            TextButton(onClick = { picking = true }) { Text(if (active.isEmpty()) L("Choose from all tasks") else L("Add one (%1\$s left)", (MAX_COMMITMENTS - active.size))) }
        }
    }
    if (picking) {
        val chosen = active.map { it.entryId }.toSet()
        val candidates = entries.filter { it.kind == EntryKind.TASK && it.status != EntryStatus.DONE && it.id !in chosen }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(L("Commit to…")) },
            text = {
                if (candidates.isEmpty()) Text(L("No open tasks to choose. Add one first."))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(candidates, key = { it.id }) { e ->
                        Text(
                            e.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.commitToday(e.id); picking = false }
                                .heightIn(min = Dimens.touchMin).padding(vertical = Dimens.s12),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(L("Cancel")) } },
        )
    }
    removing?.let { e ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(L("Take “%1\$s” off today?", e.title)) },
            text = {
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(L("Why (optional)")) }, singleLine = true)
            },
            confirmButton = { TextButton(onClick = { viewModel.uncommitToday(e.id, note); removing = null }) { Text(L("Take it off")) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(L("Keep it")) } },
        )
    }
}

/** Every paused task (the PDF: "paused tasks appear in Today and Focus with last worked time,
 * accumulated duration and resume note"), not only the one shown as current. */
@Composable
internal fun PausedWorkCard(paused: List<Entry>, viewModel: PlanViewModel, onOpenTask: (Long) -> Unit, onResume: (Long) -> Unit) {
    if (paused.isEmpty()) return
    Text(L("Paused work"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Dimens.s20, bottom = Dimens.s6))
    paused.forEach { e ->
        val sessions by remember(e.id) { viewModel.sessions(e.id) }.collectAsState(initial = emptyList())
        val tracked = sessions.sumOf { it.durationMs ?: 0L }
        val last = sessions.firstOrNull()?.endedAt
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { onOpenTask(e.id) }.padding(vertical = Dimens.s6), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        L("%1\$s so far", formatHm(tracked)),
                        last?.let { L("last worked %1\$s", it.atZone(ZoneId.systemDefault()).format(displayPattern("EEE HH:mm"))) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                e.resumeNote?.let { Text(L("Resume: %1\$s", it), style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            }
            TextButton(onClick = { onResume(e.id) }) { Text(L("Resume")) }
        }
    }
}

/** Planned money due in the next seven days (the overdue ones first), so a bill never
 * arrives as a surprise. */
@Composable
internal fun MoneyDueSoonCard(plans: List<MoneyRecord>, today: LocalDate, outstandingThisMonth: Long, onOpenMoney: () -> Unit) {
    // Overdue ones are in "From before today" above; listing them here too read as two bills.
    val soon = plans.filter { it.isOpen && it.dueOn != null && !it.dueOn.isBefore(today) && !it.dueOn.isAfter(today.plusDays(7)) && it.remainingMinor > 0 }
        .sortedBy { it.dueOn }
    if (soon.isEmpty() && outstandingThisMonth <= 0) return
    val fmt = displayPattern("EEE d MMM")
    Column(
        modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg)
            .clickable(onClick = onOpenMoney).padding(Dimens.s16),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Dimens.s6)) {
            Text(L("Money this week"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(L("Money"), style = MaterialTheme.typography.labelLarge, color = TimeThemeTokens.colors.acc)
        }
        if (soon.isEmpty()) Text(L("Nothing due in the next 7 days."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        soon.take(4).forEach { p ->
            val overdue = p.isOverdue(today)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s4)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (overdue) L("was due %1\$s", p.dueOn!!.format(fmt)) else L("due %1\$s", p.dueOn!!.format(fmt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) TimeThemeTokens.status.dangerInk else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(p.remainingMinor.toTaka(), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (soon.size > 4) Text(L("and %1\$s more", (soon.size - 4)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (outstandingThisMonth > 0) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(vertical = Dimens.s8).height(Dimens.hairline).background(TimeThemeTokens.colors.line))
            Text(L("%1\$s still owed this month", outstandingThisMonth.toTaka()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
