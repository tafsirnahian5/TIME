package com.example.timeapp.data

import com.example.timeapp.data.walletEffect
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * In-memory sample data matching the real design canvas content exactly
 * (project "Mobile app design completion", `TIME App Screens.dc.html`) —
 * Portfolio page, Groceries Tk 3,000/1,800, Level 8, etc. Stands in for Room
 * during the visual-shell phase.
 */
object SampleData {

    private val zone = ZoneId.systemDefault()
    // The day the demo is loaded, so it always looks current: fixed to the mock's 11 Sep, it
    // loaded weeks overdue on every later day.
    val today: LocalDate = LocalDate.now()

    fun instantAt(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(zone).toInstant()

    const val displayName = "Builder"

    val wallets = listOf(
        Wallet(1, "Cash", WalletKind.CASH, openingBalanceMinor = 510_000, openingDate = today.minusDays(30)),
        Wallet(2, "Bank", WalletKind.BANK, openingBalanceMinor = 1_500_000, openingDate = today.minusDays(30)),
        Wallet(3, "bKash", WalletKind.MOBILE, openingBalanceMinor = 500_000, openingDate = today.minusDays(30)),
    )

    val entries: List<Entry> = listOf(
        Entry(
            id = 1, kind = EntryKind.TASK, title = "Send invoice", category = CategoryJob,
            status = EntryStatus.OVERDUE, meta = "Due Wed 10 Sep",
        ),
        Entry(
            id = 2, kind = EntryKind.TASK, title = "Portfolio page", category = CategoryCoding,
            status = EntryStatus.PAUSED, meta = "42 min left",
            startAt = instantAt(today, LocalTime.of(9, 0)), endAt = instantAt(today, LocalTime.of(10, 30)),
        ),
        Entry(
            id = 3, kind = EntryKind.EVENT, title = "Client call", category = CategoryJob,
            status = EntryStatus.PLANNED, meta = "11:00 block", isFixed = true,  // not Doing: no session exists behind it
            startAt = instantAt(today, LocalTime.of(11, 0)), endAt = instantAt(today, LocalTime.of(11, 30)),
        ),
        Entry(
            id = 4, kind = EntryKind.TASK, title = "Review Kotlin notes", category = CategoryStudy,
            status = EntryStatus.PLANNED, meta = "30 min",
        ),
        Entry(
            id = 5, kind = EntryKind.TASK, title = "Buy notebook", category = CategoryPersonal,
            status = EntryStatus.UNSCHEDULED, meta = "no block reserved",
        ),
        Entry(
            id = 6, kind = EntryKind.TASK, title = "Finish skills page", category = CategoryCoding,
            status = EntryStatus.UNSCHEDULED, meta = "carried from last week",
        ),
        Entry(
            id = 7, kind = EntryKind.TASK, title = "Dentist appointment", category = CategoryPersonal,
            status = EntryStatus.PLANNED,
            startAt = instantAt(today.plusDays(2), LocalTime.of(10, 0)), endAt = instantAt(today.plusDays(2), LocalTime.of(10, 45)),
            isFixed = true,
        ),
    )

    /** Planned costs/income (E1) — separate list from settled history (E2). */
    val plans: List<MoneyRecord> = listOf(
        MoneyRecord(
            id = 1, direction = Direction.COST, title = "Internet bill", categoryColor = 0xFFE5A32B,
            amountMinor = 120_000, dueOn = today.minusDays(1), status = PlanStatus.PLANNED,
        ),
        MoneyRecord(
            id = 2, direction = Direction.COST, title = "Groceries", categoryColor = 0xFF12A05C,
            amountMinor = 300_000, paidMinor = 180_000, dueOn = today.plusDays(4), status = PlanStatus.PARTLY_PAID,
        ),
        MoneyRecord(
            id = 3, direction = Direction.COST, title = "Kotlin course fee", categoryColor = 0xFF6C4ED9,
            amountMinor = 500_000, dueOn = today.plusDays(17), status = PlanStatus.PLANNED,
        ),
        MoneyRecord(
            id = 4, direction = Direction.COST, title = "Eid gift", categoryColor = 0xFFE5A32B,
            amountMinor = 250_000, dueOn = null, status = PlanStatus.UNSCHEDULED,
        ),
    )

    /** Settled history (E2), reverse chronological, grouped by day in the UI. */
    val history: List<MoneyRecord> = listOf(
        MoneyRecord(
            id = 10, direction = Direction.COST, title = "Lunch", categoryColor = 0xFF12A05C,
            amountMinor = 40_000, paidMinor = 40_000, walletId = 1,
            settledAt = instantAt(today, LocalTime.of(13, 0)),
        ),
        MoneyRecord(
            id = 11, direction = Direction.COST, title = "Groceries", categoryColor = 0xFF12A05C,
            amountMinor = 120_000, paidMinor = 120_000, walletId = 1, linkedToPlan = true,
            settledAt = instantAt(today.minusDays(1), LocalTime.of(18, 0)),
        ),
        MoneyRecord(
            id = 12, direction = Direction.INCOME, title = "Freelance payment", categoryColor = 0xFF12A05C,
            amountMinor = 1_800_000, paidMinor = 1_800_000, walletId = 2,
            settledAt = instantAt(today.minusDays(3), LocalTime.of(12, 0)),
        ),
        MoneyRecord(
            id = 13, direction = Direction.TRANSFER, title = "Bank → bKash",
            amountMinor = 500_000, paidMinor = 500_000, walletId = 2, transferToWalletId = 3,
            settledAt = instantAt(today.minusDays(3), LocalTime.of(12, 5)),
        ),
        MoneyRecord(
            id = 14, direction = Direction.COST, title = "Electricity", categoryColor = 0xFFE5A32B,
            amountMinor = 150_000, paidMinor = 150_000, walletId = 3,
            settledAt = instantAt(today.minusDays(4), LocalTime.of(9, 0)),
        ),
    )

    fun walletBalance(walletId: Long): Long {
        val wallet = wallets.first { it.id == walletId }
        val delta = history.sumOf { it.walletEffect(walletId) }
        return wallet.openingBalanceMinor + delta
    }

    val ideaCollections = listOf(
        IdeaCollection(1, "Bucket list", CollectionKind.BUCKET, "menu_book", 0xFFFFFFFF, count = 24, doneCount = 6),
        IdeaCollection(2, "Sparks", CollectionKind.SPARKS, "bolt", 0xFF5B41AE, count = 41),
        IdeaCollection(3, "Reading", CollectionKind.LIST, "auto_stories", 0xFF2E7DE8, count = 12),
        IdeaCollection(4, "Places", CollectionKind.LIST, "travel_explore", 0xFF12A05C, count = 8),
    )

    val recentlyTouched = listOf(
        Idea(1, 2, "Ship an app to Play Store", body = "Needs: icon set, store listing, a privacy…", touchedLabel = "2d"),
        Idea(2, 4, "Sundarbans trip — rough costs", body = "Boat, guide, three nights. Around Tk 40,000…", touchedLabel = "5d"),
        Idea(3, 2, "Bookshelf from reclaimed wood", body = "Measure the alcove first", touchedLabel = "3w"),
    )

    val bucketOpen = listOf(
        Idea(4, 1, "See the Sundarbans", costsMoneyEstimateMinor = 4_000_000),
        Idea(5, 1, "Ship an app to Play Store", promotedToPlan = true),
        Idea(6, 1, "Learn to swim properly"),
        Idea(7, 1, "Build a bookshelf from reclaimed wood", costsMoneyEstimateMinor = 650_000),
        Idea(8, 1, "Read 50 books in a year"),
    )
    val bucketDone = listOf(
        Idea(9, 1, "Learn Kotlin basics", isDone = true),
        Idea(10, 1, "Visit Saint Martin's", isDone = true),
    )

    val bookmarks = listOf(
        Bookmark(1, "Portfolio ideas", "example.com", "Work", favourite = true),
        Bookmark(2, "Compose layouts", "developer.android.com", "Coding"),
    )
}
