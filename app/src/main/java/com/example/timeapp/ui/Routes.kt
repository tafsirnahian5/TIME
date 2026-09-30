package com.example.timeapp.ui

/**
 * Route constants matching the real design canvas (`TIME App Screens.dc.html`,
 * project "Mobile app design completion") exactly — not `02-screen-map.md`,
 * which turned out to describe a different, unbuilt nav (see TabBar.dc.html's
 * live `defs` array: today/plan/focus/money/ideas/links, no "me" tab).
 */
object Routes {
    const val SetupWelcome = "setup/welcome"          // Phase 19A — the welcome tour
    const val SetupIdentity = "setup/identity"        // A1
    const val SetupWork = "setup/work"                // A2
    const val SetupSecurity = "setup/security"        // A3 — the PIN step 1 promises

    const val Today = "today"                         // A3
    const val Plan = "plan"                           // C1
    const val PlanBoard = "plan/board"                // C1b — not in the real design canvas
    const val BoardColumns = "plan/board/columns"      // not in the real design canvas
    const val PlanMonth = "plan/month"                // C2
    const val TaskDetail = "plan/task/{entryId}"      // not in the real design canvas
    const val Focus = "focus"                         // F1
    const val FocusCountdown = "focus/countdown"      // F1b
    const val FocusPomodoro = "focus/pomodoro"        // F2
    const val FocusTimers = "focus/timers"            // not in the real design canvas
    const val FocusHistory = "focus/history"          // not in the real design canvas
    const val Money = "money"                         // E1
    const val MoneyOverview = "money/overview"        // E0 — not in the real design canvas
    const val MoneyHistory = "money/history"          // E2
    const val Savings = "money/savings"
    const val Loans = "money/loans"
    const val MoneyCalendar = "money/calendar"
    const val ProjectSummary = "projects/{projectId}"
    const val LoanDetail = "money/loans/{loanId}"               // not in the real design canvas
    const val Ideas = "ideas"                         // I1
    const val Links = "links"
    const val Habits = "habits"                       // not in the real design canvas — see HabitsScreen
    const val HabitDetail = "habits/{habitId}"

    const val Add = "add"                             // B1 (sheet)
    const val AddTask = "add/task?entryId={entryId}"  // B2 — entryId=-1 means "new"
    const val AddCost = "add/cost?planId={planId}"    // B3 — planId=-1 means "new"
    const val AddWallet = "add/wallet"
    const val AddLink = "add/link?bookmarkId={bookmarkId}"  // bookmarkId=-1 means "new"
    const val AddScheduleBlock = "add/schedule-block?startMs={startMs}&entryId={entryId}"  // -1 means "no prefill" / "new"
    const val AddHabit = "add/habit?habitId={habitId}"  // habitId=-1 means "new"
    const val AddTransaction = "add/transaction?recordId={recordId}"  // recordId=-1 means "new"
    const val Switch = "switch"                       // D1
    const val Reschedule = "reschedule/{entryId}"     // D2
    const val Pay = "money/pay/{id}"                  // E3
    const val Refund = "money/refund/{id}"            // not in the real design canvas
    const val Wallet = "money/wallet/{id}"            // E4
    const val Me = "me"                               // G1 (Achievements, via top-bar avatar)
    const val Search = "search"                       // G2
    const val Inbox = "inbox"                         // the bell — T!ME's own notification list
    const val WorkdaySettings = "settings/workday"
    const val MoneySettings = "settings/money"
    const val TimersSettings = "settings/timers"
    const val LanguageSettings = "settings/language"
    const val Settings = "settings"                   // Tier 7 — the real Settings hub
    const val Notifications = "settings/notifications" // G3
    const val Appearance = "settings/appearance"      // G4
    const val Profile = "settings/profile"            // Tier 7
    const val About = "settings/about"                // Tier 7
    const val Help = "settings/help"                  // Phase 19D
    const val HelpTour = "settings/help/tour"         // Phase 19D — the welcome tour, replayed
    const val Categories = "settings/categories"
    const val Security = "settings/security"          // Tier 8
    const val PinSetup = "settings/security/pin-setup" // Tier 8
    const val PinChange = "settings/security/pin-change" // Tier 8
    const val DataBackup = "settings/data-backup"      // Tier 9
    const val RestorePreview = "settings/data-backup/restore-preview" // Tier 9
    const val CsvImport = "settings/data-backup/import-csv"
    const val BookmarkImport = "settings/data-backup/import-bookmarks"
    const val CalendarImport = "settings/data-backup/import-calendar"
    const val Reports = "settings/reports"             // Tier 10
    const val TimeStats = "settings/reports/time"      // Tier 10
    const val MoneyStats = "settings/reports/money"    // Tier 10
    const val HabitStats = "settings/reports/habits"
    const val Workload = "settings/reports/workload"
    const val CategoryDetail = "settings/reports/category/{categoryId}"
    const val Workday = "workday"                      // Tier 11 — My Workday. It shared "settings/workday" with WorkdaySettings, so that page could never open.
    const val DailyReview = "settings/workday/daily-review"   // Tier 11
    const val WeeklyReview = "settings/workday/weekly-review" // Tier 11
    const val IdeasBucket = "ideas/bucket"             // I2
    const val IdeasNote = "ideas/note/{ideaId}"        // I3
    const val IdeasQuick = "ideas/quick?collectionId={collectionId}" // I4 — -1: Sparks
    fun ideasQuick(collectionId: Long? = null) = "ideas/quick?collectionId=${collectionId ?: -1L}"
    const val IdeasCollection = "ideas/collection/{collectionId}" // not in the real canvas — see IdeasScreen

    fun pay(id: Long) = "money/pay/$id"
    fun refund(id: Long) = "money/refund/$id"
    fun wallet(id: Long) = "money/wallet/$id"
    fun addTask(entryId: Long? = null) = "add/task?entryId=${entryId ?: -1L}"
    fun addCost(planId: Long? = null) = "add/cost?planId=${planId ?: -1L}"
    fun addLink(bookmarkId: Long? = null) = "add/link?bookmarkId=${bookmarkId ?: -1L}"
    fun addTransaction(recordId: Long? = null) = "add/transaction?recordId=${recordId ?: -1L}"
    fun reschedule(entryId: Long) = "reschedule/$entryId"
    fun ideasNote(ideaId: Long) = "ideas/note/$ideaId"
    fun ideasCollection(collectionId: Long) = "ideas/collection/$collectionId"
    fun projectSummary(id: Long) = "projects/$id"
    fun loanDetail(id: Long) = "money/loans/$id"
    fun taskDetail(entryId: Long) = "plan/task/$entryId"
    fun categoryDetail(categoryId: Long) = "settings/reports/category/$categoryId"
    fun habitDetail(habitId: Long) = "habits/$habitId"
    /** [start] is local wall-clock time; the Week/Day grid passes the tapped hour. */
    fun addScheduleBlock(start: java.time.LocalDateTime? = null) =
        "add/schedule-block?startMs=${start?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: -1L}&entryId=-1"
    /** Edits existing booked time (an `EntryKind.EVENT`) — the task form must never do this. */
    fun editScheduleBlock(entryId: Long) = "add/schedule-block?startMs=-1&entryId=$entryId"
    fun addHabit(habitId: Long? = null) = "add/habit?habitId=${habitId ?: -1L}"
}

/**
 * Six destinations — Today, Plan, Focus, Money, Ideas, Links. Matches the live
 * `TabBar` component embedded in every real screen; "Me" is not a tab — it's
 * reached via the top-bar avatar (search + gear sit beside it), same as Settings.
 */
data class TabDest(val route: String, val label: String, val icon: String)

val BottomTabs = listOf(
    TabDest(Routes.Today, "Today", "home"),
    TabDest(Routes.Plan, "Plan", "calendar_month"),
    TabDest(Routes.Focus, "Focus", "timer"),
    TabDest(Routes.Money, "Money", "account_balance_wallet"),
    TabDest(Routes.Ideas, "Ideas", "menu_book"),
    TabDest(Routes.Links, "Links", "link"),
)
