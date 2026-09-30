package com.example.timeapp.ui.workday

import kotlinx.coroutines.flow.flowOn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.WorkdaySegment
import com.example.timeapp.data.WorkdaySegmentKind
import com.example.timeapp.data.WorkdaySummary
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.WorkdayRepository
import com.example.timeapp.data.workdaySummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class WorkdayUiState(
    val date: LocalDate = LocalDate.now(),
    val summary: WorkdaySummary = WorkdaySummary(0, 0, 0, 0),
    val segments: List<WorkdaySegment> = emptyList(),
    val activeKind: WorkdaySegmentKind? = null,
    /** An earlier day that was never ended — flagged for review, not counted as hours. */
    val forgotten: WorkdaySegment? = null,
)

/**
 * Backs My Workday: today's on-duty / break / active / untracked figures (live, the running
 * interval included — it used to be left out until the next break), the day's actions, the
 * editable timeline, and a forgotten earlier day to close.
 */
class WorkdayViewModel(
    private val workdayRepository: WorkdayRepository,
    private val entryRepository: EntryRepository,
) : ViewModel() {
    private val today = LocalDate.now()
    private val zone = ZoneId.systemDefault()
    private val ticker = flow {
        while (true) {
            emit(Instant.now())
            delay(30_000)
        }
    }

    val uiState: StateFlow<WorkdayUiState> = combine(
        workdayRepository.observeForDate(today),
        entryRepository.sessionsInRange(today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant()),
        workdayRepository.observeForgottenBefore(today),
        ticker,
    ) { segments, sessions, forgotten, now ->
        WorkdayUiState(
            date = today,
            summary = workdaySummary(segments, sessions, now),
            segments = segments,
            activeKind = segments.lastOrNull { it.endedAt == null }?.kind,
            forgotten = forgotten,
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, WorkdayUiState())

    fun startDay() = viewModelScope.launch { workdayRepository.startDay(today) }
    fun takeBreak() = viewModelScope.launch { workdayRepository.takeBreak(today) }

    /** The task the current break interrupted, for Resume day to offer. */
    suspend fun interruptedTask(): Long? = workdayRepository.taskInterruptedByBreak(today)
    fun resumeDay(taskId: Long?) = viewModelScope.launch { workdayRepository.resumeDay(today, taskId) }

    suspend fun workIsRunning(): Boolean = workdayRepository.workIsRunning()
    fun endDay(finishWork: Boolean) = viewModelScope.launch { workdayRepository.endDay(today, finishWork) }

    fun closeForgotten(id: Long, at: Instant) = viewModelScope.launch { workdayRepository.closeForgotten(id, at) }

    suspend fun correct(id: Long, start: Instant, end: Instant?, reason: String): String? =
        workdayRepository.correct(id, start, end, reason)

    class Factory(
        private val workdayRepository: WorkdayRepository,
        private val entryRepository: EntryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkdayViewModel(workdayRepository, entryRepository) as T
    }
}
