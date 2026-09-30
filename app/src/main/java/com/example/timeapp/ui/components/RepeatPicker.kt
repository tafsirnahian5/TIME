package com.example.timeapp.ui.components

import com.example.timeapp.data.L
import com.example.timeapp.data.chipLabel

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.timeapp.data.Recurrence
import com.example.timeapp.data.RecurrenceFreq
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

private val options = listOf<Pair<String, Recurrence?>>(
    L("Never") to null,
    L("Daily") to Recurrence(RecurrenceFreq.DAILY),
    L("Weekly") to Recurrence(RecurrenceFreq.WEEKLY),
    L("Monthly") to Recurrence(RecurrenceFreq.MONTHLY),
)

/**
 * The repeat rule on a task or planned cost. Replaces the old hardcoded "Repeat · Monthly,
 * 15th" caption, which looked like a setting but was static text.
 *
 * Each repeat produces a **separate record**, so paying this month's bill leaves next
 * month's untouched — the caption says so, because that is the part people assume wrong.
 */
@Composable
fun RepeatPicker(
    value: Recurrence?,
    onValueChange: (Recurrence?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledCaption: String = L("Pick a due date above to repeat this."),
    /** Day of month of the first occurrence; from the 29th on, a monthly rule asks what to
     * do in months without that day. */
    anchorDay: Int? = null,
    /** Weekday of the first occurrence: plain "Weekly" (no days picked) repeats on it, so it's
     * shown picked — the row used to show no day at all, which read as "no day". */
    anchorWeekday: java.time.DayOfWeek? = null,
) {
    val colors = TimeThemeTokens.colors
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s8), verticalAlignment = Alignment.CenterVertically) {
            Text(L("Repeat"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        if (!enabled) {
            Text(disabledCaption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEach { (label, rule) ->
                val selected = value?.freq == rule?.freq
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = Dimens.s8)
                        .background(if (selected) colors.accC else androidx.compose.ui.graphics.Color.Transparent, Shapes.pill)
                        // Re-tapping the current choice keeps its weekdays instead of resetting them.
                        .semantics { this.selected = selected }.clickable { if (value?.freq != rule?.freq) onValueChange(rule) }
                        .padding(horizontal = Dimens.s14, vertical = Dimens.s8),
                )
            }
        }
        if (value?.freq == RecurrenceFreq.WEEKLY) {
            // Which days, for a habit-shaped "Mon, Wed, Fri". None picked means the same
            // weekday as the first occurrence, which is what plain "Weekly" always meant.
            val effective = if (value.weekdays.isEmpty() && anchorWeekday != null) setOf(anchorWeekday) else value.weekdays
            Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s8)) {
                java.time.DayOfWeek.entries.forEach { day ->
                    val on = day in effective
                    Text(
                        day.chipLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Dimens.touchMin)
                            .background(if (on) colors.accC else androidx.compose.ui.graphics.Color.Transparent, Shapes.pill)
                            .semantics { this.selected = on }.clickable {
                                val next = if (on) effective - day else effective + day
                                // Just the first occurrence's own day is plain weekly; the last
                                // day can't be taken away (a weekly repeat needs one).
                                val stored = if (next.isEmpty() || next == setOfNotNull(anchorWeekday)) emptySet() else next
                                onValueChange(value.copy(weekdays = stored))
                            }
                            .wrapContentHeight(),
                    )
                }
            }
        }
        if (value?.freq == RecurrenceFreq.MONTHLY && anchorDay != null && anchorDay > 28) {
            Text(
                L("In a month without a %1\$s", ordinal(anchorDay)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.s8),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s4)) {
                listOf(
                    L("Use its last day") to com.example.timeapp.data.MonthEndRule.LAST_VALID_DAY,
                    L("Skip that month") to com.example.timeapp.data.MonthEndRule.SKIP_MONTH,
                ).forEach { (label, rule) ->
                    val selected = value.monthEnd == rule
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = Dimens.s8)
                            .heightIn(min = Dimens.touchMin)
                            .background(if (selected) colors.accC else androidx.compose.ui.graphics.Color.Transparent, Shapes.pill)
                            .semantics { this.selected = selected }.clickable { onValueChange(value.copy(monthEnd = rule)) }
                            .padding(horizontal = Dimens.s14)
                            .wrapContentHeight(),
                    )
                }
            }
        }
        if (value != null) {
            Text(
                L("%1\$s — each repeat is its own record, so settling one never touches the next.", value.label()),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Dimens.s8),
            )
        }
    }
}

private fun ordinal(day: Int) = "$day" + when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}
