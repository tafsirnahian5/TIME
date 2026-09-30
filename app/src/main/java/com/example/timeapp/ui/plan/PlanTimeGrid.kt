package com.example.timeapp.ui.plan

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** One hour of grid: the 48dp minimum touch target, so every empty hour is a tappable slot. */
private val HourHeight: Dp = Dimens.touchMin
private val GutterWidth: Dp = Dimens.s34
private const val FIRST_VISIBLE_HOUR = 7
private val timeFmt get() = displayPattern("HH:mm")

/**
 * The Week (7 days) and Day (1 day) time grid — the PDF's release-1.1 Week view. Reserved
 * blocks are drawn at their real times; overlapping ones share the width side by side
 * ([layoutLanes]). Tapping an empty hour proposes a new block there, which still goes through
 * the C3 conflict check on save — the grid never places anything by itself.
 *
 * No canvas mock exists for this view; built from the shared tokens like Links.
 */
@Composable
internal fun PlanTimeGrid(
    days: List<LocalDate>,
    entries: List<Entry>,
    today: LocalDate,
    onOpenEntry: (Long) -> Unit,
    onEmptySlot: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    onDayHeader: ((LocalDate) -> Unit)? = null,
) {
    val zone = remember { ZoneId.systemDefault() }
    val colors = TimeThemeTokens.colors
    val density = LocalDensity.current
    val scroll = rememberScrollState(initial = with(density) { (HourHeight * FIRST_VISIBLE_HOUR).roundToPx() })
    val byId = entries.associateBy { it.id }

    Column(modifier = modifier) {
        if (days.size > 1) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s4)) {
                Box(Modifier.width(GutterWidth))
                days.forEach { date ->
                    val isToday = date == today
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (onDayHeader != null) Modifier.clickable { onDayHeader(date) } else Modifier)
                            .padding(vertical = Dimens.s4),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        Text(
                            date.dayOfWeek.name.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) colors.acc else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isToday) colors.acc else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll),
        ) {
            Column(modifier = Modifier.width(GutterWidth)) {
                (0 until 24).forEach { h ->
                    Text(
                        "%02d".format(h),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(GutterWidth).height(HourHeight),
                    )
                }
            }
            days.forEach { date ->
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(HourHeight * 24)
                        .border(Dimens.hairline, colors.line),
                ) {
                    // Empty-hour slots underneath the blocks.
                    Column {
                        (0 until 24).forEach { h ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(HourHeight)
                                    .border(Dimens.hairline, colors.line)
                                    .clickable { onEmptySlot(date.atTime(h, 0)) }
                                    .semantics { contentDescription = L("New booking, %1\$s %2\$s", date.format(displayPattern("EEEE d MMM")), java.time.LocalTime.of(h, 0).format(displayPattern("HH:mm"))) },
                            )
                        }
                    }
                    val spans = spansOn(entries, date, zone)
                    val lanes = layoutLanes(spans)
                    val laneAreaWidth = maxWidth
                    spans.forEach { span ->
                        val entry = byId[span.id] ?: return@forEach
                        val lane = lanes[span.id] ?: Lane(0, 1)
                        val laneWidth = laneAreaWidth / lane.columns
                        val top = HourHeight * (span.startMin / 60f)
                        val height = HourHeight * (maxOf(span.endMin - span.startMin, MIN_BLOCK_MINUTES) / 60f)
                        GridBlock(
                            entry = entry,
                            startLabel = LocalTime.of(span.startMin / 60 % 24, span.startMin % 60).format(timeFmt),
                            showTime = days.size == 1,
                            onClick = { onOpenEntry(entry.id) },
                            modifier = Modifier
                                .offset(x = laneWidth * lane.column, y = top)
                                .width(laneWidth)
                                .height(height)
                                .padding(Dimens.s2 / 2),
                        )
                    }
                    if (date == today) {
                        val now = LocalTime.now()
                        Box(
                            modifier = Modifier
                                .offset(y = HourHeight * ((now.hour * 60 + now.minute) / 60f))
                                .fillMaxWidth()
                                .height(Dimens.s2)
                                .background(TimeThemeTokens.status.dangerInk),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridBlock(entry: Entry, startLabel: String, showTime: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    val done = entry.status == EntryStatus.DONE
    val (bg, ink) = when {
        done -> status.neutral to status.neutralInk
        entry.kind == EntryKind.EVENT -> colors.accC2 to colors.accInk
        else -> colors.accC to colors.accInk
    }
    Box(
        modifier = modifier
            .background(bg, Shapes.xs)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${entry.title}, $startLabel" }
            .padding(horizontal = Dimens.s4, vertical = Dimens.s2),
    ) {
        Text(
            if (showTime) "$startLabel  ${entry.title}" else entry.title,
            style = MaterialTheme.typography.labelSmall,
            color = ink,
            maxLines = if (showTime) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
