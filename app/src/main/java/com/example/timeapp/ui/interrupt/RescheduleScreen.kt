package com.example.timeapp.ui.interrupt

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import com.example.timeapp.ui.plan.unfinishedPrerequisites
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import java.time.LocalTime
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.ui.plan.SchedulingMath
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * D2 — Schedule remaining, preview. Nothing moves until the user applies. Remaining effort,
 * both candidate slots and the RESPECTED constraints are computed for real from `Entry`'s
 * estimate/tracked-time and the live entry list — see `SchedulingMath.findFreeSlot`.
 */
@Composable
fun RescheduleScreen(
    entryId: Long,
    onBack: () -> Unit,
    onApply: (start: Instant, end: Instant) -> Unit,
    onCancel: () -> Unit,
    viewModel: PlanViewModel,
    workStart: LocalTime = LocalTime.of(9, 0),
    workEnd: LocalTime = LocalTime.of(18, 0),
) {
    val colors = TimeThemeTokens.colors
    val zone = ZoneId.systemDefault()
    val plan by viewModel.uiState.collectAsState()
    val entry = plan.entries.firstOrNull { it.id == entryId } ?: return
    val trackedMs by viewModel.trackedMs(entryId).collectAsState(initial = 0L)
    var showHelp by remember { mutableStateOf(false) }
    var showEstimateEdit by remember { mutableStateOf(false) }
    var estimateOverrideMs by remember(entryId) { mutableStateOf(entry.estimateMs) }

    val estimateMs = estimateOverrideMs
    val now = Instant.now()
    val r = estimateMs?.let { SchedulingMath.remaining(it, trackedMs, entry, now) }
    val waitingOn = plan.unfinishedPrerequisites(entry)
    val notBefore = SchedulingMath.notBeforeForDependencies(now, waitingOn)
    val timeFmt = displayPattern("HH:mm")
    val dayFmt = displayPattern("EEE d MMM")
    fun label(slot: Pair<Instant, Instant>, prefix: String) =
        "$prefix · ${slot.first.atZone(zone).toLocalTime().format(timeFmt)} – ${slot.second.atZone(zone).toLocalTime().format(timeFmt)}"

    // The proposals. A new slot replaces the task's one block, so it holds all remaining work.
    data class Proposal(val slot: Pair<Instant, Instant>, val label: String, val caption: String)
    val proposals: List<Proposal> = when {
        r == null || r.remainingMs == 0L -> emptyList()
        r.alreadyCovered -> buildList {
            if (r.overAllocatedMs > 0) {
                val s = entry.startAt!!
                val shrunk = s to s.plusMillis(r.remainingMs)
                add(Proposal(shrunk, label(shrunk, L("Shorten your booked time")), L("It's %1\$s longer than the work left — this frees that time.", com.example.timeapp.data.durationTextMs(r.overAllocatedMs))))
            }
        }
        else -> {
            val today = LocalDate.now()
            val todaySlot = SchedulingMath.findFreeSlot(plan.entries, entryId, today, r.remainingMs, notBefore, workStart, workEnd)
            val tomorrow = today.plusDays(1)
            val tomorrowSlot = SchedulingMath.findFreeSlot(
                plan.entries, entryId, tomorrow, r.remainingMs, maxOf(notBefore, tomorrow.atStartOfDay(zone).toInstant()), workStart, workEnd,
            )
            val later = if (todaySlot == null && tomorrowSlot == null) {
                SchedulingMath.nextFreeSlot(plan.entries, entryId, today.plusDays(2), r.remainingMs, notBefore, 12, workStart, workEnd)
            } else null
            listOfNotNull(
                todaySlot?.let { Proposal(it, label(it, L("Today")), L("First free slot inside your work hours today.")) },
                tomorrowSlot?.let { Proposal(it, label(it, L("Tomorrow")), L("Alternative if you'd rather not touch today.")) },
                later?.let { Proposal(it, label(it, it.first.atZone(zone).format(dayFmt)), "Nothing fits today or tomorrow — the next free slot.") },
            )
        }
    }
    var selectedSlot by remember(entryId, proposals.map { it.slot }) { mutableStateOf(proposals.firstOrNull()?.slot) }

    Scaffold(
        topBar = {
            TimeTopBar(title = L("Schedule remaining"), onBack = onBack) {
                IconButton(onClick = { showHelp = true }) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = L("Help"))
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.s16)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = Dimens.s14))
            Column(
                modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg),
            ) {
                EstimateRow(L("Estimate"), estimateMs?.let { com.example.timeapp.data.durationTextMs(it) } ?: L("Not set"))
                EstimateRow(L("− Active time already tracked"), com.example.timeapp.data.durationTextMs(trackedMs))
                Row(
                    modifier = Modifier.fillMaxWidth().background(colors.accS).padding(Dimens.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(L("Remaining work"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(r?.let { com.example.timeapp.data.durationTextMs(it.remainingMs) } ?: "—", style = MaterialTheme.typography.titleMedium, color = colors.acc)
                }
                if (r != null && r.bookedMs > 0) {
                    EstimateRow(
                        L("Booked: %1\$s", (entry.startAt!!.atZone(zone).format(displayPattern("EEE HH:mm")))),
                        com.example.timeapp.data.durationTextMs(r.bookedMs),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(vertical = Dimens.s14).clickable { showEstimateEdit = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(L("Override the estimate"), style = MaterialTheme.typography.labelMedium, color = colors.acc)
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = colors.acc, modifier = Modifier.padding(start = Dimens.s6).size(16.dp))
            }
            Text(
                L("PROPOSED — NOT SAVED YET"), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = Dimens.s10),
            )
            val explanation = when {
                estimateMs == null -> L("Set an estimate to see proposed slots.")
                r!!.remainingMs == 0L -> L("Nothing left to schedule — the time already tracked covers the estimate.")
                r.alreadyCovered && r.overAllocatedMs == 0L -> L("Your booked time already holds exactly the work left. Nothing to change.")
                r.alreadyCovered -> null
                proposals.isEmpty() -> L("No free slot of %1\$s in your work hours over the next two weeks.", com.example.timeapp.data.durationTextMs(r.remainingMs))
                r.bookedMs > 0 -> L("A new slot replaces your booked time, and holds all %1\$s left.", com.example.timeapp.data.durationTextMs(r.remainingMs))
                else -> null
            }
            explanation?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Dimens.s10))
            }
            proposals.forEach { p ->
                SlotOption(
                    label = p.label,
                    caption = p.caption,
                    selected = selectedSlot == p.slot,
                    enabled = true,
                    onClick = { selectedSlot = p.slot },
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.s16)
                    .background(colors.accS, Shapes.lg)
                    .padding(Dimens.s14),
            ) {
                Text(L("RESPECTED"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s8))
                RespectedRow(L("Everything else you've booked — untouched"))
                // The real work hours from Workday settings; this used to be a fixed 09:00 – 18:00.
                RespectedRow(L("Work hours %1\$s – %2\$s", workStart.format(timeFmt), workEnd.format(timeFmt)))
                waitingOn.forEach { p ->
                    val endAt = p.endAt
                    if (endAt != null) {
                        RespectedRow(L("Starts after “%1\$s” ends, %2\$s", p.title, (endAt.atZone(zone).format(displayPattern("EEE HH:mm")))))
                    } else {
                        Text(
                            L("“%1\$s” isn't scheduled, so this may land before it's done.", p.title),
                            style = MaterialTheme.typography.bodySmall,
                            color = TimeThemeTokens.status.warnInk,
                            modifier = Modifier.padding(bottom = Dimens.s4),
                        )
                    }
                }
            }
            // A fixed block was locked in place by the user: moving it is allowed, but only as
            // an explicit unlock, never as an ordinary apply.
            val lockedBlock = entry.isFixed && entry.startAt != null
            if (lockedBlock) {
                Text(
                    L("Its booked time is fixed — you locked it in place. Applying unlocks it and moves it."),
                    style = MaterialTheme.typography.bodySmall,
                    color = TimeThemeTokens.status.warnInk,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s10)
                        .background(TimeThemeTokens.status.warn, Shapes.md).padding(Dimens.s12),
                )
            }
            Button(onClick = { selectedSlot?.let { onApply(it.first, it.second) } }, enabled = selectedSlot != null, modifier = Modifier.fillMaxWidth()) {
                Text(if (lockedBlock) L("Unlock and move") else L("Apply 1 change"))
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s10)) {
                Text(L("Pick a time myself"))
            }
            Row {
                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                Text(
                    L("Nothing moves until you apply, and you can undo it right after. Elapsed history is never rewritten — only unused future allocation."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.s8, bottom = Dimens.s16),
                )
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(L("How this works")) },
            text = {
                Text(
                    L("Nothing moves until you apply, and an Undo appears right after. Time already passed is never rewritten — only booked time still ahead. Your other bookings, your work hours and the tasks this one waits on are respected when proposing a new slot."),
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text(L("Got it")) } },
        )
    }

    if (showEstimateEdit) {
        var minutes by remember { mutableStateOf(((estimateMs ?: 0L) / 60_000).toString()) }
        AlertDialog(
            onDismissRequest = { showEstimateEdit = false },
            title = { Text(L("Override the estimate")) },
            text = {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text(L("Minutes")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    estimateOverrideMs = (minutes.toLongOrNull() ?: 0L) * 60_000
                    showEstimateEdit = false
                }) { Text(L("Apply")) }
            },
            dismissButton = { TextButton(onClick = { showEstimateEdit = false }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun SlotOption(label: String, caption: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.s10)
            .background(TimeThemeTokens.colors.card, Shapes.lg)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) colors.acc else colors.line, Shapes.lg)
            .then(if (enabled) Modifier.semantics { this.selected = selected }.clickable(onClick = onClick) else Modifier)
            .padding(Dimens.s14),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .then(
                    if (selected) Modifier.background(colors.acc, Shapes.xs) else Modifier.border(2.dp, TimeThemeTokens.status.controlOutline, Shapes.xs),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.padding(start = Dimens.s14)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(caption, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.s4))
        }
    }
}

@Composable
private fun EstimateRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s16, vertical = Dimens.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RespectedRow(text: String) {
    Row(modifier = Modifier.padding(bottom = Dimens.s4), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = TimeThemeTokens.status.okInk, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = Dimens.s8))
    }
}
