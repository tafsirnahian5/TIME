package com.example.timeapp.ui.add

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import com.example.timeapp.data.sameRuleAs
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
import androidx.compose.material.icons.rounded.Notifications
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
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTakaInput
import com.example.timeapp.data.Wallet
import com.example.timeapp.ui.components.CategoryPicker
import com.example.timeapp.ui.components.ChipRow
import com.example.timeapp.ui.components.RepeatPicker
import com.example.timeapp.ui.components.rememberNotificationPermissionRequest
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.richtext.RichTextField
import com.example.timeapp.ui.components.InlineAmountInput
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
 * B3 — New/Edit planned cost: never posts a transaction; wallet is a suggestion only.
 * When [existingPlan] is non-null this is an edit, prefilling title/amount/category/due
 * date from it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCostScreen(
    onBack: () -> Unit,
    onSave: (title: String, amountTaka: String, walletId: Long?, categoryId: Long?, dueOn: LocalDate?, note: String?, remind: Boolean, recurrence: com.example.timeapp.data.Recurrence?, scope: com.example.timeapp.data.SeriesScope, timing: com.example.timeapp.data.ReminderTiming, links: com.example.timeapp.data.MoneyLinks) -> Unit,
    wallets: List<Wallet>,
    existingPlan: MoneyRecord? = null,
    initialRemind: Boolean = false,
    remindersMuted: Boolean = false,
    categories: List<com.example.timeapp.data.Category> = emptyList(),
    onCreateCategory: (String, Long, (Long) -> Unit) -> Unit = { _, _, _ -> },
    initialTiming: com.example.timeapp.data.ReminderTiming? = null,
    tasks: List<com.example.timeapp.data.Entry> = emptyList(),
    projects: List<com.example.timeapp.data.Category> = emptyList(),
) {
    var priority by remember { mutableStateOf(existingPlan?.priority ?: com.example.timeapp.data.Priority.NORMAL) }
    var taskId by remember { mutableStateOf(existingPlan?.taskId) }
    var projectId by remember { mutableStateOf(existingPlan?.projectId) }
    val colors = TimeThemeTokens.colors
    var timing by remember(initialTiming) { mutableStateOf(initialTiming ?: com.example.timeapp.data.ReminderTiming.PLAN_DEFAULT) }
    var title by remember { mutableStateOf(existingPlan?.title ?: "") }
    var amount by remember { mutableStateOf(existingPlan?.amountMinor?.toTakaInput() ?: "") }
    // No category until one is picked — every new bill used to be filed under "Food".
    var categoryId by remember(categories) { mutableStateOf(existingPlan?.categoryId) }
    var wallet by remember { mutableStateOf(existingPlan?.walletId ?: wallets.firstOrNull()?.id) }
    var dueDate by remember { mutableStateOf(existingPlan?.dueOn) }
    var note by remember { mutableStateOf(existingPlan?.note ?: "") }
    // Editor draft (Phase 15): typed text survives leaving the form or the app being killed.
    val draft = com.example.timeapp.ui.components.rememberDraft("cost", enabled = existingPlan == null, fields = mapOf("title" to title, "amount" to amount, "note" to note)) { d -> title = d["title"].orEmpty(); amount = d["amount"].orEmpty(); note = d["note"].orEmpty() }
    var remind by remember(initialRemind) { mutableStateOf(initialRemind) }
    var recurrence by remember { mutableStateOf(existingPlan?.recurrence) }
    val askNotificationPermission = rememberNotificationPermissionRequest()
    var showDatePicker by remember { mutableStateOf(false) }
    var askScope by remember { mutableStateOf(false) }
    val save = { scope: com.example.timeapp.data.SeriesScope ->
        draft.clear(); onSave(title, amount, wallet, categoryId, dueDate, note.ifBlank { null }, remind && dueDate != null, recurrence.takeIf { dueDate != null }, scope, timing,
            com.example.timeapp.data.MoneyLinks(categoryId, taskId, projectId ?: tasks.firstOrNull { it.id == taskId }?.projectId, priority))
    }

    Scaffold(
        topBar = {
            TimeTopBar(
                title = if (existingPlan != null) L("Edit bill to pay") else L("New bill to pay"),
                onBack = onBack,
                actions = {
                    TextButton(
                        // Editing one month of a repeating bill asks how far the edit reaches.
                        onClick = { if (existingPlan?.seriesId != null) askScope = true else save(com.example.timeapp.data.SeriesScope.ONLY_THIS) },
                        enabled = title.isNotBlank(),
                    ) { Text(L("Save")) }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.accC2, Shapes.xl)
                    .padding(vertical = Dimens.s20, horizontal = Dimens.s22)
                    .padding(top = Dimens.s6),
            ) {
                Text(L("Estimated total"), style = MaterialTheme.typography.bodyMedium)
                InlineAmountInput(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.padding(top = Dimens.s8),
                )
                Text(
                    // Plain words: this used to say "Stored as N poisha" — the app's inner unit.
                    L("Your best guess is fine — you record what you actually pay later. Up to two decimals."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Dimens.s10),
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(L("Name")) },
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s20),
                singleLine = true,
            )
            Text(L("Money category"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = Dimens.s10))
            CategoryPicker(
                categories = categories,
                selectedId = categoryId,
                onSelect = { categoryId = it },
                onCreate = { name, color -> onCreateCategory(name, color) { newId -> categoryId = newId } },
                modifier = Modifier.padding(bottom = Dimens.s12),
            )
            // Phase 13: how much it matters, and what it's for — so a project's money and time
            // can be seen together.
            Text(L("Priority"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = Dimens.s8))
            com.example.timeapp.ui.components.SegmentedToggle(
                options = listOf(L("Normal"), L("High"), L("Urgent")),
                selectedIndex = when (priority) {
                    com.example.timeapp.data.Priority.HIGH -> 1
                    com.example.timeapp.data.Priority.URGENT -> 2
                    else -> 0
                },
                onSelect = { priority = listOf(com.example.timeapp.data.Priority.NORMAL, com.example.timeapp.data.Priority.HIGH, com.example.timeapp.data.Priority.URGENT)[it] },
                modifier = Modifier.padding(bottom = Dimens.s12),
            )
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.s8), modifier = Modifier.padding(bottom = Dimens.s12)) {
                if (tasks.isNotEmpty()) {
                    com.example.timeapp.ui.components.FilterDropdownChip(
                        label = tasks.firstOrNull { it.id == taskId }?.let { L("For: ") + it.title } ?: L("For a task"),
                        active = taskId != null,
                        options = listOf<Pair<String, Long?>>(L("No task") to null) + tasks.map { it.title to it.id },
                        onPick = { taskId = it },
                    )
                }
                if (projects.isNotEmpty()) {
                    com.example.timeapp.ui.components.FilterDropdownChip(
                        label = projects.firstOrNull { it.id == projectId }?.let { L("Project: ") + it.name } ?: L("Project"),
                        active = projectId != null,
                        options = listOf<Pair<String, Long?>>(L("No project") to null) + projects.map { it.name to it.id },
                        onPick = { projectId = it },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TimeThemeTokens.colors.card, Shapes.lg)
                    // Side padding: everything in this card used to touch its edges.
                    .padding(horizontal = Dimens.s16)
                    .padding(vertical = Dimens.s4)
                    .padding(bottom = Dimens.s16),
            ) {
                DetailRow2(
                    L("Due date"),
                    dueDate?.format(dueDateFormatter) ?: L("None"),
                    modifier = Modifier.clickable { showDatePicker = true },
                )
                Text(L("Wallet"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Dimens.s10, start = Dimens.s2))
                Text(
                    L("A suggestion only — you choose again when paying"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.s2, bottom = Dimens.s10),
                )
                if (wallets.isEmpty()) {
                    Text(
                        L("No wallets yet — you can still add this bill and choose a wallet when you pay it."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Dimens.s2, bottom = Dimens.s10),
                    )
                }
                ChipRow(
                    options = wallets.map { it.name },
                    selected = setOfNotNull(wallets.firstOrNull { it.id == wallet }?.name),
                    onToggle = { name -> wallet = wallets.firstOrNull { it.name == name }?.id },
                    modifier = Modifier.padding(bottom = Dimens.s10),
                )
                RepeatPicker(
                    value = recurrence,
                    onValueChange = { recurrence = it },
                    enabled = dueDate != null,
                    disabledCaption = L("Set a due date above to repeat this bill."),
                    anchorDay = dueDate?.dayOfMonth,
                    anchorWeekday = dueDate?.dayOfWeek,
                    modifier = Modifier.padding(horizontal = Dimens.s2, vertical = Dimens.s10),
                )
            }
            RichTextField(
                value = note,
                onValueChange = { note = it },
                label = L("Notes"),
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s20),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TimeThemeTokens.status.warn, Shapes.lg)
                    .padding(Dimens.s14),
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = TimeThemeTokens.status.warnInk)
                Text(
                    L("Saving this doesn't pay anything or change a wallet. Balances only move when you record a payment."),
                    style = MaterialTheme.typography.bodySmall,
                    color = TimeThemeTokens.status.warnInk,
                    modifier = Modifier.padding(start = Dimens.s12),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.s14)
                    .background(colors.accS, Shapes.lg)
                    .padding(Dimens.s14),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(17.dp))
                Column(modifier = Modifier.weight(1f).padding(start = Dimens.s10)) {
                    Text(L("Remind me"), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            dueDate == null -> L("Set a due date above — a reminder needs a day to fire on.")
                            // Otherwise the switch would arm a real alarm that fires into a
                            // muted channel and is discarded without ever telling the user.
                            remindersMuted -> L("%1\$s — but money reminders are muted in Settings › Notifications, so this won't show.", timing.label())
                            remind && timing.at(dueDate, null)?.isBefore(java.time.Instant.now()) == true ->
                                L("%1\$s has already passed — pick a later time, or it will show as soon as you save.", timing.label())
                            else -> L("%1\$s · prompts you, never pays.", timing.label())
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
                    com.example.timeapp.ui.components.ReminderTimingKind.PLAN, timing, { timing = it },
                    modifier = Modifier.padding(horizontal = Dimens.s14),
                )
            }
        }
    }

    if (askScope && existingPlan != null) {
        com.example.timeapp.ui.components.SeriesScopeDialog(
            title = L("Save repeating cost"),
            confirmLabel = L("Save"),
            onlyThisEnabled = recurrence.takeIf { dueDate != null }.sameRuleAs(existingPlan.recurrence),
            note = L("Months that already have a payment are never changed."),
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
}

@Composable
private fun DetailRow2(label: String, value: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Dimens.s10),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
