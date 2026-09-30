package com.example.timeapp.ui.workday

import com.example.timeapp.ui.components.SettingsGroup
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.timeapp.ui.AppViewModel
import com.example.timeapp.ui.components.SegmentedToggle
import com.example.timeapp.ui.components.TimePickerPrompt
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.plan.SchedulingMath
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.DayOfWeek
import com.example.timeapp.data.shortLabel
import java.time.LocalTime

/**
 * Settings › Workday & calendar (the PDF's "work hours, week start, rest days … default
 * calendar view"). Work hours and rest days could only be set during onboarding — which never
 * even asked for the hours — and the Settings row went straight to My Workday. Built from
 * the shared tokens.
 */
@Composable
fun WorkdaySettingsScreen(onBack: () -> Unit, onOpenMyWorkday: () -> Unit, appViewModel: AppViewModel) {
    val s by appViewModel.uiState.collectAsState()
    var picking by remember { mutableStateOf<String?>(null) }
    val start = SchedulingMath.parseWorkTime(s.workStart, LocalTime.of(9, 0))
    val end = SchedulingMath.parseWorkTime(s.workEnd, LocalTime.of(18, 0))
    val hm = displayPattern("HH:mm")
    val colors = TimeThemeTokens.colors

    Scaffold(topBar = { TimeTopBar(title = L("Workday & calendar"), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.s16)) {
            SettingsGroup(L("MY WORKDAY")) {
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable(onClick = onOpenMyWorkday).padding(Dimens.s14), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(L("Today's workday"), style = MaterialTheme.typography.bodyLarge)
                        Text(L("Start, breaks, end and the timeline"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SettingsGroup(L("WORK HOURS")) {
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { picking = "start" }.padding(Dimens.s14)) {
                    Text(L("Start"), modifier = Modifier.weight(1f)); Text(start.format(hm), color = colors.acc)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { picking = "end" }.padding(Dimens.s14)) {
                    Text(L("End"), modifier = Modifier.weight(1f)); Text(end.format(hm), color = colors.acc)
                }
                Text(
                    L("Used for rescheduling suggestions and the workload view."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimens.s14, end = Dimens.s14, bottom = Dimens.s12),
                )
            }
            SettingsGroup(L("REST DAYS")) {
                // Wraps instead of scrolling sideways (the last day was cut off the screen), in the
                // order of the chosen week. Stored by the English short name, as before.
                val firstDay = runCatching { DayOfWeek.valueOf(s.weekStartsOn) }.getOrDefault(DayOfWeek.MONDAY)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.padding(Dimens.s12),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
                    verticalArrangement = Arrangement.spacedBy(Dimens.s8),
                ) {
                    (0L until 7L).map { firstDay.plus(it).shortLabel() }.forEach { day ->
                        val selected = day in s.restDays
                        Text(
                            L(day),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
                                .border(Dimens.hairline, TimeThemeTokens.status.controlOutline, Shapes.pill)
                                .semantics { this.selected = selected }.clickable { appViewModel.setRestDays(if (selected) s.restDays - day else s.restDays + day) }
                                .heightIn(min = Dimens.touchMin)
                                .padding(horizontal = Dimens.s14, vertical = Dimens.s14),
                        )
                    }
                }
            }
            SettingsGroup(L("WEEK STARTS ON")) {
                val options = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY)
                val current = runCatching { DayOfWeek.valueOf(s.weekStartsOn) }.getOrDefault(DayOfWeek.MONDAY)
                SegmentedToggle(
                    options = listOf(L("Saturday"), L("Sunday"), L("Monday")),
                    selectedIndex = options.indexOf(current).coerceAtLeast(0),
                    onSelect = { appViewModel.setWeekStartsOn(options[it]) },
                    modifier = Modifier.padding(Dimens.s12),
                )
                Text(
                    L("Weekly reports, targets, habit weeks and the calendar all start here."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimens.s14, end = Dimens.s14, bottom = Dimens.s12),
                )
            }
            SettingsGroup(L("CALENDAR OPENS ON")) {
                val views = listOf("MONTH", "WEEK", "DAY", "AGENDA")
                SegmentedToggle(
                    options = listOf(L("Month"), L("Week"), L("Day"), L("Agenda")),
                    selectedIndex = views.indexOf(s.defaultCalendarView).coerceAtLeast(0),
                    onSelect = { appViewModel.setDefaultCalendarView(views[it]) },
                    modifier = Modifier.padding(Dimens.s12),
                )
            }
        }
    }
    when (picking) {
        "start" -> TimePickerPrompt(start) { t -> picking = null; t?.let { if (it.isBefore(end)) appViewModel.setWorkHours(it.format(hm), end.format(hm)) } }
        "end" -> TimePickerPrompt(end) { t -> picking = null; t?.let { if (it.isAfter(start)) appViewModel.setWorkHours(start.format(hm), it.format(hm)) } }
        else -> Unit
    }
}

