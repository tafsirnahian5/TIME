package com.example.timeapp.ui.money

import com.example.timeapp.data.Direction
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.parseTakaToMinor

/**
 * The History screen's filters — the PDF's "search by note/payee, date range, wallet, category
 * or amount". The search box used to promise "note, payee or amount" while matching titles
 * only; it now matches all three. A null field means "any".
 */
data class LedgerFilter(
    val query: String = "",
    val type: Direction? = null,
    val walletId: Long? = null,
    val categoryId: Long? = null,
) {
    val narrowsBeyondQuery: Boolean get() = type != null || walletId != null || categoryId != null
}

/** Typed amounts match exactly: "1200", "1,200.50" and "Tk 1200" all find a Tk 1,200 row. */
fun queryAsAmountMinor(query: String): Long? =
    parseTakaToMinor(query.trim().removePrefix("Tk").removePrefix("tk").removePrefix("৳").replace(",", "").trim())

fun List<MoneyRecord>.applyLedgerFilter(filter: LedgerFilter): List<MoneyRecord> {
    val q = filter.query.trim()
    val amount = if (q.isEmpty()) null else queryAsAmountMinor(q)
    return filter { r ->
        (filter.type == null || r.direction == filter.type) &&
            // A transfer touches two wallets; either one matches.
            (filter.walletId == null || r.walletId == filter.walletId || r.transferToWalletId == filter.walletId) &&
            (filter.categoryId == null || r.categoryId == filter.categoryId) &&
            (
                q.isEmpty() ||
                    r.title.contains(q, ignoreCase = true) ||
                    r.note?.contains(q, ignoreCase = true) == true ||
                    (amount != null && r.paidMinor == amount)
                )
    }
}
