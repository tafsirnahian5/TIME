package com.example.timeapp.data.notify

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.timeapp.data.AlarmSound
import com.example.timeapp.data.AlarmSoundStore
import com.example.timeapp.data.Lang
import com.example.timeapp.data.SPEAK_EVERY_MS
import com.example.timeapp.data.rampVolume
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * Rings an alarm-style timer the way the user set it up (Settings › Timers & focus › Alarm
 * sound): their tone at their volume, fading in, vibrating, optionally read aloud — until it's
 * answered or the "stop ringing after" time passes. A notification channel can only play one
 * fixed sound at a fixed volume, which is why this is a service and not the channel.
 *
 * Its foreground notification *is* the alarm notification (same id, same actions). Anything
 * that answers the alarm — Stop, Snooze, +5 min, Break, Finish, the full-screen page — calls
 * [stopRinging]. When Android won't let it start (it only may after an exact alarm, or with the
 * app open), [start] returns false and the caller posts the channel-sound notification instead,
 * so an alarm always rings one way or the other.
 */
class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speaking = false
    private var notificationId = 0
    private var shown: Notification? = null
    private var startedAt = 0L
    private var sound = AlarmSound()
    private var speech = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = intent?.notificationExtra() ?: run { stopSelf(); return START_NOT_STICKY }
        val id = intent.getIntExtra(EXTRA_ID, 1)
        try {
            ServiceCompat.startForeground(
                this, id, notification,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
        } catch (e: Exception) {
            Log.w(TAG, "startForeground refused", e)
            stopSelf()
            return START_NOT_STICKY
        }
        // A second alarm while one rings takes over: one sound at a time.
        releaseAll(keepForeground = true)
        notificationId = id
        shown = notification
        speech = intent.getStringExtra(EXTRA_SPEECH).orEmpty()
        sound = runBlocking { AlarmSoundStore(this@AlarmService).current() }
        current = this
        startedAt = SystemClock.elapsedRealtime()
        ring()
        return START_NOT_STICKY
    }

    private fun ring() {
        if (!sound.silent) {
            player = AlarmTone.player(this, sound.toneUri)?.apply {
                isLooping = true
                setVolume(0f, 0f)
                start()
            }
            handler.post(ramp)
        }
        if (sound.vibration != com.example.timeapp.data.AlarmVibration.OFF) vibrate()
        if (sound.speak && speech.isNotBlank()) startSpeech()
        handler.postDelayed(giveUp, sound.silenceAfterMinutes * 60_000L)
    }

    private val ramp = object : Runnable {
        override fun run() {
            val p = player ?: return
            val target = sound.volumePercent / 100f
            val v = rampVolume(SystemClock.elapsedRealtime() - startedAt, target, sound.rampSeconds * 1000L)
            // Ducked under the spoken alert so the words are heard.
            val out = if (speaking) v * 0.25f else v
            runCatching { p.setVolume(out, out) }
            handler.postDelayed(this, 200)
        }
    }

    private fun vibrate() {
        val v = com.example.timeapp.data.Haptics.vibrator(this) ?: return
        vibrator = v
        vibrateAlarm(v, sound.vibration, repeat = true)
    }

    private fun startSpeech() {
        tts = TextToSpeech(this) { status ->
            val engine = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            // Bangla text needs a Bangla voice; read by an English one it's noise, so stay quiet.
            val locale = if (Lang.bangla) Locale.forLanguageTag("bn-BD") else Locale.getDefault()
            if (engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
                if (Lang.bangla || engine.setLanguage(Locale.UK) < TextToSpeech.LANG_AVAILABLE) return@TextToSpeech
            }
            engine.setAudioAttributes(alarmAttributes())
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { speaking = true }
                override fun onDone(utteranceId: String?) { speaking = false }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { speaking = false }
            })
            ttsReady = true
            handler.post(speak)
        }
    }

    private val speak = object : Runnable {
        override fun run() {
            if (!ttsReady) return
            tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "alarm")
            handler.postDelayed(this, SPEAK_EVERY_MS)
        }
    }

    /**
     * Unanswered for too long: go quiet but leave the notification, so it isn't lost — re-posted
     * as an ordinary one. Detaching kept the service's no-clear flag, so it couldn't be swiped away.
     */
    private val giveUp = Runnable {
        releaseAll(keepForeground = false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        shown?.let { n ->
            val quiet = Notification.Builder.recoverBuilder(this, n)
                .setOngoing(false)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setFullScreenIntent(null, false)
                .build()
            runCatching { androidx.core.app.NotificationManagerCompat.from(this).notify(notificationId, quiet) }
        }
        stopSelf()
    }

    private fun releaseAll(keepForeground: Boolean) {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        tts?.let { it.stop(); it.shutdown() }
        tts = null
        ttsReady = false
        speaking = false
        if (!keepForeground && current === this) current = null
    }

    /** Answered: silence it and take the alarm notification away with it. */
    fun stopNow() {
        releaseAll(keepForeground = false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseAll(keepForeground = false)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun Intent.notificationExtra(): Notification? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(EXTRA_NOTIFICATION, Notification::class.java) else getParcelableExtra(EXTRA_NOTIFICATION)

    companion object {
        private const val TAG = "AlarmService"
        private const val EXTRA_NOTIFICATION = "notification"
        private const val EXTRA_ID = "id"
        private const val EXTRA_SPEECH = "speech"

        /** The ringing alarm, if any. Same process as every caller, so no IPC is needed to stop it. */
        @Volatile private var current: AlarmService? = null

        val ringing: Boolean get() = current != null

        /**
         * Starts ringing with [notification] as the alarm notification. False when Android
         * refused a foreground service from the background — the caller then falls back.
         */
        fun start(context: Context, id: Int, notification: Notification, speech: String): Boolean = try {
            val intent = Intent(context, AlarmService::class.java)
                .putExtra(EXTRA_NOTIFICATION, notification)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_SPEECH, speech)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (API 31+) or IllegalStateException.
            Log.w(TAG, "alarm service not allowed to start", e)
            false
        }

        /** Answers whatever is ringing. Safe to call when nothing is. */
        fun stopRinging() {
            val service = current ?: return
            Handler(Looper.getMainLooper()).post { service.stopNow() }
        }

        /** Plays [pattern] at alarm usage — repeating while ringing, once as a settings sample. */
        fun vibrateAlarm(v: Vibrator, pattern: com.example.timeapp.data.AlarmVibration, repeat: Boolean) {
            if (pattern.timings.isEmpty() || !v.hasVibrator()) return
            val index = if (repeat) 0 else -1
            val effect = if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, index)
            else VibrationEffect.createWaveform(pattern.timings, index)
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, alarmAttributes())
            }
        }

        fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}

/** Resolving and playing a tone — shared by the alarm and the settings preview. */
object AlarmTone {
    /**
     * A prepared player for [toneUri], falling back to the phone's default alarm, then its
     * ringtone, then its notification sound — a deleted file or a revoked grant must never mean
     * an alarm that rings silently.
     */
    fun player(context: Context, toneUri: String?): MediaPlayer? {
        val candidates = listOfNotNull(
            toneUri?.let { Uri.parse(it) },
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        )
        for (uri in candidates) {
            val p = MediaPlayer()
            try {
                p.setAudioAttributes(AlarmService.alarmAttributes())
                p.setDataSource(context, uri)
                p.prepare()
                return p
            } catch (e: Exception) {
                p.release()
            }
        }
        return null
    }

    /** The name to show for a tone: the ringtone's or file's own title. */
    fun title(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
        }
    }.getOrNull() ?: runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
}
