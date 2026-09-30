package com.example.timeapp.ui.plan

import com.example.timeapp.data.L
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.example.timeapp.data.whenLine
import com.example.timeapp.data.blockSpans

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.Priority
import com.example.timeapp.ui.add.label
import com.example.timeapp.ui.components.RootTopBar
import com.example.timeapp.ui.components.RootTopBarTrailing
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.FilterAlt
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** C1 — Plan · List: overdue, today and unscheduled work, chronological. */
@Composable
fun PlanListScreen(
    onAdd: () -> Unit,
    onOpenMonth: () -> Unit,
    onOpenBoard: () -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (Long) -> Unit,
    onReschedule: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    viewModel: PlanViewModel,
    projects: List<com.example.timeapp.data.Category> = emptyList(),
    savedFilters: List<com.example.timeapp.data.SavedFilter> = emptyList(),
    onSavedFiltersChange: (List<com.example.timeapp.data.SavedFilter>) -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val tagNames by viewModel.tagNames.collectAsState()
    // Everything below works on the project/tag-scoped set; the status chips then narrow it.
    val scoped = ui.scopedEntries
    var filter by remember { mutableStateOf(PlanFilter.ALL) }
    var showArchived by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    // Counts always describe the whole list, so the chips don't renumber as you filter.
    val allOverdue = scoped.filter { it.status == EntryStatus.OVERDUE }
    // Grouped by the task's real date (its due date, else the day of its first booked block).
    // "Today" used to hold every Planned task — one due next month sat under TODAY — and a
    // paused task with no date was counted in neither Today's date nor "No date".
    val zone = java.time.ZoneId.systemDefault()
    val todayDate = java.time.LocalDate.now()
    // The day a task belongs to: its due date or its next booked time, whichever comes first — a
    // task due Friday but booked for today used to sit under COMING UP. With neither ahead, the
    // last booking's day (so a task booked yesterday isn't "no date").
    val nowInstant = java.time.Instant.now()
    fun com.example.timeapp.data.Entry.day(): java.time.LocalDate? {
        val spans = blockSpans()
        val nextBooking = spans.firstOrNull { it.second.isAfter(nowInstant) }?.first?.atZone(zone)?.toLocalDate()
        return listOfNotNull(dueOn, nextBooking).minOrNull() ?: spans.lastOrNull()?.first?.atZone(zone)?.toLocalDate()
    }
    fun com.example.timeapp.data.Entry.inProgress() = status == EntryStatus.DOING || status == EntryStatus.PAUSED
    val open = setOf(EntryStatus.PLANNED, EntryStatus.UNSCHEDULED, EntryStatus.DOING, EntryStatus.PAUSED)
    val allUnscheduled = scoped.filter { it.day() == null && it.status in open }
    val allHigh = scoped.filter { it.priority >= Priority.HIGH }
    val visible = when (filter) {
        PlanFilter.ALL -> scoped
        PlanFilter.OVERDUE -> allOverdue
        PlanFilter.UNSCHEDULED -> allUnscheduled
        PlanFilter.HIGH -> allHigh
    }
    val overdue = visible.filter { it.status == EntryStatus.OVERDUE }
    val today = visible.filter {
        it.status in open && (it.inProgress() || it.day()?.let { d -> !d.isAfter(todayDate) } == true)
    }
    val upcoming = visible.filter { it.status in open && !it.inProgress() && it.day()?.isAfter(todayDate) == true }
        .sortedBy { it.day() }
    val unscheduled = visible.filter { it.status in open && !it.inProgress() && it.day() == null }
    val blocked = visible.filter { it.status == EntryStatus.BLOCKED }
    // Finished tasks used to be counted in "All" and then shown nowhere — ticking one off made
    // it vanish with no way to look back or undo it from here.
    val doneList = visible.filter { it.status == EntryStatus.DONE }.sortedByDescending { it.completedAt ?: java.time.Instant.EPOCH }
    val openVisible = visible.filter { it.status != EntryStatus.DONE }

    Scaffold(
        topBar = {
            RootTopBar(
                help = com.example.timeapp.ui.components.TabHelps.plan,
                title = L("Plan"), onSearch = onSearch,
                onOpenSettings = onOpenSettings,
                trailing = RootTopBarTrailing.SETTINGS,
            )
        },
        floatingActionButton = {
            // A real FAB: it was a light-on-light Row that looked disabled and had no button role.
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = colors.accC,
                contentColor = colors.accInk,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(L("Task")) },
                )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PlanBoardCalendarToggle(selectedIndex = 0, onOpenMonth = onOpenMonth, onOpenBoard = onOpenBoard)
            FilterChipsRow(
                all = scoped.count { it.status != EntryStatus.DONE },
                overdue = allOverdue.size,
                unscheduled = allUnscheduled.size,
                high = allHigh.size,
                selected = filter,
                onSelect = { filter = it },
            )
            PlanScopeRow(projects, tagNames, ui.scope, viewModel::setScope, savedFilters = savedFilters, onSavedFiltersChange = onSavedFiltersChange)
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.screenHList)) {
                if (overdue.isNotEmpty()) {
                    item { SectionLabel(L("OVERDUE"), TimeThemeTokens.status.dangerInk) }
                    items(overdue) { PlanEntryRow(it, viewModel, onEdit, onReschedule, onOpenTask) }
                }
                if (openVisible.isEmpty()) {
                    item {
                        if (ui.entries.isEmpty()) {
                            EmptyState(
                                icon = Icons.Rounded.Checklist,
                                illustration = Illustration.PLAN,
                                headline = L("Nothing planned yet"),
                                supporting = L("A title is enough to start — you can add a due date, an estimate and a schedule later."),
                                actionLabel = L("Add a task"),
                                onAction = onAdd,
                                modifier = Modifier.padding(vertical = Dimens.s24),
                            )
                        } else if (filter == PlanFilter.ALL && doneList.isNotEmpty()) {
                            EmptyState(
                                icon = Icons.Rounded.Checklist,
                                headline = L("All done"),
                                supporting = L("Everything on your list is finished. Add the next thing when you're ready."),
                                actionLabel = L("Add a task"),
                                onAction = onAdd,
                                modifier = Modifier.padding(vertical = Dimens.s24),
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Rounded.FilterAlt,
                                headline = when (filter) {
                                    PlanFilter.OVERDUE -> L("Nothing overdue")
                                    PlanFilter.UNSCHEDULED -> L("Everything is scheduled")
                                    PlanFilter.HIGH -> L("Nothing marked high")
                                    PlanFilter.ALL -> L("Nothing planned")
                                },
                                supporting = L("No task matches this filter right now."),
                                actionLabel = L("Show all"),
                                onAction = { filter = PlanFilter.ALL; viewModel.setScope(PlanScope()) },
                                modifier = Modifier.padding(vertical = Dimens.s24),
                            )
                        }
                    }
                } else if (today.isNotEmpty()) {
                    // Only with something under it — an empty TODAY heading used to show.
                    item {
                        val label = java.time.LocalDate.now()
                            .format(displayPattern("EEE d MMM"))
                            .uppercase()
                        SectionLabel(L("TODAY · %1\$s", label))
                    }
                    items(today) { PlanEntryRow(it, viewModel, onEdit, onReschedule, onOpenTask) }
                }
                if (upcoming.isNotEmpty()) {
                    item { SectionLabel(L("COMING UP")) }
                    items(upcoming) { PlanEntryRow(it, viewModel, onEdit, onReschedule, onOpenTask) }
                }
                if (blocked.isNotEmpty()) {
                    item { SectionLabel(L("BLOCKED")) }
                    items(blocked) { PlanEntryRow(it, viewModel, onEdit, onReschedule, onOpenTask) }
                }
                if (unscheduled.isNotEmpty()) {
                    item { SectionLabel(L("NO DATE YET")) }
                    items(unscheduled) { PlanEntryRow(it, viewModel, onEdit, onReschedule, onOpenTask) }
                }
                if (doneList.isNotEmpty()) {
                    item {
                        androidx.compose.material3.TextButton(onClick = { showDone = !showDone }, modifier = Modifier.padding(top = Dimens.s8)) {
                            Text(if (showDone) L("Hide done (%1\$s)", doneList.size) else L("Done (%1\$s)", doneList.size))
                        }
                    }
                    if (showDone) {
                        items(doneList, key = { it.id }) { PlanEntryRow(it, viewModel, onEdit, onReschedule, onOpenTask) }
                    }
                }
                if (ui.archived.isNotEmpty()) {
                    item {
                        androidx.compose.material3.TextButton(onClick = { showArchived = !showArchived }, modifier = Modifier.padding(top = Dimens.s8)) {
                            Text(if (showArchived) L("Hide archived (%1\$s)", ui.archived.size) else L("Archived (%1\$s)", ui.archived.size))
                        }
                    }
                    if (showArchived) {
                        items(ui.archived, key = { it.id }) { ArchivedRow(it, onOpenTask) }
                    }
                }
                // Room under the last row for the FAB.
                item { Box(Modifier.padding(bottom = Dimens.s34 * 2)) }
            }
        }

        ui.pendingConflict?.let { proposal ->
            ConflictDialog(proposal = proposal, onChoice = viewModel::resolveConflict)
        }
    }
}

@Composable
internal fun PlanBoardCalendarToggle(selectedIndex: Int, onOpenMonth: () -> Unit, onOpenList: () -> Unit = {}, onOpenBoard: () -> Unit = {}) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenHList, vertical = Dimens.s8)
            .background(colors.accS2, Shapes.md)
            .padding(Dimens.s4),
    ) {
        listOf(L("List"), L("Board"), L("Calendar")).forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) colors.accC else Color.Transparent, Shapes.sm)
                    .padding(vertical = Dimens.s10)
                    .semantics { this.selected = selected }.clickable {
                        when (i) {
                            2 -> onOpenMonth()
                            0 -> onOpenList()
                            1 -> onOpenBoard()
                            else -> {}
                        }
                    },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

internal enum class PlanFilter { ALL, OVERDUE, UNSCHEDULED, HIGH }

@Composable
private fun FilterChipsRow(
    all: Int,
    overdue: Int,
    unscheduled: Int,
    high: Int,
    selected: PlanFilter,
    onSelect: (PlanFilter) -> Unit,
) {
    val colors = TimeThemeTokens.colors
    Row(modifier = Modifier.padding(horizontal = Dimens.screenHList, vertical = Dimens.s4)) {
        listOf(
            Triple(L("All %1\$s", all), PlanFilter.ALL, true),
            Triple(L("Overdue %1\$s", overdue), PlanFilter.OVERDUE, overdue > 0),
            Triple(L("No date %1\$s", unscheduled), PlanFilter.UNSCHEDULED, unscheduled > 0),
            Triple(L("High %1\$s", high), PlanFilter.HIGH, high > 0),
        ).forEach { (label, value, enabled) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .padding(end = Dimens.s8)
                    .background(if (isSelected) colors.accC else Color.Transparent, Shapes.pill)
                    .semantics { this.selected = isSelected }.clickable(enabled = enabled) { onSelect(value) }
                    .padding(horizontal = Dimens.s14, vertical = Dimens.s8),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isSelected -> colors.accInk
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color = TimeThemeTokens.status.neutralInk) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.semantics { heading() }.padding(vertical = Dimens.s8),
    )
}

@Composable
internal fun PlanEntryRow(
    entry: Entry,
    viewModel: PlanViewModel,
    onEdit: (Long) -> Unit,
    onReschedule: (Long) -> Unit,
    onOpen: (Long) -> Unit,
) {
    val colors = TimeThemeTokens.colors
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val done = entry.status == EntryStatus.DONE
    var finishing by remember(entry.id, entry.status) { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val reduced = com.example.timeapp.ui.components.rememberReducedMotion()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // The circle is drawn at 22dp but the tap target is the full 48dp minimum (rule 6):
        // completing a task is the most frequent action in the app, and a 22dp target was
        // easy to miss. The check uses onPrimary rather than white, which in dark mode sat
        // on a light accent fill.
        // Completing plays the check first (fill, tick, sparks) and only then marks the task
        // done — a done task leaves these sections, so it would vanish before the motion.
        com.example.timeapp.ui.components.AnimatedCheckCircle(
            checked = done || finishing,
            onClick = {
                if (done) viewModel.toggleDone(entry)
                else if (!finishing) {
                    finishing = true
                    scope.launch { com.example.timeapp.ui.components.afterCheckAnimation(reduced) { viewModel.toggleDone(entry) } }
                }
            },
            contentDescription = if (done) L("Mark %1\$s not done", entry.title) else L("Mark %1\$s done", entry.title),
            size = 24.dp,
        )
        // Tapping the task opens Task Detail — its subtasks, tracked/remaining time and
        // Resume. That screen was previously reachable only from the Board view; from this
        // list, the default Plan view, there was no way in at all.
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onOpen(entry.id) }
                .padding(start = Dimens.s6, top = Dimens.s10, bottom = Dimens.s10),
        ) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (finishing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (finishing) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
            // Pills on one line, the category and time on the next: sharing a Row, the time was
            // squeezed into what the pills left and wrapped mid-time ("9:00 am–10:00 / am").
            val waiting = viewModel.uiState.collectAsState().value.unfinishedPrerequisites(entry).size
            val hasPills = entry.status == EntryStatus.PAUSED || entry.status == EntryStatus.BLOCKED ||
                (waiting > 0 && entry.status != EntryStatus.DONE) || entry.status == EntryStatus.DOING ||
                entry.priority >= Priority.HIGH || entry.recurrence != null
            if (hasPills) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Dimens.s6).horizontalScroll(rememberScrollState())) {
                if (entry.status == EntryStatus.PAUSED) {
                    StatusPill(L("Paused"), TimeThemeTokens.status.warn, TimeThemeTokens.status.warnInk)
                }
                if (entry.status == EntryStatus.BLOCKED) {
                    StatusPill(L("Blocked"), TimeThemeTokens.status.danger, TimeThemeTokens.status.dangerInk)
                }
                if (waiting > 0 && entry.status != EntryStatus.DONE) {
                    StatusPill(L("Waiting on %1\$s", waiting), TimeThemeTokens.status.neutral, TimeThemeTokens.status.neutralInk)
                }
                if (entry.status == EntryStatus.DOING) {
                    // No elapsed figure here: only closed sessions are summed (see
                    // WorkSessionDao), so a running task would always read 0 — the Focus
                    // screen owns the live clock.
                    StatusPill(L("Tracking"), TimeThemeTokens.status.ok, TimeThemeTokens.status.okInk)
                }
                if (entry.priority >= Priority.HIGH) {
                    StatusPill(
                        entry.priority.label(),
                        if (entry.priority == Priority.URGENT) TimeThemeTokens.status.danger else TimeThemeTokens.status.warn,
                        if (entry.priority == Priority.URGENT) TimeThemeTokens.status.dangerInk else TimeThemeTokens.status.warnInk,
                    )
                }
                if (entry.recurrence != null) {
                    StatusPill(entry.recurrence.label(), colors.accS2, colors.accInk)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Dimens.s6)) {
                val whenLine = entry.whenLine()
                entry.category?.let {
                    com.example.timeapp.ui.components.CategoryMark(it.color, it.icon)
                    Text(
                        listOfNotNull(" ${it.name}", whenLine).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } ?: whenLine?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            projectAndTagsLine(entry)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.s4),
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = L("More"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text(L("Open")) }, onClick = { menuExpanded = false; onOpen(entry.id) })
                DropdownMenuItem(text = { Text(L("Edit")) }, onClick = { menuExpanded = false; onEdit(entry.id) })
                if (entry.kind == com.example.timeapp.data.EntryKind.TASK) {
                    DropdownMenuItem(text = { Text(L("Reschedule")) }, onClick = { menuExpanded = false; onReschedule(entry.id) })
                }
                DropdownMenuItem(text = { Text(L("Archive")) }, onClick = { menuExpanded = false; viewModel.archive(entry) })
                DropdownMenuItem(text = { Text(L("Delete")) }, onClick = { menuExpanded = false; confirmingDelete = true })
            }
        }
    }

    if (confirmingDelete) {
        DeleteTaskDialog(entry, viewModel, onDone = { confirmingDelete = false })
    }
}

/** An archived task: title and when it was archived; tap to open it (and restore it there). */
@Composable
private fun ArchivedRow(entry: Entry, onOpen: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(entry.id) }.padding(vertical = Dimens.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        entry.archivedAt?.let {
            Text(
                L("Archived %1\$s", it.atZone(java.time.ZoneId.systemDefault()).format(displayPattern("d MMM"))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, bg: Color, fg: Color) {
    Box(modifier = Modifier.background(bg, Shapes.pill).padding(horizontal = Dimens.s10, vertical = Dimens.s4)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

/** "Portfolio · #android #ui", or null when a task has neither. */
internal fun projectAndTagsLine(entry: Entry): String? {
    val parts = listOfNotNull(entry.project?.name, entry.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" })
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
