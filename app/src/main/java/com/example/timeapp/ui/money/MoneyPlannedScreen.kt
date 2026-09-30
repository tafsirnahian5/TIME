package com.example.timeapp.ui.money

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.outstandingThisMonth
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.ConfirmDeleteDialog
import com.example.timeapp.ui.components.RootTopBar
import com.example.timeapp.ui.components.RootTopBarTrailing
import com.example.timeapp.ui.richtext.RichTextView
import com.example.timeapp.ui.richtext.toggleChecklistLine
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material.icons.rounded.FilterAlt
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** E1 — Money · Planned: upcoming/outstanding costs; balances only move on payment. */
@Composable
fun MoneyPlannedScreen(
    onAdd: () -> Unit,
    onOpenOverview: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPay: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MoneyViewModel,
    /** Straight to the bill form — the empty state's button used to open the whole Add sheet. */
    onAddBill: () -> Unit = onAdd,
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val cancelled by viewModel.cancelledPlans.collectAsState()
    var showCancelled by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(PlanFilter.ALL) }
    val today = java.time.LocalDate.now()
    // Overdue by date, whatever the payment status — a part-paid bill past its date is overdue
    // too, and used to be missing from both this filter and the carryover.
    val overduePlans = ui.plans.filter { it.isOverdue(today) }
    val visiblePlans = when (filter) {
        PlanFilter.ALL -> ui.plans
        PlanFilter.OVERDUE -> overduePlans
        // By the date itself: a part-paid bill with no date used to drop out of this filter.
        PlanFilter.UNSCHEDULED -> ui.plans.filter { it.dueOn == null }
    }

    Scaffold(
        topBar = {
            RootTopBar(
                help = com.example.timeapp.ui.components.TabHelps.money,
                title = L("Money"), onSearch = onSearch,
                onOpenSettings = onOpenSettings,
                trailing = RootTopBarTrailing.MORE_VERT,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = colors.acc, shape = Shapes.lg) {
                Icon(Icons.Rounded.Add, contentDescription = L("Add"), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MoneyTabs(selectedIndex = 1, onOpenOverview = onOpenOverview, onOpenHistory = onOpenHistory)
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.s16)) {
                item {
                    val monthName = java.time.LocalDate.now().month
                        .getDisplayName(java.time.format.TextStyle.FULL, com.example.timeapp.data.Lang.locale)
                    val outstanding = ui.plans.outstandingThisMonth()
                    val overdueCarryover = overduePlans.sumOf { it.remainingMinor }
                    // Bills with no date count as owed now here (and on Today) but sit outside
                    // Overview's month forecast — so each page names them rather than disagreeing.
                    val undated = ui.plans.filter { it.dueOn == null }.sumOf { it.remainingMinor }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s14), horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap)) {
                        SummaryCard(
                            L("Still to pay"), outstanding.toTaka(),
                            if (undated > 0) L("this month, incl. %1\$s with no date", undated.toTaka()) else L("due in %1\$s", monthName),
                            colors.accC2, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f),
                        )
                        // Only when something is actually late — a red "Tk 0" card read as an alarm.
                        if (overdueCarryover > 0) {
                            SummaryCard(L("Already late"), overdueCarryover.toTaka(), L("past its date — counted in the total"), TimeThemeTokens.status.danger, TimeThemeTokens.status.dangerInk, Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.padding(bottom = Dimens.s8)) {
                        FilterChip(L("All %1\$s", ui.plans.size), filter == PlanFilter.ALL, onClick = { filter = PlanFilter.ALL })
                        FilterChip(
                            L("Overdue %1\$s", overduePlans.size),
                            filter == PlanFilter.OVERDUE,
                            onClick = { filter = PlanFilter.OVERDUE },
                        )
                        FilterChip(
                            L("No date %1\$s", (ui.plans.count { it.dueOn == null })),
                            filter == PlanFilter.UNSCHEDULED,
                            onClick = { filter = PlanFilter.UNSCHEDULED },
                        )
                    }
                }
                if (visiblePlans.isEmpty()) {
                    item {
                        // A fresh install now starts genuinely empty, so this is the first
                        // thing a new user sees here — rule 5, empty states are designed.
                        if (ui.plans.isEmpty()) {
                            EmptyState(
                                icon = Icons.Rounded.Upcoming,
                                illustration = Illustration.MONEY,
                                headline = L("No bills to pay yet"),
                                supporting = L("Money you expect to spend — rent, a bill, a subscription. Adding one here moves nothing until you record the payment."),
                                actionLabel = L("Add a bill to pay"),
                                onAction = onAddBill,
                            )
                        } else {
                            EmptyState(
                                icon = Icons.Rounded.FilterAlt,
                                headline = when (filter) {
                                    PlanFilter.OVERDUE -> L("Nothing overdue")
                                    PlanFilter.UNSCHEDULED -> L("No bills without a date")
                                    PlanFilter.ALL -> L("Nothing planned")
                                },
                                supporting = L("No bill matches this filter right now."),
                                actionLabel = L("Show all"),
                                onAction = { filter = PlanFilter.ALL },
                            )
                        }
                    }
                }
                items(visiblePlans) { plan ->
                    PlanCard(
                        plan,
                        onClick = { onOpenPay(plan.id) },
                        onEdit = { onEdit(plan.id) },
                        onDelete = { scope -> viewModel.deletePlan(plan.id, scope) },
                        onCancel = { viewModel.cancelPlan(plan.id) },
                        onToggleNote = { lineIndex -> viewModel.updatePlan(plan.copy(note = toggleChecklistLine(plan.note ?: "", lineIndex))) },
                    )
                }
                if (cancelled.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showCancelled = !showCancelled }, modifier = Modifier.padding(top = Dimens.s4)) {
                            Text(if (showCancelled) L("Hide cancelled") else L("Cancelled (%1\$s)", cancelled.size))
                        }
                    }
                    if (showCancelled) {
                        items(cancelled, key = { it.id }) { plan ->
                            CancelledPlanRow(plan, onReopen = { viewModel.reopenCancelledPlan(plan.id) })
                        }
                    }
                }
                // Room under the last card for the FAB.
                item { Box(Modifier.padding(bottom = Dimens.s34 * 2)) }
            }
        }
    }
}

@Composable
internal fun MoneyTabs(selectedIndex: Int, onOpenOverview: () -> Unit = {}, onOpenPlanned: () -> Unit = {}, onOpenHistory: () -> Unit = {}) {
    val colors = TimeThemeTokens.colors
    Row(modifier = Modifier.fillMaxWidth().border(0.dp, colors.line3)) {
        listOf(L("Overview") to onOpenOverview, L("Planned") to onOpenPlanned, L("History") to onOpenHistory).forEachIndexed { i, (label, action) ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = action)
                    .padding(vertical = Dimens.s12),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.acc else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, caption: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(bg, Shapes.lg).padding(Dimens.s14)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = fg)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = fg, modifier = Modifier.padding(top = Dimens.s4))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = fg, modifier = Modifier.padding(top = Dimens.s4))
    }
}

private enum class PlanFilter { ALL, OVERDUE, UNSCHEDULED }

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit = {}) {
    val colors = TimeThemeTokens.colors
    Box(
        modifier = Modifier
            .padding(end = Dimens.s8)
            .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
            .border(if (selected) 0.dp else 1.dp, TimeThemeTokens.status.controlOutline, Shapes.pill)
            .semantics { this.selected = selected }.clickable(onClick = onClick)
            .padding(horizontal = Dimens.s14, vertical = Dimens.s8),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanCard(
    plan: MoneyRecord,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (com.example.timeapp.data.SeriesScope) -> Unit,
    onCancel: () -> Unit,
    onToggleNote: (Int) -> Unit,
) {
    val colors = TimeThemeTokens.colors
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingCancel by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.s10)
            .background(TimeThemeTokens.colors.card, Shapes.lg)
            .border(1.dp, colors.line, Shapes.lg)
            .clickable(onClick = onClick)
            .padding(Dimens.s16),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Dimens.s10)) {
            com.example.timeapp.ui.components.CategoryMark(plan.categoryColor, plan.categoryIcon, size = 18.dp)
            Text(plan.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = Dimens.s10))
            if (plan.priority >= com.example.timeapp.data.Priority.HIGH) {
                Text(
                    if (plan.priority == com.example.timeapp.data.Priority.URGENT) L("Urgent") else L("High"),
                    style = MaterialTheme.typography.labelSmall,
                    color = TimeThemeTokens.status.warnInk,
                    modifier = Modifier.padding(end = Dimens.s6).background(TimeThemeTokens.status.warn, Shapes.pill).padding(horizontal = Dimens.s8, vertical = Dimens.s2),
                )
            }
            StatusBadge(plan.status, plan.dueOn)
            Box {
                // Full 48dp target (rule 6); this was a 24dp button.
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = L("More"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(L("Record payment")) }, onClick = { menuExpanded = false; onClick() })
                    DropdownMenuItem(text = { Text(L("Edit")) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text(L("Cancel bill")) }, onClick = { menuExpanded = false; confirmingCancel = true })
                    DropdownMenuItem(text = { Text(L("Delete")) }, onClick = { menuExpanded = false; confirmingDelete = true })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (plan.paidMinor > 0) plan.remainingMinor.toTaka() else plan.amountMinor.toTaka(), style = MaterialTheme.typography.titleMedium)
            // Dates read as dates — this printed the raw "2026-09-28".
            val due = plan.dueOn?.format(displayPattern("EEE d MMM"))
            Text(
                when (plan.status) {
                    PlanStatus.OVERDUE -> L("outstanding · was due %1\$s", due)
                    PlanStatus.PARTLY_PAID -> L("of %1\$s", plan.amountMinor.toTaka()) + (due?.let { L(" · due %1\$s", it) } ?: "")
                    PlanStatus.PLANNED -> L("due %1\$s", due)
                    PlanStatus.UNSCHEDULED -> L("no date — counted as owed now")
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimens.s8).weight(1f),
            )
            // Paying a bill was hidden behind a tap on the card, with no sign it could be tapped.
            androidx.compose.material3.FilledTonalButton(onClick = onClick) { Text(L("Pay")) }
        }
        if (plan.status == PlanStatus.PARTLY_PAID) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s10)) {
                val fraction = plan.paidMinor.toFloat() / plan.amountMinor
                Box(modifier = Modifier.fillMaxWidth().size(5.dp).background(colors.ring, Shapes.xs))
                Box(modifier = Modifier.fillMaxWidth(fraction).size(5.dp).background(colors.acc, Shapes.xs))
            }
        }
        if (!plan.note.isNullOrBlank()) {
            RichTextView(raw = plan.note, modifier = Modifier.padding(top = Dimens.s10), onToggleChecklistItem = onToggleNote)
        }
    }

    if (confirmingCancel) {
        AlertDialog(
            onDismissRequest = { confirmingCancel = false },
            title = { Text(L("Cancel %1\$s?", plan.title)) },
            text = {
                Text(
                    if (plan.paidMinor > 0) {
                        L("The %1\$s still owed stops counting. The %2\$s already paid stays in your history.", plan.remainingMinor.toTaka(), plan.paidMinor.toTaka())
                    } else {
                        L("It stops counting as owed and its reminder goes. It stays under Cancelled, where you can reopen it.")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { confirmingCancel = false; onCancel() }) { Text(L("Cancel bill")) } },
            dismissButton = { TextButton(onClick = { confirmingCancel = false }) { Text(L("Keep it")) } },
        )
    }
    if (confirmingDelete && plan.paidMinor > 0) {
        // A plan with payments can't be deleted: its ledger rows would point at nothing.
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(L("Cancel it instead?")) },
            text = {
                Text(
                    L("%1\$s has been paid against %2\$s, so it can't be deleted — ", plan.paidMinor.toTaka(), plan.title) +
                        L("those payments would point at nothing. Cancelling stops the rest counting as owed and keeps them."),
                )
            },
            confirmButton = { TextButton(onClick = { confirmingDelete = false; onCancel() }) { Text(L("Cancel bill")) } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(L("Keep it")) } },
        )
    } else if (confirmingDelete) {
        if (plan.seriesId != null) {
            com.example.timeapp.ui.components.SeriesScopeDialog(
                title = L("Delete %1\$s?", plan.title),
                confirmLabel = L("Delete"),
                note = L("Months that already have a payment are kept."),
                onConfirm = { scope -> confirmingDelete = false; onDelete(scope) },
                onDismiss = { confirmingDelete = false },
            )
        } else ConfirmDeleteDialog(
            undoable = true,
            itemLabel = plan.title,
            onConfirm = { confirmingDelete = false; onDelete(com.example.timeapp.data.SeriesScope.ONLY_THIS) },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/** A cancelled plan: what it was for, what had been paid, and a way back. */
@Composable
private fun CancelledPlanRow(plan: MoneyRecord, onReopen: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s6), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(plan.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (plan.paidMinor > 0) L("%1\$s paid of %2\$s", plan.paidMinor.toTaka(), plan.amountMinor.toTaka()) else L("%1\$s · nothing paid", plan.amountMinor.toTaka()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onReopen) { Text(L("Reopen")) }
    }
}

@Composable
private fun StatusBadge(status: PlanStatus?, dueOn: java.time.LocalDate? = null) {
    val overdueDays = dueOn?.let { java.time.temporal.ChronoUnit.DAYS.between(it, java.time.LocalDate.now()) } ?: 1L
    val (label, bg, fg) = when (status) {
        PlanStatus.OVERDUE -> Triple(L("Overdue %1\$sd", overdueDays), TimeThemeTokens.status.danger, TimeThemeTokens.status.dangerInk)
        PlanStatus.PARTLY_PAID -> Triple(L("Partly paid"), TimeThemeTokens.status.warn, TimeThemeTokens.status.warnInk)
        PlanStatus.PLANNED -> Triple(L("Planned"), null, null)
        PlanStatus.UNSCHEDULED -> Triple(L("No date"), null, null)
        else -> Triple("", null, null)
    }
    val colors = TimeThemeTokens.colors
    if (status == PlanStatus.PLANNED) {
        Box(modifier = Modifier.background(colors.accC, Shapes.pill).padding(horizontal = Dimens.s10, vertical = Dimens.s4)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.accInk)
        }
    } else if (status == PlanStatus.UNSCHEDULED) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (bg != null) {
        Box(modifier = Modifier.background(bg, Shapes.pill).padding(horizontal = Dimens.s10, vertical = Dimens.s4)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg ?: Color.Black)
        }
    }
}
