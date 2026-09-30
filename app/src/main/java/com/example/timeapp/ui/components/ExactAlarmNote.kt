package com.example.timeapp.ui.components

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.timeapp.data.L
import com.example.timeapp.data.notify.ReminderScheduler
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/**
 * Shown on Countdown and Pomodoro while Android withholds exact alarms (the default for new
 * installs since Android 14): the end alert can then arrive minutes late. Work still stops at
 * the right moment — only the alert is late — and the button opens the system page to allow it.
 * Re-checked every time the screen comes back, so it disappears once allowed.
 */
@Composable
fun ExactAlarmNote(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ReminderScheduler(context).canScheduleExact()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = ReminderScheduler(context).canScheduleExact()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (allowed || Build.VERSION.SDK_INT < 31) return
    val status = TimeThemeTokens.status
    Row(
        modifier = modifier.fillMaxWidth().background(status.warn, Shapes.lg).padding(Dimens.s14),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.Alarm, contentDescription = null, tint = status.warnInk)
        Column(Modifier.padding(start = Dimens.s12)) {
            Text(
                L("The end alert may be a few minutes late. Work still stops on time — allow exact alarms for an alert on the minute."),
                style = MaterialTheme.typography.bodySmall, color = status.warnInk,
            )
            Box(
                modifier = Modifier
                    .padding(top = Dimens.s8)
                    .heightIn(min = Dimens.touchMin)
                    .clickable(role = Role.Button) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(android.net.Uri.parse("package:${context.packageName}")),
                        )
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(L("Allow exact alarms"), style = MaterialTheme.typography.labelLarge, color = status.warnInk)
            }
        }
    }
}
