package com.example.timeapp.ui.stats

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.timeapp.data.parseRestDays
import com.example.timeapp.data.workload
import com.example.timeapp.ui.AppViewModel
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.ui.plan.SchedulingMath
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reports › Workload: the next seven days of scheduled and due work against the work time
 * there actually is (work hours from Workday settings, rest days excluded). It states the
 * gap; it never moves anything — Reschedule is where changes happen. Not in a design canvas.
 */
@Composable
fun WorkloadScreen(onBack: () -> Unit, onOpenTask: (Long) -> Unit, planViewModel: PlanViewModel, appViewModel: AppViewModel) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    val plan by planViewModel.uiState.collectAsState()
    val settings by appViewModel.uiState.collectAsState()
    val w = workload(
        plan.entries,
        Instant.now(),
        ZoneId.systemDefault(),
        SchedulingMath.parseWorkTime(settings.workStart, LocalTime.of(9, 0)),
        SchedulingMath.parseWorkTime(settings.workEnd, LocalTime.of(18, 0)),
        parseRestDays(settings.restDays),
    )
    val over = w.overMs > 0
    val dayFmt = displayPattern("EEE d")

    Scaffold(topBar = { TimeTopBar(title = L("Workload"), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16).verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s12)
                    .background(if (over) status.warn else colors.accC2, Shapes.xl).padding(Dimens.s20),
            ) {
                val ink = if (over) status.warnInk else colors.accInk
                Text(L("Next 7 days"), style = MaterialTheme.typography.bodySmall, color = ink)
                Text(L("%1\$s planned", formatHm(w.plannedMs)), style = MaterialTheme.typography.displaySmall, color = ink)
                Text(L("against %1\$s of work time", formatHm(w.capacityMs)), style = MaterialTheme.typography.bodyMedium, color = ink)
                Text(
                    if (over) L("%1\$s more than fits — something has to move or shrink.", formatHm(w.overMs))
                    else L("%1\$s of work time still free.", (formatHm(w.capacityMs - w.plannedMs))),
                    style = MaterialTheme.typography.bodySmall,
                    color = ink,
                    modifier = Modifier.padding(top = Dimens.s8),
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("BY DAY"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s10))
                val maxMs = w.days.maxOfOrNull { maxOf(it.capacityMs, it.plannedMs) }?.coerceAtLeast(1) ?: 1
                w.days.forEach { d ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s6), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.date.format(dayFmt), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(Dimens.s34 * 2))
                        Box(Modifier.weight(1f).height(Dimens.s10).background(colors.ring, Shapes.pill)) {
                            // Capacity as the track's filled part, demand drawn over it.
                            if (d.capacityMs > 0) Box(Modifier.fillMaxWidth(d.capacityMs.toFloat() / maxMs).height(Dimens.s10).background(colors.accDim, Shapes.pill))
                            if (d.plannedMs > 0) {
                                Box(
                                    Modifier.fillMaxWidth(d.plannedMs.toFloat() / maxMs).height(Dimens.s10)
                                        .background(if (d.overMs > 0) status.warnInk else colors.acc, Shapes.pill),
                                )
                            }
                        }
                        Text(
                            when {
                                d.rest && d.plannedMs == 0L -> L("Rest")
                                d.rest -> L("%1\$s on a rest day", formatHm(d.plannedMs))
                                d.overMs > 0 -> "+${formatHm(d.overMs)}"
                                else -> "${formatHm(d.plannedMs)} / ${formatHm(d.capacityMs)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (d.overMs > 0) status.warnInk else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Dimens.s10),
                        )
                    }
                }
                Text(
                    // Not "lighter/darker": the accent is the lighter of the two in dark mode and the darker in light.
                    L("Each bar is that day's work time. The part filled from the left is what's booked, plus estimates of tasks due that day with no time booked."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.s8),
                )
            }

            if (w.unestimated.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(colors.card, Shapes.lg).padding(Dimens.s16)) {
                    Text(L("NOT COUNTED — NO ESTIMATE"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = Dimens.s6))
                    w.unestimated.forEach { e ->
                        Text(
                            e.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onOpenTask(e.id) }.padding(vertical = Dimens.s10),
                        )
                    }
                    Text(
                        L("Give these an estimate and they'll count toward the week."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(Modifier.padding(bottom = Dimens.s24))
        }
    }
}
