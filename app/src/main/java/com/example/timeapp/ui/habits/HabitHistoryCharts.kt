package com.example.timeapp.ui.habits

import com.example.timeapp.data.L
import com.example.timeapp.data.displayLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.DayState
import com.example.timeapp.data.habitHeatmap
import com.example.timeapp.data.weekdayRates
import com.example.timeapp.data.weeklyCompletions
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * A habit's longer history (Phase 12, after Loop and Track & Graph): a year of days as a
 * heatmap, how it goes on each weekday, and check-ins per week. Plain counts and shares —
 * no score. Token-built; no design canvas covers it.
 */
@Composable
fun HabitHistoryCharts(row: HabitRow, today: LocalDate) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    val cell = 11.dp
    Section(L("LAST 12 MONTHS")) {
        val weeks = habitHeatmap(row.habit, row.marks, today)
        // Newest on the right, scrolled into view.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(Int.MAX_VALUE)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { (_, state) ->
                        val base = Modifier.size(cell)
                        Box(
                            when (state) {
                                DayState.DONE -> base.background(colors.acc, Shapes.xs)
                                DayState.LOGGED -> base.background(colors.accC, Shapes.xs)
                                DayState.REST -> base.background(status.neutral, Shapes.xs)
                                DayState.MISSED -> base.background(colors.ring, Shapes.xs)
                                DayState.OPEN -> base.border(Dimens.hairline, colors.acc, Shapes.xs)
                                // Before the start or still ahead: a faint outline. Drawn as nothing, a
                                // new habit's year was a blank card with two dots in one corner.
                                DayState.NONE -> base.border(Dimens.hairline, colors.line, Shapes.xs)
                            },
                        )
                    }
                }
            }
        }
        val started = listOfNotNull(row.habit.startedOn, row.marks.keys.minOrNull()).min()
        if (started.isAfter(today.minusWeeks(8))) {
            Text(
                L("Started %1\$s — the rest fills in as the weeks go by.", started.format(com.example.timeapp.data.displayPattern("d MMM"))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s8),
            )
        }
        Row(modifier = Modifier.padding(top = Dimens.s8), horizontalArrangement = Arrangement.spacedBy(Dimens.s12)) {
            Key(colors.acc, L("Done")); Key(colors.accC, L("Below target")); Key(status.neutral, L("Rest")); Key(colors.ring, L("Missed"))
        }
    }
    Section(L("BY WEEKDAY · LAST 12 WEEKS")) {
        val rates = weekdayRates(row.habit, row.marks, today.minusWeeks(12), today)
        val order = (0L..6L).map { com.example.timeapp.data.WeekConfig.firstDay.plus(it) }
        order.forEach { day ->
            val rate = rates[day]
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s2)
                    .semantics { contentDescription = "${day.displayLabel()}: " + (rate?.let { L("%1\$s percent", ((it * 100).roundToInt())) } ?: L("not enough data")) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(day.displayLabel(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
                Box(Modifier.weight(1f).height(8.dp).background(colors.ring, Shapes.xs)) {
                    Box(Modifier.fillMaxWidth((rate ?: 0.0).toFloat()).fillMaxHeight().background(colors.acc, Shapes.xs))
                }
                Text(rate?.let { "${(it * 100).roundToInt()}%" } ?: "—", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp).padding(start = Dimens.s8))
            }
        }
    }
    Section(L("CHECK-INS PER WEEK")) {
        val counts = weeklyCompletions(row.marks, today)
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.s4),
            verticalAlignment = Alignment.Bottom,
        ) {
            counts.forEach { (week, n) ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .semantics { contentDescription = L("Week of %1\$s %2\$s: %3\$s", week.dayOfMonth, week.month.name.lowercase(), n) },
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (n > 0) Text(com.example.timeapp.data.Lang.digits(n.toString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().fillMaxHeight(n / 7f).background(if (n > 0) colors.acc else Color.Transparent, Shapes.xs))
                }
            }
        }
        Text(L("Oldest on the left; this week on the right."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Dimens.s6))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.xl)
            .border(Dimens.hairline, TimeThemeTokens.colors.line, Shapes.xl).padding(Dimens.s14),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = Dimens.s10))
        content()
    }
}

@Composable
private fun Key(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, Shapes.xs))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Dimens.s4))
    }
}
