package com.example.timeapp.ui.focus

import kotlinx.coroutines.flow.flowOn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.Clocks
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.data.FocusRuntime
import com.example.timeapp.data.elapsedMs
import com.example.timeapp.data.dueAt
import com.example.timeapp.data.remainingMs
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.TimerClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which task the next Focus run will be on, before it starts: not chosen yet (the screen
 * suggests the paused task), explicitly none, or a task. */
sealed class TaskChoice {
    data object Unset : TaskChoice()
    data object NoTask : TaskChoice()
    data class Task(val id: Long) : TaskChoice()
}

data class FocusUiState(
    val runtime: FocusRuntime = FocusRuntime(),
    val clocks: Clocks = TimerClock.now(),
    val choice: TaskChoice = TaskChoice.Unset,
) {
    val running: Boolean get() = runtime.running
    val elapsedMs: Long get() = runtime.elapsedMs(clocks)
    val remainingMs: Long? get() = runtime.remainingMs(clocks)
    val inRun: Boolean get() = runtime.phase != FocusPhase.IDLE
}

/**
 * Focus is a view of the **one work timer** (`WorkTimer`) — the same timer a task's Start
 * button runs. It used to be a separate in-memory clock whose time was recorded nowhere and
 * which could run beside a task's own session; now starting here opens a real work session,
 * and Today, Task Detail, the reports and the timesheet all see it.
 *
 * The screen ticks from the runtime's anchors once a second; nothing is written per tick.
 */
class FocusViewModel(
    private val entries: EntryRepository,
    /** Ends a phase that ran out, alerting like its alarm would (FocusSurface.expireNow). */
    private val expire: suspend (java.time.Instant) -> Unit = { entries.timer.onPhaseExpired(it) },
) : ViewModel() {
    private val timer get() = entries.timer
    private val choice = MutableStateFlow<TaskChoice>(TaskChoice.Unset)
    private val ticker = flow {
        while (true) {
            emit(TimerClock.now())
            delay(1000)
        }
    }

    val uiState: StateFlow<FocusUiState> = combine(timer.observe(), ticker, choice) { runtime, clocks, picked ->
        FocusUiState(runtime, clocks, picked)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, FocusUiState())

    init {
        // A countdown or phase reaching zero while the screen is open ends right then; its
        // alarm covers the same moment when the app is in the background.
        viewModelScope.launch {
            var lastCheckpoint = 0L
            uiState.collect { s ->
                val rt = s.runtime
                if (rt.running && rt.targetMs != null && s.remainingMs == 0L) {
                    expire(rt.dueAt(s.clocks) ?: s.clocks.wall)
                }
                // Sparse checkpoints (the PDF): where a session cut off by a reboot is closed.
                if (rt.running && rt.phase == FocusPhase.WORK && s.clocks.elapsed - lastCheckpoint > CHECKPOINT_EVERY_MS) {
                    lastCheckpoint = s.clocks.elapsed
                    timer.checkpoint()
                }
            }
        }
    }

    /** The last two weeks of sessions, newest first — Focus › History. */
    val history: StateFlow<List<com.example.timeapp.data.SessionRecord>> =
        timer.observeSince(java.time.Instant.now().minus(java.time.Duration.ofDays(14)))
            .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val needsReview: StateFlow<List<com.example.timeapp.data.SessionRecord>> = timer.observeNeedingReview()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val presets: StateFlow<List<com.example.timeapp.data.TimerPreset>> = timer.observePresets()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sessionActions = sessionActionsFor(entries)

    fun savePreset(name: String, mode: FocusMode, minutes: Int, breakMinutes: Int? = null) =
        launch { timer.savePreset(name, mode, minutes, breakMinutes) }
    fun deletePreset(id: Long) = launch { timer.deletePreset(id) }
    fun applyPreset(preset: com.example.timeapp.data.TimerPreset) = launch { timer.applyPreset(preset) }

    fun choose(value: TaskChoice) = choice.update { value }

    /** Starts a run in [mode] on [entryId], or switches the running one to it. */
    fun start(entryId: Long?, mode: FocusMode) = launch { answerAlarm(); timer.start(entryId, mode) }
    fun pause() = launch { answerAlarm(); timer.pause() }
    fun resume() = launch { answerAlarm(); timer.resume() }
    fun finish(completeTask: Boolean = false) = launch {
        answerAlarm()
        timer.finish(completeTask)
        choice.update { TaskChoice.Unset }
    }
    fun lap() = launch { timer.lap() }
    fun clearLaps() = launch { timer.clearLaps() }
    fun extend(minutes: Int) = launch { answerAlarm(); timer.extend(minutes * 60_000L) }
    fun continueRun() = launch { answerAlarm(); timer.continueRun() }
    fun takeBreak() = launch { answerAlarm(); timer.takeBreak() }
    fun skipPhase() = launch { answerAlarm(); timer.skipPhase() }
    fun setMode(mode: FocusMode) = launch { timer.setMode(mode) }
    fun setCountdownMinutes(minutes: Int) = launch { timer.setLengths(countdownMinutes = minutes) }
    fun setDurations(workMinutes: Int, breakMinutes: Int) = launch { timer.setLengths(workMinutes = workMinutes, breakMinutes = breakMinutes) }
    fun setAutoStart(enabled: Boolean) = launch { timer.setLengths(autoStart = enabled) }
    fun setWorkMinutes(minutes: Int) = launch { timer.setLengths(workMinutes = minutes) }
    fun setBreakMinutes(minutes: Int) = launch { timer.setLengths(breakMinutes = minutes) }
    fun setLongBreakMinutes(minutes: Int) = launch { timer.setLengths(longBreakMinutes = minutes) }

    /** Acting on the run from inside the app answers a ringing phase alarm. */
    private fun answerAlarm() {
        com.example.timeapp.data.notify.AlarmService.stopRinging()
        com.example.timeapp.data.Haptics.perform(com.example.timeapp.data.HapticEvent.CONFIRM)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(
        private val entries: EntryRepository,
        private val expire: (suspend (java.time.Instant) -> Unit)? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            (if (expire != null) FocusViewModel(entries, expire) else FocusViewModel(entries)) as T
    }

    private companion object {
        const val CHECKPOINT_EVERY_MS = 5 * 60_000L
    }
}

fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val text = if (h > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, h, m, s) else "%02d:%02d".format(java.util.Locale.ROOT, m, s)
    return com.example.timeapp.data.Lang.digits(text)
}

/** The session editor's actions, the same wherever a session is shown. */
fun sessionActionsFor(entries: EntryRepository) = SessionActions(
    save = { id, start, end, note, reason -> entries.timer.editSession(id, start, end, note, reason) },
    addFive = { id -> entries.timer.addTime(id) },
    confirm = { id -> entries.timer.confirmSession(id) },
    delete = { id -> entries.timer.deleteSession(id) },
)
