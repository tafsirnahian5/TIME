package com.example.timeapp.ui.me

import kotlinx.coroutines.flow.flowOn
import com.example.timeapp.data.L

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.Attribute
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.ReviewPeriod
import com.example.timeapp.data.computeAttributes
import com.example.timeapp.data.repository.CommitmentRepository
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.ReviewRepository
import com.example.timeapp.data.shortLabel
import com.example.timeapp.data.weekStart
import com.example.timeapp.data.withBlocksExpanded
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The Me page's period: this week, or the last four weeks (this one included). */
enum class MePeriod(val label: String, val weeks: Int) { THIS_WEEK(L("This week"), 1), FOUR_WEEKS(L("Last 4 weeks"), 4) }

data class MeUiState(
    val period: MePeriod = MePeriod.THIS_WEEK,
    val from: LocalDate = LocalDate.now().weekStart(),
    val attributes: List<Attribute> = emptyList(),
)

/** Backs the Me attributes (see [computeAttributes]) from live records. */
@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModel(
    entryRepository: EntryRepository,
    commitmentRepository: CommitmentRepository,
    reviewRepository: ReviewRepository,
    settingsStore: AppSettingsDataStore,
) : ViewModel() {
    private val period = MutableStateFlow(MePeriod.THIS_WEEK)
    private val zone = ZoneId.systemDefault()

    fun setPeriod(value: MePeriod) { period.value = value }

    val uiState: StateFlow<MeUiState> = period.flatMapLatest { p ->
        val today = LocalDate.now()
        val weekStarts = (p.weeks - 1 downTo 0).map { today.weekStart().minusWeeks(it.toLong()) }
        val from = weekStarts.first()
        val fromInstant = from.atStartOfDay(zone).toInstant()
        val toInstant = today.plusDays(1).atStartOfDay(zone).toInstant()
        combine(
            entryRepository.sessionsInRange(fromInstant, toInstant),
            entryRepository.observeWorkTargets(),
            commitmentRepository.observeBetween(from, today),
            entryRepository.observeEntries(),
            combine(reviewRepository.observeAll(), settingsStore.settings) { r, s -> r to s },
        ) { sessions, targets, commitments, entries, (reviews, settings) ->
            val weekly = reviews.filter { it.period == ReviewPeriod.WEEKLY }
            val restDays = DayOfWeek.entries.filter { d -> d.shortLabel() in settings.restDays }.toSet()
            val taskBlocks = entries.filter { it.kind == EntryKind.TASK }.withBlocksExpanded()
                .filter { b -> b.startAt != null && !b.startAt.isBefore(fromInstant) && b.startAt.isBefore(toInstant) }
            MeUiState(
                period = p,
                from = from,
                attributes = computeAttributes(
                    weekStarts = weekStarts, today = today, now = Instant.now(), zone = zone,
                    sessions = sessions, targets = targets, commitments = commitments,
                    entriesById = entries.associateBy { it.id }, restDays = restDays,
                    taskBlocks = taskBlocks,
                    reviewKeys = weekly.map { it.periodKey }.toSet(),
                    firstReviewWeek = weekly.minByOrNull { it.savedAt }?.savedAt?.atZone(zone)?.toLocalDate()?.weekStart(),
                ),
            )
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeUiState())

    class Factory(
        private val entryRepository: EntryRepository,
        private val commitmentRepository: CommitmentRepository,
        private val reviewRepository: ReviewRepository,
        private val settingsStore: AppSettingsDataStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MeViewModel(entryRepository, commitmentRepository, reviewRepository, settingsStore) as T
    }
}
