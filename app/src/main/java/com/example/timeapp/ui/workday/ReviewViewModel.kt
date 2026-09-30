package com.example.timeapp.ui.workday

import kotlinx.coroutines.flow.flowOn
import com.example.timeapp.data.displayStatus
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.ReviewPeriod
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.ReviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DailyReviewUiState(
    val completedToday: List<Entry> = emptyList(),
    val carryoverTomorrow: List<Entry> = emptyList(),
    val note: String? = null, // null = not loaded from Room yet
)

/** Backs Daily and Weekly Review. Every number either screen shows is a live view over
 * `Entry`/`WorkSession`/`MoneyRecord` data (Weekly's metrics come from `StatsViewModel`,
 * its Money section from `computeMoneyOverview` — this ViewModel only owns the one new
 * piece of stored state either review needs: the free-text reflection). */
class ReviewViewModel(
    private val reviewRepository: ReviewRepository,
    private val entryRepository: EntryRepository,
) : ViewModel() {
    private val todayKey = LocalDate.now().toString()
    private val dailyNoteFlow = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            dailyNoteFlow.value = reviewRepository.getReview(ReviewPeriod.DAILY, todayKey)?.note ?: ""
        }
    }

    val dailyUiState: StateFlow<DailyReviewUiState> = combine(entryRepository.observeEntries(), dailyNoteFlow) { entries, note ->
        val today = LocalDate.now()
        DailyReviewUiState(
            completedToday = entries.filter { it.status == EntryStatus.DONE && it.dueOn == today },
            carryoverTomorrow = entries.filter {
                it.status == EntryStatus.UNSCHEDULED || it.displayStatus(today) == EntryStatus.OVERDUE
            },
            note = note,
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, DailyReviewUiState())

    fun saveDailyReview(note: String, onDone: () -> Unit) {
        viewModelScope.launch {
            reviewRepository.saveReview(ReviewPeriod.DAILY, todayKey, note)
            onDone()
        }
    }

    private val periodNoteFlow = MutableStateFlow<String?>(null)
    private var loadedKey: Pair<ReviewPeriod, String>? = null
    val periodNote: StateFlow<String?> = periodNoteFlow

    /**
     * Loads the reflection for one week ("2026-W40") or month ("2026-09"). Weekly and monthly
     * reviews are separate records — the Month view used to save into the *weekly* review of
     * the week holding the 1st, overwriting it. Idempotent; safe on every composition.
     */
    fun ensureNoteLoaded(period: ReviewPeriod, periodKey: String) {
        if (loadedKey == period to periodKey) return
        loadedKey = period to periodKey
        periodNoteFlow.value = null
        viewModelScope.launch {
            periodNoteFlow.value = reviewRepository.getReview(period, periodKey)?.note ?: ""
        }
    }

    fun saveReview(period: ReviewPeriod, periodKey: String, note: String, onDone: () -> Unit) {
        viewModelScope.launch {
            reviewRepository.saveReview(period, periodKey, note)
            onDone()
        }
    }

    class Factory(
        private val reviewRepository: ReviewRepository,
        private val entryRepository: EntryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReviewViewModel(reviewRepository, entryRepository) as T
    }
}
