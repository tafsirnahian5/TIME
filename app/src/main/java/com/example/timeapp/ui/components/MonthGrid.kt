package com.example.timeapp.ui.components

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeThemeTokens
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

/** Six weeks from the week holding the 1st. It used to start on the phone locale's week start
 * while the headings were always M–S, so columns and headings could disagree. */
fun buildMonthCells(month: YearMonth, firstDay: java.time.DayOfWeek = com.example.timeapp.data.WeekConfig.firstDay): List<LocalDate> {
    val first = month.atDay(1)
    val start = first.minusDays(com.example.timeapp.data.leadingBlanks(first, firstDay).toLong())
    return (0 until 42).map { start.plusDays(it.toLong()) }
}

/** 7-column month grid, up to 3 marker dots per day. */
@Composable
fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate?,
    markersFor: (LocalDate) -> List<MarkerKind>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TimeThemeTokens.colors
    val days = buildMonthCells(month)
    val weeks = days.chunked(7)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            com.example.timeapp.data.weekdayInitials().forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = Dimens.s8),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val inMonth = date.month == month.month
                    val selected = date == selectedDate
                    val markers = markersFor(date).take(3)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.85f)
                            // TalkBack hears the whole date and how much is on it, not "28".
                            .semantics {
                                contentDescription = date.format(displayPattern("EEEE d MMMM")) +
                                    if (markers.isEmpty()) "" else L(", %1\$s marked", markers.size)
                                this.selected = selected
                            }
                            .clickable { onSelect(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    if (selected) colors.accC else Color.Transparent,
                                    CircleShape,
                                )
                                .padding(Dimens.s6),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (!inMonth) TimeThemeTokens.status.dim else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            markers.forEach { MarkerDot(it) }
                        }
                    }
                }
            }
        }
    }
}
