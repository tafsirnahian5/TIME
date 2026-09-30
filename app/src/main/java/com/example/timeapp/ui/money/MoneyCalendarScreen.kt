package com.example.timeapp.ui.money

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.example.timeapp.data.leadingBlanks
import com.example.timeapp.data.moneyByDay
import com.example.timeapp.data.toTaka
import com.example.timeapp.data.weekdayInitials
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Money › History › Calendar (Phase 13, after XPENC): a month of days with what was spent each
 * day; tap one to see its transactions. Token-built; no design canvas covers it.
 */
@Composable
fun MoneyCalendarScreen(onBack: () -> Unit, viewModel: MoneyViewModel) {
    val ui by viewModel.uiState.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    val zone = ZoneId.systemDefault()
    val days = moneyByDay(ui.history, month, zone)
    val colors = TimeThemeTokens.colors
    Scaffold(topBar = { TimeTopBar(title = L("Money calendar"), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.s16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = L("Previous month")) }
                Text(month.format(displayPattern("MMMM yyyy")), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = L("Next month")) }
            }
            Text(
                L("Spent %1\$s · earned %2\$s", (days.values.sumOf { it.spentMinor }.toTaka()), (days.values.sumOf { it.incomeMinor }.toTaka())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimens.s8),
            )
            Row(Modifier.fillMaxWidth()) {
                weekdayInitials().forEach { Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
            val first = month.atDay(1)
            val cells: List<LocalDate?> = List(leadingBlanks(first)) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        if (date == null) {
                            Box(Modifier.weight(1f))
                        } else {
                            val d = days[date]
                            val on = date == selected
                            Column(
                                modifier = Modifier.weight(1f).heightIn(min = Dimens.touchMin + Dimens.s8).padding(Dimens.s2 / 2)
                                    .background(if (on) colors.accC else Color.Transparent, Shapes.sm)
                                    .border(Dimens.hairline, if (d != null) colors.line else Color.Transparent, Shapes.sm)
                                    .clickable { selected = date }
                                    .semantics { contentDescription = L("%1\$s: spent %2\$s", date.dayOfMonth, ((d?.spentMinor ?: 0).toTaka())) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(com.example.timeapp.data.Lang.digits(date.dayOfMonth.toString()), style = MaterialTheme.typography.labelMedium)
                                if (d != null && d.spentMinor != 0L) Text(compact(d.spentMinor), style = MaterialTheme.typography.labelSmall, color = TimeThemeTokens.status.warnInk, maxLines = 1)
                                if (d != null && d.incomeMinor > 0) Text("+" + compact(d.incomeMinor), style = MaterialTheme.typography.labelSmall, color = TimeThemeTokens.status.okInk, maxLines = 1)
                            }
                        }
                    }
                    repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                }
            }
            selected?.let { day ->
                val d = days[day]
                Text(day.format(displayPattern("EEEE d MMM")), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Dimens.s16, bottom = Dimens.s6))
                if (d == null) Text(L("Nothing recorded."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                d?.rows?.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.s6)) {
                        Text(r.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(r.paidMinor.toTaka(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** "1.2k" for a cell; the full amount is in the day's list below. */
private fun compact(minor: Long): String {
    val taka = minor / 100.0
    return if (kotlin.math.abs(taka) >= 1000) "%.1fk".format(taka / 1000) else "%.0f".format(taka)
}
