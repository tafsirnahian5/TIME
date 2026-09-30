package com.example.timeapp.ui.add

import androidx.compose.foundation.layout.heightIn
import com.example.timeapp.data.L
import com.example.timeapp.data.blockSpans

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.Entry
import com.example.timeapp.data.Priority
import com.example.timeapp.data.SeriesScope
import com.example.timeapp.data.sameRuleAs
import com.example.timeapp.ui.components.SeriesScopeDialog
import com.example.timeapp.ui.components.CategoryPicker
import com.example.timeapp.ui.components.RepeatPicker
import com.example.timeapp.ui.components.rememberNotificationPermissionRequest
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.richtext.RichTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val dueDateFormatter get() = displayPattern("EEE d MMM")

/**
 * B2 — New/Edit task. Progressive disclosure: title first, details revealed on demand.
 * When [existingEntry] is non-null this is an edit: title/category/due-date-and-block
 * prefill from it, from the structured [Entry.dueOn] field (falling back to [Entry.startAt]
 * for entries scheduled before dueOn existed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    onBack: () -> Unit,
    onSave: (title: String, categoryId: Long?, dueOn: LocalDate?, blockStart: java.time.LocalTime?, estimateMinutes: Int?, description: String?, remind: Boolean, recurrence: com.example.timeapp.data.Recurrence?, priority: Priority, scope: SeriesScope, projectId: Long?, tags: List<String>, timing: com.example.timeapp.data.ReminderTiming) -> Unit,
    existingEntry: Entry? = null,
    initialRemind: Boolean = false,
    remindersMuted: Boolean = false,
    categories: List<com.example.timeapp.data.Category> = emptyList(),
    onCreateCategory: (String, Long, (Long) -> Unit) -> Unit = { _, _, _ -> },
    projects: List<com.example.timeapp.data.Category> = emptyList(),
    onCreateProject: (String, Long, (Long) -> Unit) -> Unit = { _, _, _ -> },
    tagSuggestions: List<String> = emptyList(),
    initialTiming: com.example.timeapp.data.ReminderTiming? = null,
) {
    var timing by remember(initialTiming) { mutableStateOf(initialTiming ?: com.example.timeapp.data.ReminderTiming.TASK_DEFAULT) }
    val colors = TimeThemeTokens.colors
    var title by remember { mutableStateOf(existingEntry?.title ?: "") }
    // Priority, project, tags and notes sit behind "More options": the form opened as one long
    // page of fifteen controls. Open straight away when editing a task that uses any of them.
    var showMore by remember {
        mutableStateOf(
            existingEntry != null && (existingEntry.priority != Priority.NORMAL || existingEntry.projectId != null ||
                existingEntry.tags.isNotEmpty() || !existingEntry.description.isNullOrBlank()),
        )
    }
    // No category until one is picked — every new task used to be filed under the first one ("Coding").
    var categoryId by remember(categories) { mutableStateOf(existingEntry?.categoryId) }
    var priority by remember { mutableStateOf(existingEntry?.priority ?: Priority.NORMAL) }
    var projectId by remember { mutableStateOf(existingEntry?.projectId) }
    var tags by remember { mutableStateOf(existingEntry?.tags.orEmpty()) }
    var description by remember { mutableStateOf(existingEntry?.description ?: "") }
    var reserveBlock by remember { mutableStateOf(existingEntry?.startAt != null) }
    var remind by remember(initialRemind) { mutableStateOf(initialRemind) }
    var recurrence by remember { mutableStateOf(existingEntry?.recurrence) }
    val askNotificationPermission = rememberNotificationPermissionRequest()
    var dueDate by remember {
        // The due date only — this used to fall back to the booking's day, so saving an edit
        // gave booked time a due date it never had.
        mutableStateOf(existingEntry?.dueOn)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var estimateMinutesText by remember { mutableStateOf(existingEntry?.estimateMs?.let { (it / 60_000).toString() } ?: "") }
    var showEstimatePicker by remember { mutableStateOf(false) }
    // Booked time used to be fixed at 09:00 on the due date, with no way to choose.
    var blockStart by remember { mutableStateOf(java.time.LocalTime.of(9, 0)) }
    var pickingBlockStart by remember { mutableStateOf(false) }
    val todayDate = LocalDate.now()
    var askScope by remember { mutableStateOf(false) }
    // Editor draft (Phase 15): typed text survives leaving the form or the app being killed.
    val draft = com.example.timeapp.ui.components.rememberDraft("task", enabled = existingEntry == null, fields = mapOf("title" to title, "description" to description, "estimate" to estimateMinutesText)) { d -> title = d["title"].orEmpty(); description = d["description"].orEmpty(); estimateMinutesText = d["estimate"].orEmpty() }
    val save = { scope: SeriesScope ->
        draft.clear(); onSave(
            title, categoryId, dueDate, blockStart.takeIf { reserveBlock },
            estimateMinutesText.toIntOrNull(), description.ifBlank { null },
            remind && dueDate != null, recurrence.takeIf { dueDate != null }, priority, scope,
            projectId, tags, timing,
        )
    }

    Scaffold(
        topBar = {
            TimeTopBar(
                title = if (existingEntry != null) L("Edit task") else L("New task"),
                onBack = onBack,
                actions = {
                    TextButton(
                        // Editing one occurrence of a repeating task asks how far the edit reaches.
                        onClick = { if (existingEntry?.seriesId != null) askScope = true else save(SeriesScope.ONLY_THIS) },
                        enabled = title.isNotBlank(),
                    ) { Text(L("Save")) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                // These forms are taller than a phone screen once Details is expanded,
                // the notes Preview is showing, or a large display size is set. Without
                // this they were a plain Column: everything past the fold was clipped and
                // simply unreachable, including the reminder switch. imePadding keeps the
                // focused field above the keyboard instead of behind it.
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.s16),
        ) {
            com.example.timeapp.ui.components.DraftBanner(draft, Modifier.padding(top = Dimens.s8))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(L("Title")) },
                placeholder = { Text(L("Fix mobile spacing")) },
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s6, bottom = Dimens.s16),
                singleLine = true,
            )
            SectionHeading(L("When is it due?"))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.s8),
                modifier = Modifier.padding(bottom = Dimens.s12),
            ) {
                val custom = dueDate?.takeIf { it != todayDate && it != todayDate.plusDays(1) }
                ChoiceChip(L("No date"), dueDate == null) { dueDate = null }
                ChoiceChip(L("Today"), dueDate == todayDate) { dueDate = todayDate }
                ChoiceChip(L("Tomorrow"), dueDate == todayDate.plusDays(1)) { dueDate = todayDate.plusDays(1) }
                ChoiceChip(custom?.format(dueDateFormatter) ?: L("Pick a date…"), custom != null, icon = Icons.Rounded.CalendarMonth) { showDatePicker = true }
            }
            SectionHeading(L("How long will it take?"))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.s8),
                modifier = Modifier.padding(bottom = Dimens.s12),
            ) {
                val minutes = estimateMinutesText.toIntOrNull()?.takeIf { it > 0 }
                val quick = listOf(15, 30, 60, 120)
                ChoiceChip(L("Not sure"), minutes == null) { estimateMinutesText = "" }
                quick.forEach { q -> ChoiceChip(com.example.timeapp.ui.components.durationLabel(q), minutes == q) { estimateMinutesText = q.toString() } }
                val other = minutes?.takeIf { it !in quick }
                ChoiceChip(other?.let { com.example.timeapp.ui.components.durationLabel(it) } ?: L("Other…"), other != null, icon = Icons.Rounded.Schedule) { showEstimatePicker = true }
            }
            SectionHeading(L("Work category"))
            CategoryPicker(
                categories = categories,
                selectedId = categoryId,
                onSelect = { categoryId = it },
                onCreate = { name, color -> onCreateCategory(name, color) { newId -> categoryId = newId } },
                modifier = Modifier.padding(bottom = Dimens.s4),
            )
            if (existingEntry != null) {
                // An edit never touches booked time (it used to be rebuilt from the due date,
                // moving or dropping the booking) — it's shown here and changed on the task's page.
                val spans = existingEntry.blockSpans()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.s12)
                        .background(colors.accS, Shapes.lg)
                        .padding(Dimens.s16),
                ) {
                    Text(L("Booked time"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            spans.isEmpty() -> L("None yet. Book time from the task's page — anything it overlaps is named there.")
                            spans.size == 1 -> L("%1\$s. Change it from the task's page.", com.example.timeapp.data.bookingLabel(spans[0].first, spans[0].second))
                            else -> L("%1\$s and %2\$s more. Change them from the task's page.", com.example.timeapp.data.bookingLabel(spans[0].first, spans[0].second), spans.size - 1)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.s12)
                        .background(colors.accS, Shapes.lg)
                        .padding(Dimens.s16),
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(L("Book time for it"), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                L("A due date says when it must be done; booked time says when you'll do it."),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = reserveBlock,
                            onCheckedChange = { on ->
                                reserveBlock = on
                                // Today, start at the next full hour rather than a 09:00 already gone.
                                if (on && dueDate == todayDate) {
                                    // The next full hour, but early enough that the booking still ends
                                    // today — at 22:10 "next hour" gave 23:00–01:00 into tomorrow.
                                    val length = (estimateMinutesText.toIntOrNull()?.takeIf { it > 0 } ?: 45).toLong()
                                    val next = java.time.LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
                                    val latest = java.time.LocalTime.MAX.minusMinutes(length).withSecond(0).withNano(0)
                                    blockStart = if (next.hour == 0 || next.isAfter(latest)) latest.withMinute(latest.minute - latest.minute % 15) else next
                                }
                            },
                        )
                    }
                    if (reserveBlock) {
                        val due = dueDate
                        if (due == null) {
                            Text(
                                L("Pick a due date above — the time is booked on that day."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Dimens.s8),
                            )
                        } else {
                            val length = estimateMinutesText.toIntOrNull()?.takeIf { it > 0 } ?: 45
                            val zone = ZoneId.systemDefault()
                            val start = due.atTime(blockStart).atZone(zone).toInstant()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8).heightIn(min = Dimens.touchMin)
                                    .clip(Shapes.md).clickable { pickingBlockStart = true }.padding(horizontal = Dimens.s4),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Text(L("Starts at"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(blockStart.format(displayPattern("HH:mm")), style = MaterialTheme.typography.titleMedium, color = colors.acc)
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = colors.acc, modifier = Modifier.padding(start = Dimens.s8).size(18.dp))
                            }
                            if (start.isBefore(java.time.Instant.now())) {
                                // Otherwise a booking for 09:00 today, made at 19:00, saved as if it lay ahead.
                                Text(
                                    L("That time has already passed today — pick a later one, or book it for another day."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TimeThemeTokens.status.warnInk,
                                )
                            }
                            Text(
                                if (estimateMinutesText.toIntOrNull()?.takeIf { it > 0 } != null) {
                                    L("Books %1\$s — as long as your estimate.", com.example.timeapp.data.bookingLabel(start, start.plusSeconds(length * 60L)))
                                } else {
                                    L("Books %1\$s — 45 min, since there's no estimate.", com.example.timeapp.data.bookingLabel(start, start.plusSeconds(length * 60L)))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.s12)
                    .background(colors.accS, Shapes.lg)
                    .padding(Dimens.s16),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(L("Remind me"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            dueDate == null -> L("Pick a due date above — a reminder needs a day to fire on.")
                            remindersMuted -> L("%1\$s — but schedule reminders are muted in Settings › Notifications, so this won't show.", timing.label())
                            // Say so when the chosen time has already gone — it used to arm
                            // silently and fire the moment the task was saved.
                            remind && timing.at(dueDate, null)?.isBefore(java.time.Instant.now()) == true ->
                                L("%1\$s has already passed — pick a later time, or it will show as soon as you save.", timing.label())
                            // "Notifies you: 9:00 am on the day" — the label already carries its own "on the
                            // day"/"before", so no "at" (in Bangla the extra -এ ending read "দিনেই-এ").
                            else -> L("Notifies you: %1\$s.", timing.label())
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = remind && dueDate != null,
                    onCheckedChange = { remind = it; if (it) askNotificationPermission() },
                    enabled = dueDate != null,
                )
            }
            if (remind && dueDate != null) {
                com.example.timeapp.ui.components.ReminderTimingRow(
                    com.example.timeapp.ui.components.ReminderTimingKind.TASK, timing, { timing = it },
                    modifier = Modifier.padding(horizontal = Dimens.s16),
                )
            }
            RepeatPicker(
                value = recurrence,
                onValueChange = { recurrence = it },
                enabled = dueDate != null,
                anchorDay = dueDate?.dayOfMonth,
                anchorWeekday = dueDate?.dayOfWeek,
                modifier = Modifier.padding(top = Dimens.s16),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8).heightIn(min = Dimens.touchMin)
                    .clip(Shapes.md).clickable { showMore = !showMore }.padding(vertical = Dimens.s8),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(L("More options"), style = MaterialTheme.typography.titleSmall, color = colors.acc)
                    if (!showMore) {
                        Text(L("Priority, project, tags and notes"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(if (showMore) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = colors.acc)
            }
            if (showMore) {
                SectionHeading(L("Priority"))
                com.example.timeapp.ui.components.SegmentedToggle(
                    options = Priority.entries.map { it.label() },
                    selectedIndex = Priority.entries.indexOf(priority),
                    onSelect = { priority = Priority.entries[it] },
                    modifier = Modifier.padding(bottom = Dimens.s16),
                )
                SectionHeading(L("Project"))
                CategoryPicker(
                    categories = projects,
                    selectedId = projectId,
                    // Tapping the chosen project again clears it: most tasks belong to none.
                    onSelect = { projectId = if (projectId == it) null else it },
                    onCreate = { name, color -> onCreateProject(name, color) { newId -> projectId = newId } },
                    createTitle = L("New project"),
                    modifier = Modifier.padding(bottom = Dimens.s12),
                )
                SectionHeading(L("Tags"))
                com.example.timeapp.ui.components.TagEditor(
                    tags = tags,
                    onTagsChange = { tags = it },
                    suggestions = tagSuggestions,
                    modifier = Modifier.padding(bottom = Dimens.s16),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TimeThemeTokens.colors.card, Shapes.lg)
                        .padding(Dimens.s16),
                ) {
                    Text(L("Description"), style = MaterialTheme.typography.bodySmall)
                    RichTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = L("Notes for this task…"),
                        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8),
                        minLines = 3,
                    )
                }
            }
            Row(modifier = Modifier.padding(vertical = Dimens.s14)) {
                Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(
                    L("Saving a task never starts a timer."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.s8),
                )
            }
        }
    }

    if (askScope && existingEntry != null) {
        SeriesScopeDialog(
            title = L("Save repeating task"),
            confirmLabel = L("Save"),
            onlyThisEnabled = recurrence.takeIf { dueDate != null }.sameRuleAs(existingEntry.recurrence),
            onConfirm = { askScope = false; save(it) },
            onDismiss = { askScope = false },
        )
    }

    if (showDatePicker) {
        val state = androidx.compose.runtime.remember { androidx.compose.material3.DatePickerState(
            locale = com.example.timeapp.ui.components.pickerLocale(),
            initialSelectedDateMillis = dueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        ) }
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        dueDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(L("OK")) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(L("Cancel")) } },
        ) { DatePicker(state = state) }
    }

    if (showEstimatePicker) {
        com.example.timeapp.ui.components.DurationPickerPrompt(estimateMinutesText.toIntOrNull()) { picked ->
            showEstimatePicker = false
            picked?.let { estimateMinutesText = if (it > 0) it.toString() else "" }
        }
    }
    if (pickingBlockStart) {
        com.example.timeapp.ui.components.TimePickerPrompt(blockStart, title = L("Start the booked time at")) { t ->
            pickingBlockStart = false
            t?.let { blockStart = it }
        }
    }
}

/** Display label for a [Priority]; the enum name itself is storage, not copy. */
internal fun Priority.label(): String = when (this) {
    Priority.LOW -> L("Low")
    Priority.NORMAL -> L("Normal")
    Priority.HIGH -> L("High")
    Priority.URGENT -> L("Urgent")
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s8),
    )
}

/** One choice in a row of chips — selected is filled, the rest outlined. Material chips already
 * take a 48dp touch area, so the chip itself stays its normal size (forcing 48dp made them chunky). */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val colors = TimeThemeTokens.colors
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        shape = Shapes.pill,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.accC,
            selectedLabelColor = colors.accInk,
            selectedLeadingIconColor = colors.accInk,
        ),
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = TimeThemeTokens.status.controlOutline,
            selectedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}
