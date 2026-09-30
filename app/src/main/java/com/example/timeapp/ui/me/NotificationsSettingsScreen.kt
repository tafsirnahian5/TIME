package com.example.timeapp.ui.me

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.timeapp.data.NotifyChannel
import com.example.timeapp.data.notify.ReminderScheduler
import com.example.timeapp.ui.AppViewModel
import com.example.timeapp.ui.components.TimeTopBar
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

private const val TEST_CHANNEL_ID = "time_test_notifications"

/** G3 — Notifications: honest about platform limits. The channel toggles, redact switch and
 * quiet hours all gate real delivery now (see `data/notify/ReminderReceiver`), and the banner
 * reports the platform's live permission/exact-alarm state rather than asserting either. */
@Composable
fun NotificationsSettingsScreen(onBack: () -> Unit, viewModel: AppViewModel) {
    val colors = TimeThemeTokens.colors
    val context = LocalContext.current
    val ui by viewModel.uiState.collectAsState()

    fun postTestNotification() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(TEST_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(TEST_CHANNEL_ID, "Test notifications", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(com.example.timeapp.R.drawable.ic_stat_time)
            .setContentTitle("T!ME")
            .setContentText(L("This is what a T!ME notification looks like."))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, notification)
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) postTestNotification()
    }

    Scaffold(topBar = { TimeTopBar(title = L("Notifications"), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = Dimens.s16)) {  // scrolls: quiet hours and the test notification were clipped off the bottom
            // Real, live status — both values are read from the platform on each composition
            // rather than asserted, so this never claims a capability the OS has revoked.
            val notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
            val exactAllowed = remember { ReminderScheduler(context).canScheduleExact() }
            if (!notificationsAllowed || !exactAllowed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s14).background(TimeThemeTokens.status.warn, Shapes.lg).padding(Dimens.s14),
                ) {
                    Icon(Icons.Rounded.Alarm, contentDescription = null, tint = TimeThemeTokens.status.warnInk)
                    Column(modifier = Modifier.padding(start = Dimens.s12)) {
                        Text(
                            if (!notificationsAllowed) L("Notifications are turned off") else L("Exact alarms are not allowed"),
                            style = MaterialTheme.typography.titleSmall, color = TimeThemeTokens.status.warnInk,
                        )
                        Text(
                            if (!notificationsAllowed) {
                                L("Reminders are still scheduled and kept, but nothing can be shown until you allow notifications.")
                            } else {
                                L("Reminders will fire approximately — usually within a few minutes of the set time.")
                            },
                            style = MaterialTheme.typography.bodySmall, color = TimeThemeTokens.status.warnInk,
                            modifier = Modifier.padding(top = Dimens.s4),
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = Dimens.s10)
                                .background(TimeThemeTokens.status.warnInk, Shapes.pill)
                                .clickable {
                                    val intent = if (!notificationsAllowed) {
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    } else {
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                            .setData(android.net.Uri.parse("package:${context.packageName}"))
                                    }
                                    context.startActivity(intent)
                                }
                                .padding(horizontal = Dimens.s16, vertical = Dimens.s8),
                        ) {
                            Text(L("Open system setting"), color = TimeThemeTokens.status.warn, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.s14).background(colors.accS, Shapes.lg).padding(Dimens.s14),
                ) {
                    Icon(Icons.Rounded.Alarm, contentDescription = null, tint = colors.acc)
                    Text(
                        L("Reminders can fire at the exact minute you set."),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = Dimens.s12),
                    )
                }
            }
            Text(L("CHANNELS"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(bottom = Dimens.s8))
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg)) {
                ChannelRow(
                    Icons.Rounded.Timer, L("Work & timers"), L("Active session, countdown finished, break"),
                    ui.notifyWorkChannel, onToggle = { viewModel.setNotifyChannel(NotifyChannel.WORK, it) },
                )
                ChannelRow(
                    Icons.Rounded.Event, L("Schedule"), L("Deadlines, event start, scheduled work"),
                    ui.notifyScheduleChannel, onToggle = { viewModel.setNotifyChannel(NotifyChannel.SCHEDULE, it) },
                )
                ChannelRow(
                    Icons.Rounded.Payments, L("Money"), L("Bills due, heading over budget"),
                    ui.notifyMoneyChannel, onToggle = { viewModel.setNotifyChannel(NotifyChannel.MONEY, it) },
                )
                ChannelRow(
                    Icons.Rounded.CloudDone, L("Maintenance"), L("Backup completed or failed"),
                    ui.notifyMaintenanceChannel, onToggle = { viewModel.setNotifyChannel(NotifyChannel.MAINTENANCE, it) },
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s16).background(TimeThemeTokens.colors.card, Shapes.lg)) {
                Row(modifier = Modifier.fillMaxWidth().padding(Dimens.s14), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Hide details on the lock screen"), style = MaterialTheme.typography.bodyLarge)
                        Text(L("Notifications there leave out task names and amounts"), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = ui.notifyRedactContent, onCheckedChange = { viewModel.setRedactContent(it) })
                }
                Row(modifier = Modifier.fillMaxWidth().padding(Dimens.s14), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Budget alerts"), style = MaterialTheme.typography.bodyLarge)
                        Text(L("At 80%, when bills still to pay would take you over, and when you're over — once a month each."), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = ui.budgetAlertsEnabled, onCheckedChange = { viewModel.setBudgetAlertsEnabled(it) })
                }
                // Editable now (was a fixed 22:00–07:00 stated as text). Timers and Focus alerts
                // still ring: one started late is meant to.
                var picking by remember { mutableStateOf<String?>(null) }
                Row(modifier = Modifier.fillMaxWidth().padding(Dimens.s14), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L("Quiet hours"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L("Reminders still arrive, silently — never dropped. Timers still ring."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = ui.quietHoursEnabled, onCheckedChange = { viewModel.setQuietHours(it, ui.quietStart, ui.quietEnd) })
                }
                if (ui.quietHoursEnabled) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s14).padding(bottom = Dimens.s8)) {
                        androidx.compose.material3.TextButton(onClick = { picking = "start" }) { Text(L("From %1\$s", quietLabel(ui.quietStart))) }
                        androidx.compose.material3.TextButton(onClick = { picking = "end" }) { Text(L("Until %1\$s", quietLabel(ui.quietEnd))) }
                    }
                }
                val parse = { v: String -> runCatching { java.time.LocalTime.parse(v) }.getOrDefault(java.time.LocalTime.of(22, 0)) }
                when (picking) {
                    "start" -> com.example.timeapp.ui.components.TimePickerPrompt(parse(ui.quietStart)) { t ->
                        picking = null; t?.let { viewModel.setQuietHours(true, it.toString(), ui.quietEnd) }
                    }
                    "end" -> com.example.timeapp.ui.components.TimePickerPrompt(parse(ui.quietEnd)) { t ->
                        picking = null; t?.let { viewModel.setQuietHours(true, ui.quietStart, it.toString()) }
                    }
                    else -> Unit
                }
            }
            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        postTestNotification()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s14),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(19.dp))
                Text(L("Send a test notification"), modifier = Modifier.padding(start = Dimens.s8))
            }
            Row(modifier = Modifier.fillMaxWidth().background(colors.accS, Shapes.lg).padding(Dimens.s12)) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.acc)
                Text(
                    L("Force-stopping T!ME, a powered-off phone, or aggressive battery policies can delay or skip delivery. Missed items are surfaced when you reopen the app, not replayed as a burst of alarms."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.s10),
                )
            }
            Row(modifier = Modifier.padding(vertical = Dimens.s12)) {
                Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                Text(
                    L("A money notification never records a payment."),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.s10),
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = TimeThemeTokens.colors
    Row(modifier = Modifier.fillMaxWidth().padding(Dimens.s14), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (enabled) colors.acc else TimeThemeTokens.status.dim)
        Column(modifier = Modifier.weight(1f).padding(start = Dimens.s14)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

/** A stored "22:00" in the app's clock and digits (it showed 24-hour under a 12-hour app). */
private fun quietLabel(stored: String): String =
    runCatching { java.time.LocalTime.parse(stored).format(com.example.timeapp.data.displayPattern("HH:mm")) }.getOrDefault(stored)
