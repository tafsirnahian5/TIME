package com.example.timeapp.ui.stats

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.SegmentedToggle
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** Settings › Reports & goals › Money report — real Week/Month stats from settled ledger data. */
@Composable
fun MoneyStatsScreen(onBack: () -> Unit, viewModel: StatsViewModel, netWorth: List<Pair<java.time.YearMonth, Long>> = emptyList()) {
    val colors = TimeThemeTokens.colors
    val period by viewModel.periodState.collectAsState()
    var drillDown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<PlanVariance?>(null) }
    val ui by viewModel.moneyStats.collectAsState()
    val ok = TimeThemeTokens.status.okInk

    Scaffold(topBar = { TimeTopBar(title = L("Money report"), onBack = onBack) }) { padding ->
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

            Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                MoneyChip(L("Income"), "+${ui.incomeMinor.toTaka()}", ok, Modifier.weight(1f), ui.incomeComparison)
                MoneyChip(L("Expense"), ui.expenseMinor.toTaka(), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f), ui.expenseComparison)
                MoneyChip(L("Net"), (if (ui.netMinor >= 0) "+" else "") + ui.netMinor.toTaka(), if (ui.netMinor >= 0) ok else TimeThemeTokens.status.dangerInk, Modifier.weight(1f))
            }
            if (netWorth.isNotEmpty()) NetWorthCard(netWorth)

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("SPENDING BY CATEGORY"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s14))
                if (ui.byCategory.isEmpty()) {
                    Text(L("No spending in this period."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val total = ui.byCategory.sumOf { it.minor }.coerceAtLeast(1)
                ui.byCategory.forEach { cat ->
                    val pct = (cat.minor * 100 / total).toInt()
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s14), verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.padding(top = 6.dp).size(10.dp).background(Color(cat.colorLong), CircleShape))
                        Column(modifier = Modifier.padding(start = Dimens.s10).weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(cat.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(L("%1\$s · %2\$s%%", cat.minor.toTaka(), pct), style = MaterialTheme.typography.bodySmall)
                            }
                            // With a budget set, the bar measures spend against that limit
                            // rather than against the other categories — the question a
                            // budget is actually asking.
                            val budget = cat.budgetMinor
                            val fraction = if (budget != null && budget > 0) {
                                (cat.minor.toFloat() / budget).coerceIn(0f, 1f)
                            } else {
                                cat.minor.toFloat() / total
                            }
                            val overBudget = budget != null && budget > 0 && cat.minor > budget
                            Box(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s6).height(8.dp).background(colors.ring, Shapes.pill)) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(fraction).height(8.dp)
                                        .background(if (overBudget) MaterialTheme.colorScheme.error else Color(cat.colorLong), Shapes.pill),
                                )
                            }
                            if (budget != null && budget > 0) {
                                Text(
                                    if (overBudget) {
                                        L("%1\$s over the %2\$s budget", ((cat.minor - budget).toTaka()), budget.toTaka())
                                    } else {
                                        L("%1\$s left of %2\$s", ((budget - cat.minor).toTaka()), budget.toTaka())
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Dimens.s4),
                                )
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16, bottom = Dimens.s20).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("PLANNED VS ACTUAL"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s6))
                if (ui.variances.isEmpty()) {
                    Text(L("No bills settled in this period."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val planned = ui.variances.sumOf { it.plannedMinor }
                    val actual = ui.variances.sumOf { it.actualMinor }
                    val total = actual - planned
                    Text(
                        L("Planned %1\$s · actual %2\$s · ", planned.toTaka(), actual.toTaka()) +
                            (if (total <= 0) L("%1\$s under overall", kotlin.math.abs(total).toTaka()) else L("%1\$s over overall", total.toTaka())),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = Dimens.s6),
                    )
                }
                ui.variances.forEach { v ->
                    val diff = v.actualMinor - v.plannedMinor
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { drillDown = v }.padding(vertical = Dimens.s10),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(v.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOf(
                                    L("expected %1\$s, paid %2\$s", v.plannedMinor.toTaka(), v.actualMinor.toTaka()),
                                    if (v.sources.size == 1) L("1 record") else L("%1\$s records", v.sources.size),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        val under = diff <= 0
                        Box(
                            modifier = Modifier
                                .background(if (under) TimeThemeTokens.status.ok else TimeThemeTokens.status.danger, Shapes.pill)
                                .padding(horizontal = Dimens.s10, vertical = Dimens.s4),
                        ) {
                            Text(
                                if (under) L("%1\$s under", kotlin.math.abs(diff).toTaka()) else L("%1\$s over", kotlin.math.abs(diff).toTaka()),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (under) ok else TimeThemeTokens.status.dangerInk,
                            )
                        }
                    }
                }
            }
        }
    }

    drillDown?.let { v ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { drillDown = null },
            title = { Text(v.title) },
            text = {
                Column {
                    Text(L("Estimate %1\$s", v.plannedMinor.toTaka()), style = MaterialTheme.typography.bodyMedium)
                    v.sources.forEach { r ->
                        val refund = r.direction == com.example.timeapp.data.Direction.REFUND
                        Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8)) {
                            Text(
                                (r.settledAt?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()
                                    ?.format(displayPattern("d MMM")) ?: "") +
                                    if (refund) L(" · refund") else L(" · payment"),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text((if (refund) "−" else "") + r.paidMinor.toTaka(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        L("Net paid %1\$s", v.actualMinor.toTaka()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Dimens.s12),
                    )
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { drillDown = null }) { Text(L("Close")) } },
        )
    }
}

@Composable
private fun MoneyChip(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier, comparison: String = "") {
    Column(modifier = modifier.background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s14)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor, modifier = Modifier.padding(top = Dimens.s4))
        // Neutral ink either way: a comparison is a fact, not a verdict.
        if (comparison.isNotEmpty()) {
            Text(comparison, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Dimens.s2))
        }
    }
}

/**
 * Net worth over the last year (Phase 13, after XPENC): money in wallets plus what's owed to you,
 * minus what you owe, at each month's end. A fact, not a verdict — neutral colours.
 */
@Composable
private fun NetWorthCard(points: List<Pair<java.time.YearMonth, Long>>) {
    val colors = TimeThemeTokens.colors
    val max = points.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1)
    Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(colors.card, Shapes.lg).padding(Dimens.s16)) {
        Text(L("NET WORTH · 12 MONTHS"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() })
        Text(points.last().second.toTaka(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Dimens.s4))
        Text(L("Wallets, plus what people owe you, minus what you owe."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.s12).height(Dimens.s34 * 2),
            horizontalArrangement = Arrangement.spacedBy(Dimens.s4),
            verticalAlignment = androidx.compose.ui.Alignment.Bottom,
        ) {
            points.forEach { (m, v) ->
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(1f).fillMaxHeight((kotlin.math.abs(v).toFloat() / max).coerceAtLeast(0.02f))
                        .background(if (v >= 0) colors.acc else TimeThemeTokens.status.danger, Shapes.xs)
                        .semantics { contentDescription = m.format(displayPattern("MMMM yyyy")) + ": " + v.toTaka() },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Dimens.s4)) {
            Text(points.first().first.format(displayPattern("MMM yyyy")), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(points.last().first.format(displayPattern("MMM yyyy")), style = MaterialTheme.typography.labelSmall)
        }
    }
}
