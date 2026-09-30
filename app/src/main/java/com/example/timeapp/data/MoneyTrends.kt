package com.example.timeapp.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One day in the money calendar. */
data class DayMoney(val spentMinor: Long, val incomeMinor: Long, val rows: List<MoneyRecord>)

/**
 * The money calendar (Phase 13, after XPENC): each day of [month] with what was spent (net of
 * refunds) and earned. Transfers and loans move money but aren't either, so they only appear in
 * the day's list. Pure; tested in MoneyTrendsTest.
 */
fun moneyByDay(history: List<MoneyRecord>, month: YearMonth, zone: ZoneId): Map<LocalDate, DayMoney> =
    history.filter { it.settledAt != null && YearMonth.from(it.settledAt.atZone(zone)) == month }
        .groupBy { it.settledAt!!.atZone(zone).toLocalDate() }
        .mapValues { (_, rows) ->
            DayMoney(rows.sumOf { it.netSpendMinor }, rows.filter { it.direction == Direction.INCOME }.sumOf { it.paidMinor }, rows.sortedBy { it.settledAt })
        }

/**
 * Net worth at the end of each of the last [months] months, oldest first: money in wallets
 * (opening balances from their opening date, plus every posted row), plus what people owe you,
 * minus what you owe, on loans open at that point. Savings earmarks don't change it — the money
 * is still in the wallets.
 */
fun netWorthByMonth(
    wallets: List<Wallet>,
    history: List<MoneyRecord>,
    loans: List<Loan>,
    today: LocalDate,
    zone: ZoneId,
    months: Int = 12,
): List<Pair<YearMonth, Long>> {
    val now = YearMonth.from(today)
    return (months - 1 downTo 0).map { back ->
        val month = now.minusMonths(back.toLong())
        val endDay = if (month == now) today else month.atEndOfMonth()
        val end = endDay.plusDays(1).atStartOfDay(zone).toInstant()
        val posted = history.filter { it.settledAt != null && it.settledAt.isBefore(end) }
        val cash = wallets.filter { !it.openingDate.isAfter(endDay) }.sumOf { it.openingBalanceMinor } + posted.sumOf { it.totalEffect }
        val loansThen = loans.filter { !it.startedOn.isAfter(endDay) && (it.closedAt == null || !it.closedAt.isBefore(end)) }
        val owedToYou = loansThen.filter { it.lent }.sumOf { loanBalance(it, posted).outstandingMinor }
        val youOwe = loansThen.filterNot { it.lent }.sumOf { loanBalance(it, posted).outstandingMinor }
        month to cash + owedToYou - youOwe
    }
}
