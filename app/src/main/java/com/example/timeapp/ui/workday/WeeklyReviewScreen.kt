package com.example.timeapp.ui.workday

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import com.example.timeapp.data.targetForWeek
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Icon
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.AppViewModel
import com.example.timeapp.ui.components.SegmentedToggle
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.money.MoneyViewModel
import com.example.timeapp.ui.money.computeMoneyOverview
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.ui.richtext.RichTextField
import com.example.timeapp.ui.stats.StatsPeriod
import com.example.timeapp.ui.stats.StatsViewModel
import com.example.timeapp.ui.stats.formatHm
import com.example.timeapp.ui.stats.periodRange
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens
import com.example.timeapp.ui.theme.Shapes

/**
 * Settings › Workday & calendar › Weekly review — reuses [StatsViewModel] for its metrics,
 * [computeMoneyOverview] for its Money section (both already-real, not re-derived here), and
 * the existing Reschedule flow unchanged for "Move forward". No XP copy — see Tier 11 plan.
 */
@Composable
fun WeeklyReviewScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onReschedule: (Long) -> Unit,
    statsViewModel: StatsViewModel,
    moneyViewModel: MoneyViewModel,
    appViewModel: AppViewModel,
    reviewViewModel: ReviewViewModel,
    planViewModel: PlanViewModel,
) {
    val period by statsViewModel.periodState.collectAsState()
    val timeStats by statsViewModel.timeStats.collectAsState()
    val money by moneyViewModel.uiState.collectAsState()
    val settings by appViewModel.uiState.collectAsState()
    val plan by planViewModel.uiState.collectAsState()
    val moneyNumbers = computeMoneyOverview(money, includeCarryover = settings.includeOverdueCarryover)
    val unfinished = plan.entries.filter { it.status == EntryStatus.OVERDUE }

    val range = periodRange(period)
    val monthly = period == StatsPeriod.MONTH
    val reviewPeriod = if (monthly) com.example.timeapp.data.ReviewPeriod.MONTHLY else com.example.timeapp.data.ReviewPeriod.WEEKLY
    // ISO weeks for the weekly key; the month itself for the monthly one.
    val periodKey = if (monthly) java.time.YearMonth.from(range.start).toString()
    else com.example.timeapp.data.weeklyReviewKey(range.start)
    LaunchedEffect(reviewPeriod, periodKey) { reviewViewModel.ensureNoteLoaded(reviewPeriod, periodKey) }
    val loadedNote by reviewViewModel.periodNote.collectAsState()
    val moneyStats by statsViewModel.moneyStats.collectAsState()
    // Plan completion over the period: tasks due in it, done or not.
    val dueInPeriod = plan.allEntries.filter { it.kind == com.example.timeapp.data.EntryKind.TASK && it.dueOn != null && it.dueOn >= range.start && it.dueOn < range.endExclusive }
    val doneInPeriod = dueInPeriod.count { it.status == EntryStatus.DONE }
    var note by remember(loadedNote) { mutableStateOf(loadedNote ?: "") }

    Scaffold(topBar = { TimeTopBar(title = if (monthly) L("Monthly review") else L("Weekly review"), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.s16)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                L("%1\$s–%2\$s · To date", range.start.format(displayPattern("d")), (range.endExclusive.minusDays(1).format(displayPattern("d MMM yyyy")))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s10, bottom = Dimens.s14),
            )
            SegmentedToggle(
                options = listOf(L("Week"), L("Month")),
                selectedIndex = if (period == StatsPeriod.WEEK) 0 else 1,
                onSelect = { statsViewModel.setPeriod(if (it == 0) StatsPeriod.WEEK else StatsPeriod.MONTH) },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                MetricRow(L("Active time"), formatHm(timeStats.totalMs))
                if (timeStats.totalComparison.isNotEmpty()) {
                    Text(timeStats.totalComparison, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MetricRow(
                    L("Plan completion"),
                    if (dueInPeriod.isEmpty()) L("Nothing was due") else L("%1\$s of %2\$s due tasks done", doneInPeriod, dueInPeriod.size),
                )
                MetricRow(L("Planned hours"), formatHm(timeStats.plannedMs))
                if (!monthly) {
                    val targets by statsViewModel.workTargets.collectAsState()
                    targets.targetForWeek(range.start)?.let { t ->
                        MetricRow(L("Target"), L("%1\$s of %2\$s h", formatHm(timeStats.totalMs), com.example.timeapp.ui.stats.formatHours(t)))
                    }
                }
                MetricRow(L("Interruptions"), if (timeStats.interruptions == 0) L("None") else L("%1\$s (%2\$s switches)", timeStats.interruptions, timeStats.switches))
                MetricRow(L("Rest days"), if (settings.restDays.isEmpty()) L("None") else settings.restDays.joinToString(", ") { com.example.timeapp.data.L(it) })
            }

            if (timeStats.byCategory.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                    Text(L("WHERE THE TIME WENT"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s6))
                    timeStats.byCategory.take(5).forEach { MetricRow(it.category.name, formatHm(it.ms)) }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("WHAT WENT WELL?"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = Dimens.s10))
                RichTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = if (monthly) L("Write a short reflection on this month…") else L("Write a short reflection on this week…"),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("MOVE FORWARD"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s6))
                if (unfinished.isEmpty()) {
                    Text(L("Nothing unfinished right now."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                unfinished.forEach { entry ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s8), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onReschedule(entry.id) }) {
                            Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(L("Reschedule"), modifier = Modifier.padding(start = Dimens.s6))
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("MONEY REVIEW"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s10))
                // The period's own spending (net of refunds), not always "this month".
                MetricRow(if (monthly) L("Spent this month") else L("Spent this week"), moneyStats.expenseMinor.toTaka())
                if (moneyStats.expenseComparison.isNotEmpty()) {
                    Text(moneyStats.expenseComparison, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (moneyStats.variances.isNotEmpty()) {
                    val planned = moneyStats.variances.sumOf { it.plannedMinor }
                    val actual = moneyStats.variances.sumOf { it.actualMinor }
                    MetricRow(
                        L("Settled bills"),
                        if (actual <= planned) L("%1\$s under estimate", ((planned - actual).toTaka())) else L("%1\$s over estimate", ((actual - planned).toTaka())),
                    )
                }
                MetricRow(L("Planned remaining"), moneyNumbers.plannedRemaining.toTaka())
            }

            Button(
                onClick = { reviewViewModel.saveReview(reviewPeriod, periodKey, note, onDone = onDone) },
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16),
            ) { Text(L("Save review")) }
            Box(modifier = Modifier.padding(bottom = Dimens.s16))
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s8), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
