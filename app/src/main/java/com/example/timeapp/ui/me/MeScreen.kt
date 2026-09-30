package com.example.timeapp.ui.me

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height

import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.Achievement
import com.example.timeapp.ui.MiscViewModel
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Me — the Phase 15 attributes (plain ratios with their formulas, from the Phase 14–15 canvas)
 * above G1's Achievements: milestones, not scores. Nothing here unlocks functionality.
 */
@Composable
fun MeScreen(onBack: () -> Unit, viewModel: MiscViewModel, meViewModel: MeViewModel, userName: String, onOpen: (String) -> Unit = {}) {
    val achievements by viewModel.achievements.collectAsState()
    val me by meViewModel.uiState.collectAsState()
    val earned = achievements.filter { it.earnedLabel != null }
    // The PDF's achievement collections: each group with its own earned count.
    val collections = com.example.timeapp.data.repository.AchievementsRepository.COLLECTIONS
        .map { name -> name to achievements.filter { it.collection == name } }
        .filter { it.second.isNotEmpty() }

    Scaffold(topBar = { TimeTopBar(title = L("Me"), onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            item { AttributesHeader(userName, me, meViewModel::setPeriod) }
            // Phase 18C: only measures that have data, each with a plain meaning; the formulas sit
            // behind "How your week is measured". It opened on five "Not enough data" formula cards.
            item { WeekSummary(me.attributes) }
            // The reports existed all along, three levels deep in Settings; they belong here.
            item { ReportsCard(onOpen) }
            item { SectionLabel(L("ACHIEVEMENTS")) }
            item {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = Dimens.s14)) {
                    Text(L("%1\$s", earned.size), style = MaterialTheme.typography.displaySmall)
                    // The real count — this said "of 12" whatever the list held.
                    Text(L(" of %1\$s earned", achievements.size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Dimens.s8))
                    Box(modifier = Modifier.weight(1f))
                    Text(L("milestones, not scores"), style = MaterialTheme.typography.bodySmall)
                }
            }
            collections.forEach { (name, list) ->
                item(key = "c-$name") {
                    SectionLabel(L("%1\$s · %2\$s of %3\$s", name.uppercase(), (list.count { it.earnedLabel != null }), list.size))
                }
                // Earned first within a collection, then what's in progress, nearest first.
                items(list.sortedWith(compareBy({ it.earnedLabel == null }, { -(it.progressFraction ?: 1f) }))) {
                    if (it.earnedLabel != null) EarnedRow(it) else InProgressCard(it)
                }
            }
            item {
                Row(modifier = Modifier.padding(vertical = Dimens.s16)) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                    Text(
                        L("Milestones only — nothing here unlocks features, and there are no points or scores."),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = Dimens.s10),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s10, bottom = Dimens.s10))
}

/** Every icon name AchievementsRepository uses. The derived milestones' names were never
 * added here, so each one — earned or not — fell through to a lock. */
private fun iconFor(name: String) = when (name) {
    "star" -> Icons.Rounded.Star
    "rate_review" -> Icons.Rounded.RateReview
    "verified" -> Icons.Rounded.Verified
    "event_available" -> Icons.Rounded.EventAvailable
    "savings" -> Icons.Rounded.Savings
    "check_circle" -> Icons.Rounded.CheckCircle
    "timer" -> Icons.Rounded.Timer
    "payments" -> Icons.Rounded.Payments
    "event_note" -> Icons.AutoMirrored.Rounded.EventNote
    "cloud_done" -> Icons.Rounded.CloudDone
    "event_repeat" -> Icons.Rounded.EventRepeat
    "local_fire_department" -> Icons.Rounded.LocalFireDepartment
    "task_alt" -> Icons.Rounded.TaskAlt
    else -> Icons.Rounded.Star
}

@Composable
private fun EarnedRow(achievement: Achievement) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s10).background(colors.accC2, Shapes.lg).padding(Dimens.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(44.dp).background(TimeThemeTokens.colors.card, CircleShape), contentAlignment = Alignment.Center) {
            Icon(iconFor(achievement.icon), contentDescription = null, tint = colors.acc)
        }
        Column(modifier = Modifier.padding(start = Dimens.s14)) {
            Text(achievement.title, style = MaterialTheme.typography.bodyLarge)
            Text(achievement.earnedLabel ?: "", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InProgressCard(achievement: Achievement) {
    val colors = TimeThemeTokens.colors
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s10).background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s14)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Dimens.s10)) {
            Box(modifier = Modifier.size(44.dp).background(colors.accS2, CircleShape), contentAlignment = Alignment.Center) {
                Icon(iconFor(achievement.icon), contentDescription = null, tint = TimeThemeTokens.status.dim)
            }
            Column(modifier = Modifier.padding(start = Dimens.s14)) {
                Text(achievement.title, style = MaterialTheme.typography.bodyLarge)
                Text(achievement.progressLabel ?: "", style = MaterialTheme.typography.bodySmall)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().size(5.dp).background(colors.ring, Shapes.xs)) {
            Box(modifier = Modifier.fillMaxWidth(achievement.progressFraction ?: 0f).size(5.dp).background(colors.acc, Shapes.xs))
        }
    }
}

@Composable
private fun AttributesHeader(userName: String, me: MeUiState, onPeriod: (MePeriod) -> Unit) {
    val colors = TimeThemeTokens.colors
    val fmt = displayPattern("EEE d MMM")
    Column(modifier = Modifier.padding(top = Dimens.s8, bottom = Dimens.s12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).background(colors.accC, CircleShape), contentAlignment = Alignment.Center) {
                Text(userName.firstOrNull()?.uppercase() ?: "•", style = MaterialTheme.typography.titleMedium, color = colors.accInk)
            }
            Column(modifier = Modifier.padding(start = Dimens.s12)) {
                if (userName.isNotBlank()) Text(userName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${me.period.label} · ${me.from.format(fmt)} – ${java.time.LocalDate.now().format(fmt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        com.example.timeapp.ui.components.SegmentedToggle(
            options = MePeriod.entries.map { it.label },
            selectedIndex = me.period.ordinal,
            onSelect = { onPeriod(MePeriod.entries[it]) },
            modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s12),
        )
    }
}

/** What each measure means, in a line — keyed by the attribute's (translated) name. */
private fun meaningOf(name: String): String = when (name) {
    L("Focus") -> L("Work time compared with your weekly target")
    L("Delivery") -> L("How many of Today's 3 you finished")
    L("Consistency") -> L("Workdays with at least an hour of work")
    L("Planning") -> L("Booked time you actually started on time")
    L("Money review") -> L("Weeks you looked back over your money")
    else -> ""
}

@Composable
private fun WeekSummary(attributes: List<com.example.timeapp.data.Attribute>) {
    val colors = TimeThemeTokens.colors
    var showHow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val withData = attributes.filter { it.value != null }
    Column(Modifier.fillMaxWidth().padding(bottom = Dimens.s12).background(colors.card, Shapes.lg).padding(Dimens.s16)) {
        Text(L("Your week"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        if (withData.isEmpty()) {
            Text(
                L("Nothing to show yet. As you track work, finish Today's 3 and review your week, you'll see how it's going here — plain numbers, no points or scores."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s8),
            )
        }
        withData.forEach { a ->
            Column(Modifier.padding(top = Dimens.s12)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.bodyLarge)
                        Text(meaningOf(a.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(a.value!!, style = MaterialTheme.typography.titleMedium)
                }
                a.fraction?.let { f ->
                    Box(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8).height(6.dp).background(colors.line, Shapes.pill)) {
                        Box(modifier = Modifier.fillMaxWidth(f).height(6.dp).background(colors.acc, Shapes.pill))
                    }
                }
            }
        }
        androidx.compose.material3.TextButton(onClick = { showHow = !showHow }, modifier = Modifier.padding(top = Dimens.s4)) {
            Text(if (showHow) L("Hide how it's measured") else L("How your week is measured"))
        }
        if (showHow) {
            attributes.forEach { a ->
                Column(Modifier.padding(vertical = Dimens.s6)) {
                    Text(a.name + " — " + meaningOf(a.name), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        (a.value ?: L("Not enough data yet")) + " · " + a.formula,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsCard(onOpen: (String) -> Unit) {
    val colors = TimeThemeTokens.colors
    Text(L("REPORTS"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s10, bottom = Dimens.s10))
    Column(Modifier.fillMaxWidth().padding(bottom = Dimens.s12).background(colors.card, Shapes.lg)) {
        listOf(
            Triple(Icons.Rounded.Timer, L("Time") to L("Where your hours went — by day, category and project"), com.example.timeapp.ui.Routes.TimeStats),
            Triple(Icons.Rounded.Payments, L("Money") to L("Income, spending by category, bills vs what you paid"), com.example.timeapp.ui.Routes.MoneyStats),
            Triple(Icons.Rounded.EventRepeat, L("Habits") to L("Streaks, done-rates and a year at a glance"), com.example.timeapp.ui.Routes.HabitStats),
            Triple(Icons.Rounded.DateRange, L("Workload") to L("The next 7 days: work booked vs hours you have"), com.example.timeapp.ui.Routes.Workload),
            Triple(Icons.Rounded.RateReview, L("Weekly review") to L("Look back at your week and plan the next"), com.example.timeapp.ui.Routes.WeeklyReview),
        ).forEach { (icon, text, route) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onOpen(route) }.padding(horizontal = Dimens.s16, vertical = Dimens.s10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).background(colors.accC, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = colors.accInk, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f).padding(start = Dimens.s12)) {
                    Text(text.first, style = MaterialTheme.typography.bodyLarge)
                    Text(text.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
