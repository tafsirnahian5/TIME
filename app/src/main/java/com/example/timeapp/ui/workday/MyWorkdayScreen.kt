package com.example.timeapp.ui.workday

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.timeapp.data.WorkdaySegment
import com.example.timeapp.ui.components.TimePickerPrompt
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.WorkdaySegmentKind
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.stats.formatHm
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.ZoneId

/**
 * Settings › Workday & calendar › My Workday — today's figures (live), Start / Take a break /
 * Resume / End with the PDF's rules (a break pauses work; Resume offers the task it
 * interrupted; End day asks what to do with running work), a forgotten earlier day to close,
 * and the day's timeline, where tapping an interval corrects it (reason and originals kept).
 */
@Composable
fun MyWorkdayScreen(
    onBack: () -> Unit,
    onReviewDay: () -> Unit,
    onWeeklyReview: () -> Unit,
    viewModel: WorkdayViewModel,
    taskTitle: (Long) -> String? = { null },
) {
    val ui by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var resumeOffer by remember { mutableStateOf<Long?>(null) }
    var askingEnd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkdaySegment?>(null) }
    var closingForgotten by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val hm = displayPattern("H:mm")
    val s = ui.summary

    Scaffold(
        topBar = {
            TimeTopBar(
                title = L("My Workday"), onBack = onBack,
                actions = { TextButton(onClick = onWeeklyReview) { Text(L("Review")) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.s16)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                ui.date.format(displayPattern("EEE d MMM")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s10, bottom = Dimens.s14),
            )
            ui.forgotten?.let { f ->
                // The PDF: "a forgotten End day is flagged for review rather than creating hours".
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s14).background(TimeThemeTokens.status.warn, Shapes.lg).padding(Dimens.s14)) {
                    Text(
                        L("Your day on %1\$s was never ended (on since %2\$s).", (f.date.format(displayPattern("EEE d MMM"))), f.startedAt.atZone(zone).format(hm)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TimeThemeTokens.status.warnInk,
                    )
                    TextButton(onClick = { closingForgotten = true }) { Text(L("Set when it ended")) }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg)) {
                StatListRow(Icons.Rounded.Schedule, L("On duty"), formatHm(s.onDutyMs))
                StatListRow(Icons.Rounded.Timeline, L("Active work"), formatHm(s.activeMs))
                StatListRow(Icons.Rounded.Coffee, L("Breaks"), formatHm(s.breakMs))
                StatListRow(Icons.Rounded.HourglassEmpty, L("Untracked"), formatHm(s.untrackedMs), showDivider = s.outsideMs > 0)
                if (s.outsideMs > 0) StatListRow(Icons.Rounded.Timeline, L("Work outside the day"), formatHm(s.outsideMs), showDivider = false)
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                val end = {
                    scope.launch { if (viewModel.workIsRunning()) askingEnd = true else viewModel.endDay(false) }
                    Unit
                }
                when (ui.activeKind) {
                    null -> Button(onClick = { viewModel.startDay() }, modifier = Modifier.fillMaxWidth()) {
                        // After End day the timeline above is full — "Start day" read as if it hadn't begun.
                        Text(if (ui.segments.isEmpty()) L("Start day") else L("Start again"))
                    }
                    WorkdaySegmentKind.ON_DUTY -> {
                        OutlinedButton(onClick = { viewModel.takeBreak() }, modifier = Modifier.weight(1f)) { Text(L("Take a break")) }
                        OutlinedButton(onClick = end, modifier = Modifier.weight(1f)) { Text(L("End day")) }
                    }
                    WorkdaySegmentKind.BREAK -> {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val task = viewModel.interruptedTask()
                                    if (task != null) resumeOffer = task else viewModel.resumeDay(null)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(L("Resume day")) }
                        OutlinedButton(onClick = end, modifier = Modifier.weight(1f)) { Text(L("End day")) }
                    }
                }
            }
            if (ui.activeKind == WorkdaySegmentKind.ON_DUTY) {
                Text(
                    L("A break pauses whatever you're working on."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.s6),
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16, bottom = Dimens.s20).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("TODAY'S TIMELINE"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s6))
                if (ui.segments.isEmpty()) {
                    Text(L("Nothing logged yet today."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(L("Tap a line to correct it."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ui.segments.forEach { segment ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { editing = segment }.padding(vertical = Dimens.s6),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            segment.startedAt.atZone(zone).format(hm) + "–" + (segment.endedAt?.atZone(zone)?.format(hm) ?: "now"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = Dimens.s12),
                        )
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(10.dp).background(if (segment.kind == WorkdaySegmentKind.ON_DUTY) TimeThemeTokens.status.okInk else TimeThemeTokens.status.warnInk, CircleShape),
                        )
                        Column(modifier = Modifier.padding(start = Dimens.s12)) {
                            Text(if (segment.kind == WorkdaySegmentKind.ON_DUTY) L("On duty") else L("Break"), style = MaterialTheme.typography.bodyLarge)
                            segment.correctionReason?.let { reason ->
                                val was = segment.originalStartedAt?.let { o ->
                                    L(" · was ") + o.atZone(zone).format(hm) + "–" + (segment.originalEndedAt?.atZone(zone)?.format(hm) ?: "open")
                                } ?: ""
                                Text(L("Edited: %1\$s%2\$s", reason, was), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            OutlinedButton(onClick = onReviewDay, modifier = Modifier.fillMaxWidth()) { Text(L("Review day")) }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = Dimens.s16))
        }
    }

    resumeOffer?.let { taskId ->
        AlertDialog(
            onDismissRequest = { resumeOffer = null },
            title = { Text(L("Back to %1\$s?", (taskTitle(taskId) ?: L("that task")))) },
            text = { Text(L("Your break paused it. Pick it up again as you go back on duty?")) },
            confirmButton = { TextButton(onClick = { resumeOffer = null; viewModel.resumeDay(taskId) }) { Text(L("Resume it")) } },
            dismissButton = { TextButton(onClick = { resumeOffer = null; viewModel.resumeDay(null) }) { Text(L("Not now")) } },
        )
    }
    if (askingEnd) {
        AlertDialog(
            onDismissRequest = { askingEnd = false },
            title = { Text(L("Work is still running")) },
            text = { Text(L("Ending the day stops it. Pause it to carry on later, or finish the session.")) },
            confirmButton = { TextButton(onClick = { askingEnd = false; viewModel.endDay(false) }) { Text(L("Pause it")) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { askingEnd = false }) { Text(L("Cancel")) }
                    TextButton(onClick = { askingEnd = false; viewModel.endDay(true) }) { Text(L("Finish session")) }
                }
            },
        )
    }
    if (closingForgotten) {
        ui.forgotten?.let { f ->
            TimePickerPrompt(java.time.LocalTime.of(18, 0)) { t ->
                closingForgotten = false
                t?.let { viewModel.closeForgotten(f.id, f.date.atTime(it).atZone(zone).toInstant()) }
            }
        }
    }
    editing?.let { seg -> SegmentEditDialog(seg, onSave = { a, b, reason -> viewModel.correct(seg.id, a, b, reason) }, onDismiss = { editing = null }) }
}

/** Corrects one timeline interval's times on its own day; the reason is required and kept. */
@Composable
private fun SegmentEditDialog(
    segment: WorkdaySegment,
    onSave: suspend (java.time.Instant, java.time.Instant?, String) -> String?,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var start by remember { mutableStateOf(segment.startedAt.atZone(zone).toLocalTime()) }
    var end by remember { mutableStateOf(segment.endedAt?.atZone(zone)?.toLocalTime()) }
    var reason by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val hm = displayPattern("H:mm")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (segment.kind == WorkdaySegmentKind.ON_DUTY) L("Correct on-duty time") else L("Correct break")) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { picking = "start" }, verticalAlignment = Alignment.CenterVertically) {
                    Text(L("From"), modifier = Modifier.weight(1f)); Text(start.format(hm), color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable(enabled = end != null) { picking = "end" }, verticalAlignment = Alignment.CenterVertically) {
                    Text(L("To"), modifier = Modifier.weight(1f)); Text(end?.format(hm) ?: "still going", color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text(L("Why")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.s6)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val p = onSave(segment.date.atTime(start).atZone(zone).toInstant(), end?.let { segment.date.atTime(it).atZone(zone).toInstant() }, reason)
                    if (p == null) onDismiss() else problem = p
                }
            }) { Text(L("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
    when (picking) {
        "start" -> TimePickerPrompt(start) { t -> picking = null; t?.let { start = it } }
        "end" -> TimePickerPrompt(end ?: start) { t -> picking = null; t?.let { end = it } }
        else -> Unit
    }
}

@Composable
private fun StatListRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(Dimens.s14), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TimeThemeTokens.colors.acc, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = Dimens.s12))
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        if (showDivider) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s14).height(Dimens.hairline).background(TimeThemeTokens.colors.line))
        }
    }
}
