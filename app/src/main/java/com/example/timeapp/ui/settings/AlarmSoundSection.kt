package com.example.timeapp.ui.settings

import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.example.timeapp.data.AlarmSound
import com.example.timeapp.data.AlarmSoundStore
import com.example.timeapp.data.L
import com.example.timeapp.data.RAMP_CHOICES
import com.example.timeapp.data.SILENCE_AFTER_CHOICES
import com.example.timeapp.data.SILENT_TONE
import com.example.timeapp.data.notify.AlarmTone
import com.example.timeapp.ui.components.SettingsDivider
import com.example.timeapp.ui.components.SettingsGroup
import com.example.timeapp.ui.components.SettingsSwitchRow
import com.example.timeapp.ui.components.SettingsValueRow
import com.example.timeapp.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings › Timers & focus › ALARM SOUND: how an alarm-style timer rings (AlarmService). The
 * tone can be the phone's default alarm, any sound from Android's own picker, an audio file of
 * the user's (kept with a persisted read grant), or silence; plus volume with a preview,
 * fade-in, vibration, text-to-speech and when to stop ringing. Device-specific: not in backups.
 */
@Composable
fun AlarmSoundSection(enabled: Boolean) {
    val context = LocalContext.current
    val store = remember { AlarmSoundStore(context.applicationContext) }
    val sound by store.sound.collectAsState(initial = AlarmSound())
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf(false) }

    fun keepGrant(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    fun dropOldGrant() {
        val old = sound.toneUri?.takeIf { it != SILENT_TONE }?.let(Uri::parse) ?: return
        runCatching { context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    val pickSystem = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri: Uri? = result.data?.let {
            if (Build.VERSION.SDK_INT >= 33) it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else @Suppress("DEPRECATION") it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        if (uri != null) scope.launch { dropOldGrant(); store.setTone(uri.toString(), AlarmTone.title(context, uri)) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            keepGrant(uri)
            scope.launch { dropOldGrant(); store.setTone(uri.toString(), AlarmTone.title(context, uri) ?: L("Your sound file")) }
        }
    }

    val toneLabel = when {
        sound.silent -> L("Silent — vibrate only")
        sound.toneUri == null -> L("Phone's default alarm")
        else -> sound.toneName ?: L("Chosen sound")
    }

    SettingsGroup(L("ALARM SOUND")) {
        if (!enabled) {
            Text(
                L("Used when Ring like an alarm is on."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.s16, end = Dimens.s16, top = Dimens.s12),
            )
        }
        SettingsValueRow(L("Tone"), toneLabel) { choosing = "tone" }
        SettingsDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.s16, vertical = Dimens.s10)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("Volume · %1\$s%%", sound.volumePercent), style = MaterialTheme.typography.bodyLarge)
                    Text(L("Of the phone's alarm volume"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { previewing = !previewing }, enabled = !sound.silent) {
                    Text(if (previewing) L("Stop") else L("Preview"))
                }
            }
            var slider by remember(sound.volumePercent) { mutableFloatStateOf(sound.volumePercent.toFloat()) }
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = { scope.launch { store.setVolume(slider.toInt()) } },
                valueRange = 10f..100f,
                steps = 8,
                enabled = !sound.silent,
            )
        }
        SettingsDivider()
        SettingsValueRow(L("Gradually louder"), rampLabel(sound.rampSeconds), subtitle = L("Starts quiet and rises to full volume")) { choosing = "ramp" }
        SettingsDivider()
        SettingsValueRow(L("Vibration"), vibrationLabel(sound.vibration)) { choosing = "vibration" }
        SettingsDivider()
        SettingsSwitchRow(
            L("Say it aloud"), sound.speak,
            subtitle = L("Reads out what finished every 30 s, with your phone's text-to-speech voice. With content hidden on a locked phone it names nothing."),
        ) { on -> scope.launch { store.setSpeak(on) } }
        SettingsDivider()
        SettingsValueRow(L("Stop ringing after"), L("%1\$s min", sound.silenceAfterMinutes), subtitle = L("The notification stays, so it isn't missed")) { choosing = "silence" }
    }

    // Preview: a few seconds at the chosen volume (no fade-in), stopped on leaving the page.
    if (previewing) {
        DisposableEffect(sound.toneUri, sound.volumePercent) {
            val player: MediaPlayer? = AlarmTone.player(context, sound.toneUri)?.apply {
                val v = sound.volumePercent / 100f
                setVolume(v, v)
                isLooping = true
                start()
            }
            onDispose { player?.let { runCatching { it.stop() }; it.release() } }
        }
        LaunchedEffect(Unit) { delay(6_000); previewing = false }
    }

    when (choosing) {
        "tone" -> ChoiceDialog(
            title = L("Tone"),
            options = listOf(
                "default" to L("Phone's default alarm"),
                "system" to L("Choose from the phone's sounds…"),
                "file" to L("Choose an audio file…"),
                "silent" to L("Silent — vibrate only"),
            ),
            selected = when {
                sound.silent -> "silent"
                sound.toneUri == null -> "default"
                else -> null
            },
            onDismiss = { choosing = null },
        ) { key ->
            choosing = null
            when (key) {
                "default" -> scope.launch { dropOldGrant(); store.setTone(null, null) }
                "silent" -> scope.launch { dropOldGrant(); store.setTone(SILENT_TONE, null) }
                "system" -> pickSystem.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, L("Alarm tone"))
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, sound.toneUri?.takeIf { it != SILENT_TONE }?.let(Uri::parse)),
                )
                "file" -> runCatching { pickFile.launch(arrayOf("audio/*")) }
            }
        }
        "vibration" -> ChoiceDialog(
            title = L("Vibration"),
            options = com.example.timeapp.data.AlarmVibration.entries.map { it.name to vibrationLabel(it) },
            selected = sound.vibration.name,
            onDismiss = { choosing = null },
            // Each pick plays one round of the pattern, so it can be felt before choosing.
            onSample = { key ->
                com.example.timeapp.data.Haptics.vibrator(context)?.let {
                    com.example.timeapp.data.notify.AlarmService.vibrateAlarm(it, com.example.timeapp.data.AlarmVibration.valueOf(key), repeat = false)
                }
            },
        ) { key -> choosing = null; scope.launch { store.setVibration(com.example.timeapp.data.AlarmVibration.valueOf(key)) } }
        "ramp" -> ChoiceDialog(
            title = L("Gradually louder"),
            options = RAMP_CHOICES.map { it.toString() to rampLabel(it) },
            selected = sound.rampSeconds.toString(),
            onDismiss = { choosing = null },
        ) { key -> choosing = null; scope.launch { store.setRamp(key.toInt()) } }
        "silence" -> ChoiceDialog(
            title = L("Stop ringing after"),
            options = SILENCE_AFTER_CHOICES.map { it.toString() to L("%1\$s min", it) },
            selected = sound.silenceAfterMinutes.toString(),
            onDismiss = { choosing = null },
        ) { key -> choosing = null; scope.launch { store.setSilenceAfter(key.toInt()) } }
    }
}

private fun vibrationLabel(v: com.example.timeapp.data.AlarmVibration): String = when (v) {
    com.example.timeapp.data.AlarmVibration.OFF -> L("Off")
    com.example.timeapp.data.AlarmVibration.GENTLE -> L("Gentle")
    com.example.timeapp.data.AlarmVibration.STANDARD -> L("Standard")
    com.example.timeapp.data.AlarmVibration.PULSE -> L("Pulse")
    com.example.timeapp.data.AlarmVibration.HEARTBEAT -> L("Heartbeat")
}

private fun rampLabel(seconds: Int): String = when {
    seconds <= 0 -> L("Off")
    seconds < 60 -> L("Over %1\$s s", seconds)
    else -> L("Over %1\$s min", seconds / 60)
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onDismiss: () -> Unit,
    onSample: ((String) -> Unit)? = null,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin)
                            .selectable(selected = key == selected, role = Role.RadioButton) { onSample?.invoke(key); onPick(key) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Dimens.s12))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("Cancel")) } },
    )
}
