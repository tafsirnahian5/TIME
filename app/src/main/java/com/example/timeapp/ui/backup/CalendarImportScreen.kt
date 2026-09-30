package com.example.timeapp.ui.backup

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.timeapp.data.Ics
import com.example.timeapp.ui.components.EmptyState
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Settings › Data & backup › Import calendar. The PDF's "timezone preview, occurrence identity
 * and duplicate choices": every event is shown in the user's own zone (with where it was
 * written), anything already imported (same UID) is unticked and ticking it *updates* the
 * saved one, overlaps with the existing schedule are pointed out but never resolved, and every
 * calendar field T!ME doesn't support is listed. Not in the design canvas.
 */
@Composable
fun CalendarImportScreen(onBack: () -> Unit, viewModel: PlanViewModel) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val plan by viewModel.uiState.collectAsState()
    var parsed by remember { mutableStateOf<Ics.ParseResult?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf<Int?>(null) }

    val items = remember(parsed) { parsed?.let { Ics.plan(it, plan.allEntries, zone) } }
    var chosen by remember(items) {
        mutableStateOf(items.orEmpty().indices.filter { items!![it].importable && items[it].existingId == null }.toSet())
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val result = text?.let { Ics.parse(it) }
            when {
                result == null -> loadError = L("Couldn't read that file.")
                result.events.isEmpty() -> loadError = L("No events found — is it an .ics calendar file?")
                else -> { loadError = null; parsed = result }
            }
        }
    }
    val fmt = displayPattern("EEE d MMM yyyy · HH:mm")

    Scaffold(topBar = { TimeTopBar(title = L("Import calendar"), onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            imported?.let { n ->
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.EventNote,
                        headline = if (n == 1) L("1 event imported") else L("%1\$s events imported", n),
                        supporting = L("Timed events become booked time on your calendar; all-day ones become tasks due that day."),
                        actionLabel = L("Done"),
                        onAction = onBack,
                        modifier = Modifier.padding(vertical = Dimens.s24),
                    )
                }
                return@LazyColumn
            }
            val list = items
            if (list == null) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.EventNote,
                        headline = L("Import from a calendar file"),
                        supporting = L("Choose an .ics file exported from a calendar app. You'll see every event, in your time zone, before anything is saved."),
                        actionLabel = L("Choose an .ics file"),
                        onAction = { picker.launch(arrayOf("text/calendar", "text/*", "application/octet-stream")) },
                        modifier = Modifier.padding(vertical = Dimens.s24),
                    )
                    loadError?.let { Text(it, color = status.dangerInk, style = MaterialTheme.typography.bodySmall) }
                }
                return@LazyColumn
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s12).background(colors.accC2, Shapes.lg).padding(Dimens.s14)) {
                    Text(L("%1\$s of %2\$s events ticked · times in %3\$s", chosen.size, list.size, zone.id), style = MaterialTheme.typography.bodyLarge, color = colors.accInk)
                    val again = list.count { it.existingId != null }
                    if (again > 0) Text(L("%1\$s already imported — tick one to update it", again), style = MaterialTheme.typography.bodySmall, color = colors.accInk)
                    val ignored = parsed?.ignored.orEmpty()
                    if (ignored.isNotEmpty()) {
                        Text(
                            L("Not supported, left out: ") + ignored.entries.sortedByDescending { it.value }.joinToString { "${it.key} ×${it.value}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accInk,
                        )
                    }
                }
                Button(
                    onClick = { viewModel.importCalendar(list.filterIndexed { i, _ -> i in chosen }) { imported = it } },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s12),
                ) { Text(if (chosen.size == 1) L("Import 1 event") else L("Import %1\$s events", chosen.size)) }
            }
            itemsIndexed(list) { index, item ->
                val ticked = index in chosen
                val overlaps = if (item.start != null && item.end != null) {
                    plan.entries.filter { e ->
                        e.id != item.existingId && e.startAt != null && e.endAt != null &&
                            e.startAt.isBefore(item.end) && e.endAt.isAfter(item.start)
                    }
                } else emptyList()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.touchMin)
                        .clickable(enabled = item.importable) { chosen = if (ticked) chosen - index else chosen + index }
                        .padding(vertical = Dimens.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = ticked, onCheckedChange = null, enabled = item.importable)
                    Column(modifier = Modifier.weight(1f).padding(start = Dimens.s8)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when {
                                item.start != null -> item.start.atZone(zone).format(fmt) +
                                    " – " + item.end!!.atZone(zone).toLocalTime().format(displayPattern("HH:mm"))
                                item.dueOn != null -> L("Due ") + item.dueOn.format(displayPattern("EEE d MMM yyyy"))
                                else -> ""
                            } + (item.recurrence?.let { " · ${it.label()}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        item.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = status.dangerInk) }
                        if (item.existingId != null) {
                            Text(L("Already imported — tick to update the saved one"), style = MaterialTheme.typography.bodySmall, color = status.warnInk)
                        }
                        item.remarks.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (overlaps.isNotEmpty()) {
                            Text(
                                L("Overlaps “%1\$s”", overlaps.first().title) + if (overlaps.size > 1) L(" and %1\$s more", (overlaps.size - 1)) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = status.warnInk,
                            )
                        }
                    }
                }
            }
            item { Text("", modifier = Modifier.padding(bottom = Dimens.s24)) }
        }
    }
}
