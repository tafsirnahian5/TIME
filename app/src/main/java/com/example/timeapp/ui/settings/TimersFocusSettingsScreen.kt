package com.example.timeapp.ui.settings

import com.example.timeapp.data.L

import android.provider.Settings
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
import androidx.compose.material.icons.rounded.Delete
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
import com.example.timeapp.data.FocusMode
import com.example.timeapp.ui.AppViewModel
import com.example.timeapp.ui.components.MinutesDialog
import com.example.timeapp.ui.components.SettingsDivider
import com.example.timeapp.ui.components.SettingsGroup
import com.example.timeapp.ui.components.SettingsSwitchRow
import com.example.timeapp.ui.components.SettingsValueRow
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.focus.FocusViewModel
import com.example.timeapp.ui.theme.Dimens

/**
 * Settings › Timers & focus (Phase 15; layout from the Phase 14–15 canvas): Pomodoro lengths
 * and auto-start, how a timer ending behaves (alarm-style ringing, the system's own sound and
 * vibration for that channel, the break reminder), and the saved presets. Everything here was
 * already settable from inside Focus; this is the one place that gathers it.
 */
@Composable
fun TimersFocusSettingsScreen(onBack: () -> Unit, focusViewModel: FocusViewModel, appViewModel: AppViewModel) {
    val focus by focusViewModel.uiState.collectAsState()
    val presets by focusViewModel.presets.collectAsState()
    val settings by appViewModel.uiState.collectAsState()
    val rt = focus.runtime
    var editing by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TimeTopBar(title = L("Timers & focus"), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16).verticalScroll(rememberScrollState())) {
            SettingsGroup(L("POMODORO")) {
                SettingsValueRow(L("Work"), L("%1\$s min", rt.workMinutes)) { editing = "work" }
                SettingsDivider()
                SettingsValueRow(L("Short break"), L("%1\$s min", rt.breakMinutes)) { editing = "break" }
                SettingsDivider()
                SettingsValueRow(L("Long break, every %1\$sth", rt.cyclesPerLongBreak), L("%1\$s min", rt.longBreakMinutes)) { editing = "long" }
                SettingsDivider()
                SettingsSwitchRow(L("Start the next phase by itself"), rt.autoStart) { focusViewModel.setAutoStart(it) }
            }
            SettingsGroup(L("COUNTDOWN")) {
                SettingsValueRow(L("Default length"), L("%1\$s min", rt.countdownMinutes)) { editing = "countdown" }
            }
            SettingsGroup(L("WHEN A TIMER ENDS")) {
                SettingsSwitchRow(
                    L("Ring like an alarm"), settings.timerAlarmStyle,
                    subtitle = L("Alarm sound until you answer, even in quiet hours"),
                ) { appViewModel.setTimerAlarmStyle(it) }
                SettingsDivider()
                SettingsValueRow(
                    L("Break reminder after"),
                    if (settings.focusBreakReminderMinutes > 0) L("%1\$s min", settings.focusBreakReminderMinutes) else L("Off"),
                ) { editing = "breakReminder" }
            }
            AlarmSoundSection(enabled = settings.timerAlarmStyle)
            SettingsGroup(L("PRESETS")) {
                if (presets.isEmpty()) {
                    Text(
                        L("Save one from Countdown or Pomodoro in Focus."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Dimens.s16),
                    )
                }
                presets.forEachIndexed { i, p ->
                    if (i > 0) SettingsDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin + Dimens.s8).padding(start = Dimens.s16), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (p.mode == FocusMode.POMODORO) L("Pomodoro · %1\$s + %2\$s", p.minutes, (p.breakMinutes ?: rt.breakMinutes)) else L("Countdown · %1\$s min", p.minutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { focusViewModel.deletePreset(p.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = L("Delete %1\$s", p.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Box(Modifier.padding(bottom = Dimens.s24))
        }
    }

    when (editing) {
        "work" -> MinutesDialog(L("Work"), rt.workMinutes, 1..240, { editing = null }, { focusViewModel.setWorkMinutes(it); editing = null })
        "break" -> MinutesDialog(L("Short break"), rt.breakMinutes, 1..120, { editing = null }, { focusViewModel.setBreakMinutes(it); editing = null })
        "long" -> MinutesDialog(L("Long break"), rt.longBreakMinutes, 1..120, { editing = null }, { focusViewModel.setLongBreakMinutes(it); editing = null })
        "countdown" -> MinutesDialog(L("Countdown length"), rt.countdownMinutes, 1..480, { editing = null }, { focusViewModel.setCountdownMinutes(it); editing = null })
        "breakReminder" -> MinutesDialog(
            L("Break reminder"), settings.focusBreakReminderMinutes, 0..480, { editing = null },
            { appViewModel.setFocusBreakReminderMinutes(it); editing = null },
            caption = L("0 turns it off"),
        )
    }
}
