package com.example.timeapp

import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.TimeTheme

/**
 * The full-screen page an alarm-style timer shows, over the lock screen (Phase 11). The sound
 * comes from AlarmService (or, when it couldn't start, the alarm channel's repeating sound) —
 * so it rings whether or not this page could be shown. *Snooze 5 min* restarts the timer; stopping takes a
 * press-and-hold, so a pocket tap can't silence it. It shows only what the notification's
 * lock-screen version would: with content hidden, no timer name.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: com.example.timeapp.data.L("Time's up")
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val snooze = intent.getParcelableExtraCompat(EXTRA_SNOOZE)
        setContent {
            TimeTheme {
                AlarmPage(
                    title = title,
                    body = body,
                    onSnooze = snooze?.let { s -> { sendBroadcast(s); stop(notificationId) } },
                    onStop = { stop(notificationId) },
                )
            }
        }
    }

    private fun stop(notificationId: Int) {
        com.example.timeapp.data.Haptics.perform(this, com.example.timeapp.data.HapticEvent.CONFIRM)
        com.example.timeapp.data.notify.AlarmService.stopRinging()
        getSystemService(NotificationManager::class.java)?.cancel(notificationId)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(key: String): Intent? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Intent::class.java) else getParcelableExtra(key)

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        /** The broadcast *Snooze 5 min* sends (the same one the notification action sends). */
        const val EXTRA_SNOOZE = "snooze"
    }
}

@Composable
private fun AlarmPage(title: String, body: String, onSnooze: (() -> Unit)?, onStop: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Dimens.s24),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (body.isNotEmpty()) {
                Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Dimens.s12))
            }
            if (onSnooze != null) {
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().padding(top = Dimens.s34).height(56.dp)) { Text(com.example.timeapp.data.L("Snooze 5 min")) }
            }
            HoldToStop(onStop, Modifier.fillMaxWidth().padding(top = Dimens.s16).height(72.dp))
        }
    }
}

/** How long Stop must be held. */
private const val HOLD_MS = 1_200

/**
 * Hold, not tap: a pocket or a sleepy hand shouldn't be able to silence it. The fill shows how
 * far along the hold is; letting go early resets it. It used to be a Button with a long-press
 * detector wrapped around it — the Button consumed the touch first, so the long press never
 * fired and the alarm could not be stopped from this page at all. TalkBack gets a long-click
 * action ("double-tap and hold").
 */
@Composable
private fun HoldToStop(onStop: () -> Unit, modifier: Modifier = Modifier) {
    val progress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val label = com.example.timeapp.data.L("Hold to stop")
    val colors = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(colors.primaryContainer)
            .semantics {
                contentDescription = label
                role = androidx.compose.ui.semantics.Role.Button
                onLongClick(label = com.example.timeapp.data.L("Stop the alarm")) { onStop(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val fill = scope.launch {
                        progress.animateTo(1f, androidx.compose.animation.core.tween(HOLD_MS, easing = androidx.compose.animation.core.LinearEasing))
                    }
                    val released = withTimeoutOrNull(HOLD_MS.toLong()) { waitForUpOrCancellation() }
                    if (released == null) {
                        onStop()
                    } else {
                        fill.cancel()
                        scope.launch { progress.animateTo(0f, androidx.compose.animation.core.tween(150)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(colors.primary, size = size.copy(width = size.width * progress.value))
            },
        )
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (progress.value > 0.5f) colors.onPrimary else colors.onPrimaryContainer)
    }
}
