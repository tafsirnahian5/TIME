package com.example.timeapp.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Savings goals — the PDF's "savings targets with target amount/date and optional earmarks".
 *
 * Money is **set aside** for a goal from a wallet ([GoalAllocation]); it never leaves the
 * wallet. The wallet balance stays the real posted balance, and what's set aside is shown
 * separately as earmarked, with the rest as *unearmarked* — "earmarking does not change the
 * real wallet balance; show unearmarked money separately". Goals never appear in spending or
 * income: they aren't transactions. Pure; tested in SavingsTest.
 */
data class SavingsGoal(
    val id: Long,
    val name: String,
    val targetMinor: Long,
    val targetDate: LocalDate? = null,
    val archived: Boolean = false,
)

/** One set-aside (positive) or take-back (negative) against a goal, from one wallet. */
data class GoalAllocation(val id: Long, val goalId: Long, val walletId: Long, val amountMinor: Long, val at: Instant)

fun List<GoalAllocation>.savedFor(goalId: Long): Long = filter { it.goalId == goalId }.sumOf { it.amountMinor }

/** How much of each wallet is set aside, across every goal. */
fun List<GoalAllocation>.earmarkedByWallet(): Map<Long, Long> =
    groupBy { it.walletId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }.filterValues { it != 0L }

/** What's left to set aside from a wallet: its balance less what's already earmarked there.
 * Never negative — a balance that has since dropped below its earmarks shows 0 free. */
fun unearmarkedMinor(balanceMinor: Long, earmarkedMinor: Long): Long = (balanceMinor - earmarkedMinor).coerceAtLeast(0)

/**
 * Where a goal stands as of [today]. With a target date: how much to set aside per month
 * (this month counted) to arrive on time, rounded up to the poisha so it's never short.
 */
data class GoalPace(val savedMinor: Long, val remainingMinor: Long, val monthsLeft: Int?, val perMonthMinor: Long?, val late: Boolean) {
    val reached: Boolean get() = remainingMinor == 0L
}

fun goalPace(goal: SavingsGoal, savedMinor: Long, today: LocalDate): GoalPace {
    val remaining = (goal.targetMinor - savedMinor).coerceAtLeast(0)
    val date = goal.targetDate ?: return GoalPace(savedMinor, remaining, null, null, late = false)
    if (date.isBefore(today)) return GoalPace(savedMinor, remaining, 0, null, late = remaining > 0)
    val months = (ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(date)) + 1).toInt()
    val perMonth = if (remaining == 0L) 0L else (remaining + months - 1) / months
    return GoalPace(savedMinor, remaining, months, perMonth, late = false)
}
