package com.example.timeapp.ui.components

import com.example.timeapp.data.L

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.timeapp.data.ReminderTiming
import com.example.timeapp.ui.theme.Dimens
import java.time.LocalTime

/** Which reminder times a form offers. */
enum class ReminderTimingKind(val presets: List<ReminderTiming>) {
    TASK(listOf(ReminderTiming.OnDay(0, LocalTime.of(9, 0)), ReminderTiming.OnDay(0, LocalTime.of(8, 0)), ReminderTiming.OnDay(-1, LocalTime.of(20, 0)))),
    PLAN(listOf(ReminderTiming.OnDay(-1, LocalTime.of(9, 0)), ReminderTiming.OnDay(0, LocalTime.of(9, 0)), ReminderTiming.OnDay(-3, LocalTime.of(9, 0)))),
    EVENT(listOf(ReminderTiming.BeforeStart(0), ReminderTiming.BeforeStart(10), ReminderTiming.BeforeStart(30), ReminderTiming.BeforeStart(60))),
}

/**
 * "When" under a form's Remind me switch (Phase 11: per-reminder custom time). Offers a few
 * common choices and, for date-based reminders, any time on the day or the day before.
 */
@Composable
fun ReminderTimingRow(kind: ReminderTimingKind, value: ReminderTiming, onChange: (ReminderTiming) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf<Int?>(null) }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { open = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(L("When"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value.label(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(L("Remind me")) },
            text = {
                Column {
                    kind.presets.forEach { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { onChange(p); open = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = p == value, onClick = { onChange(p); open = false })
                            Text(p.label())
                        }
                    }
                    if (kind != ReminderTimingKind.EVENT) {
                        TextButton(onClick = { open = false; pickingTime = 0 }) { Text(L("Another time on the day…")) }
                        TextButton(onClick = { open = false; pickingTime = -1 }) { Text(L("Another time the day before…")) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(L("Close")) } },
        )
    }
    pickingTime?.let { offset ->
        val initial = (value as? ReminderTiming.OnDay)?.time ?: LocalTime.of(9, 0)
        TimePickerPrompt(initial) { t ->
            pickingTime = null
            t?.let { onChange(ReminderTiming.OnDay(offset, it)) }
        }
    }
}
