package com.example.timeapp.ui.add

import com.example.timeapp.data.L
import com.example.timeapp.data.sameRuleAs

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import com.example.timeapp.ui.components.DatePickerPrompt
import com.example.timeapp.ui.components.TimePickerPrompt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.timeapp.ui.components.TimeTopBar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val dateFmt get() = displayPattern("MMM d, yyyy")
private val timeFmt get() = displayPattern("HH:mm")

/**
 * B2b — New schedule block: a distinct flow from Add Task (see AddSheet.kt's RecordType).
 * A block can stand alone with no task attached — `EntryKind.EVENT`, matching the PDF's
 * nullable-taskId schedule block. Still goes through the same conflict-checked commit path
 * as tasks (proposeSchedule), so an overlap still surfaces C3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScheduleBlockScreen(
    onBack: () -> Unit,
    onSave: (title: String, start: Instant, end: Instant, remind: Boolean, recurrence: com.example.timeapp.data.Recurrence?, fixed: Boolean, timing: com.example.timeapp.data.ReminderTiming, scope: com.example.timeapp.data.SeriesScope) -> Unit,
    remindersMuted: Boolean = false,
    initialStart: java.time.LocalDateTime? = null,
    /** Non-null: edit this booking. Booked time had no edit form at all — its Edit opened the
     * task form, which rebuilt the time from a due date and moved or dropped the booking. */
    existing: com.example.timeapp.data.Entry? = null,
    initialRemind: Boolean = false,
    initialTiming: com.example.timeapp.data.ReminderTiming? = null,
    /** What else is booked in a range, other than [existing] — named before saving an edit. */
    overlapFor: (Instant, Instant) -> String? = { _, _ -> null },
) {
    val colors = TimeThemeTokens.colors
    val zone = ZoneId.systemDefault()
    var title by remember { mutableStateOf(existing?.title ?: "") }
    // Editor draft (Phase 15): typed text survives leaving the form or the app being killed.
    // New bookings only — for an edit the record is the saved version.
    val draft = com.example.timeapp.ui.components.rememberDraft("block", enabled = existing == null, fields = mapOf("title" to title)) { d -> title = d["title"].orEmpty() }
    // A tapped hour on the Week/Day grid arrives as [initialStart]; an hour-long block from it.
    val existingStart = existing?.startAt?.atZone(zone)?.toLocalDateTime()
    val defaultStart = existingStart ?: initialStart ?: LocalDate.now().atTime(9, 0)
    val defaultEnd = existing?.endAt?.atZone(zone)?.toLocalDateTime()?.takeIf { it.isAfter(defaultStart) } ?: defaultStart.plusHours(1)
    var startDate by remember { mutableStateOf(defaultStart.toLocalDate()) }
    var startTime by remember { mutableStateOf(defaultStart.toLocalTime()) }
    var endDate by remember { mutableStateOf(defaultEnd.toLocalDate()) }
    var endTime by remember { mutableStateOf(defaultEnd.toLocalTime()) }
    var editing by remember { mutableStateOf<String?>(null) } // "startDate" | "startTime" | "endDate" | "endTime"
    var remind by remember { mutableStateOf(initialRemind) }
    var recurrence by remember { mutableStateOf(existing?.recurrence) }
    var askScope by remember { mutableStateOf(false) }
    // The PDF: "Fixed blocks are locked by the user. Flexible blocks can be moved or split
    // through an explicit action." A standalone block is usually an appointment, so Fixed.
    var fixed by remember { mutableStateOf(existing?.isFixed ?: true) }
    var timing by remember { mutableStateOf(initialTiming ?: com.example.timeapp.data.ReminderTiming.EVENT_DEFAULT) }
    val askNotificationPermission = com.example.timeapp.ui.components.rememberNotificationPermissionRequest()
    val start = startDate.atTime(startTime).atZone(zone).toInstant()
    val end = endDate.atTime(endTime).atZone(zone).toInstant()
    // "A new block requires end after start" — this used to save whatever was entered.
    val rangeError = when {
        end.isBefore(start) -> L("Ends before it starts.")
        end == start -> L("Starts and ends at the same moment — give it some length.")
        else -> null
    }
    // A new booking goes through the C3 conflict dialog on save; an edit names the overlap here
    // and saving is the confirmation (rule 4: surfaced, never applied silently).
    val overlap = if (existing != null && rangeError == null) overlapFor(start, end) else null
    fun save(scope: com.example.timeapp.data.SeriesScope) {
        draft.clear()
        onSave(title, start, end, remind, recurrence, fixed, timing, scope)
    }

    /** Moving the start carries the end with it, keeping the block's length (an hour when the
     * current range isn't valid) — so changing the day can't leave the end behind. */
    fun moveStart(date: LocalDate = startDate, time: java.time.LocalTime = startTime) {
        val length = java.time.Duration.between(startDate.atTime(startTime), endDate.atTime(endTime))
            .takeIf { !it.isNegative && !it.isZero } ?: java.time.Duration.ofHours(1)
        val newEnd = date.atTime(time).plus(length)
        startDate = date
        startTime = time
        endDate = newEnd.toLocalDate()
        endTime = newEnd.toLocalTime()
    }

    Scaffold(
        topBar = {
            TimeTopBar(
                title = if (existing != null) L("Edit booked time") else L("Book time"),
                onBack = onBack,
                actions = {
                    TextButton(
                        onClick = { if (existing?.seriesId != null) askScope = true else save(com.example.timeapp.data.SeriesScope.ONLY_THIS) },
                        enabled = title.isNotBlank() && rangeError == null,
                    ) { Text(if (overlap != null) L("Save anyway") else L("Save")) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)
                // These forms are taller than a phone screen once Details is expanded,
                // the notes Preview is showing, or a large display size is set. Without
                // this they were a plain Column: everything past the fold was clipped and
                // simply unreachable, including the reminder switch. imePadding keeps the
                // focused field above the keyboard instead of behind it.
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.s16)) {
            com.example.timeapp.ui.components.DraftBanner(draft, Modifier.padding(top = Dimens.s8))
            Text(L("TITLE"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s8, bottom = Dimens.s6))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(L("Study session")) },
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20),
                singleLine = true,
            )
            Text(L("STARTS"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s6))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20)) {
                FieldBox(startDate.format(dateFmt), Modifier.weight(1f).padding(end = Dimens.s10)) { editing = "startDate" }
                FieldBox(startTime.format(timeFmt), Modifier) { editing = "startTime" }
            }
            Text(L("ENDS"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s6))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = if (rangeError == null) Dimens.s20 else Dimens.s6)) {
                FieldBox(endDate.format(dateFmt), Modifier.weight(1f).padding(end = Dimens.s10)) { editing = "endDate" }
                FieldBox(endTime.format(timeFmt), Modifier) { editing = "endTime" }
            }
            rangeError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = Dimens.s20),
                )
            }
            overlap?.let {
                Text(
                    L("Overlaps “%1\$s”. Saving keeps both — nothing else moves.", it),
                    style = MaterialTheme.typography.bodySmall,
                    color = TimeThemeTokens.status.warnInk,
                    modifier = Modifier.padding(top = Dimens.s6, bottom = Dimens.s20),
                )
            }
            Text(L("KIND"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s6))
            com.example.timeapp.ui.components.SegmentedToggle(
                options = listOf(L("Fixed"), L("Flexible")),
                selectedIndex = if (fixed) 0 else 1,
                onSelect = { fixed = it == 0 },
            )
            Text(
                if (fixed) L("Locked in place — rescheduling asks before moving it.")
                else L("Can be moved when you reschedule."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s6, bottom = Dimens.s20),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20).background(colors.accS, Shapes.lg).padding(Dimens.s14),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(L("Remind me"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (remindersMuted) L("%1\$s — but schedule reminders are muted in Settings › Notifications, so this won't show.", timing.label())
                        else L("A notification: %1\$s.", (timing.label().replaceFirstChar { it.lowercase() })),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                androidx.compose.material3.Switch(checked = remind, onCheckedChange = { remind = it; if (it) askNotificationPermission() })
            }
            if (remind) {
                com.example.timeapp.ui.components.ReminderTimingRow(
                    com.example.timeapp.ui.components.ReminderTimingKind.EVENT, timing, { timing = it },
                    modifier = Modifier.padding(start = Dimens.s14, end = Dimens.s14, bottom = Dimens.s12),
                )
            }
            // A repeating block keeps its time of day and length on every occurrence — the
            // "Gym, Mon/Wed/Fri 07:00" shape.
            com.example.timeapp.ui.components.RepeatPicker(
                value = recurrence,
                onValueChange = { recurrence = it },
                anchorDay = startDate.dayOfMonth,
                anchorWeekday = startDate.dayOfWeek,
                modifier = Modifier.padding(bottom = Dimens.s20),
            )
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.accS, Shapes.lg).padding(Dimens.s14),
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.acc)
                Text(
                    if (existing != null) L("If the new time overlaps something else on your calendar, it's named under the times before you save.")
                    else L("Booked time can stand alone — no task is needed. If it overlaps something else on your calendar, you'll be asked what to do."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.s10),
                )
            }
        }
    }

    if (askScope && existing != null) {
        com.example.timeapp.ui.components.SeriesScopeDialog(
            title = L("Save repeating booked time"),
            confirmLabel = L("Save"),
            onlyThisEnabled = recurrence.sameRuleAs(existing.recurrence),
            onConfirm = { askScope = false; save(it) },
            onDismiss = { askScope = false },
        )
    }

    when (editing) {
        "startDate" -> DatePickerPrompt(startDate) { editing = null; it?.let { d -> moveStart(date = d) } }
        "endDate" -> DatePickerPrompt(endDate) { editing = null; it?.let { d -> endDate = d } }
        "startTime" -> TimePickerPrompt(startTime) { editing = null; it?.let { t -> moveStart(time = t) } }
        "endTime" -> TimePickerPrompt(endTime) { editing = null; it?.let { t -> endTime = t } }
        else -> Unit
    }
}

@Composable
private fun FieldBox(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = modifier
            .background(TimeThemeTokens.colors.card, Shapes.sm)
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.s14, horizontal = Dimens.s12),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.acc)
    }
}
