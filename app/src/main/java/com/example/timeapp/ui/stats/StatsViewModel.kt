package com.example.timeapp.ui.stats

import kotlinx.coroutines.flow.flowOn
import com.example.timeapp.data.L

import kotlinx.coroutines.launch
import com.example.timeapp.data.msByDay
import com.example.timeapp.data.weekStart
import com.example.timeapp.data.netSpendMinor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timeapp.data.Direction
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.WorkCategory
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.MoneyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class StatsPeriod { WEEK, MONTH }

data class PeriodRange(val start: LocalDate, val endExclusive: LocalDate) {
    fun toInstantRange(): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        return start.atStartOfDay(zone).toInstant() to endExclusive.atStartOfDay(zone).toInstant()
    }
}

fun periodRange(period: StatsPeriod, today: LocalDate = LocalDate.now()): PeriodRange = when (period) {
    StatsPeriod.WEEK -> {
        val monday = today.weekStart()
        PeriodRange(monday, monday.plusDays(7))
    }
    StatsPeriod.MONTH -> {
        val month = YearMonth.from(today)
        PeriodRange(month.atDay(1), month.atEndOfMonth().plusDays(1))
    }
}

data class CategoryTime(val category: WorkCategory, val ms: Long, val id: Long? = null)
data class DailyTime(val date: LocalDate, val ms: Long)

data class TimeStatsUiState(
    val totalMs: Long = 0L,
    val sessionCount: Int = 0,
    val activeDayCount: Int = 0,
    val completedTaskCount: Int = 0,
    val byCategory: List<CategoryTime> = emptyList(),
    val byDay: List<DailyTime> = emptyList(),
    // Sum of scheduled-block durations (Entry.startAt..endAt) starting in the period —
    // Weekly Review's "Planned hours" reuses this instead of re-deriving it.
    val plannedMs: Long = 0L,
    /** Same sessions grouped by project; tasks in no project are left out, not lumped. */
    val byProject: List<CategoryTime> = emptyList(),
    /** Times work was stopped mid-task (Doing → Paused/Blocked) in the period… */
    val interruptions: Int = 0,
    /** …and how many of those were a switch straight to another task. */
    val switches: Int = 0,
    /** Tasks completed in the period that had an estimate: estimate vs all tracked time. */
    val estimates: List<EstimateVsActual> = emptyList(),
    /** Active time against the same elapsed part of the previous period. */
    val totalComparison: String = "",
)

/** One finished task's estimate against its real tracked time — facts, no score. */
data class EstimateVsActual(val title: String, val estimateMs: Long, val trackedMs: Long)

data class CategorySpend(val name: String, val colorLong: Long, val minor: Long, val budgetMinor: Long? = null)
/** [actualMinor] is net paid (payments minus refunds); [sources] are those ledger rows, for
 * the PDF's "drill-down into the source records". */
data class PlanVariance(
    val title: String,
    val plannedMinor: Long,
    val actualMinor: Long,
    val sources: List<com.example.timeapp.data.MoneyRecord> = emptyList(),
)

data class MoneyStatsUiState(
    val incomeMinor: Long = 0L,
    val expenseMinor: Long = 0L,
    val netMinor: Long = 0L,
    val byCategory: List<CategorySpend> = emptyList(),
    val variances: List<PlanVariance> = emptyList(),
    val expenseComparison: String = "",
    val incomeComparison: String = "",
)

/** Backs both Time and Money reports — one ViewModel since they share the Week/Month period
 * machinery and this codebase already constructs both repositories once per [com.example.timeapp.ui.TimeNavHost] composition. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val entryRepository: EntryRepository,
    private val moneyRepository: MoneyRepository,
    private val categoryRepository: com.example.timeapp.data.repository.CategoryRepository,
) : ViewModel() {

    private val period = MutableStateFlow(StatsPeriod.WEEK)
    val periodState: StateFlow<StatsPeriod> = period

    fun setPeriod(p: StatsPeriod) { period.value = p }

    val timeStats: StateFlow<TimeStatsUiState> = period.flatMapLatest { p ->
        val (from, to) = periodRange(p).toInstantRange()
        combine(
            entryRepository.sessionsInRange(from, to),
            entryRepository.observeEntries(),
            entryRepository.eventsInRange(from, to),
            // All-time sessions up to the period's end: a task finished this week may have been
            // worked on last week too, and its estimate is for all of it.
            entryRepository.sessionsInRange(Instant.EPOCH, to),
            comparisonSpan(p).let { (pf, pt) -> entryRepository.sumDurationMsInRange(pf, pt) },
        ) { sessions, entries, events, allSessions, previousMs ->
            val entryById = entries.associateBy { it.id }
            // Keyed by category id too, so a row can open that category's detail.
            val byCategory = sessions
                .mapNotNull { s -> entryById[s.entryId]?.let { e -> e.category?.let { (e.categoryId to it) to (s.endedAt.toEpochMilli() - s.startedAt.toEpochMilli()) } } }
                .groupBy({ it.first }, { it.second })
                .map { (key, durations) -> CategoryTime(key.second, durations.sum(), key.first) }
                // Focus run with no task: still work time, shown as its own line rather than
                // counted in the total but missing from every row.
                .plus(
                    sessions.filter { it.entryId == null }.sumOf { it.endedAt.toEpochMilli() - it.startedAt.toEpochMilli() }
                        .takeIf { it > 0 }
                        ?.let { listOf(CategoryTime(com.example.timeapp.data.WorkCategory(L("Focus, no task"), NO_TASK_COLOR), it)) }
                        .orEmpty(),
                )
                // Time on tasks with no category: the card said "No tracked time in this period"
                // under a total that plainly had some.
                .plus(
                    sessions.filter { s -> s.entryId != null && entryById[s.entryId]?.category == null }
                        .sumOf { it.endedAt.toEpochMilli() - it.startedAt.toEpochMilli() }
                        .takeIf { it > 0 }
                        ?.let { listOf(CategoryTime(com.example.timeapp.data.WorkCategory(L("Uncategorised"), NO_TASK_COLOR), it)) }
                        .orEmpty(),
                )
                .sortedByDescending { it.ms }
            val byProject = sessions
                .mapNotNull { s -> entryById[s.entryId]?.project?.let { it to (s.endedAt.toEpochMilli() - s.startedAt.toEpochMilli()) } }
                .groupBy({ it.first }, { it.second })
                .map { (project, durations) -> CategoryTime(project, durations.sum()) }
                .sortedByDescending { it.ms }
            val zone = ZoneId.systemDefault()
            // Split at midnight: a late session counts on each day it ran through.
            val msByDay = sessions.msByDay(zone)
            val byDay = msByDay.map { (date, ms) -> DailyTime(date, ms) }
            // Finished in this period — by when it was done, not when it was due (a task with no
            // due date was never counted, and one due now but done last month was).
            val completed = entries.count { e ->
                e.status == EntryStatus.DONE && e.completedAt != null && e.completedAt >= from && e.completedAt < to
            }
            val plannedMs = entries
                .filter { it.startAt != null && it.endAt != null && it.startAt >= from && it.startAt < to }
                .sumOf { it.endAt!!.toEpochMilli() - it.startAt!!.toEpochMilli() }
            TimeStatsUiState(
                totalMs = sessions.sumOf { it.endedAt.toEpochMilli() - it.startedAt.toEpochMilli() },
                sessionCount = sessions.size,
                activeDayCount = msByDay.count { it.value > 0 },
                completedTaskCount = completed,
                byCategory = byCategory,
                byDay = byDay,
                plannedMs = plannedMs,
                byProject = byProject,
                totalComparison = com.example.timeapp.data.compareText(
                    sessions.sumOf { it.endedAt.toEpochMilli() - it.startedAt.toEpochMilli() } / 60_000,
                    previousMs / 60_000,
                    if (p == StatsPeriod.WEEK) L("last week") else L("last month"),
                    comparison(p).toDate,
                ),
                interruptions = events.count {
                    it.fromStatus == EntryStatus.DOING && (it.toStatus == EntryStatus.PAUSED || it.toStatus == EntryStatus.BLOCKED)
                },
                switches = events.count { it.fromStatus == EntryStatus.DOING && it.relatedEntryId != null },
                estimates = run {
                    val trackedByEntry = allSessions.groupBy { it.entryId }
                        .mapValues { (_, spans) -> spans.sumOf { it.endedAt.toEpochMilli() - it.startedAt.toEpochMilli() } }
                    events.filter { it.toStatus == EntryStatus.DONE }.map { it.entryId }.distinct().mapNotNull { id ->
                        val e = entryById[id] ?: return@mapNotNull null
                        val est = e.estimateMs ?: return@mapNotNull null
                        EstimateVsActual(e.title, est, trackedByEntry[id] ?: 0L)
                    }
                },
            )
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, TimeStatsUiState())

    val moneyStats: StateFlow<MoneyStatsUiState> = period.flatMapLatest { p ->
        val (from, to) = periodRange(p).toInstantRange()
        combine(
            moneyRepository.observeHistory(),
            moneyRepository.observeSettledPlansInRange(from, to),
            categoryRepository.observeAll(),
        ) { history, settledPlans, categories ->
            val inRange = history.filter { it.settledAt != null && it.settledAt >= from && it.settledAt < to }
            val income = inRange.filter { it.direction == Direction.INCOME }.sumOf { it.paidMinor }
            // Net of refunds, which are not income: they take spending back instead.
            val expense = inRange.sumOf { it.netSpendMinor }
            val categoriesById = categories.associateBy { it.id }
            val byCategory = inRange
                .filter { it.direction == Direction.COST || it.direction == Direction.REFUND }
                .groupBy { it.categoryId }
                .map { (categoryId, records) ->
                    val category = categoryId?.let { categoriesById[it] }
                    CategorySpend(
                        name = category?.name ?: L("Uncategorised"),
                        colorLong = category?.colorArgb ?: records.firstOrNull()?.categoryColor ?: 0xFF6C4ED9,
                        minor = records.sumOf { it.netSpendMinor },
                        budgetMinor = category?.budgetMinor,
                    )
                }
                .sortedByDescending { it.minor }
            val variances = settledPlans
                .filter { it.status == PlanStatus.SETTLED }
                .map { plan ->
                    PlanVariance(
                        plan.title, plan.amountMinor, plan.paidMinor,
                        sources = history.filter { it.linkedPlanId == plan.id }.sortedBy { it.settledAt },
                    )
                }
            val (pf, pt) = comparisonSpan(p)
            val previous = history.filter { it.settledAt != null && it.settledAt >= pf && it.settledAt < pt }
            val label = if (p == StatsPeriod.WEEK) L("last week") else L("last month")
            val toDate = comparison(p).toDate
            MoneyStatsUiState(
                expenseComparison = com.example.timeapp.data.compareText(expense, previous.sumOf { it.netSpendMinor }, label, toDate),
                incomeComparison = com.example.timeapp.data.compareText(
                    income, previous.filter { it.direction == Direction.INCOME }.sumOf { it.paidMinor }, label, toDate,
                ),
                incomeMinor = income,
                expenseMinor = expense,
                netMinor = income - expense,
                byCategory = byCategory,
                variances = variances,
            )
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, MoneyStatsUiState())

    private fun comparison(p: StatsPeriod) = com.example.timeapp.data.comparisonFor(
        if (p == StatsPeriod.WEEK) com.example.timeapp.data.ComparePeriod.WEEK else com.example.timeapp.data.ComparePeriod.MONTH,
        LocalDate.now(),
    )

    /** The previous period's equivalent elapsed span, as instants. */
    private fun comparisonSpan(p: StatsPeriod): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val prev = comparison(p).previous
        return prev.start.atStartOfDay(zone).toInstant() to prev.endExclusive.atStartOfDay(zone).toInstant()
    }

    /** Every closed session ever — the category detail's source. */
    val allSessions: StateFlow<List<com.example.timeapp.data.WorkSessionSpan>> =
        entryRepository.sessionsInRange(Instant.EPOCH, Instant.now().plusSeconds(3_650L * 86_400))
            .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every version of the weekly work target, oldest first. */
    val workTargets: StateFlow<List<com.example.timeapp.data.WorkTarget>> = entryRepository.observeWorkTargets()
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Sets the weekly target from this week's Monday on; past weeks keep theirs. 0 clears it. */
    fun setWeeklyTarget(minutes: Int) {
        val monday = LocalDate.now().weekStart()
        viewModelScope.launch { entryRepository.setWorkTarget(minutes.coerceAtLeast(0), monday) }
    }

    class Factory(
        private val entryRepository: EntryRepository,
        private val moneyRepository: MoneyRepository,
        private val categoryRepository: com.example.timeapp.data.repository.CategoryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StatsViewModel(entryRepository, moneyRepository, categoryRepository) as T
    }
}

/** The neutral swatch for Focus time with no task (a data value, like a category's colour). */
private const val NO_TASK_COLOR = 0xFF8A8494
