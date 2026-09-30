package com.example.timeapp.ui.plan

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.Entry
import com.example.timeapp.ui.components.DatePickerPrompt
import com.example.timeapp.ui.components.TimePickerPrompt
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One row of the card: [blockId] null is the entry's own first block. */
private data class BlockRow(val blockId: Long?, val start: Instant, val end: Instant, val fixed: Boolean)

/**
 * Task Detail's *Time reserved* card (Phase 14–15 canvas): every block reserved for the task,
 * past ones struck through as used, with *+ Add block*, *Split* and *Remove*. Nothing is placed
 * silently — an overlap is named before adding (rule 4), and a split's second half goes where
 * the user confirms.
 */
@Composable
internal fun TaskBlocksCard(
    entry: Entry,
    remainingMs: Long?,
    viewModel: PlanViewModel,
    workStart: LocalTime,
    workEnd: LocalTime,
) {
    val colors = TimeThemeTokens.colors
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val rows = buildList {
        if (entry.startAt != null && entry.endAt != null) add(BlockRow(null, entry.startAt, entry.endAt, entry.isFixed))
        entry.extraBlocks.forEach { add(BlockRow(it.id, it.startAt, it.endAt, it.isFixed)) }
    }.sortedBy { it.start }
    val reservedFutureMs = rows.filter { it.start.isAfter(now) }.sumOf { Duration.between(it.start, it.end).toMillis() }
    var adding by remember { mutableStateOf(false) }
    var splitting by remember { mutableStateOf<BlockRow?>(null) }
    val dayFmt = displayPattern("EEE d MMM")
    val timeFmt = displayPattern("HH:mm")

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(colors.card, Shapes.lg).border(1.dp, colors.line, Shapes.lg).padding(Dimens.s16),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Dimens.s8)) {
            Text(L("Booked time"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { adding = true }) { Text(L("+ Book time")) }
        }
        if (rows.isEmpty()) {
            Text(L("No time booked yet."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEach { row ->
            val used = !row.end.isAfter(now)
            var menu by remember(row) { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(Dimens.s4).height(Dimens.s34).background(if (used) TimeThemeTokens.status.dim else colors.acc, Shapes.xs))
                Column(modifier = Modifier.weight(1f).padding(start = Dimens.s12)) {
                    val s = row.start.atZone(zone)
                    Text(
                        "${s.format(dayFmt)} · ${s.format(timeFmt)} – ${row.end.atZone(zone).format(timeFmt)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (used) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (used) TextDecoration.LineThrough else null,
                    )
                    Text(
                        listOf(if (used) L("Used") else if (row.fixed) L("Fixed") else L("Flexible"), com.example.timeapp.data.durationText(Duration.between(row.start, row.end).toMinutes())).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!used) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = L("More for this booked time"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (Duration.between(row.start, row.end).toMinutes() >= 10) {
                                DropdownMenuItem(text = { Text(L("Split")) }, onClick = { menu = false; splitting = row })
                            }
                            DropdownMenuItem(text = { Text(L("Remove")) }, onClick = { menu = false; viewModel.removeBlock(entry.id, row.blockId) })
                        }
                    }
                }
            }
        }
        // Over-allocation stated, never fixed for the user (the PDF's Reschedule step 2).
        if (remainingMs != null && reservedFutureMs > remainingMs && reservedFutureMs > 0) {
            Text(
                L("%1\$s booked for %2\$s of work left. Shorten one, or keep the extra as buffer.", com.example.timeapp.ui.stats.formatHm(reservedFutureMs), com.example.timeapp.ui.stats.formatHm(remainingMs)),
                style = MaterialTheme.typography.bodySmall,
                color = TimeThemeTokens.status.warnInk,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8).background(TimeThemeTokens.status.warn, Shapes.md).padding(Dimens.s10),
            )
        }
    }

    if (adding) {
        AddBlockDialog(
            entry = entry,
            defaultMinutes = ((remainingMs?.minus(reservedFutureMs))?.takeIf { it > 0 }?.div(60_000)?.toInt() ?: 45).coerceIn(5, 8 * 60),
            viewModel = viewModel,
            onDone = { adding = false },
        )
    }
    splitting?.let { row ->
        SplitBlockDialog(entry, row, viewModel, workStart, workEnd, onDone = { splitting = null })
    }
}

@Composable
private fun AddBlockDialog(entry: Entry, defaultMinutes: Int, viewModel: PlanViewModel, onDone: () -> Unit) {
    val zone = ZoneId.systemDefault()
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)) }
    var minutes by remember { mutableStateOf(defaultMinutes.toString()) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val mins = minutes.toIntOrNull()?.takeIf { it in 1..(24 * 60) }
    val start = date.atTime(time).atZone(zone).toInstant()
    val end = mins?.let { start.plusSeconds(it * 60L) }
    val overlap = end?.let { viewModel.overlapFor(start, it) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (entry.startAt == null) L("Book time") else L("Book more time")) },
        text = {
            Column {
                Text(
                    date.format(displayPattern("EEE d MMM")),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { pickingDate = true }.padding(vertical = Dimens.s12),
                )
                Text(
                    L("Starts %1\$s", time.format(displayPattern("HH:mm"))),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { pickingTime = true }.padding(vertical = Dimens.s12),
                )
                OutlinedTextField(
                    value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text(L("Minutes")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = mins == null,
                )
                overlap?.let {
                    Text(
                        L("Overlaps “%1\$s”. Adding keeps both — nothing is moved.", it.title),
                        style = MaterialTheme.typography.bodySmall,
                        color = TimeThemeTokens.status.warnInk,
                        modifier = Modifier.padding(top = Dimens.s8),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = end != null, onClick = {
                if (end != null) viewModel.addBlock(entry.id, start, end)
                onDone()
            }) { Text(if (overlap != null) L("Add anyway") else L("Add")) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(L("Cancel")) } },
    )
    if (pickingDate) DatePickerPrompt(date) { picked -> picked?.let { date = it }; pickingDate = false }
    if (pickingTime) TimePickerPrompt(time) { picked -> picked?.let { time = it }; pickingTime = false }
}

@Composable
private fun SplitBlockDialog(entry: Entry, row: BlockRow, viewModel: PlanViewModel, workStart: LocalTime, workEnd: LocalTime, onDone: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val totalMin = Duration.between(row.start, row.end).toMinutes().toInt()
    var first by remember { mutableStateOf((totalMin / 2).toString()) }
    var slot by remember { mutableStateOf<Pair<Instant, Instant>?>(null) }
    var searched by remember { mutableStateOf(false) }
    val firstMin = first.toIntOrNull()?.takeIf { it in 1 until totalMin }
    val fmt = displayPattern("EEE d MMM · HH:mm")
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(L("Split this booked time")) },
        text = {
            Column {
                Text(
                    L("%1\$s is split in two. Nothing moves until you choose where the second part goes.", row.start.atZone(zone).format(fmt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it.filter(Char::isDigit).take(4); slot = null; searched = false },
                    label = { Text(L("First part, minutes")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = firstMin == null,
                    supportingText = { Text(firstMin?.let { L("Second part: %1\$s min", (totalMin - it)) } ?: L("Between 1 and %1\$s", (totalMin - 1))) },
                    modifier = Modifier.padding(top = Dimens.s8),
                )
                slot?.let { (s, _) ->
                    Text(L("Second part: %1\$s", s.atZone(zone).format(fmt)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Dimens.s8))
                }
                if (searched && slot == null) {
                    Text(L("No free slot in the next two weeks' work hours."), style = MaterialTheme.typography.bodySmall, color = TimeThemeTokens.status.warnInk)
                }
            }
        },
        confirmButton = {
            val chosen = slot
            if (chosen == null) {
                TextButton(enabled = firstMin != null, onClick = {
                    val secondMs = (totalMin - (firstMin ?: return@TextButton)) * 60_000L
                    val firstEnd = row.start.plusSeconds(firstMin * 60L)
                    slot = SchedulingMath.nextFreeSlot(
                        viewModel.uiState.value.entries, -1L,
                        from = firstEnd.atZone(zone).toLocalDate(), durationMs = secondMs,
                        notBefore = maxOf(firstEnd, Instant.now()), days = 14, workStart = workStart, workEnd = workEnd,
                    )
                    searched = true
                }) { Text(L("Find a slot")) }
            } else {
                TextButton(onClick = {
                    viewModel.splitBlock(entry.id, row.blockId, (firstMin ?: return@TextButton) * 60_000L, chosen.first)
                    onDone()
                }) { Text(L("Split")) }
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(L("Cancel")) } },
    )
}
