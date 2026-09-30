package com.example.timeapp.ui.stats

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import com.example.timeapp.data.targetProgress
import com.example.timeapp.data.targetForWeek
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.components.SegmentedToggle
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** Settings › Reports & goals › Time report — real Week/Month stats from WorkSession data. */
@Composable
fun TimeStatsScreen(onBack: () -> Unit, viewModel: StatsViewModel, onOpenCategory: (Long) -> Unit = {}) {
    val colors = TimeThemeTokens.colors
    val period by viewModel.periodState.collectAsState()
    val ui by viewModel.timeStats.collectAsState()

    Scaffold(topBar = { TimeTopBar(title = L("Time report"), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.s16)
                .verticalScroll(rememberScrollState()),
        ) {
            SegmentedToggle(
                options = listOf(L("Week"), L("Month")),
                selectedIndex = if (period == StatsPeriod.WEEK) 0 else 1,
                onSelect = { viewModel.setPeriod(if (it == 0) StatsPeriod.WEEK else StatsPeriod.MONTH) },
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16),
            )

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(colors.accC2, Shapes.xl).padding(Dimens.s22)) {
                Text(L("Total active time"), style = MaterialTheme.typography.bodySmall, color = colors.accInk)
                Text(formatHm(ui.totalMs), style = MaterialTheme.typography.displaySmall, color = colors.accInk, modifier = Modifier.padding(top = Dimens.s6))
                Text(
                    // Whole sentences per count: "across 1 days" read wrong.
                    when {
                        ui.sessionCount == 1 -> L("1 stretch of work")
                        ui.activeDayCount == 1 -> L("%1\$s stretches of work, all on one day", ui.sessionCount)
                        else -> L("%1\$s stretches of work across %2\$s days", ui.sessionCount, ui.activeDayCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accInk,
                    modifier = Modifier.padding(top = Dimens.s6),
                )
                if (ui.totalComparison.isNotEmpty()) {
                    Text(ui.totalComparison, style = MaterialTheme.typography.bodySmall, color = colors.accInk)
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                StatChip(L("Completed tasks"), com.example.timeapp.data.Lang.digits(ui.completedTaskCount.toString()), Modifier.weight(1f))
                StatChip(L("Sessions"), com.example.timeapp.data.Lang.digits(ui.sessionCount.toString()), Modifier.weight(1f))
            }

            // The weekly work target: judged against the version in force for the week shown.
            val targets by viewModel.workTargets.collectAsState()
            val monday = periodRange(StatsPeriod.WEEK).start
            val target = targets.targetForWeek(monday)
            var editingTarget by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg)
                    .clickable { editingTarget = true }.padding(Dimens.s16),
            ) {
                Text(L("WEEKLY TARGET"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s8))
                if (target == null) {
                    Text(L("No target set — tap to set hours a week."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (period == StatsPeriod.WEEK) {
                    val tracked = ui.totalMs / 60_000
                    Text(L("%1\$s of %2\$s h this week", formatHm(ui.totalMs), formatHours(target)), style = MaterialTheme.typography.bodyLarge)
                    Box(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8).height(8.dp).background(colors.ring, Shapes.pill)) {
                        val f = targetProgress(tracked, target) ?: 0f
                        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(8.dp).background(colors.acc, Shapes.pill))
                    }
                    Text(
                        if (tracked >= target) L("Target met.") else L("%1\$s to go.", (formatHm((target - tracked) * 60_000L))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Dimens.s6),
                    )
                } else {
                    Text(L("%1\$s h a week — switch to Week to see progress.", formatHours(target)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (editingTarget) {
                WorkTargetDialog(
                    current = target,
                    history = targets,
                    onSave = { viewModel.setWeeklyTarget(it); editingTarget = false },
                    onDismiss = { editingTarget = false },
                )
            }

            // The PDF's interruption report: counts and estimate-vs-actual, never a score.
            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("INTERRUPTIONS"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s8))
                Text(
                    when (ui.interruptions) {
                        0 -> L("No work was interrupted in this period.")
                        1 -> if (ui.switches == 1) L("You paused a task once, to switch to something else.") else L("You paused a task once before finishing it.")
                        else -> when (ui.switches) {
                            0 -> L("You paused tasks %1\$s times before finishing them.", ui.interruptions)
                            1 -> L("You paused tasks %1\$s times before finishing them — once to switch to something else.", ui.interruptions)
                            else -> L("You paused tasks %1\$s times before finishing them — %2\$s times to switch to something else.", ui.interruptions, ui.switches)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (ui.estimates.isNotEmpty()) {
                    Text(L("ESTIMATE VS ACTUAL · FINISHED THIS PERIOD"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s16, bottom = Dimens.s8))
                    ui.estimates.forEach { e ->
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s6)) {
                            Text(e.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                L("%1\$s est · %2\$s tracked", formatHm(e.estimateMs), formatHm(e.trackedMs)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            TimeBreakdownCard(L("TRACKED BY CATEGORY"), ui.byCategory, L("No tracked time in this period."), onOpen = onOpenCategory)
            // Only once a project exists and has time — otherwise an empty card for a feature
            // the user may never use.
            if (ui.byProject.isNotEmpty()) {
                TimeBreakdownCard(L("TRACKED BY PROJECT"), ui.byProject, "")
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16, bottom = Dimens.s20).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("DAILY ACTIVITY"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s14))
                val maxDay = ui.byDay.maxOfOrNull { it.ms }?.coerceAtLeast(1) ?: 1
                val range = periodRange(period)
                val days = generateSequence(range.start) { it.plusDays(1) }.takeWhile { it < range.endExclusive }.toList()
                val isWeek = period == StatsPeriod.WEEK
                // A month is ~30 bars — a label (and Dimens.s10 gap) under every one of them
                // overflows and interleaves illegibly, unlike the week view's spacious 7.
                // Month view labels only every 5th day (plus the first/last) by day-of-month
                // number instead of a 3-letter weekday, and tightens the inter-bar gap.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isWeek) Dimens.s10 else Dimens.s2),
                ) {
                    days.forEachIndexed { index, day ->
                        val ms = ui.byDay.firstOrNull { it.date == day }?.ms ?: 0L
                        val fraction = (ms.toFloat() / maxDay).coerceIn(0.03f, 1f)
                        val showLabel = isWeek || index == 0 || index == days.lastIndex || day.dayOfMonth % 5 == 0
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.fillMaxWidth().height(78.dp), contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(78.dp * fraction)
                                        .background(colors.acc, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                                )
                            }
                            Text(
                                if (!showLabel) "" else if (isWeek) day.format(displayPattern("EEE")).take(3) else day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = Dimens.s6),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s14)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Dimens.s4))
    }
}

internal fun formatHm(ms: Long): String = com.example.timeapp.data.durationTextMs(ms)

@Composable
private fun TimeBreakdownCard(title: String, rows: List<CategoryTime>, emptyText: String, onOpen: ((Long) -> Unit)? = null) {
    val colors = TimeThemeTokens.colors
    Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
        Text(title, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = Dimens.s14))
        if (rows.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val maxMs = rows.maxOfOrNull { it.ms }?.coerceAtLeast(1) ?: 1
        rows.forEach { cat ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .then(if (onOpen != null && cat.id != null) Modifier.clickable { onOpen(cat.id) } else Modifier)
                    .padding(bottom = Dimens.s14),
                verticalAlignment = Alignment.Top,
            ) {
                Box(modifier = Modifier.padding(top = 6.dp).size(10.dp).background(Color(cat.category.color), CircleShape))
                Column(modifier = Modifier.padding(start = Dimens.s10).weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(cat.category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(formatHm(cat.ms), style = MaterialTheme.typography.bodySmall)
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s6).height(8.dp).background(colors.ring, Shapes.pill)) {
                        Box(
                            modifier = Modifier.fillMaxWidth(cat.ms.toFloat() / maxMs)
                                .height(8.dp).background(Color(cat.category.color), Shapes.pill),
                        )
                    }
                }
            }
        }
    }
}
