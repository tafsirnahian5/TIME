package com.example.timeapp.ui.interrupt

import androidx.compose.material.icons.rounded.Close
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.timeapp.ui.components.TimerRing
import com.example.timeapp.ui.focus.formatElapsed
import com.example.timeapp.ui.richtext.RichTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * D1 — Switch work: one timestamp closes the current segment and opens the next (see
 * `PlanViewModel.switchTask`/`pauseTask`). No overlap, nothing lost.
 */
@Composable
fun SwitchWorkScreen(
    currentEntryId: Long?,
    currentEntryTitle: String,
    trackedMs: Long,
    remainingMs: Long?,
    candidates: List<Entry>,
    onBack: () -> Unit,
    onReschedule: () -> Unit,
    onConfirm: (action: String, resumeNote: String, switchToId: Long?) -> Unit,
) {
    val colors = TimeThemeTokens.colors
    var resumeNote by remember { mutableStateOf("") }
    var switchToId by remember(candidates) { mutableStateOf(candidates.firstOrNull()?.id) }
    var scheduleRemaining by remember { mutableStateOf(remainingMs != null) }
    val progress = remainingMs?.let { r -> if (trackedMs + r > 0) trackedMs.toFloat() / (trackedMs + r) else 0f } ?: 0f

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Dimens.s16),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Close, contentDescription = L("Close"))
                }
                Text(L("Focus"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Dimens.s20))
                TimerRing(progress = progress, label = formatElapsed(trackedMs), size = 170.dp, strokeWidth = 10.dp)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.accS3, Shapes.sheet)
                    .padding(Dimens.s22),
            ) {
                // Scrolls above the pinned Switch/Pause buttons, for the same reason as TaskDetail: the
                // candidate list plus a multi-line resume note outgrew the sheet and got clipped.
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(L("Switch work"), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        L("One timestamp closes the current segment and opens the next. No overlap, nothing lost."),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Dimens.s4, bottom = Dimens.s16),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TimeThemeTokens.colors.card, Shapes.lg)
                            .padding(Dimens.s16),
                    ) {
                        Text(L("PAUSING"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() })
                        Row(
                            modifier = Modifier.padding(top = Dimens.s10),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(9.dp).background(colors.acc, CircleShape))
                            Text(currentEntryTitle, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = Dimens.s12))
                            Text(formatElapsed(trackedMs), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            remainingMs?.let { L("Becomes Paused. %1\$s of the estimate remain.", com.example.timeapp.data.durationTextMs(it)) } ?: L("Becomes Paused. No estimate set for this task."),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Dimens.s6),
                        )
                    }
                    RichTextField(
                        value = resumeNote,
                        onValueChange = { resumeNote = it },
                        label = L("Resume note"),
                        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s16),
                    )
                    Text(L("START INSTEAD"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s10))
                    if (candidates.isEmpty()) {
                        Text(
                            L("Nothing else is planned or unscheduled right now."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Dimens.s10),
                        )
                    }
                    candidates.forEach { candidate ->
                        val selected = switchToId == candidate.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) colors.accC else Color.Transparent, Shapes.lg)
                                .semantics { this.selected = selected }.clickable { switchToId = candidate.id }
                                .padding(horizontal = Dimens.s14, vertical = Dimens.s10),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .border(if (selected) 6.dp else 2.dp, if (selected) colors.acc else TimeThemeTokens.status.controlOutline, CircleShape)
                                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent, CircleShape),
                            )
                            Text(
                                candidate.title, modifier = Modifier.weight(1f).padding(start = Dimens.s14),
                                color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurface,
                            )
                            candidate.category?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    if (remainingMs != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TimeThemeTokens.colors.card, Shapes.lg)
                                .padding(Dimens.s14)
                                .padding(top = Dimens.s12),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(L("Schedule the remaining %1\$s after", com.example.timeapp.data.durationTextMs(remainingMs)), modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(checked = scheduleRemaining, onCheckedChange = { scheduleRemaining = it })
                        }
                    }
                }
                Button(
                    onClick = {
                        onConfirm("switch", resumeNote, switchToId)
                        if (scheduleRemaining && remainingMs != null) onReschedule()
                    },
                    enabled = switchToId != null,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s10),
                ) { Text(candidates.firstOrNull { it.id == switchToId }?.let { L("Switch to %1\$s", it.title) } ?: L("Switch task")) }
                OutlinedButton(
                    onClick = { onConfirm("pause", resumeNote, null) },
                    enabled = currentEntryId != null,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s10),
                ) { Text(L("Just pause, start nothing")) }
            }
        }
    }
}
