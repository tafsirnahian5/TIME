package com.example.timeapp.data

import java.time.YearMonth

/** One budget and where it stands this month. [plannedMinor] is what's still owed in the
 * month's window (for the projection); a category has no projection when plans lack one. */
data class BudgetStanding(val scope: String, val name: String, val limitMinor: Long, val spentMinor: Long, val plannedMinor: Long)

/** A budget notice to post at most once per [key] (the key carries the month). */
data class BudgetAlert(val key: String, val title: String, val body: String)

/**
 * Budget alerts (Phase 11; the PDF's "projected budget warning"): for each budget, the most
 * serious of — over the limit, projected to go over (spent + still planned > limit), or 80%
 * used. Each fires once a month per level; a limit of zero or less never alerts. Words, not a
 * verdict: they state the numbers. Pure; tested in BudgetAlertsTest.
 */
fun budgetAlerts(month: YearMonth, standings: List<BudgetStanding>): List<BudgetAlert> = standings.mapNotNull { b ->
    if (b.limitMinor <= 0) return@mapNotNull null
    val tag = "${b.scope}|$month"
    when {
        b.spentMinor > b.limitMinor -> BudgetAlert(
            "over|$tag", L("%1\$s: over budget", b.name),
            L("%1\$s spent this month — %2\$s over the %3\$s budget.", b.spentMinor.toTaka(), ((b.spentMinor - b.limitMinor).toTaka()), b.limitMinor.toTaka()),
        )
        b.spentMinor + b.plannedMinor > b.limitMinor -> BudgetAlert(
            "projected|$tag", L("%1\$s: heading over budget", b.name),
            L("%1\$s spent and %2\$s still planned this month — %3\$s more than the %4\$s budget.", b.spentMinor.toTaka(), b.plannedMinor.toTaka(), ((b.spentMinor + b.plannedMinor - b.limitMinor).toTaka()), b.limitMinor.toTaka()),
        )
        b.spentMinor * 10 >= b.limitMinor * 8 -> BudgetAlert(
            "80|$tag", L("%1\$s: 80%% of the budget used", b.name),
            L("%1\$s of %2\$s spent this month — %3\$s left.", b.spentMinor.toTaka(), b.limitMinor.toTaka(), ((b.limitMinor - b.spentMinor).toTaka())),
        )
        else -> null
    }
}
