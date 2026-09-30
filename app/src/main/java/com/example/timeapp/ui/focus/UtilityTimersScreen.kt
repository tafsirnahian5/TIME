package com.example.timeapp.ui.focus

import androidx.compose.foundation.selection.toggleable
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.example.timeapp.data.db.UtilityTimerEntity
import com.example.timeapp.data.db.remainingAt
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import com.example.timeapp.ui.components.rememberNotificationPermissionRequest
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import kotlinx.coroutines.delay
import java.time.Instant

private val presetMinutes = listOf(3, 5, 10, 15, 25, 30, 60)

/**
 * Focus › Timers — the PDF's "extra utility countdowns … do not contribute to work
 * statistics". Several can run at once, each with its own alarm. The ticking you see is
 * drawn from each timer's end instant once a second while any is running; nothing is written
 * per tick. Not in the design canvas; token-built.
 */
@Composable
fun UtilityTimersScreen(
    onOpenStopwatch: () -> Unit,
    onOpenCountdown: () -> Unit,
    onOpenPomodoro: () -> Unit,
    viewModel: UtilityTimersViewModel,
    alarmStyle: Boolean = false,
    onAlarmStyle: (Boolean) -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    val timers by viewModel.timers.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val anyRunning = timers.any { it.endsAt != null }
    LaunchedEffect(anyRunning) {
        while (anyRunning) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val askNotificationPermission = rememberNotificationPermissionRequest()

    Scaffold(
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s12), verticalAlignment = Alignment.CenterVertically) {
                com.example.timeapp.ui.components.TabTitle(L("Focus"), com.example.timeapp.ui.components.TabHelps.focus, Modifier.weight(1f))
            }
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { adding = true },
                containerColor = colors.acc,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Shapes.lg,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(L("Timer")) },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.s16)) {
            item {
                FocusModeToggle(selectedIndex = 3, onOpenStopwatch = onOpenStopwatch, onOpenCountdown = onOpenCountdown, onOpenPomodoro = onOpenPomodoro, onOpenTimers = {})
                AlarmStyleRow(alarmStyle, onAlarmStyle)
                Text(
                    L("Kitchen-style timers that run alongside your work. They never count as work time.") +
                        if (!viewModel.canRingExactly()) L(" Exact alarms are off for T!ME, so a timer may ring a few minutes late.") else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Dimens.s12),
                )
            }
            if (timers.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Timer,
                        illustration = Illustration.FOCUS,
                        headline = L("No timers"),
                        supporting = L("Tea, laundry, a parking meter — start one and get on with things."),
                        actionLabel = L("New timer"),
                        onAction = { adding = true },
                    )
                }
            }
            items(timers, key = { it.id }) { timer ->
                TimerRow(
                    timer = timer,
                    remaining = timer.remainingAt(Instant.ofEpochMilli(now)),
                    onToggle = { if (timer.endsAt == null) askNotificationPermission(); viewModel.toggle(timer) },
                    onReset = { viewModel.reset(timer) },
                    onDelete = { viewModel.delete(timer) },
                )
            }
            item {
                Text(L("QUICK START"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s12, bottom = Dimens.s8))
                FlowRow {
                    listOf(3 to L("3 min"), 10 to L("10 min"), 25 to L("25 min"), 60 to L("1 hour")).forEach { (m, label) ->
                        androidx.compose.material3.OutlinedButton(
                            onClick = { askNotificationPermission(); viewModel.add(label, m * 60_000L, startNow = true) },
                            modifier = Modifier.padding(end = Dimens.s8, bottom = Dimens.s8),
                        ) { Text(label) }
                    }
                }
            }
            item { Box(Modifier.padding(bottom = Dimens.s34 * 2)) }
        }
    }

    if (adding) {
        var label by remember { mutableStateOf("") }
        var minutes by remember { mutableStateOf(10) }
        var custom by remember { mutableStateOf("") }
        val chosen = custom.toIntOrNull()?.takeIf { it > 0 } ?: minutes
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(L("New timer")) },
            text = {
                Column {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(L("For")) }, placeholder = { Text(L("Tea")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(modifier = Modifier.padding(top = Dimens.s12)) {
                        presetMinutes.forEach { m ->
                            val selected = custom.isBlank() && m == minutes
                            Text(
                                L("%1\$s min", m),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = Dimens.s8, bottom = Dimens.s8)
                                    .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
                                    .then(if (selected) Modifier else Modifier.border(Dimens.hairline, TimeThemeTokens.status.controlOutline, Shapes.pill))
                                    .semantics { this.selected = selected }.clickable { minutes = m; custom = "" }
                                    .heightIn(min = Dimens.touchMin)
                                    .padding(horizontal = Dimens.s14)
                                    .wrapContentHeight(),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                        label = { Text(L("Other (minutes)")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askNotificationPermission()
                    viewModel.add(label, chosen * 60_000L, startNow = true)
                    adding = false
                }) { Text(L("Start %1\$s min", chosen)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun TimerRow(timer: UtilityTimerEntity, remaining: Long, onToggle: () -> Unit, onReset: () -> Unit, onDelete: () -> Unit) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    val running = timer.endsAt != null
    val finished = !running && timer.remainingMs == 0L
    val minutes = timer.durationMs / 60_000
    val clock = displayPattern("HH:mm")
    val ink = when {
        running -> colors.accInk
        finished -> status.okInk
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.cardGap)
            .then(
                when {
                    running -> Modifier.background(colors.accC2, Shapes.xl)
                    finished -> Modifier.background(status.ok, Shapes.xl)
                    else -> Modifier.background(colors.card, Shapes.xl).border(Dimens.hairline, colors.line, Shapes.xl)
                },
            )
            .padding(start = Dimens.s16, end = Dimens.s8, top = Dimens.s14, bottom = Dimens.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            // The canvas's running timer: a ring that empties as time runs out.
            com.example.timeapp.ui.components.ProgressRing(
                fraction = if (timer.durationMs > 0) remaining.toFloat() / timer.durationMs else 0f,
                color = colors.acc, track = colors.accDim, size = 72.dp, stroke = 6.dp,
            ) { Icon(Icons.Rounded.Timer, contentDescription = null, tint = colors.acc) }
        }
        Column(modifier = Modifier.weight(1f).padding(start = if (running) Dimens.s16 else Dimens.s2)) {
            Text(timer.label, style = MaterialTheme.typography.titleSmall, color = ink)
            Text(
                if (finished) L("Done") else formatElapsed(remaining),
                style = if (running) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                color = ink,
            )
            Text(
                when {
                    running -> L("of %1\$s min · rings at %2\$s", minutes, timer.endsAt!!.atZone(java.time.ZoneId.systemDefault()).format(clock))
                    finished -> L("Rang · %1\$s min", minutes)
                    else -> L("Paused · of %1\$s min", minutes)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (running || finished) ink else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!finished) {
            IconButton(
                onClick = onToggle,
                colors = if (running) androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = colors.acc, contentColor = MaterialTheme.colorScheme.onPrimary)
                else androidx.compose.material3.IconButtonDefaults.iconButtonColors(contentColor = colors.acc),
            ) {
                Icon(
                    if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (running) L("Pause %1\$s", timer.label) else L("Start %1\$s", timer.label),
                )
            }
        }
        if (!running) {
            IconButton(onClick = onReset, enabled = timer.remainingMs != timer.durationMs) {
                Icon(Icons.Rounded.Replay, contentDescription = if (finished) L("Run %1\$s again", timer.label) else L("Reset %1\$s", timer.label), tint = ink)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = L("Delete %1\$s", timer.label), tint = ink) }
        }
    }
}

/**
 * "Ring like an alarm" (Phase 11): these timers and Focus countdowns then sound at alarm
 * volume, vibrate, repeat until answered and open a full-screen page when the phone is locked.
 * When Android won't allow full-screen alerts (a special access on Android 14+), it says so and
 * links there — the alarm still rings as a notification.
 */
@androidx.compose.runtime.Composable
internal fun AlarmStyleRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fullScreenAllowed = android.os.Build.VERSION.SDK_INT < 34 ||
        context.getSystemService(android.app.NotificationManager::class.java)?.canUseFullScreenIntent() == true
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(bottom = Dimens.s12)
            .background(TimeThemeTokens.colors.card, Shapes.lg).padding(Dimens.s14),
    ) {
        // The whole row toggles (it used to be the switch alone), and reads as one switch.
        androidx.compose.foundation.layout.Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                .toggleable(value = enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                Text(L("Ring like an alarm"), style = MaterialTheme.typography.bodyLarge)
                Text(
                    L("Timers and Focus countdowns sound at alarm volume and keep ringing until you stop them. Do Not Disturb's alarm setting still applies."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Switch(checked = enabled, onCheckedChange = null)
        }
        if (enabled && !fullScreenAllowed) {
            Text(
                L("Full-screen alerts are off for T!ME, so a locked phone shows a notification instead of the alarm page."),
                style = MaterialTheme.typography.bodySmall,
                color = TimeThemeTokens.status.warnInk,
                modifier = androidx.compose.ui.Modifier.padding(top = Dimens.s8),
            )
            androidx.compose.material3.TextButton(onClick = {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(android.net.Uri.parse("package:" + context.packageName)),
                )
            }) { Text(L("Allow full-screen alerts")) }
        }
    }
}
