package com.example.timeapp.ui.setup

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.defaultMinSize
import com.example.timeapp.data.displayLabel
import com.example.timeapp.data.shortLabel
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * A2 — Setup 2: your week. Every control here is real now: this step used to show work hours,
 * a week start and "Asia/Dhaka" as fixed text, a "daily target" that was saved nowhere and a
 * currency row whose arrow did nothing — while Continue always saved 09:00–18:00. All of it is
 * editable later in Settings › Workday & calendar.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SetupWorkScreen(
    onBack: () -> Unit,
    onFinish: (startTime: String, endTime: String, restDays: Set<String>, weekStart: java.time.DayOfWeek) -> Unit,
) {
    val colors = TimeThemeTokens.colors
    var restDays by remember { mutableStateOf(setOf("Fri", "Sat")) }
    var start by remember { mutableStateOf(java.time.LocalTime.of(9, 0)) }
    var end by remember { mutableStateOf(java.time.LocalTime.of(18, 0)) }
    var weekStart by remember { mutableStateOf(java.time.DayOfWeek.MONDAY) }
    var picking by remember { mutableStateOf<String?>(null) }
    val timeFormat = com.example.timeapp.data.displayPattern("HH:mm")
    val weekStarts = listOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY)

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.screenH)) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = L("Back"))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.s6),
                ) {
                    Box(modifier = Modifier.weight(1f).size(4.dp).background(colors.acc, Shapes.xs))
                    Box(modifier = Modifier.weight(1f).size(4.dp).background(colors.acc, Shapes.xs))
                    Box(modifier = Modifier.weight(1f).size(4.dp).background(colors.ring, Shapes.xs))
                }
                Text(L("STEP 2 OF 3"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s12))
                Text(L("Your week"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Dimens.s10, bottom = Dimens.s8))
                Text(
                    L("When you usually work and which days you rest — T!ME uses them to plan your time and for your weekly reports. You can change all of it later in Settings."),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Dimens.s20),
                )
                Column(modifier = Modifier.fillMaxWidth().background(TimeThemeTokens.colors.card, Shapes.lg).border(1.dp, colors.line, Shapes.lg)) {
                    InfoRow(Icons.Rounded.Schedule, L("Work starts"), start.format(timeFormat), onClick = { picking = "start" })
                    InfoRow(Icons.Rounded.Schedule, L("Work ends"), end.format(timeFormat), onClick = { picking = "end" })
                    InfoRow(Icons.Rounded.Public, L("Time zone"), L("%1\$s · follows your phone", java.time.ZoneId.systemDefault().id))
                }
                if (!end.isAfter(start)) {
                    Text(L("Work ends before it starts — pick a later end time."), style = MaterialTheme.typography.bodySmall, color = TimeThemeTokens.status.dangerInk, modifier = Modifier.padding(top = Dimens.s8))
                }
                Text(L("Week starts on"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Dimens.s22, bottom = Dimens.s10))
                com.example.timeapp.ui.components.SegmentedToggle(
                    options = listOf(L("Saturday"), L("Sunday"), L("Monday")),
                    selectedIndex = weekStarts.indexOf(weekStart),
                    onSelect = { weekStart = weekStarts[it] },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(L("Rest days"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Dimens.s22, bottom = Dimens.s10))
                // Wraps, so all seven days are on screen — Sunday used to be off the right edge.
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.s8),
                    verticalArrangement = Arrangement.spacedBy(Dimens.s8),
                ) {
                    java.time.DayOfWeek.entries.forEach { d ->
                        val day = d.shortLabel()
                        val selected = day in restDays
                        Row(
                            modifier = Modifier
                                .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
                                .border(if (selected) 0.dp else 1.dp, TimeThemeTokens.status.controlOutline, Shapes.pill)
                                .semantics { this.selected = selected }.clickable { restDays = if (selected) restDays - day else restDays + day }
                                .defaultMinSize(minHeight = Dimens.touchMin)
                                .padding(horizontal = Dimens.s14),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accInk, modifier = Modifier.size(16.dp).padding(end = Dimens.s4))
                            Text(d.displayLabel(), style = MaterialTheme.typography.labelMedium, color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    L("A rest day never counts as a missed day."),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.s8, bottom = Dimens.s16),
                )
            }
            Button(
                onClick = { onFinish(start.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), end.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), restDays, weekStart) },
                enabled = end.isAfter(start),
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8, bottom = Dimens.s16),
            ) { Text(L("Continue")) }
        }
    }

    picking?.let { which ->
        com.example.timeapp.ui.components.TimePickerPrompt(initial = if (which == "start") start else end) { picked ->
            if (picked != null) { if (which == "start") start = picked else end = picked }
            picking = null
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = Dimens.touchMin)
            .padding(horizontal = Dimens.s16, vertical = Dimens.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.acc)
        Text(label, modifier = Modifier.weight(1f).padding(start = Dimens.s14))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (onClick != null) colors.acc else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
