package com.example.timeapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.focusStateDataStore by preferencesDataStore(name = "focus_state")

/** Saved Focus timer state, so a force-stop mid-session recovers correctly within the same
 * boot. [SavedFocusState.anchorElapsedRealtime] is `SystemClock.elapsedRealtime()` at the
 * last state transition — it survives process death but resets on reboot, matching what's
 * actually reconstructable without a foreground service (full reboot-survival isn't attempted). */
data class SavedFocusState(
    val running: Boolean = false,
    val accumulatedMs: Long = 0L,
    val anchorElapsedRealtime: Long? = null,
    val startedAtEpochMilli: Long? = null,
    val pomodoroPhaseIsWork: Boolean = true,
    val pomodoroCycles: Int = 0,
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val autoStart: Boolean = false,
    val countdownMinutes: Int = 30,
)

class FocusStateStore(private val context: Context) {
    private object Keys {
        val RUNNING = booleanPreferencesKey("running")
        val ACCUMULATED_MS = longPreferencesKey("accumulated_ms")
        val ANCHOR_ELAPSED_REALTIME = longPreferencesKey("anchor_elapsed_realtime")
        val STARTED_AT_EPOCH_MILLI = longPreferencesKey("started_at_epoch_milli")
        val POMODORO_PHASE_IS_WORK = booleanPreferencesKey("pomodoro_phase_is_work")
        val POMODORO_CYCLES = intPreferencesKey("pomodoro_cycles")
        val WORK_MINUTES = intPreferencesKey("work_minutes")
        val BREAK_MINUTES = intPreferencesKey("break_minutes")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val COUNTDOWN_MINUTES = intPreferencesKey("countdown_minutes")
    }

    suspend fun load(): SavedFocusState {
        val prefs = context.focusStateDataStore.data.first()
        return SavedFocusState(
            running = prefs[Keys.RUNNING] ?: false,
            accumulatedMs = prefs[Keys.ACCUMULATED_MS] ?: 0L,
            anchorElapsedRealtime = prefs[Keys.ANCHOR_ELAPSED_REALTIME],
            startedAtEpochMilli = prefs[Keys.STARTED_AT_EPOCH_MILLI],
            pomodoroPhaseIsWork = prefs[Keys.POMODORO_PHASE_IS_WORK] ?: true,
            pomodoroCycles = prefs[Keys.POMODORO_CYCLES] ?: 0,
            workMinutes = prefs[Keys.WORK_MINUTES] ?: 25,
            breakMinutes = prefs[Keys.BREAK_MINUTES] ?: 5,
            autoStart = prefs[Keys.AUTO_START] ?: false,
            countdownMinutes = prefs[Keys.COUNTDOWN_MINUTES] ?: 30,
        )
    }

    suspend fun save(state: SavedFocusState) {
        context.focusStateDataStore.edit {
            it[Keys.RUNNING] = state.running
            it[Keys.ACCUMULATED_MS] = state.accumulatedMs
            if (state.anchorElapsedRealtime != null) it[Keys.ANCHOR_ELAPSED_REALTIME] = state.anchorElapsedRealtime else it.remove(Keys.ANCHOR_ELAPSED_REALTIME)
            if (state.startedAtEpochMilli != null) it[Keys.STARTED_AT_EPOCH_MILLI] = state.startedAtEpochMilli else it.remove(Keys.STARTED_AT_EPOCH_MILLI)
            it[Keys.POMODORO_PHASE_IS_WORK] = state.pomodoroPhaseIsWork
            it[Keys.POMODORO_CYCLES] = state.pomodoroCycles
            it[Keys.WORK_MINUTES] = state.workMinutes
            it[Keys.BREAK_MINUTES] = state.breakMinutes
            it[Keys.AUTO_START] = state.autoStart
            it[Keys.COUNTDOWN_MINUTES] = state.countdownMinutes
        }
    }
}
