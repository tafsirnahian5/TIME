package com.example.timeapp.ui.focus

import kotlinx.coroutines.flow.flowOn
import com.example.timeapp.data.L

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.UtilityTimerEntity
import com.example.timeapp.data.db.remainingAt
import com.example.timeapp.data.repository.ReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Utility timers: start/pause/reset each one independently. The end of a running timer is a
 * reminder alarm (owner kind TIMER), so it rings through reboots and app death like any
 * other reminder, and is decided at delivery. Nothing here touches work sessions.
 */
class UtilityTimersViewModel(
    private val db: TimeDatabase,
    private val reminders: ReminderRepository,
) : ViewModel() {
    private val dao get() = db.utilityTimerDao()

    val timers: StateFlow<List<UtilityTimerEntity>> = dao.observeAll()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Exact alarms may be refused by the system; the screen says so rather than promising. */
    fun canRingExactly(): Boolean = reminders.canScheduleExact()

    fun add(label: String, durationMs: Long, startNow: Boolean) {
        if (durationMs <= 0) return
        viewModelScope.launch {
            val id = dao.insert(UtilityTimerEntity(label = label.trim().ifEmpty { L("Timer") }, durationMs = durationMs, remainingMs = durationMs))
            if (startNow) dao.getById(id)?.let { start(it) }
        }
    }

    fun toggle(timer: UtilityTimerEntity) {
        com.example.timeapp.data.notify.AlarmService.stopRinging()
        viewModelScope.launch { if (timer.endsAt != null) pause(timer) else start(timer) }
    }

    private suspend fun start(timer: UtilityTimerEntity) {
        val left = if (timer.remainingMs > 0) timer.remainingMs else timer.durationMs
        val endsAt = Instant.now().plusMillis(left)
        dao.update(timer.copy(endsAt = endsAt, remainingMs = left))
        reminders.setReminder(ReminderOwnerKind.TIMER, timer.id, endsAt)
    }

    private suspend fun pause(timer: UtilityTimerEntity) {
        dao.update(timer.copy(endsAt = null, remainingMs = timer.remainingAt(Instant.now())))
        reminders.clearReminder(ReminderOwnerKind.TIMER, timer.id)
    }

    fun reset(timer: UtilityTimerEntity) {
        com.example.timeapp.data.notify.AlarmService.stopRinging()
        viewModelScope.launch {
            dao.update(timer.copy(endsAt = null, remainingMs = timer.durationMs))
            reminders.clearReminder(ReminderOwnerKind.TIMER, timer.id)
        }
    }

    fun delete(timer: UtilityTimerEntity) {
        com.example.timeapp.data.notify.AlarmService.stopRinging()
        viewModelScope.launch {
            reminders.clearReminder(ReminderOwnerKind.TIMER, timer.id)
            dao.delete(timer.id)
        }
    }

    class Factory(private val db: TimeDatabase, private val reminders: ReminderRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UtilityTimersViewModel(db, reminders) as T
    }
}
