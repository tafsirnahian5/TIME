package com.example.timeapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * How an alarm-style timer sounds (Settings › Timers & focus › Alarm sound): the tone, how loud,
 * whether it fades in, vibrates, speaks, and when it gives up. Played by `AlarmService`.
 *
 * Device-specific, so kept in its own store and **not** in backups: a tone is a content URI
 * (a system ringtone, or a file the user granted) that means nothing on another phone.
 */
data class AlarmSound(
    /** null = the phone's default alarm tone; [SILENT_TONE] = vibrate only; else a content URI. */
    val toneUri: String? = null,
    /** What the user picked, as shown in settings (the file or ringtone's own title). */
    val toneName: String? = null,
    /** Loudness relative to the phone's alarm volume, 10–100. */
    val volumePercent: Int = 80,
    /** Fade-in from quiet to [volumePercent]; 0 = full volume at once. */
    val rampSeconds: Int = 30,
    val vibration: AlarmVibration = AlarmVibration.STANDARD,
    /** Read the alert out with the phone's text-to-speech voice. */
    val speak: Boolean = false,
    /** Stop ringing after this long unanswered (the notification stays). */
    val silenceAfterMinutes: Int = 5,
) {
    val silent: Boolean get() = toneUri == SILENT_TONE
}

/**
 * How an alarm vibrates, repeating until it's answered. [timings] alternate off/on in ms;
 * [amplitudes] (0–255) are used where the phone can vary strength, else only the rhythm is.
 */
enum class AlarmVibration(val timings: LongArray, val amplitudes: IntArray) {
    OFF(longArrayOf(), intArrayOf()),
    GENTLE(longArrayOf(0, 400, 1400), intArrayOf(0, 90, 0)),
    STANDARD(longArrayOf(0, 800, 600), intArrayOf(0, 200, 0)),
    PULSE(longArrayOf(0, 150, 150, 150, 150, 150, 700), intArrayOf(0, 255, 0, 255, 0, 255, 0)),
    HEARTBEAT(longArrayOf(0, 120, 120, 220, 900), intArrayOf(0, 200, 0, 255, 0)),
}

const val SILENT_TONE = "silent"
val RAMP_CHOICES = listOf(0, 15, 30, 60)
val SILENCE_AFTER_CHOICES = listOf(1, 5, 10, 20)
/** How often a spoken alert repeats while the alarm keeps ringing. */
const val SPEAK_EVERY_MS = 30_000L

/**
 * The player volume [elapsedMs] into ringing: rises linearly from a quiet start to [target]
 * over [rampMs], then holds. Starts audible (a tenth of the target) rather than at zero, so the
 * first seconds aren't silence that looks like a failure.
 */
fun rampVolume(elapsedMs: Long, target: Float, rampMs: Long): Float {
    val t = target.coerceIn(0f, 1f)
    if (rampMs <= 0 || elapsedMs >= rampMs) return t
    val start = t * 0.1f
    return start + (t - start) * (elapsedMs.coerceAtLeast(0).toFloat() / rampMs)
}

/**
 * What the alarm says aloud. With content hidden on a locked phone it names nothing — the
 * same promise the lock-screen notification makes — so a task title is never read out to the
 * room.
 */
fun spokenAlert(title: String, hideDetails: Boolean): String =
    if (hideDetails || title.isBlank()) L("Your timer is done.") else title

private val Context.alarmSoundStore by preferencesDataStore(name = "alarm_sound")

class AlarmSoundStore(private val context: Context) {
    private object Keys {
        val TONE = stringPreferencesKey("tone_uri")
        val TONE_NAME = stringPreferencesKey("tone_name")
        val VOLUME = intPreferencesKey("volume_percent")
        val RAMP = intPreferencesKey("ramp_seconds")
        val VIBRATION = stringPreferencesKey("vibration")
        val SPEAK = booleanPreferencesKey("speak")
        val SILENCE_AFTER = intPreferencesKey("silence_after_minutes")
    }

    val sound: Flow<AlarmSound> = context.alarmSoundStore.data.map { p ->
        AlarmSound(
            toneUri = p[Keys.TONE],
            toneName = p[Keys.TONE_NAME],
            volumePercent = (p[Keys.VOLUME] ?: 80).coerceIn(10, 100),
            rampSeconds = p[Keys.RAMP] ?: 30,
            vibration = p[Keys.VIBRATION]?.let { runCatching { AlarmVibration.valueOf(it) }.getOrNull() } ?: AlarmVibration.STANDARD,
            speak = p[Keys.SPEAK] ?: false,
            silenceAfterMinutes = p[Keys.SILENCE_AFTER] ?: 5,
        )
    }

    suspend fun current(): AlarmSound = sound.first()

    /** [uri] null = the phone's default alarm. */
    suspend fun setTone(uri: String?, name: String?) = context.alarmSoundStore.edit {
        if (uri == null) it.remove(Keys.TONE) else it[Keys.TONE] = uri
        if (name == null) it.remove(Keys.TONE_NAME) else it[Keys.TONE_NAME] = name
    }
    suspend fun setVolume(percent: Int) = context.alarmSoundStore.edit { it[Keys.VOLUME] = percent.coerceIn(10, 100) }
    suspend fun setRamp(seconds: Int) = context.alarmSoundStore.edit { it[Keys.RAMP] = seconds }
    suspend fun setVibration(v: AlarmVibration) = context.alarmSoundStore.edit { it[Keys.VIBRATION] = v.name }
    suspend fun setSpeak(on: Boolean) = context.alarmSoundStore.edit { it[Keys.SPEAK] = on }
    suspend fun setSilenceAfter(minutes: Int) = context.alarmSoundStore.edit { it[Keys.SILENCE_AFTER] = minutes }
}
