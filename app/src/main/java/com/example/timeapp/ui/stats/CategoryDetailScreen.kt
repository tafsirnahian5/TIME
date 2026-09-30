package com.example.timeapp.ui.stats

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.timeapp.data.Category
import com.example.timeapp.data.categoryInsight
import com.example.timeapp.data.label
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.LocalDate
import java.time.ZoneId

/**
 * One work category over time — the PDF's skill view with XP and levels left out: tracked
 * time (all time and this month), sessions, tasks finished, the last eight weeks, and what
 * was worked on most recently. Reached from the Time report's category rows.
 */
@Composable
fun CategoryDetailScreen(
    category: Category?,
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    statsViewModel: StatsViewModel,
    planViewModel: PlanViewModel,
) {
    val colors = TimeThemeTokens.colors
    val sessions by statsViewModel.allSessions.collectAsState()
    val plan by planViewModel.uiState.collectAsState()
    Scaffold(topBar = { TimeTopBar(title = category?.name ?: L("Category"), onBack = onBack) }) { padding ->
        if (category == null) return@Scaffold
        val i = categoryInsight(category.id, plan.allEntries, sessions, LocalDate.now(), ZoneId.systemDefault())
        val tint = Color(category.colorArgb)
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16).verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s12).background(colors.accC2, Shapes.xl).padding(Dimens.s20)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(Dimens.s12).background(tint, CircleShape))
                    Text(L("All time"), style = MaterialTheme.typography.bodySmall, color = colors.accInk, modifier = Modifier.padding(start = Dimens.s8))
                }
                Text(formatHm(i.totalMs), style = MaterialTheme.typography.displaySmall, color = colors.accInk)
                Text(L("%1\$s this month", formatHm(i.thisMonthMs)), style = MaterialTheme.typography.bodyMedium, color = colors.accInk)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s12), horizontalArrangement = Arrangement.spacedBy(Dimens.s10)) {
                Stat(L("Sessions"), i.sessions.toString(), Modifier.weight(1f))
                Stat(L("Tasks finished"), i.completedTasks.toString(), Modifier.weight(1f))
            }
            Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(colors.card, Shapes.lg).padding(Dimens.s16)) {
                Text(L("LAST 8 WEEKS"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s14))
                val max = i.weekly.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.s8)) {
                    i.weekly.forEach { (week, ms) ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.fillMaxWidth().height(Dimens.s34 * 2), contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height(Dimens.s34 * 2 * (ms.toFloat() / max).coerceIn(0.04f, 1f))
                                        .background(if (ms > 0) tint else colors.ring, Shapes.xs),
                                )
                            }
                            Text(week.format(displayPattern("d/M")), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Dimens.s4))
                        }
                    }
                }
            }
            if (i.recent.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s16).background(colors.card, Shapes.lg).padding(Dimens.s16)) {
                    Text(L("RECENTLY WORKED ON"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s6))
                    i.recent.forEach { e ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenTask(e.id) }.padding(vertical = Dimens.s10),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(e.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(e.status.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text(
                L("Summed from your tracked sessions — the same numbers as the Time report."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Dimens.s16),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier.background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s14)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Dimens.s4))
    }
}
