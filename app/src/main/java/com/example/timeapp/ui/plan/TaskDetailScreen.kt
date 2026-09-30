package com.example.timeapp.ui.plan

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import com.example.timeapp.data.label
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.richtext.RichTextView
import com.example.timeapp.ui.richtext.toggleChecklistLine
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Task Detail — opened from a Board card. Due/Estimate come from Entry's structured fields,
 * Tracked/Remaining from real WorkSession totals. Not in the real design canvas; built from
 * the design generated in this pass (see the "Plan Board and Task Detail" canvas).
 */
@Composable
fun TaskDetailScreen(
    entryId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onReschedule: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: PlanViewModel,
    bookmarks: List<com.example.timeapp.data.Bookmark> = emptyList(),
    money: List<com.example.timeapp.data.MoneyRecord> = emptyList(),
    workStart: String = "09:00",
    workEnd: String = "18:00",
) {
    val colors = TimeThemeTokens.colors
    val plan by viewModel.uiState.collectAsState()
    // Archived tasks open too (from Search), so this looks beyond the default views.
    val entry = plan.find(entryId) ?: return
    val trackedMs by viewModel.trackedMs(entryId).collectAsState(initial = 0L)
    val subtasks by viewModel.subtasks(entryId).collectAsState(initial = emptyList())
    val events by viewModel.events(entryId).collectAsState(initial = emptyList())
    val sessions by remember(entryId) { viewModel.sessions(entryId) }.collectAsState(initial = emptyList())
    var editingSession by remember { mutableStateOf<com.example.timeapp.data.SessionRecord?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingBlockedStart by remember { mutableStateOf(false) }
    var addingPrerequisite by remember { mutableStateOf(false) }
    var linkingBookmark by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Starting work here posts the running-timer notification, so ask for permission the way the
    // Focus screens do — starting from here never asked, and nothing showed.
    val askNotificationPermission = com.example.timeapp.ui.components.rememberNotificationPermissionRequest()
    val waitingOn = plan.unfinishedPrerequisites(entry)
    var showAddSubtask by remember { mutableStateOf(false) }

    // While the task runs, the open stretch counts too — "Tracked" used to sit at 0 min for as
    // long as the timer ran, since only finished stretches were summed. Ticks on screen only.
    val openSession = sessions.firstOrNull { it.endedAt == null }
    var nowMs by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(openSession?.id) {
        while (openSession != null) { kotlinx.coroutines.delay(1_000); nowMs = System.currentTimeMillis() }
    }
    val liveTrackedMs = trackedMs + (openSession?.let { (nowMs - it.startedAt.toEpochMilli()).coerceAtLeast(0) } ?: 0L)
    val remainingMs = entry.estimateMs?.let { (it - liveTrackedMs).coerceAtLeast(0) }

    Scaffold(
        topBar = {
            TimeTopBar(title = entry.title, onBack = onBack) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = L("More"))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(L("Edit")) }, onClick = { menuExpanded = false; onEdit() })
                        if (!entry.isArchived) {
                            // Booked time moves with Change time; Reschedule sizes remaining work.
                            if (entry.kind == com.example.timeapp.data.EntryKind.TASK) {
                                DropdownMenuItem(text = { Text(L("Reschedule")) }, onClick = { menuExpanded = false; onReschedule() })
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(if (entry.isArchived) L("Restore") else L("Archive")) },
                            onClick = { menuExpanded = false; if (entry.isArchived) viewModel.restore(entry) else viewModel.archive(entry) },
                        )
                        DropdownMenuItem(text = { Text(L("Delete")) }, onClick = { menuExpanded = false; confirmingDelete = true })
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            // Scrolls above the pinned Resume/Schedule actions. This used to be a plain Column with
            // a weight spacer, so a long description or subtask list was clipped and unreachable —
            // and a verticalScroll can't simply be added to a Column holding a weight(1f) child.
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.padding(top = Dimens.s8, bottom = Dimens.s16)) {
                    StatusChip(entry.status)
                }
                if (entry.isArchived) {
                    Text(
                        L("Archived — off the List, Board and calendar, still counted in reports. Restore it from the menu."),
                        style = MaterialTheme.typography.bodySmall,
                        color = TimeThemeTokens.status.neutralInk,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s16)
                            .background(TimeThemeTokens.status.neutral, Shapes.md).padding(Dimens.s12),
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg).border(1.dp, colors.line, Shapes.lg).padding(bottom = Dimens.s4),
                ) {
                    entry.project?.let { StatRow(L("Project"), it.name) }
                    if (entry.tags.isNotEmpty()) StatRow(L("Tags"), entry.tags.joinToString(" ") { "#$it" })
                    // One word for "nothing here" ("None" sat beside "Not set"), hours past 60 minutes
                    // ("300 min"), and no accent-coloured dash that looked like a link.
                    val isBooking = entry.kind == com.example.timeapp.data.EntryKind.EVENT
                    if (isBooking) {
                        // Booked time used to show only task rows (Due, Estimate, Remaining) and
                        // never said when it was.
                        val start = entry.startAt
                        val end = entry.endAt
                        StatRow(L("When"), if (start != null && end != null) com.example.timeapp.data.bookingLabel(start, end) else L("Not set"))
                        StatRow(L("Kind"), if (entry.isFixed) L("Fixed") else L("Flexible"))
                    } else {
                        StatRow(L("Due"), entry.dueOn?.format(displayPattern("MMM d")) ?: L("Not set"))
                        StatRow(L("Estimate"), entry.estimateMs?.let { com.example.timeapp.ui.stats.formatHm(it) } ?: L("Not set"))
                    }
                    StatRow(
                        L("Tracked"),
                        if (openSession != null) L("%1\$s · running", com.example.timeapp.ui.focus.formatElapsed(liveTrackedMs)) else com.example.timeapp.ui.stats.formatHm(liveTrackedMs),
                    )
                    // Booked time has no estimate, so nothing "remains" on it.
                    if (!isBooking) {
                        if (remainingMs != null) {
                            StatRow(L("Remaining"), com.example.timeapp.ui.stats.formatHm(remainingMs), valueColor = colors.acc)
                        } else {
                            StatRow(L("Remaining"), L("Needs an estimate"), valueColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (entry.kind == com.example.timeapp.data.EntryKind.TASK) {
                    TaskBlocksCard(
                        entry, remainingMs, viewModel,
                        workStart = SchedulingMath.parseWorkTime(workStart, java.time.LocalTime.of(9, 0)),
                        workEnd = SchedulingMath.parseWorkTime(workEnd, java.time.LocalTime.of(18, 0)),
                    )
                }
                if (!entry.description.isNullOrBlank()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).border(1.dp, colors.line, Shapes.lg).padding(Dimens.s16),
                    ) {
                        Text(L("Description"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = Dimens.s8))
                        RichTextView(
                            raw = entry.description,
                            onToggleChecklistItem = { lineIndex ->
                                viewModel.updateTask(entry.copy(description = toggleChecklistLine(entry.description ?: "", lineIndex)))
                            },
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).border(1.dp, colors.line, Shapes.lg).padding(Dimens.s16),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Dimens.s14)) {
                        Text(L("Next steps"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showAddSubtask = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = L("Add step"), tint = colors.acc)
                        }
                    }
                    // Checklist lines written into the description can become real steps —
                    // tracked here, one tap each, instead of text to edit.
                    val checklistCount = remember(entry.description) {
                        entry.description?.let { com.example.timeapp.ui.richtext.extractChecklist(it).first.size } ?: 0
                    }
                    if (checklistCount > 0) {
                        TextButton(onClick = { viewModel.checklistToSubtasks(entry.id) }, modifier = Modifier.padding(bottom = Dimens.s4)) {
                            Text(if (checklistCount == 1) L("Turn the description's 1 checklist item into a step") else L("Turn the description's %1\$s checklist items into steps", checklistCount))
                        }
                    }
                    if (subtasks.isEmpty()) {
                        Text(
                            L("No steps yet."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Dimens.s6),
                        )
                    }
                    subtasks.forEach { step ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleSubtask(step) }.padding(vertical = Dimens.s6),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .then(if (step.done) Modifier.background(colors.acc, Shapes.xs) else Modifier.border(2.dp, TimeThemeTokens.status.controlOutline, Shapes.xs)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (step.done) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                            }
                            Text(
                                step.title, style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (step.done) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (step.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(start = Dimens.s12),
                            )
                            IconButton(onClick = { viewModel.deleteSubtask(step.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = L("Remove step"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                // Saved links for this task (the PDF's task/bookmark linking).
                val links = entry.bookmarkIds.mapNotNull { id -> bookmarks.firstOrNull { it.id == id } }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).border(1.dp, colors.line, Shapes.lg).padding(Dimens.s16),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L("Links"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { linkingBookmark = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = L("Link a saved link"), tint = colors.acc)
                        }
                    }
                    if (links.isEmpty()) {
                        Text(L("No links yet — add one from your saved links."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    links.forEach { b ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                modifier = Modifier.weight(1f).clickable { com.example.timeapp.ui.links.openInBrowser(context, b.url) }.padding(vertical = Dimens.s6),
                            ) {
                                Text(b.title, style = MaterialTheme.typography.bodyLarge, color = colors.acc)
                                Text(com.example.timeapp.data.displayUrl(b.url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            IconButton(onClick = { viewModel.unlinkBookmark(entry.id, b.id) }) {
                                Icon(Icons.Rounded.Close, contentDescription = L("Unlink %1\$s", b.title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // Dependencies: what this task waits on, and what waits on it.
                val prerequisites = entry.prerequisiteIds.mapNotNull { id -> plan.find(id) }
                val neededBy = plan.entries.filter { entry.id in it.prerequisiteIds }
                // Tasks only: booked time doesn't wait on work.
                if (entry.kind == com.example.timeapp.data.EntryKind.TASK) Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).border(1.dp, colors.line, Shapes.lg).padding(Dimens.s16),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L("Waiting on"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { addingPrerequisite = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = L("Add a task this waits on"), tint = colors.acc)
                        }
                    }
                    if (prerequisites.isEmpty()) {
                        Text(L("Nothing — it can start any time."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    prerequisites.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.title,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (p.status == EntryStatus.DONE) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = Modifier.weight(1f),
                            )
                            Text(p.status.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { viewModel.removeDependency(entry.id, p.id) }) {
                                Icon(Icons.Rounded.Close, contentDescription = L("Stop waiting on %1\$s", p.title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (neededBy.isNotEmpty()) {
                        Text(
                            L("Needed by %1\$s", neededBy.joinToString { "“${it.title}”" }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Dimens.s8),
                        )
                    }
                }
                if (sessions.isNotEmpty()) {
                    // Each stretch of work on this task — tap one to fix its times or add a note.
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg)
                            .border(1.dp, colors.line, Shapes.lg).padding(Dimens.s16),
                    ) {
                        Text(L("Sessions"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = Dimens.s4))
                        sessions.take(8).forEach { s ->
                            com.example.timeapp.ui.focus.SessionRow(s, showDate = true) { editingSession = s }
                        }
                        if (sessions.size > 8) {
                            Text(L("and %1\$s earlier", (sessions.size - 8)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (money.isNotEmpty()) {
                    // Money linked to this task (Phase 13) — what it cost, and what's still planned.
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg)
                            .border(1.dp, colors.line, Shapes.lg).padding(Dimens.s16),
                    ) {
                        Text(L("Money"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = Dimens.s6))
                        money.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = Dimens.s6)) {
                                Text(r.title + if (r.isOpen) L(" · planned") else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text((if (r.isOpen) r.remainingMinor else r.paidMinor).toTaka(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (events.isNotEmpty()) {
                    // The PDF's "inspect state transitions and interruption reasons": every
                    // start, pause, switch and block, with the note left at the time.
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).border(1.dp, colors.line, Shapes.lg).padding(Dimens.s16),
                    ) {
                        Text(L("History"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = Dimens.s10))
                        val titles = plan.entries.associate { it.id to it.title }
                        val fmt = displayPattern("d MMM · HH:mm")
                        events.take(30).forEach { ev ->
                            Column(modifier = Modifier.padding(bottom = Dimens.s10)) {
                                Row {
                                    Text(
                                        com.example.timeapp.data.transitionLabel(ev.fromStatus, ev.toStatus, ev.relatedEntryId?.let { titles[it] }, ev.relatedEntryId != null),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        ev.at.atZone(java.time.ZoneId.systemDefault()).format(fmt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                ev.note?.takeIf { it.isNotBlank() }?.let {
                                    Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(vertical = Dimens.s16)) {
                if (entry.isArchived) {
                    // Work actions wait until it's back: a task started while archived would be
                    // running somewhere no default view shows.
                    OutlinedButton(onClick = { viewModel.restore(entry) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(L("Restore"), modifier = Modifier.padding(start = Dimens.s8))
                    }
                } else if (entry.status == EntryStatus.DONE) {
                    // A task's own page used to have no way to finish it, or to take it back.
                    OutlinedButton(onClick = { viewModel.toggleDone(entry) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(L("Reopen"), modifier = Modifier.padding(start = Dimens.s8))
                    }
                } else {
                    Button(
                        onClick = {
                            when (entry.status) {
                                EntryStatus.DOING -> viewModel.pauseTask(entry.id)
                                EntryStatus.PAUSED -> { askNotificationPermission(); viewModel.resumeTask(entry.id) }
                                // Warn, never refuse (PDF: a blocked task can still be started
                                // by an explicit override).
                                EntryStatus.BLOCKED -> confirmingBlockedStart = true
                                // Same override for unfinished prerequisites: warn, don't refuse.
                                else -> if (waitingOn.isNotEmpty()) confirmingBlockedStart = true else { askNotificationPermission(); viewModel.startTask(entry.id) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(if (entry.status == EntryStatus.DOING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            when (entry.status) {
                                EntryStatus.DOING -> L("Pause")
                                EntryStatus.PAUSED -> L("Resume work")
                                EntryStatus.BLOCKED -> L("Start anyway")
                                else -> L("Start work")
                            },
                            modifier = Modifier.padding(start = Dimens.s8),
                        )
                    }
                    // The PDF's "Stop and complete": closes the running session and marks the
                    // task done in one step.
                    OutlinedButton(onClick = { viewModel.finishTask(entry.id) }, modifier = Modifier.fillMaxWidth().padding(top = Dimens.s10)) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (entry.status == EntryStatus.DOING) L("Stop and mark done") else L("Mark done"), modifier = Modifier.padding(start = Dimens.s8))
                    }
                    if (entry.kind == com.example.timeapp.data.EntryKind.EVENT) {
                        // Booked time is moved by editing it; "remaining work" means nothing here.
                        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(top = Dimens.s10)) {
                            Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(L("Change time"), modifier = Modifier.padding(start = Dimens.s8))
                        }
                    } else {
                        OutlinedButton(onClick = onReschedule, modifier = Modifier.fillMaxWidth().padding(top = Dimens.s10)) {
                            Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(L("Schedule remaining"), modifier = Modifier.padding(start = Dimens.s8))
                        }
                    }
                }
                if (entry.status != EntryStatus.DONE && !entry.isArchived && entry.kind == com.example.timeapp.data.EntryKind.TASK) {
                    TextButton(
                        onClick = { viewModel.setBlocked(entry, entry.status != EntryStatus.BLOCKED) },
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s4),
                    ) {
                        Text(if (entry.status == EntryStatus.BLOCKED) L("Unblock") else L("Mark as blocked"))
                    }
                }
            }
        }
    }

    if (linkingBookmark) {
        val candidates = bookmarks.filter { it.id !in entry.bookmarkIds }
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { linkingBookmark = false },
            title = { Text(L("Link a saved link")) },
            text = {
                Column {
                    OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text(L("Search links")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val shown = candidates.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) }.take(8)
                    if (shown.isEmpty()) {
                        Text(L("No saved link to add. Save one in Links first."), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.s12))
                    }
                    shown.forEach { b ->
                        Text(
                            b.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.linkBookmark(entry.id, b.id); linkingBookmark = false }.padding(vertical = Dimens.s12),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linkingBookmark = false }) { Text(L("Cancel")) } },
        )
    }

    if (addingPrerequisite) {
        // Only tasks that can be waited on without a loop, and that aren't finished already.
        val links = plan.entries.flatMap { t -> t.prerequisiteIds.map { t.id to it } }
        val candidates = plan.entries.filter { c ->
            c.kind == com.example.timeapp.data.EntryKind.TASK && c.status != EntryStatus.DONE &&
                c.id !in entry.prerequisiteIds && !com.example.timeapp.data.dependencyWouldCycle(links, entry.id, c.id)
        }
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingPrerequisite = false },
            title = { Text(L("Waits on…")) },
            text = {
                Column {
                    OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text(L("Search tasks")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val shown = candidates.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }.take(8)
                    if (shown.isEmpty()) {
                        Text(
                            L("No task fits. Finished tasks, and ones that already wait on this one, can't be added."),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = Dimens.s12),
                        )
                    }
                    shown.forEach { c ->
                        Text(
                            c.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.addDependency(entry.id, c.id); addingPrerequisite = false }
                                .padding(vertical = Dimens.s12),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addingPrerequisite = false }) { Text(L("Cancel")) } },
        )
    }

    if (confirmingBlockedStart) {
        AlertDialog(
            onDismissRequest = { confirmingBlockedStart = false },
            title = { Text(if (entry.status == EntryStatus.BLOCKED) L("Start a blocked task?") else L("Start before what it waits on?")) },
            text = {
                Text(
                    if (entry.status == EntryStatus.BLOCKED) {
                        L("It's marked as waiting on something else. Starting it moves it to Doing and starts tracking time.")
                    } else {
                        (if (waitingOn.size == 1) L("It waits on %1\$s, which isn't done yet. ", waitingOn.joinToString { "“${it.title}”" })
                        else L("It waits on %1\$s, which aren't done yet. ", waitingOn.joinToString { "“${it.title}”" })) +
                            L("Starting it moves it to Doing and starts tracking time.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingBlockedStart = false; askNotificationPermission(); viewModel.startTask(entry.id) }) { Text(L("Start anyway")) }
            },
            dismissButton = { TextButton(onClick = { confirmingBlockedStart = false }) { Text(L("Cancel")) } },
        )
    }

    editingSession?.let { s ->
        com.example.timeapp.ui.focus.SessionEditDialog(s, viewModel.sessionActions, onDismiss = { editingSession = null })
    }

    if (confirmingDelete) {
        DeleteTaskDialog(entry, viewModel, onDone = { confirmingDelete = false }, onDeleted = onDeleted)
    }

    if (showAddSubtask) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSubtask = false },
            title = { Text(L("Next step")) },
            text = {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(L("Title")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addSubtask(entry.id, title, subtasks.size)
                    showAddSubtask = false
                }, enabled = title.isNotBlank()) { Text(L("Add")) }
            },
            dismissButton = { TextButton(onClick = { showAddSubtask = false }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun StatusChip(status: EntryStatus) {
    val (label, bg, fg) = when (status) {
        EntryStatus.PAUSED -> Triple(L("Paused"), TimeThemeTokens.status.warn, TimeThemeTokens.status.warnInk)
        EntryStatus.BLOCKED -> Triple(L("Blocked"), TimeThemeTokens.status.danger, TimeThemeTokens.status.dangerInk)
        EntryStatus.DOING -> Triple(L("Doing"), TimeThemeTokens.status.ok, TimeThemeTokens.status.okInk)
        EntryStatus.OVERDUE -> Triple(L("Overdue"), TimeThemeTokens.status.danger, TimeThemeTokens.status.dangerInk)
        EntryStatus.DONE -> Triple(L("Done"), TimeThemeTokens.status.neutral, TimeThemeTokens.status.neutralInk)
        EntryStatus.PLANNED -> Triple(L("Planned"), TimeThemeTokens.colors.accC, TimeThemeTokens.colors.accInk)
        EntryStatus.UNSCHEDULED -> Triple(L("No date"), TimeThemeTokens.status.neutral, TimeThemeTokens.status.neutralInk)
    }
    Box(modifier = Modifier.background(bg, Shapes.pill).padding(horizontal = Dimens.s14, vertical = Dimens.s6)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s16, vertical = Dimens.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}
