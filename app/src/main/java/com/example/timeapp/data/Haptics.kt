package com.example.timeapp.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** How strongly T!ME answers a touch (Settings › Appearance › Haptic feedback). */
enum class HapticLevel { OFF, LIGHT, MEDIUM, STRONG }

/** The moments T!ME gives haptic feedback for — few and meaningful, never every tap. */
enum class HapticEvent {
    /** A small change: a checklist line, dropping a dragged card. */
    TICK,
    /** Something done or started: a task or habit ticked off, Focus started/paused/finished. */
    CONFIRM,
    /** Picking a card up to drag it. */
    PICK_UP,
    /** A real milestone (a savings goal reached, a streak landmark). */
    MILESTONE,
}

/** One vibration, described without Android types so the table below is testable. */
sealed interface HapticEffect {
    /** A system effect (VibrationEffect.EFFECT_TICK / CLICK / HEAVY_CLICK / DOUBLE_CLICK). */
    data class Predefined(val effectId: Int) : HapticEffect
    /** A pattern: [timings] alternate off/on in ms, [amplitudes] 0–255 per step. */
    data class Pattern(val timings: LongArray, val amplitudes: IntArray) : HapticEffect
}

const val EFFECT_CLICK = 0      // VibrationEffect.EFFECT_CLICK
const val EFFECT_DOUBLE_CLICK = 1
const val EFFECT_TICK = 2
const val EFFECT_HEAVY_CLICK = 5

/**
 * What [event] feels like at [level]; null = nothing. Light keeps to the gentlest system
 * effects, Medium matches what Android itself uses, Strong goes a step firmer and makes a
 * milestone a clear double pulse.
 */
fun hapticEffectFor(level: HapticLevel, event: HapticEvent): HapticEffect? = when (level) {
    HapticLevel.OFF -> null
    HapticLevel.LIGHT -> when (event) {
        HapticEvent.MILESTONE -> HapticEffect.Predefined(EFFECT_CLICK)
        else -> HapticEffect.Predefined(EFFECT_TICK)
    }
    HapticLevel.MEDIUM -> when (event) {
        HapticEvent.TICK -> HapticEffect.Predefined(EFFECT_TICK)
        HapticEvent.CONFIRM, HapticEvent.PICK_UP -> HapticEffect.Predefined(EFFECT_CLICK)
        HapticEvent.MILESTONE -> HapticEffect.Predefined(EFFECT_DOUBLE_CLICK)
    }
    HapticLevel.STRONG -> when (event) {
        HapticEvent.TICK -> HapticEffect.Predefined(EFFECT_CLICK)
        HapticEvent.CONFIRM, HapticEvent.PICK_UP -> HapticEffect.Predefined(EFFECT_HEAVY_CLICK)
        HapticEvent.MILESTONE -> HapticEffect.Pattern(longArrayOf(0, 60, 90, 60, 90, 120), intArrayOf(0, 255, 0, 255, 0, 255))
    }
}

/**
 * Plays haptic feedback at the user's chosen level. Like [WeekConfig] and [DisplayFormats] it's
 * app-wide state set from settings (MainActivity / TimeApplication), so any screen or ViewModel
 * can call [perform] without threading the setting through.
 *
 * Uses the *touch* vibration usage, so Android's own "touch feedback off" still silences it —
 * the app never overrides the user's system choice.
 */
object Haptics {
    @Volatile var level: HapticLevel = HapticLevel.MEDIUM
    @Volatile private var appContext: Context? = null

    /** Called once from TimeApplication, so ViewModels can give feedback without a context. */
    fun init(context: Context) { appContext = context.applicationContext }

    fun perform(event: HapticEvent) { appContext?.let { perform(it, event) } }

    fun perform(context: Context, event: HapticEvent, at: HapticLevel = level) {
        val effect = hapticEffectFor(at, event) ?: return
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val vibration = when (effect) {
            is HapticEffect.Predefined ->
                if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(effect.effectId)
                else VibrationEffect.createOneShot(if (effect.effectId == EFFECT_TICK) 10 else 25, VibrationEffect.DEFAULT_AMPLITUDE)
            is HapticEffect.Pattern ->
                if (vibrator.hasAmplitudeControl()) VibrationEffect.createWaveform(effect.timings, effect.amplitudes, -1)
                else VibrationEffect.createWaveform(effect.timings, -1)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(vibration, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibration)
            }
        }
    }

    fun vibrator(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}
