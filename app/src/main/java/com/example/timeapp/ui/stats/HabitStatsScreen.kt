package com.example.timeapp.ui.stats

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.timeapp.data.habitPeriodSummary
import com.example.timeapp.data.streakLabel
import com.example.timeapp.ui.components.SegmentedToggle
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.habits.HabitsViewModel
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import kotlin.math.roundToInt

/**
 * Reports › Habits: how each habit went this week or month. The same rules as the streaks —
 * rest days, the future and a still-open today are never counted as misses — via
 * [habitPeriodSummary]. Percentages only where something was judged; otherwise the PDF's
 * "Not enough data", never a misleading 0%.
 */
@Composable
fun HabitStatsScreen(onBack: () -> Unit, onOpenHabit: (Long) -> Unit, viewModel: HabitsViewModel) {
    val colors = TimeThemeTokens.colors
    val state by viewModel.uiState.collectAsState()
    var period by remember { mutableStateOf(StatsPeriod.WEEK) }
    val range = periodRange(period, state.today)
    val rows = state.active.map { it to habitPeriodSummary(it.habit, it.marks, range.start, range.endExclusive, state.today) }
    val checkIns = rows.sumOf { it.second.done }
    val restDays = rows.sumOf { it.second.restDays }

    Scaffold(topBar = { TimeTopBar(title = L("Habit report"), onBack = onBack) }) { padding ->
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
                onSelect = { period = if (it == 0) StatsPeriod.WEEK else StatsPeriod.MONTH },
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16),
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(colors.accC2, Shapes.xl).padding(Dimens.s22)) {
                Text(L("Check-ins"), style = MaterialTheme.typography.bodySmall, color = colors.accInk)
                Text(com.example.timeapp.data.Lang.digits(checkIns.toString()), style = MaterialTheme.typography.displaySmall, color = colors.accInk, modifier = Modifier.padding(top = Dimens.s6))
                Text(
                    // Two separate facts, each with its own singular ("1 active habits" read wrong).
                    listOf(
                        if (rows.size == 1) L("1 active habit") else L("%1\$s active habits", rows.size),
                        if (restDays == 1) L("1 rest day") else L("%1\$s rest days", restDays),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accInk,
                    modifier = Modifier.padding(top = Dimens.s6),
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16, bottom = Dimens.s20).background(colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("BY HABIT"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s14))
                if (rows.isEmpty()) {
                    Text(L("No active habits yet."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                rows.forEach { (row, summary) ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { onOpenHabit(row.habit.id) }.padding(bottom = Dimens.s14)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(row.habit.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(
                                summary.rate?.let { "${(it * 100).roundToInt()}%" } ?: L("Not enough data"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            // Separate facts, each with its own singular ("1 check-ins" read wrong).
                            listOfNotNull(
                                if (summary.weekly) L("%1\$s of %2\$s weeks met", summary.met, summary.judged)
                                else L("%1\$s of %2\$s due days", summary.met, summary.judged),
                                if (!summary.weekly) null else if (summary.done == 1) L("1 check-in") else L("%1\$s check-ins", summary.done),
                                L("best streak %1\$s", row.stats.streakLabel(row.stats.bestStreak)),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s6).height(Dimens.s8).background(colors.ring, Shapes.pill)) {
                            summary.rate?.let { rate ->
                                Box(Modifier.fillMaxWidth(rate.toFloat().coerceIn(0.02f, 1f)).height(Dimens.s8).background(colors.acc, Shapes.pill))
                            }
                        }
                    }
                }
                Text(
                    L("Rest days, days before a habit started and today (until it's done) never count as misses."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
