package com.example.timeapp.data

/**
 * Today's getting-started checklist (Phase 19B). Every step ticks itself from real records —
 * nothing is ticked by tapping it, so the card can never claim something that didn't happen.
 */
enum class StartStep { TASK, TIMER, WALLET, BILL, HABIT }

data class StartFacts(
    val hasTask: Boolean,
    val hasTrackedTime: Boolean,
    val hasWallet: Boolean,
    val hasPlannedCost: Boolean,
    val hasHabit: Boolean,
)

fun StartFacts.isDone(step: StartStep): Boolean = when (step) {
    StartStep.TASK -> hasTask
    StartStep.TIMER -> hasTrackedTime
    StartStep.WALLET -> hasWallet
    StartStep.BILL -> hasPlannedCost
    StartStep.HABIT -> hasHabit
}

fun StartFacts.doneCount(): Int = StartStep.entries.count { isDone(it) }

/** Shown until every step is done or the person closes it. */
fun showGettingStarted(facts: StartFacts, dismissed: Boolean): Boolean =
    !dismissed && facts.doneCount() < StartStep.entries.size
