package com.example.timeapp.ui.habits

import kotlinx.coroutines.flow.flowOn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.HabitStats
import com.example.timeapp.data.ReminderOwnerKind
import com.example.timeapp.data.habitStats
import com.example.timeapp.data.next
import com.example.timeapp.data.nextHabitReminder
import com.example.timeapp.data.repository.HabitRepository
import com.example.timeapp.data.repository.ReminderRepository
import com.example.timeapp.data.showsToday
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** One habit as every habit surface shows it — its marks and derived stats, computed once. */
data class HabitRow(
    val habit: Habit,
    val marks: Map<LocalDate, HabitMark>,
    val stats: HabitStats,
    val showsToday: Boolean,
    /** Full day records — amounts and notes. */
    val checks: Map<LocalDate, com.example.timeapp.data.HabitCheck> = emptyMap(),
)

data class HabitsUiState(
    val active: List<HabitRow> = emptyList(),
    val archived: List<HabitRow> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** Timed habits whose timer is running: habit id → when it rings. */
    val runningTimers: Map<Long, java.time.Instant> = emptyMap(),
) {
    val dueToday: List<HabitRow> get() = active.filter { it.showsToday }
}

/** Shared by the Habits list, Habit detail, the habit form and Today's Habits section. */
class HabitsViewModel(
    private val repository: HabitRepository,
    private val reminders: ReminderRepository,
) : ViewModel() {

    /** Re-derives "today" each minute, so streaks and Today's list roll over at midnight
     * while the app stays open instead of showing yesterday's state. */
    private val today = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<HabitsUiState> =
        combine(repository.observeHabits(), repository.observeChecks(), today, repository.observeHabitTimers()) { habits, checks, day, timers ->
            val byHabit = checks.groupBy { it.habitId }
            val rows = habits.map { habit ->
                val mine = byHabit[habit.id].orEmpty()
                val marks = mine.associate { it.date to it.mark }
                HabitRow(habit, marks, habitStats(habit, marks, day), habit.showsToday(marks, day), mine.associateBy { it.date })
            }
            HabitsUiState(
                active = rows.filterNot { it.habit.archived },
                archived = rows.filter { it.habit.archived },
                today = day,
                runningTimers = timers,
            )
        }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, HabitsUiState())

    /** New when [Habit.id] is 0, otherwise an edit. The reminder is re-derived either way. */
    fun save(habit: Habit) {
        viewModelScope.launch {
            val id = if (habit.id == 0L) repository.add(habit) else habit.id.also { repository.update(habit) }
            syncReminder(habit.copy(id = id))
        }
    }

    /** Moves a habit up or down the list; the order is the user's, kept everywhere. */
    fun move(habitId: Long, offset: Int) {
        val ids = uiState.value.active.map { it.habit.id }.toMutableList()
        val from = ids.indexOf(habitId)
        val to = (from + offset).coerceIn(0, ids.lastIndex)
        if (from < 0 || from == to) return
        ids.removeAt(from); ids.add(to, habitId)
        viewModelScope.launch { repository.setOrder(ids) }
    }

    fun setArchived(habit: Habit, archived: Boolean) {
        viewModelScope.launch {
            val updated = habit.copy(archived = archived)
            repository.update(updated)
            syncReminder(updated)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            reminders.clearReminder(ReminderOwnerKind.HABIT, id)
            repository.delete(id)
        }
    }

    /** A day's tap: nothing → done → rest day → nothing. Future days are never markable. */
    fun cycle(habitId: Long, date: LocalDate, current: HabitMark?) {
        if (date.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            val next = current.next()
            repository.setMark(habitId, date, next)
            if (next == HabitMark.DONE) celebrateIfMilestone(habitId)
        }
    }

    /** Starts a timed habit's timer; it rings on the Work & timers channel and checks the day off. */
    fun startTimer(habit: Habit) {
        viewModelScope.launch {
            val (id, ends) = repository.startTimer(habit) ?: return@launch
            reminders.setReminder(ReminderOwnerKind.TIMER, id, ends)
        }
    }

    /** The day editor's save — see [HabitRepository.setDay]. */
    fun setDay(habitId: Long, date: LocalDate, mark: HabitMark?, value: Double?, note: String?) {
        if (date.isAfter(LocalDate.now())) return
        viewModelScope.launch {
            repository.setDay(habitId, date, mark, value, note)
            if (repository.marksFor(habitId)[date] == HabitMark.DONE) celebrateIfMilestone(habitId)
        }
    }

    /** Today's one-tap check: done or not. Marking a rest day lives on the day strip. */
    fun toggleDone(habitId: Long, date: LocalDate, current: HabitMark?) {
        viewModelScope.launch {
            val done = current != HabitMark.DONE
            repository.setMark(habitId, date, if (done) HabitMark.DONE else null)
            if (done) celebrateIfMilestone(habitId)
        }
    }

    /** Confetti when a check-in lands the current streak exactly on a milestone. */
    private suspend fun celebrateIfMilestone(habitId: Long) {
        val habit = repository.getHabit(habitId) ?: return
        val stats = habitStats(habit, repository.marksFor(habitId), LocalDate.now())
        if (stats.currentStreak in com.example.timeapp.ui.components.StreakMilestones ||
            (stats.unit == com.example.timeapp.data.StreakUnit.WEEKS && stats.currentStreak in setOf(4, 12, 26, 52)) ||
            (stats.unit == com.example.timeapp.data.StreakUnit.MONTHS && stats.currentStreak in setOf(3, 6, 12))
        ) {
            val unit = when (stats.unit) {
                com.example.timeapp.data.StreakUnit.WEEKS -> "week"
                com.example.timeapp.data.StreakUnit.MONTHS -> "month"
                com.example.timeapp.data.StreakUnit.DAYS -> "day"
            }
            com.example.timeapp.ui.components.CelebrationBus.emit(
                com.example.timeapp.ui.components.Celebration.Streak(habit.name, "${stats.currentStreak}-$unit"),
            )
        }
    }

    /** One reminder row per habit, pointed at its next due day; the receiver walks it forward
     * after each delivery. Archived or reminder-off habits have none. */
    private suspend fun syncReminder(habit: Habit) {
        val at = habit.remindAt
        if (habit.archived || at == null) {
            reminders.clearReminder(ReminderOwnerKind.HABIT, habit.id)
            return
        }
        val zone = ZoneId.systemDefault()
        val next = nextHabitReminder(habit.schedule, at, LocalDateTime.now(zone))
        reminders.setReminder(ReminderOwnerKind.HABIT, habit.id, next.atZone(zone).toInstant())
    }

    class Factory(
        private val repository: HabitRepository,
        private val reminders: ReminderRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HabitsViewModel(repository, reminders) as T
    }
}
