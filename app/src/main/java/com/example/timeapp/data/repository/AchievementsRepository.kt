package com.example.timeapp.data.repository

import com.example.timeapp.data.L

import com.example.timeapp.data.Achievement
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.ReviewPeriod
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.habitStats
import com.example.timeapp.data.netSpendMinor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Milestones **derived from live records**, not a stored list, grouped into the PDF's
 * "achievement collections".
 *
 * The old Achievements screen read a frozen seeded table that no code ever updated, so it
 * showed the same made-up badges forever and nothing could ever be earned. Everything here
 * is recomputed from what the user has actually done, which also means there is no reward
 * ledger to keep in sync and nothing to reconcile after an edit, delete or restore.
 *
 * Deliberately not XP: no points, levels or scores. These are just "you have done this"
 * markers.
 */
class AchievementsRepository(
    private val db: TimeDatabase,
    private val settingsStore: AppSettingsDataStore,
) {
    /** Everything besides entries/sessions/reviews/history, in one flow (combine takes five). */
    private data class Extra(
        val settings: com.example.timeapp.ui.AppSettingsUiState,
        val habits: List<com.example.timeapp.data.db.HabitEntity>,
        val checks: List<com.example.timeapp.data.db.HabitCheckEntity>,
        val goals: List<com.example.timeapp.data.db.SavingsGoalEntity>,
        val allocations: List<com.example.timeapp.data.db.GoalAllocationEntity>,
    )

    fun observe(): Flow<List<Achievement>> = combine(
        db.entryDao().observeAll(),
        db.workSessionDao().sessionsInRange(Instant.EPOCH, Instant.now().plusSeconds(YEAR_SECONDS)),
        db.reviewDao().observeAll(),
        db.moneyRecordDao().observeHistory(),
        combine(
            settingsStore.settings, db.habitDao().observeAll(), db.habitDao().observeAllChecks(),
            db.savingsDao().observeGoals(), db.savingsDao().observeAllocations(), ::Extra,
        ),
    ) { entries, sessions, reviews, history, extra ->
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val completed = entries.count { it.status == EntryStatus.DONE }
        val trackedHours = (sessions.sumOf { s -> s.endedAt?.let { it.toEpochMilli() - s.startedAt.toEpochMilli() } ?: 0L } / 3_600_000L).toInt()
        val dailyReviews = reviews.count { it.period == ReviewPeriod.DAILY }
        val weeklyReviews = reviews.count { it.period == ReviewPeriod.WEEKLY }
        val monthlyReviews = reviews.count { it.period == ReviewPeriod.MONTHLY }

        val doneChecks = extra.checks.count { it.mark == HabitMark.DONE }
        val checksByHabit = extra.checks.groupBy { it.habitId }
        // Longest day-based streak any habit has reached; weekly-target habits count weeks,
        // a different unit, so they don't feed the day milestones.
        val bestDayStreak = extra.habits.map { it.toDomain() }
            .filter { !it.schedule.isPeriodTarget }
            .maxOfOrNull { habit ->
                val marks = checksByHabit[habit.id].orEmpty().associate { it.date to it.mark }
                habitStats(habit, marks, today).bestStreak
            } ?: 0

        // A goal counts as reached if what was set aside for it ever met its target — even if
        // finishing it has since released the money.
        val goalsReached = extra.goals.count { g ->
            var running = 0L
            var peak = 0L
            extra.allocations.filter { it.goalId == g.id }.sortedBy { it.at }.forEach { running += it.amountMinor; peak = maxOf(peak, running) }
            peak >= g.targetMinor
        }
        // Last complete month's net spending against the monthly budget.
        val lastMonth = YearMonth.from(today).minusMonths(1)
        val spentLastMonth = history.map { it.toDomain() }
            .filter { it.settledAt != null && YearMonth.from(it.settledAt.atZone(zone)) == lastMonth }
            .sumOf { it.netSpendMinor }
        val hadSpending = history.any { it.settledAt != null && YearMonth.from(it.settledAt.atZone(zone)) == lastMonth }
        val underBudget = hadSpending && extra.settings.monthlyBudgetMinor > 0 && spentLastMonth <= extra.settings.monthlyBudgetMinor

        listOf(
            milestone(WORK, L("First task finished"), "check_circle", completed, 1),
            milestone(WORK, L("Ten tracked sessions"), "timer", sessions.size, 10),
            milestone(WORK, L("Fifty hours tracked"), "timer", trackedHours, 50),
            milestone(WORK, L("A hundred tasks finished"), "task_alt", completed, 100),
            milestone(HABITS, L("First habit check-in"), "event_repeat", doneChecks, 1),
            milestone(HABITS, L("Seven-day habit streak"), "local_fire_department", bestDayStreak, 7),
            milestone(HABITS, L("Fifty habit check-ins"), "task_alt", doneChecks, 50),
            milestone(HABITS, L("Thirty-day habit streak"), "local_fire_department", bestDayStreak, 30),
            milestone(MONEY, L("First payment recorded"), "payments", history.size, 1),
            milestone(MONEY, L("First savings goal reached"), "savings", goalsReached, 1),
            milestone(MONEY, L("A month under budget"), "savings", if (underBudget) 1 else 0, 1),
            milestone(REVIEWS, L("A week of daily reviews"), "event_note", dailyReviews, 7),
            milestone(REVIEWS, L("First weekly review"), "rate_review", weeklyReviews, 1),
            milestone(REVIEWS, L("First monthly review"), "rate_review", monthlyReviews, 1),
            milestone(SAFETY, L("First backup saved"), "cloud_done", if (extra.settings.lastBackupEpochMs > 0) 1 else 0, 1),
        )
    }

    private fun milestone(collection: String, title: String, icon: String, done: Int, target: Int): Achievement {
        val achieved = done >= target
        return Achievement(
            title = title,
            icon = icon,
            // Earned markers carry no date: nothing records *when* a milestone first became
            // true, and inventing one would be worse than omitting it.
            earnedLabel = if (achieved) L("Earned") else null,
            progressLabel = if (achieved) null else L("%1\$s of %2\$s", done.coerceAtMost(target), target),
            progressFraction = if (achieved) null else (done.toFloat() / target).coerceIn(0f, 1f),
            collection = collection,
        )
    }

    companion object {
        private const val YEAR_SECONDS = 365L * 24 * 60 * 60
        const val WORK = "Work"
        const val HABITS = "Habits"
        const val MONEY = "Money"
        const val REVIEWS = "Reviews"
        const val SAFETY = "Safety"
        /** Collection order on the screen. */
        val COLLECTIONS = listOf(WORK, HABITS, MONEY, REVIEWS, SAFETY)
    }
}
