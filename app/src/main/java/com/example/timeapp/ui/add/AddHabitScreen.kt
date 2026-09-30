package com.example.timeapp.ui.add

import com.example.timeapp.data.L
import com.example.timeapp.data.chipLabel

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.example.timeapp.data.Category
import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitSchedule
import com.example.timeapp.data.HabitScheduleKind
import com.example.timeapp.ui.components.CategoryPicker
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.components.rememberNotificationPermissionRequest
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val timeFormat get() = displayPattern("HH:mm")

/**
 * New/Edit habit. A name is enough; how often defaults to every day. Like the other forms it
 * scrolls and keeps the focused field above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitScreen(
    onBack: () -> Unit,
    onSave: (Habit) -> Unit,
    existing: Habit? = null,
    remindersMuted: Boolean = false,
    categories: List<Category> = emptyList(),
    onCreateCategory: (String, Long, (Long) -> Unit) -> Unit = { _, _, _ -> },
) {
    val colors = TimeThemeTokens.colors
    var name by remember { mutableStateOf(existing?.name ?: "") }
    // Editor draft (Phase 15): typed text survives leaving the form or the app being killed.
    val draft = com.example.timeapp.ui.components.rememberDraft("habit", enabled = existing == null, fields = mapOf("name" to name)) { d -> name = d["name"].orEmpty() }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var kind by remember { mutableStateOf(existing?.schedule?.kind ?: HabitScheduleKind.DAILY) }
    var weekdays by remember {
        mutableStateOf(existing?.schedule?.weekdays?.takeIf { it.isNotEmpty() } ?: setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
    }
    var timesPerWeek by remember { mutableStateOf(existing?.schedule?.timesPerWeek ?: 3) }
    var everyNDays by remember { mutableStateOf(existing?.schedule?.everyNDays ?: 2) }
    var timesPerMonth by remember { mutableStateOf(existing?.schedule?.timesPerMonth ?: 10) }
    var measured by remember { mutableStateOf(existing?.target != null) }
    var targetText by remember { mutableStateOf(existing?.target?.amount?.let { com.example.timeapp.data.formatAmount(it) } ?: "") }
    var unit by remember { mutableStateOf(existing?.target?.unit ?: "") }
    var atMost by remember { mutableStateOf(existing?.target?.atMost ?: false) }
    var timed by remember { mutableStateOf(existing?.timerMinutes != null) }
    var timerMinutes by remember { mutableStateOf(existing?.timerMinutes ?: 10) }
    var remind by remember { mutableStateOf(existing?.remindAt != null) }
    var remindAt by remember { mutableStateOf(existing?.remindAt ?: LocalTime.of(8, 0)) }
    var pickingTime by remember { mutableStateOf(false) }
    val askNotificationPermission = rememberNotificationPermissionRequest()

    val schedule = when (kind) {
        HabitScheduleKind.DAILY -> HabitSchedule(HabitScheduleKind.DAILY)
        HabitScheduleKind.WEEKDAYS -> HabitSchedule(HabitScheduleKind.WEEKDAYS, weekdays = weekdays)
        HabitScheduleKind.TIMES_PER_WEEK -> HabitSchedule(HabitScheduleKind.TIMES_PER_WEEK, timesPerWeek = timesPerWeek)
        // Counted from the habit's first day (or today for a new one), kept when editing.
        HabitScheduleKind.EVERY_N_DAYS -> HabitSchedule(
            HabitScheduleKind.EVERY_N_DAYS, everyNDays = everyNDays,
            anchor = existing?.schedule?.anchor ?: existing?.startedOn ?: LocalDate.now(),
        )
        HabitScheduleKind.TIMES_PER_MONTH -> HabitSchedule(HabitScheduleKind.TIMES_PER_MONTH, timesPerMonth = timesPerMonth)
    }
    val targetAmount = targetText.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
    val valid = name.isNotBlank() && (kind != HabitScheduleKind.WEEKDAYS || weekdays.isNotEmpty()) && (!measured || targetAmount != null)

    Scaffold(
        topBar = {
            TimeTopBar(
                title = if (existing != null) L("Edit habit") else L("New habit"),
                onBack = onBack,
                actions = {
                    TextButton(
                        enabled = valid,
                        onClick = {
                            draft.clear(); onSave(
                                (existing ?: Habit(id = 0, name = "", schedule = schedule, startedOn = LocalDate.now())).copy(
                                    name = name.trim(),
                                    schedule = schedule,
                                    categoryId = categoryId,
                                    remindAt = remindAt.takeIf { remind },
                                    target = if (measured && targetAmount != null) com.example.timeapp.data.HabitTarget(targetAmount, unit.trim(), atMost) else null,
                                    timerMinutes = timerMinutes.takeIf { timed },
                                ),
                            )
                        },
                    ) { Text(L("Save")) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Dimens.s16),
        ) {
            com.example.timeapp.ui.components.DraftBanner(draft, Modifier.padding(top = Dimens.s8))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(L("Habit")) },
                placeholder = { Text(L("Read 20 pages")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s6, bottom = Dimens.s16),
            )

            Text(L("How often"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = Dimens.s10))
            listOf(
                listOf(HabitScheduleKind.DAILY to L("Every day"), HabitScheduleKind.WEEKDAYS to L("Set days"), HabitScheduleKind.EVERY_N_DAYS to L("Every N days")),
                listOf(HabitScheduleKind.TIMES_PER_WEEK to L("Per week"), HabitScheduleKind.TIMES_PER_MONTH to L("Per month")),
            ).forEach { rowOptions ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s6).background(colors.card, Shapes.pill)) {
                rowOptions.forEach { (option, label) ->
                    val selected = option == kind
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Dimens.touchMin)
                            .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
                            .semantics { this.selected = selected }.clickable { kind = option }
                            .wrapContentHeight(),
                    )
                }
            }
            }
            when (kind) {
                HabitScheduleKind.WEEKDAYS -> Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s12)) {
                    DayOfWeek.entries.forEach { day ->
                        val on = day in weekdays
                        Text(
                            day.chipLabel(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = Dimens.touchMin)
                                .background(if (on) colors.accC else Color.Transparent, Shapes.pill)
                                .semantics { this.selected = on }.clickable { weekdays = if (on) weekdays - day else weekdays + day }
                                .wrapContentHeight(),
                        )
                    }
                }
                HabitScheduleKind.TIMES_PER_WEEK -> Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.s12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (timesPerWeek == 1) L("Once a week, any day") else L("%1\$s times a week, any days", timesPerWeek),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { timesPerWeek-- }, enabled = timesPerWeek > 1) {
                        Icon(Icons.Rounded.Remove, contentDescription = L("Fewer"))
                    }
                    IconButton(onClick = { timesPerWeek++ }, enabled = timesPerWeek < 6) {
                        Icon(Icons.Rounded.Add, contentDescription = L("More"))
                    }
                }
                HabitScheduleKind.EVERY_N_DAYS -> Stepper(
                if (everyNDays == 1) L("Every day") else L("Every %1\$s days", everyNDays),
                onLess = { everyNDays-- }, lessEnabled = everyNDays > 2,
                onMore = { everyNDays++ }, moreEnabled = everyNDays < 60,
            )
                HabitScheduleKind.TIMES_PER_MONTH -> Stepper(
                L("%1\$s times a month, any days", timesPerMonth),
                onLess = { timesPerMonth-- }, lessEnabled = timesPerMonth > 1,
                onMore = { timesPerMonth++ }, moreEnabled = timesPerMonth < 28,
            )
                HabitScheduleKind.DAILY -> Unit
            }
            Text(
                when (kind) {
                    HabitScheduleKind.DAILY -> L("Shows on Today every day.")
                    HabitScheduleKind.WEEKDAYS ->
                        if (weekdays.isEmpty()) L("Pick at least one day.") else L("Shows on Today on the days you pick; other days never count.")
                    HabitScheduleKind.TIMES_PER_WEEK -> L("Shows on Today until this week's target is met. The streak counts weeks.")
                    HabitScheduleKind.EVERY_N_DAYS -> L("Due every %1\$s days from its first day; the days between never count.", everyNDays)
                    HabitScheduleKind.TIMES_PER_MONTH -> L("Shows on Today until this month's target is met. The streak counts months.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s8, bottom = Dimens.s20),
            )

            // Measurable habits (Loop, Track & Graph): record an amount; the day counts when it
            // meets the target.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(L("Measure it"), style = MaterialTheme.typography.bodyLarge)
                    Text(L("Record an amount each day — pages, glasses, minutes."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = measured, onCheckedChange = { measured = it })
            }
            if (measured) {
                com.example.timeapp.ui.components.SegmentedToggle(
                    options = listOf(L("At least"), L("At most")),
                    selectedIndex = if (atMost) 1 else 0,
                    onSelect = { atMost = it == 1 },
                    modifier = Modifier.padding(bottom = Dimens.s8),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s10), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) },
                        label = { Text(L("Target")) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unit, onValueChange = { unit = it.take(16) }, label = { Text(L("Unit")) },
                        placeholder = { Text(L("pages")) }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    if (atMost) L("A day counts when it stays at or under the target — logging 0 counts too.")
                    else L("A day counts when it reaches the target; less is kept, but doesn't count."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.s6, bottom = Dimens.s20),
                )
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Dimens.s12))
            }

            // Timed habits ("Meditate 10 min"): a utility timer — never counted as work — that
            // checks the day off when it rings.
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s8), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(L("Timed"), style = MaterialTheme.typography.bodyLarge)
                    Text(L("Start a timer from Today; when it rings, the day is checked off."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = timed, onCheckedChange = { timed = it })
            }
            if (timed) {
                Stepper(L("%1\$s minutes", timerMinutes), onLess = { timerMinutes -= 5 }, lessEnabled = timerMinutes > 5, onMore = { timerMinutes += 5 }, moreEnabled = timerMinutes < 180)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Dimens.s16))
            }

            Text(L("Category"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = Dimens.s10))
            CategoryPicker(
                categories = categories,
                selectedId = categoryId,
                onSelect = { categoryId = if (categoryId == it) null else it },
                onCreate = { n, color -> onCreateCategory(n, color) { newId -> categoryId = newId } },
                modifier = Modifier.padding(bottom = Dimens.s20),
            )

            Row(
                modifier = Modifier.fillMaxWidth().background(colors.accS, Shapes.lg).padding(Dimens.s16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(L("Remind me"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            !remind -> L("A notification on the days this habit is due.")
                            remindersMuted -> L("At %1\$s — but schedule reminders are muted in Settings › Notifications, so this won't show.", remindAt.format(timeFormat))
                            else -> L("At %1\$s on due days — skipped once it's checked off.", remindAt.format(timeFormat))
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (remind) {
                        TextButton(onClick = { pickingTime = true }, modifier = Modifier.padding(top = Dimens.s4)) {
                            Text(L("Change time"))
                        }
                    }
                }
                Switch(checked = remind, onCheckedChange = { remind = it; if (it) askNotificationPermission() })
            }
            Text(
                L("Rest days: mark a day as rest from the habit's day strip. A rest day never breaks a streak."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Dimens.s16),
            )
        }
    }

    if (pickingTime) {
        com.example.timeapp.ui.components.TimePickerPrompt(remindAt, title = L("Remind me at")) { t ->
            pickingTime = false
            t?.let { remindAt = it }
        }
    }
}

@Composable
private fun Stepper(label: String, onLess: () -> Unit, lessEnabled: Boolean, onMore: () -> Unit, moreEnabled: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s12), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onLess, enabled = lessEnabled) { Icon(Icons.Rounded.Remove, contentDescription = L("Fewer")) }
        IconButton(onClick = onMore, enabled = moreEnabled) { Icon(Icons.Rounded.Add, contentDescription = L("More")) }
    }
}
