package com.example.timeapp.ui.focus

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import kotlinx.coroutines.launch
import com.example.timeapp.data.SessionRecord
import com.example.timeapp.ui.components.DatePickerPrompt
import com.example.timeapp.ui.components.TimePickerPrompt
import com.example.timeapp.ui.theme.Dimens
import java.time.LocalDateTime
import java.time.ZoneId

/** What the session editor can do; each returns a problem to show, or null when done. */
class SessionActions(
    val save: suspend (id: Long, start: java.time.Instant, end: java.time.Instant?, note: String?, reason: String) -> String?,
    val addFive: suspend (id: Long) -> String?,
    val confirm: suspend (id: Long) -> Unit,
    val delete: suspend (id: Long) -> Unit,
)

/**
 * Edits one work session: its times (a finished one), its note, and — for one flagged Needs
 * review — *Looks right*. Changed times ask for a reason, kept with the original values;
 * overlapping another session is refused, since that minute would count twice.
 */
@Composable
fun SessionEditDialog(session: SessionRecord, actions: SessionActions, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    var start by remember { mutableStateOf(LocalDateTime.ofInstant(session.startedAt, zone)) }
    var end by remember { mutableStateOf(session.endedAt?.let { LocalDateTime.ofInstant(it, zone) }) }
    var note by remember { mutableStateOf(session.note.orEmpty()) }
    var reason by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val timesChanged = start.atZone(zone).toInstant() != session.startedAt || end?.atZone(zone)?.toInstant() != session.endedAt
    val fmt = displayPattern("EEE d MMM, HH:mm")

    fun run(block: suspend () -> String?) {
        scope.launch {
            val p = block()
            if (p == null) onDismiss() else problem = p
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(session.title ?: L("Focus, no task")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (session.needsReview) {
                    Text(
                        L("Needs review — it ran across a restart, a clock change or a very long stretch. Check the times."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = Dimens.s10),
                    )
                }
                TimeRow(L("Started"), start.format(fmt), enabled = end != null) { editing = "startDate" }
                if (end != null) TimeRow(L("Ended"), end!!.format(fmt), enabled = true) { editing = "endDate" }
                else Text(L("Still running — finish it to change its times."), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = Dimens.s8))
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text(L("Note")) },
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
                )
                if (timesChanged) {
                    OutlinedTextField(
                        value = reason, onValueChange = { reason = it }, label = { Text(L("Why the change")) },
                        placeholder = { Text(L("Forgot to stop it")) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
                    )
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.s8)) }
                Row(modifier = Modifier.padding(top = Dimens.s8)) {
                    if (session.endedAt != null && !timesChanged) TextButton(onClick = { run { actions.addFive(session.id) } }) { Text(L("+5 min")) }
                    if (session.needsReview && !timesChanged) TextButton(onClick = { run { actions.confirm(session.id); null } }) { Text(L("Looks right")) }
                    if (session.endedAt != null) TextButton(onClick = { confirmingDelete = true }) { Text(L("Delete")) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                run { actions.save(session.id, start.atZone(zone).toInstant(), end?.atZone(zone)?.toInstant(), note, reason) }
            }) { Text(L("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )

    when (editing) {
        "startDate" -> DatePickerPrompt(start.toLocalDate()) { d -> editing = if (d != null) { start = d.atTime(start.toLocalTime()); "startTime" } else null }
        "startTime" -> TimePickerPrompt(start.toLocalTime()) { t -> editing = null; t?.let { start = start.toLocalDate().atTime(it) } }
        "endDate" -> DatePickerPrompt(end!!.toLocalDate()) { d -> editing = if (d != null) { end = d.atTime(end!!.toLocalTime()); "endTime" } else null }
        "endTime" -> TimePickerPrompt(end!!.toLocalTime()) { t -> editing = null; t?.let { end = end!!.toLocalDate().atTime(it) } }
        else -> Unit
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(L("Delete this session?")) },
            text = { Text(L("Its time comes out of every report. This can't be undone.")) },
            confirmButton = { TextButton(onClick = { confirmingDelete = false; run { actions.delete(session.id); null } }) { Text(L("Delete")) } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun TimeRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "09:05–09:40 · 35 min". */
fun sessionSpanLabel(s: SessionRecord): String {
    val zone = ZoneId.systemDefault()
    val t = displayPattern("HH:mm")
    val from = s.startedAt.atZone(zone).format(t)
    val to = s.endedAt?.atZone(zone)?.format(t) ?: "now"
    val mins = s.durationMs?.let { " · ${com.example.timeapp.ui.stats.formatHm(it)}" } ?: L(" · running")
    return "$from–$to$mins"
}
