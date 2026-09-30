package com.example.timeapp.ui

import com.example.timeapp.data.L

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.repository.EntryRepository
import com.example.timeapp.data.repository.IdeaRepository
import com.example.timeapp.data.repository.MiscRepository
import com.example.timeapp.data.repository.MoneyRepository
import com.example.timeapp.ui.add.AddCostScreen
import com.example.timeapp.ui.add.AddLinkScreen
import com.example.timeapp.ui.add.AddSheetContent
import com.example.timeapp.ui.add.AddTaskScreen
import com.example.timeapp.ui.add.AddWalletScreen
import com.example.timeapp.ui.add.RecordType
import com.example.timeapp.ui.components.TimeNavBar
import com.example.timeapp.ui.focus.FocusPomodoroScreen
import com.example.timeapp.ui.focus.FocusStopwatchScreen
import com.example.timeapp.ui.focus.FocusViewModel
import com.example.timeapp.ui.ideas.BucketListScreen
import com.example.timeapp.ui.ideas.IdeasScreen
import com.example.timeapp.ui.ideas.IdeasViewModel
import com.example.timeapp.ui.ideas.NotebookPageScreen
import com.example.timeapp.ui.ideas.QuickCaptureScreen
import com.example.timeapp.ui.interrupt.RescheduleScreen
import com.example.timeapp.ui.interrupt.SwitchWorkScreen
import com.example.timeapp.ui.links.LinksScreen
import com.example.timeapp.ui.me.AboutScreen
import com.example.timeapp.ui.me.AppearanceSettingsScreen
import com.example.timeapp.ui.me.MeScreen
import com.example.timeapp.ui.me.NotificationsSettingsScreen
import com.example.timeapp.ui.me.ProfileSettingsScreen
import com.example.timeapp.ui.me.SearchScreen
import com.example.timeapp.ui.me.SettingsScreen
import com.example.timeapp.ui.money.MoneyHistoryScreen
import com.example.timeapp.ui.money.MoneyPlannedScreen
import com.example.timeapp.ui.money.MoneyViewModel
import com.example.timeapp.ui.money.preferring
import com.example.timeapp.ui.money.RecordPaymentScreen
import com.example.timeapp.ui.money.WalletDetailScreen
import com.example.timeapp.ui.plan.PlanListScreen
import com.example.timeapp.ui.plan.PlanMonthScreen
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.data.BackupManager
import com.example.timeapp.ui.backup.BackupViewModel
import com.example.timeapp.ui.backup.DataBackupScreen
import com.example.timeapp.ui.backup.RestorePreviewScreen
import com.example.timeapp.ui.security.LockScreen
import com.example.timeapp.ui.security.PinChangeScreen
import com.example.timeapp.ui.security.PinSetupScreen
import com.example.timeapp.ui.security.SecuritySettingsScreen
import com.example.timeapp.ui.security.SecurityViewModel
import com.example.timeapp.ui.stats.MoneyStatsScreen
import com.example.timeapp.ui.stats.ReportsScreen
import com.example.timeapp.ui.stats.StatsViewModel
import com.example.timeapp.ui.stats.TimeStatsScreen
import com.example.timeapp.data.repository.ReviewRepository
import com.example.timeapp.data.repository.WorkdayRepository
import com.example.timeapp.ui.workday.DailyReviewScreen
import com.example.timeapp.ui.workday.MyWorkdayScreen
import com.example.timeapp.ui.workday.ReviewViewModel
import com.example.timeapp.ui.workday.WeeklyReviewScreen
import com.example.timeapp.ui.workday.WorkdayViewModel
import com.example.timeapp.ui.setup.SetupIdentityScreen
import com.example.timeapp.ui.setup.SetupSecurityScreen
import com.example.timeapp.ui.setup.SetupWorkScreen
import com.example.timeapp.ui.today.TodayScreen
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.os.SystemClock
import java.time.ZoneId

/** Screens that show the bottom nav bar — the six root tab destinations. */
private val tabRoutes = BottomTabs.map { it.route }.toSet()

/** A tab's other views (Plan's Board and Calendar, Focus's modes, Money's segments) keep the tab
 * bar with their tab selected — switching Focus to Countdown used to make the bar vanish. */
private val tabViews = mapOf(
    Routes.PlanBoard to Routes.Plan, Routes.PlanMonth to Routes.Plan,
    Routes.FocusCountdown to Routes.Focus, Routes.FocusPomodoro to Routes.Focus, Routes.FocusTimers to Routes.Focus,
    Routes.MoneyOverview to Routes.Money, Routes.MoneyHistory to Routes.Money,
)

private fun tabOf(route: String?): String? = if (route in tabRoutes) route else tabViews[route]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeApp(
    appViewModel: AppViewModel,
    database: TimeDatabase,
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
    openRoute: String? = null,
    onOpenRouteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val reminderRepository = remember(database) {
        com.example.timeapp.data.repository.ReminderRepository(
            database,
            com.example.timeapp.data.notify.ReminderScheduler(appContext),
        )
    }
    val categoryViewModel: CategoryViewModel = viewModel(
        factory = CategoryViewModel.Factory(com.example.timeapp.data.repository.CategoryRepository(database)),
    )
    val planViewModel: PlanViewModel = viewModel(
        factory = PlanViewModel.Factory(EntryRepository(database), reminderRepository, com.example.timeapp.data.repository.CommitmentRepository(database)),
    )
    val moneyViewModel: MoneyViewModel = viewModel(factory = MoneyViewModel.Factory(MoneyRepository(database), reminderRepository))
    val ideasViewModel: IdeasViewModel = viewModel(
        factory = IdeasViewModel.Factory(IdeaRepository(database.ideaCollectionDao(), database.ideaDao())),
    )
    val miscViewModel: MiscViewModel = viewModel(
        factory = MiscViewModel.Factory(
            MiscRepository(database.achievementDao(), database.bookmarkDao(), database),
            com.example.timeapp.data.repository.AchievementsRepository(database, appViewModel.settingsStore),
        ),
    )
    val habitsViewModel: com.example.timeapp.ui.habits.HabitsViewModel = viewModel(
        factory = com.example.timeapp.ui.habits.HabitsViewModel.Factory(
            com.example.timeapp.data.repository.HabitRepository(database),
            reminderRepository,
        ),
    )
    val savingsViewModel: com.example.timeapp.ui.money.SavingsViewModel = viewModel(
        factory = com.example.timeapp.ui.money.SavingsViewModel.Factory(
            com.example.timeapp.data.repository.SavingsRepository(database),
            MoneyRepository(database),
        ),
    )
    val utilityTimersViewModel: com.example.timeapp.ui.focus.UtilityTimersViewModel = viewModel(
        factory = com.example.timeapp.ui.focus.UtilityTimersViewModel.Factory(database, reminderRepository),
    )
    val loansViewModel: com.example.timeapp.ui.money.LoansViewModel = viewModel(
        factory = com.example.timeapp.ui.money.LoansViewModel.Factory(com.example.timeapp.data.repository.LoanRepository(database), reminderRepository),
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusViewModel: FocusViewModel = viewModel(
        factory = FocusViewModel.Factory(EntryRepository(database)) { due ->
            com.example.timeapp.data.notify.FocusSurface.expireNow(context.applicationContext, due)
        },
    )
    val backupViewModel: BackupViewModel = viewModel(
        factory = BackupViewModel.Factory(BackupManager(context.applicationContext, database, appViewModel.settingsStore), appViewModel.settingsStore, database, reminderRepository),
    )
    val statsViewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory(EntryRepository(database), MoneyRepository(database), com.example.timeapp.data.repository.CategoryRepository(database)))
    val workdayViewModel: WorkdayViewModel = viewModel(
        factory = WorkdayViewModel.Factory(WorkdayRepository(database), EntryRepository(database)),
    )
    val reviewViewModel: ReviewViewModel = viewModel(
        factory = ReviewViewModel.Factory(ReviewRepository(database), EntryRepository(database)),
    )
    val inboxViewModel: com.example.timeapp.ui.inbox.InboxViewModel = viewModel(
        factory = com.example.timeapp.ui.inbox.InboxViewModel.Factory(database),
    )
    val meViewModel: com.example.timeapp.ui.me.MeViewModel = viewModel(
        factory = com.example.timeapp.ui.me.MeViewModel.Factory(
            EntryRepository(database), com.example.timeapp.data.repository.CommitmentRepository(database),
            ReviewRepository(database), appViewModel.settingsStore,
        ),
    )

    // Wait for DataStore's real value before ever composing NavHost — otherwise its
    // default-false initial state briefly starts every launch on Setup before flipping
    // to Today once the real flag loads.
    val onboardingComplete = appViewModel.onboardingComplete.collectAsState().value ?: return
    // Whether a reminder set from a creation form would actually reach the user. The forms
    // say so rather than arming an alarm that fires into a muted channel.
    val appSettings by appViewModel.uiState.collectAsState()

    val securityViewModel: SecurityViewModel = viewModel(factory = SecurityViewModel.Factory(appViewModel.settingsStore))
    // Same "wait for the real value" pattern as onboardingComplete above — null means
    // lockEnabled hasn't been read from DataStore yet.
    val locked = securityViewModel.locked.collectAsState().value ?: return

    var sharedUrl by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> securityViewModel.onBackground(SystemClock.elapsedRealtime())
                Lifecycle.Event.ON_START -> securityViewModel.onForeground(SystemClock.elapsedRealtime())
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Lock the moment the phone's screen turns off (the PDF's "immediately after device lock").
    // appContext: the LocalContext read at the top of TimeApp.
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) = securityViewModel.onScreenOff()
        }
        androidx.core.content.ContextCompat.registerReceiver(
            appContext, receiver, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { appContext.unregisterReceiver(receiver) } }
    }
    // Recents shows a blank card while the lock is on (setting on by default). Android 13+ can
    // hide just the preview; older versions need FLAG_SECURE, which also blocks screenshots.
    val securityState by securityViewModel.uiState.collectAsState()
    val hideRecents = securityState.lockEnabled && securityState.hideInRecents
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    androidx.compose.runtime.LaunchedEffect(hideRecents, activity) {
        if (activity == null) return@LaunchedEffect
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            activity.setRecentsScreenshotEnabled(!hideRecents)
        } else if (hideRecents) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // A PIN can only be set up from a screen reachable post-onboarding, so this guard
    // makes locking during onboarding structurally impossible — not just unlikely.
    if (onboardingComplete && locked) {
        LockScreen(onUnlocked = {}, viewModel = securityViewModel)
        return
    }

    // A URL shared from a browser opens the Add-link form prefilled, and a notification opens
    // its own screen — both only past the lock gate above. (The share handler used to sit
    // before the gate, where it navigated while the lock screen was up.)
    androidx.compose.runtime.LaunchedEffect(sharedText) {
        val text = sharedText ?: return@LaunchedEffect
        sharedUrl = text.trim().substringAfter("http", "").let { if (it.isEmpty()) text.trim() else "http$it" }
            .substringBefore(' ')
        onSharedTextConsumed()
        navController.navigate(Routes.addLink())
    }
    androidx.compose.runtime.LaunchedEffect(openRoute) {
        val route = openRoute ?: return@LaunchedEffect
        onOpenRouteConsumed()
        runCatching { navController.navigate(route) }
    }

    var showAddSheet by remember { mutableStateOf(false) }

    fun openSettings() = navController.navigate(Routes.Settings)
    fun openSearch() = navController.navigate(Routes.Search)
    /** A fast double tap on Save ran the save twice — the form stays on screen while it animates
     * away — which is how a task could appear twice. Only the form that is still the current
     * screen may save; a save refused by validation doesn't leave, so a retry still works. */
    fun isCurrent(entry: androidx.navigation.NavBackStackEntry) = navController.currentBackStackEntry?.id == entry.id
    /** Booked time has its own form; the task form rebuilt the time from a due date and moved
     * or dropped the booking. */
    fun editEntry(id: Long) = navController.navigate(
        if (planViewModel.uiState.value.find(id)?.kind == EntryKind.EVENT) Routes.editScheduleBlock(id) else Routes.addTask(id),
    )

    // App-wide Undo (UndoBus) — shown over whichever screen the user lands on.
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val undo by com.example.timeapp.ui.plan.UndoBus.current.collectAsState()
    androidx.compose.runtime.LaunchedEffect(undo) {
        val action = undo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = action.message,
            actionLabel = L("Undo"),
            duration = androidx.compose.material3.SnackbarDuration.Long,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) action.undo()
        com.example.timeapp.ui.plan.UndoBus.consume(action)
    }

    // Tablets, foldables and landscape phones get a side rail instead of the bottom bar —
    // the PDF's "adaptive tablet layout". Same six tabs, same behaviour.
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val selectTab: (com.example.timeapp.ui.TabDest) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // The bell on every tab bar: read once here, handed down through a composition local so no
    // screen needs new parameters.
    val inboxUnread by inboxViewModel.unreadCount.collectAsState()
    val inboxBell = com.example.timeapp.ui.components.InboxBell(inboxUnread) { navController.navigate(Routes.Inbox) { launchSingleTop = true } }

    androidx.compose.runtime.CompositionLocalProvider(com.example.timeapp.ui.components.LocalInboxBell provides inboxBell) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        bottomBar = {
            val tab = tabOf(currentRoute)
            if (tab != null && !wide) {
                TimeNavBar(currentRoute = tab, onSelect = selectTab)
            }
        },
    ) { scaffoldPadding ->
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
        if (wide && tabOf(currentRoute) != null) {
            com.example.timeapp.ui.components.TimeNavRail(currentRoute = tabOf(currentRoute), onSelect = selectTab)
        }
        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = if (onboardingComplete) Routes.Today else Routes.SetupWelcome,
                modifier = Modifier.weight(1f),
            ) {
                composable(Routes.SetupWelcome) {
                    com.example.timeapp.ui.setup.WelcomeTourScreen(
                        onDone = { navController.navigate(Routes.SetupIdentity) },
                    )
                }
                composable(Routes.SetupIdentity) {
                    SetupIdentityScreen(
                        onNext = { name, title, avatarUri ->
                            appViewModel.setIdentity(name, title, avatarUri)
                            navController.navigate(Routes.SetupWork)
                        },
                    )
                }
                composable(Routes.SetupWork) {
                    SetupWorkScreen(
                        onBack = { navController.popBackStack() },
                        onFinish = { startTime, endTime, restDays, weekStart ->
                            val settings = appViewModel.uiState.value
                            appViewModel.setWorkSetup(startTime, endTime, java.time.ZoneId.systemDefault().id, settings.currency, restDays)
                            appViewModel.setWeekStartsOn(weekStart)
                            navController.navigate(Routes.SetupSecurity)
                        },
                    )
                }
                composable(Routes.SetupSecurity) {
                    // Onboarding only completes here — step 1 promises a step 3, so the
                    // lock offer is the last thing between setup and the app.
                    val security by securityViewModel.uiState.collectAsState()
                    val finish = {
                        // The tour has just explained Today; its tip sheet popping up straight
                        // after read as the same lesson twice.
                        appViewModel.markTipSeen(Routes.Today)
                        appViewModel.setOnboardingComplete(true)
                        navController.navigate(Routes.Today) {
                            popUpTo(Routes.SetupWelcome) { inclusive = true }
                        }
                    }
                    SetupSecurityScreen(
                        onBack = { navController.popBackStack() },
                        onSetPin = { navController.navigate(Routes.PinSetup) },
                        onFinish = finish,
                        pinAlreadySet = security.hasPin,
                    )
                }

                composable(Routes.Today) {
                    TodayScreen(
                        onAdd = { showAddSheet = true },
                        onOpenMe = { navController.navigate(Routes.Me) },
                        onSearch = { openSearch() },
                        onOpenSettings = { openSettings() },
                        onSwitchWork = { navController.navigate(Routes.Switch) },
                        onOpenMoney = { navController.navigate(Routes.Money) },
                        onOpenHabits = { navController.navigate(Routes.Habits) },
                        onOpenHabit = { id -> navController.navigate(Routes.habitDetail(id)) },
                        viewModel = planViewModel,
                        moneyViewModel = moneyViewModel,
                        habitsViewModel = habitsViewModel,
                        workdayViewModel = workdayViewModel,
                        userName = appSettings.userName,
                        onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                        onOpenFocus = { selectTab(com.example.timeapp.ui.BottomTabs.first { it.route == Routes.Focus }) },
                        onOpenPlan = { selectTab(com.example.timeapp.ui.BottomTabs.first { it.route == Routes.Plan }) },
                        onOpenCalendar = { navController.navigate(Routes.PlanMonth) },
                        onOpenWorkday = { navController.navigate(Routes.Workday) },
                        onPay = { id -> navController.navigate(Routes.pay(id)) },
                        onAddTask = { navController.navigate(Routes.addTask()) },
                        onAddHabit = { navController.navigate(Routes.addHabit()) },
                        onAddWallet = { navController.navigate(Routes.AddWallet) },
                        onAddCost = { navController.navigate(Routes.addCost()) },
                        gettingStartedDismissed = appSettings.gettingStartedDismissed,
                        onDismissGettingStarted = { appViewModel.dismissGettingStarted() },
                        onOpenReport = { route -> navController.navigate(route) },
                    )
                }

                composable(Routes.Plan) {
                    PlanListScreen(
                        // The button says "Task": it opened the whole Add sheet, one more tap for the
                        // one thing it names. The calendar keeps the sheet (a day can take any kind).
                        onAdd = { navController.navigate(Routes.addTask()) },
                        onOpenMonth = { navController.navigate(Routes.PlanMonth) },
                        onOpenBoard = { navController.navigate(Routes.PlanBoard) },
                        onSearch = { openSearch() },
                        onOpenSettings = { openSettings() },
                        onEdit = { id -> editEntry(id) },
                        onReschedule = { id -> navController.navigate(Routes.reschedule(id)) },
                        onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                        viewModel = planViewModel,
                        projects = categoryViewModel.projects.collectAsState().value,
                        savedFilters = com.example.timeapp.data.SavedFilters.decode(appSettings.savedFilters),
                        onSavedFiltersChange = appViewModel::setSavedFilters,
                    )
                }
                composable(Routes.PlanBoard) {
                    com.example.timeapp.ui.plan.PlanBoardScreen(
                        onAdd = { navController.navigate(Routes.addTask()) },
                        onOpenList = { navController.popBackStack() },
                        onOpenMonth = {
                            navController.navigate(Routes.PlanMonth) { popUpTo(Routes.Plan) }
                        },
                        onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                        onSearch = { openSearch() },
                        onOpenSettings = { openSettings() },
                        viewModel = planViewModel,
                        projects = categoryViewModel.projects.collectAsState().value,
                        onEditColumns = { navController.navigate(Routes.BoardColumns) },
                        savedFilters = com.example.timeapp.data.SavedFilters.decode(appSettings.savedFilters),
                        onSavedFiltersChange = appViewModel::setSavedFilters,
                        sort = com.example.timeapp.data.BoardSort.entries.firstOrNull { it.name == appSettings.boardSort } ?: com.example.timeapp.data.BoardSort.MANUAL,
                        onSort = appViewModel::setBoardSort,
                    )
                }
                composable(Routes.BoardColumns) {
                    com.example.timeapp.ui.plan.BoardColumnsScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = planViewModel,
                    )
                }
                composable(Routes.PlanMonth) {
                    PlanMonthScreen(
                        onAdd = { showAddSheet = true },
                        onOpenList = { navController.popBackStack() },
                        onOpenBoard = {
                            navController.navigate(Routes.PlanBoard) { popUpTo(Routes.Plan) }
                        },
                        onSearch = { openSearch() },
                        onOpenSettings = { openSettings() },
                        viewModel = planViewModel,
                        moneyViewModel = moneyViewModel,
                        onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                        onNewBlockAt = { start -> navController.navigate(Routes.addScheduleBlock(start)) },
                        initialView = appSettings.defaultCalendarView,
                    )
                }
                composable(
                    Routes.TaskDetail,
                    arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
                ) { entry ->
                    val entryId = entry.arguments?.getLong("entryId") ?: 0L
                    com.example.timeapp.ui.plan.TaskDetailScreen(
                        entryId = entryId,
                        onBack = { navController.popBackStack() },
                        onEdit = { editEntry(entryId) },
                        onReschedule = { navController.navigate(Routes.reschedule(entryId)) },
                        onDeleted = { navController.popBackStack() },
                        viewModel = planViewModel,
                        bookmarks = miscViewModel.bookmarks.collectAsState().value,
                        money = moneyViewModel.uiState.collectAsState().value.let { m -> (m.plans + m.history).filter { it.taskId == entryId } },
                        workStart = appSettings.workStart,
                        workEnd = appSettings.workEnd,
                    )
                }

                composable(Routes.Focus) {
                    FocusStopwatchScreen(
                        onOpenCountdown = { navController.navigate(Routes.FocusCountdown) },
                        onOpenPomodoro = { navController.navigate(Routes.FocusPomodoro) },
                        onSwitchWork = { navController.navigate(Routes.Switch) },
                        viewModel = focusViewModel,
                        planViewModel = planViewModel,
                        onOpenTimers = { navController.navigate(Routes.FocusTimers) },
                        onOpenHistory = { navController.navigate(Routes.FocusHistory) },
                        breakReminderMinutes = appSettings.focusBreakReminderMinutes,
                        onSetBreakReminder = { appViewModel.setFocusBreakReminderMinutes(it) },
                    )
                }
                composable(Routes.FocusHistory) {
                    com.example.timeapp.ui.focus.SessionHistoryScreen(onBack = { navController.popBackStack() }, viewModel = focusViewModel)
                }
                composable(Routes.FocusTimers) {
                    com.example.timeapp.ui.focus.UtilityTimersScreen(
                        onOpenStopwatch = { navController.popBackStack() },
                        onOpenCountdown = { navController.navigate(Routes.FocusCountdown) { popUpTo(Routes.Focus) } },
                        onOpenPomodoro = { navController.navigate(Routes.FocusPomodoro) { popUpTo(Routes.Focus) } },
                        viewModel = utilityTimersViewModel,
                        alarmStyle = appSettings.timerAlarmStyle,
                        onAlarmStyle = { appViewModel.setTimerAlarmStyle(it) },
                    )
                }
                composable(Routes.FocusCountdown) {
                    com.example.timeapp.ui.focus.FocusCountdownScreen(
                        onOpenStopwatch = { navController.popBackStack() },
                        onOpenPomodoro = {
                            navController.navigate(Routes.FocusPomodoro) { popUpTo(Routes.Focus) }
                        },
                        viewModel = focusViewModel,
                        planViewModel = planViewModel,
                        onSwitchWork = { navController.navigate(Routes.Switch) },
                        onOpenTimers = { navController.navigate(Routes.FocusTimers) { popUpTo(Routes.Focus) } },
                    )
                }
                composable(Routes.FocusPomodoro) {
                    FocusPomodoroScreen(
                        onOpenStopwatch = { navController.popBackStack() },
                        onOpenCountdown = {
                            navController.navigate(Routes.FocusCountdown) { popUpTo(Routes.Focus) }
                        },
                        viewModel = focusViewModel,
                        planViewModel = planViewModel,
                        onSwitchWork = { navController.navigate(Routes.Switch) },
                        onOpenTimers = { navController.navigate(Routes.FocusTimers) { popUpTo(Routes.Focus) } },
                    )
                }

                composable(Routes.Money) {
                    MoneyPlannedScreen(
                        onAdd = { showAddSheet = true },
                        onOpenOverview = { navController.navigate(Routes.MoneyOverview) },
                        onOpenHistory = { navController.navigate(Routes.MoneyHistory) },
                        onOpenPay = { id -> navController.navigate(Routes.pay(id)) },
                        onEdit = { id -> navController.navigate(Routes.addCost(id)) },
                        onSearch = { openSearch() },
                        onOpenSettings = { openSettings() },
                        viewModel = moneyViewModel,
                        onAddBill = { navController.navigate(Routes.addCost()) },
                    )
                }
                composable(Routes.MoneyOverview) {
                    com.example.timeapp.ui.money.MoneyOverviewScreen(
                        onOpenPlanned = { navController.popBackStack() },
                        onAddWallet = { navController.navigate(Routes.AddWallet) },
                        onOpenHistory = {
                            navController.navigate(Routes.MoneyHistory) { popUpTo(Routes.Money) }
                        },
                        onOpenWallet = { id -> navController.navigate(Routes.wallet(id)) },
                        onSearch = { openSearch() },
                        onOpenSettings = { openSettings() },
                        viewModel = moneyViewModel,
                        appViewModel = appViewModel,
                        savings = savingsViewModel.uiState.collectAsState().value,
                        onOpenSavings = { navController.navigate(Routes.Savings) },
                        onOpenLoans = { navController.navigate(Routes.Loans) },
                        loans = loansViewModel.uiState.collectAsState().value,
                    )
                }
                composable(Routes.MoneyHistory) {
                    MoneyHistoryScreen(
                        onOpenOverview = {
                            navController.navigate(Routes.MoneyOverview) { popUpTo(Routes.Money) }
                        },
                        onOpenPlanned = { navController.popBackStack() },
                        onEditRecord = { id -> navController.navigate(Routes.addTransaction(id)) },
                        viewModel = moneyViewModel,
                        onRefund = { id -> navController.navigate(Routes.refund(id)) },
                        onOpenCalendar = { navController.navigate(Routes.MoneyCalendar) },
                        // Archived ones too, so old records stay filterable by their category.
                        categories = categoryViewModel.all.collectAsState().value
                            .filter { it.kind == com.example.timeapp.data.CategoryKind.MONEY },
                    )
                }
                composable(
                    Routes.Refund,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    com.example.timeapp.ui.money.RefundScreen(
                        recordId = entry.arguments?.getLong("id") ?: -1L,
                        onBack = { navController.popBackStack() },
                        viewModel = moneyViewModel,
                    )
                }
                composable(
                    Routes.Pay,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    RecordPaymentScreen(
                        planId = id,
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() },
                        viewModel = moneyViewModel,
                        preferredWalletId = appSettings.defaultWalletId,
                    )
                }
                composable(
                    Routes.Wallet,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    WalletDetailScreen(walletId = id, onBack = { navController.popBackStack() }, viewModel = moneyViewModel)
                }

                composable(Routes.Ideas) {
                    IdeasScreen(
                        onOpenBucket = { navController.navigate(Routes.IdeasBucket) },
                        onOpenNote = { id -> navController.navigate(Routes.ideasNote(id)) },
                        onQuickCapture = { navController.navigate(Routes.ideasQuick()) },
                        onOpenCollection = { id -> navController.navigate(Routes.ideasCollection(id)) },
                        onSearch = { openSearch() },
                        viewModel = ideasViewModel,
                    )
                }
                composable(Routes.IdeasBucket) {
                    BucketListScreen(
                        onBack = { navController.popBackStack() },
                        onAdd = {
                            val bucket = ideasViewModel.uiState.value.collections.firstOrNull { it.kind == com.example.timeapp.data.CollectionKind.BUCKET }
                            navController.navigate(Routes.ideasQuick(bucket?.id))
                        },
                        viewModel = ideasViewModel,
                    )
                }
                composable(
                    Routes.IdeasCollection,
                    arguments = listOf(navArgument("collectionId") { type = NavType.LongType }),
                ) { entry ->
                    val collectionId = entry.arguments?.getLong("collectionId") ?: 0L
                    com.example.timeapp.ui.ideas.CollectionDetailScreen(
                        collectionId = collectionId,
                        onBack = { navController.popBackStack() },
                        onOpenNote = { id -> navController.navigate(Routes.ideasNote(id)) },
                        viewModel = ideasViewModel,
                        onAdd = { navController.navigate(Routes.ideasQuick(collectionId)) },
                    )
                }
                composable(
                    Routes.IdeasNote,
                    arguments = listOf(navArgument("ideaId") { type = NavType.LongType }),
                ) { entry ->
                    val ideaId = entry.arguments?.getLong("ideaId") ?: 0L
                    NotebookPageScreen(
                        ideaId = ideaId,
                        onBack = { navController.popBackStack() },
                        onPromoteToPlan = { title ->
                            planViewModel.addTask(title, categoryId = null, dueOn = null)
                        },
                        viewModel = ideasViewModel,
                        miscViewModel = miscViewModel,
                    )
                }
                composable(
                    Routes.IdeasQuick,
                    arguments = listOf(navArgument("collectionId") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    val preset = entry.arguments?.getLong("collectionId")?.takeIf { it > 0 }
                    val ideasUi by ideasViewModel.uiState.collectAsState()
                    QuickCaptureScreen(
                        collections = ideasUi.collections,
                        initialCollectionId = preset,
                        onBack = { navController.popBackStack() },
                        onSave = { title, collectionId ->
                            if (!isCurrent(entry)) return@QuickCaptureScreen
                            ideasViewModel.addIdea(title, collectionId)
                            navController.popBackStack()
                        },
                    )
                }

                composable(Routes.Links) {
                    LinksScreen(
                        // Its FAB says "Add link", so it adds a link — it used to open the
                        // general Add sheet.
                        onAdd = { navController.navigate(Routes.addLink()) },
                        onEdit = { id -> navController.navigate(Routes.addLink(id)) },
                        onSearch = { openSearch() },
                        onOpenSettings = { openSettings() },
                        viewModel = miscViewModel,
                        tasks = planViewModel.uiState.collectAsState().value.entries,
                        onLinkTask = { entryId, bookmarkId -> planViewModel.linkBookmark(entryId, bookmarkId) },
                        onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                    )
                }
                composable(
                    Routes.AddLink,
                    arguments = listOf(navArgument("bookmarkId") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    val bookmarkId = entry.arguments?.getLong("bookmarkId") ?: -1L
                    val bookmarks by miscViewModel.bookmarks.collectAsState()
                    val existingBookmark = if (bookmarkId >= 0) bookmarks.firstOrNull { it.id == bookmarkId } else null
                    AddLinkScreen(
                        onBack = { navController.popBackStack() },
                        existingBookmark = existingBookmark,
                        initialUrl = sharedUrl,
                        allBookmarks = bookmarks,
                        onOpenExisting = { id ->
                            navController.navigate(Routes.addLink(id)) { popUpTo(Routes.AddLink) { inclusive = true } }
                        },
                        onSave = { title, url, folder, note, favourite ->
                            if (!isCurrent(entry)) return@AddLinkScreen
                            if (existingBookmark != null) {
                                miscViewModel.saveBookmarkEdit(existingBookmark.copy(title = title, url = url, folder = folder, note = note, favourite = favourite))
                            } else {
                                miscViewModel.addBookmark(title, url, folder, note, favourite)
                            }
                            navController.popBackStack()
                        },
                    )
                }

                composable(Routes.Me) {
                    MeScreen(onBack = { navController.popBackStack() }, viewModel = miscViewModel, meViewModel = meViewModel, userName = appSettings.userName, onOpen = { route -> navController.navigate(route) })
                }
                composable(Routes.Inbox) {
                    com.example.timeapp.ui.inbox.InboxScreen(
                        viewModel = inboxViewModel,
                        onBack = { navController.popBackStack() },
                        // Routes the app itself wrote; an unknown one (from an older version) is ignored.
                        onOpen = { route -> runCatching { navController.navigate(route) } },
                    )
                }
                composable(Routes.Search) {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        // Task Detail, not the edit form — search is for finding, not changing.
                        onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                        onOpenMoney = { navController.navigate(Routes.Money) },
                        onOpenIdea = { id -> navController.navigate(Routes.ideasNote(id)) },
                        onOpenLinks = { navController.navigate(Routes.Links) },
                        planViewModel = planViewModel,
                        moneyViewModel = moneyViewModel,
                        ideasViewModel = ideasViewModel,
                        miscViewModel = miscViewModel,
                        habitsViewModel = habitsViewModel,
                        onOpenHabit = { id -> navController.navigate(Routes.habitDetail(id)) },
                    )
                }
                composable(Routes.Settings) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenNotifications = { navController.navigate(Routes.Notifications) },
                        onOpenAppearance = { navController.navigate(Routes.Appearance) },
                        onOpenProfile = { navController.navigate(Routes.Profile) },
                        onOpenAbout = { navController.navigate(Routes.About) },
                        onOpenHelp = { navController.navigate(Routes.Help) },
                        onOpenSecurity = { navController.navigate(Routes.Security) },
                        onOpenDataBackup = { navController.navigate(Routes.DataBackup) },
                        onOpenReports = { navController.navigate(Routes.Reports) },
                        onOpenWorkday = { navController.navigate(Routes.WorkdaySettings) },
                        onOpenMoneySettings = { navController.navigate(Routes.MoneySettings) },
                        onOpenCategories = { navController.navigate(Routes.Categories) },
                        onOpenTimers = { navController.navigate(Routes.TimersSettings) },
                        onOpenLanguage = { navController.navigate(Routes.LanguageSettings) },
                    )
                }
                composable(Routes.Help) {
                    com.example.timeapp.ui.settings.HelpScreen(
                        onBack = { navController.popBackStack() },
                        onWatchTour = { navController.navigate(Routes.HelpTour) },
                        onShowTipsAgain = { appViewModel.resetHelp() },
                    )
                }
                composable(Routes.HelpTour) {
                    com.example.timeapp.ui.setup.WelcomeTourScreen(
                        onDone = { navController.popBackStack() },
                        finishLabel = L("Done"),
                    )
                }
                composable(Routes.TimersSettings) {
                    com.example.timeapp.ui.settings.TimersFocusSettingsScreen(
                        onBack = { navController.popBackStack() }, focusViewModel = focusViewModel, appViewModel = appViewModel,
                    )
                }
                composable(Routes.LanguageSettings) {
                    com.example.timeapp.ui.settings.LanguageRegionScreen(onBack = { navController.popBackStack() }, appViewModel = appViewModel)
                }
                composable(Routes.Reports) {
                    ReportsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenTimeStats = { navController.navigate(Routes.TimeStats) },
                        onOpenMoneyStats = { navController.navigate(Routes.MoneyStats) },
                        onOpenHabitStats = { navController.navigate(Routes.HabitStats) },
                        onOpenSavings = { navController.navigate(Routes.Savings) },
                        onOpenWorkload = { navController.navigate(Routes.Workload) },
                    )
                }
                composable(Routes.Workload) {
                    com.example.timeapp.ui.stats.WorkloadScreen(
                        onBack = { navController.popBackStack() },
                        onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                        planViewModel = planViewModel,
                        appViewModel = appViewModel,
                    )
                }
                composable(Routes.MoneyCalendar) {
                    com.example.timeapp.ui.money.MoneyCalendarScreen(onBack = { navController.popBackStack() }, viewModel = moneyViewModel)
                }
                composable(Routes.Loans) {
                    com.example.timeapp.ui.money.LoansScreen(
                        onBack = { navController.popBackStack() },
                        onOpen = { id -> navController.navigate(Routes.loanDetail(id)) },
                        viewModel = loansViewModel,
                        wallets = moneyViewModel.uiState.collectAsState().value.activeWallets.preferring(appSettings.defaultWalletId),
                    )
                }
                composable(Routes.LoanDetail, arguments = listOf(navArgument("loanId") { type = NavType.LongType })) { entry ->
                    com.example.timeapp.ui.money.LoanDetailScreen(
                        loanId = entry.arguments?.getLong("loanId") ?: 0L,
                        onBack = { navController.popBackStack() },
                        viewModel = loansViewModel,
                        wallets = moneyViewModel.uiState.collectAsState().value.activeWallets,
                    )
                }
                composable(Routes.Savings) {
                    com.example.timeapp.ui.money.SavingsGoalsScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = savingsViewModel,
                    )
                }
                composable(Routes.HabitStats) {
                    com.example.timeapp.ui.stats.HabitStatsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenHabit = { id -> navController.navigate(Routes.habitDetail(id)) },
                        viewModel = habitsViewModel,
                    )
                }
                composable(Routes.TimeStats) {
                    TimeStatsScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = statsViewModel,
                        onOpenCategory = { id -> navController.navigate(Routes.categoryDetail(id)) },
                    )
                }
                composable(
                    Routes.CategoryDetail,
                    arguments = listOf(navArgument("categoryId") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("categoryId") ?: -1L
                    com.example.timeapp.ui.stats.CategoryDetailScreen(
                        category = categoryViewModel.all.collectAsState().value.firstOrNull { it.id == id },
                        onBack = { navController.popBackStack() },
                        onOpenTask = { taskId -> navController.navigate(Routes.taskDetail(taskId)) },
                        statsViewModel = statsViewModel,
                        planViewModel = planViewModel,
                    )
                }
                composable(Routes.MoneyStats) {
                    val moneyNow by moneyViewModel.uiState.collectAsState()
                    val loansNow by loansViewModel.uiState.collectAsState()
                    MoneyStatsScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = statsViewModel,
                        netWorth = com.example.timeapp.data.netWorthByMonth(
                            moneyNow.wallets, moneyNow.history, (loansNow.open + loansNow.closed).map { it.loan },
                            java.time.LocalDate.now(), ZoneId.systemDefault(),
                        ),
                    )
                }
                composable(Routes.MoneySettings) {
                    com.example.timeapp.ui.money.MoneySettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCategories = { navController.navigate(Routes.Categories) },
                        appViewModel = appViewModel,
                        wallets = moneyViewModel.uiState.collectAsState().value.activeWallets,
                    )
                }
                composable(Routes.WorkdaySettings) {
                    com.example.timeapp.ui.workday.WorkdaySettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenMyWorkday = { navController.navigate(Routes.Workday) },
                        appViewModel = appViewModel,
                    )
                }
                composable(Routes.Workday) {
                    MyWorkdayScreen(
                        onBack = { navController.popBackStack() },
                        onReviewDay = { navController.navigate(Routes.DailyReview) },
                        onWeeklyReview = { navController.navigate(Routes.WeeklyReview) },
                        viewModel = workdayViewModel,
                        taskTitle = { id -> planViewModel.uiState.value.find(id)?.title },
                    )
                }
                composable(Routes.DailyReview) {
                    DailyReviewScreen(
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() },
                        viewModel = reviewViewModel,
                    )
                }
                composable(Routes.WeeklyReview) {
                    WeeklyReviewScreen(
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() },
                        onReschedule = { id -> navController.navigate(Routes.reschedule(id)) },
                        statsViewModel = statsViewModel,
                        moneyViewModel = moneyViewModel,
                        appViewModel = appViewModel,
                        reviewViewModel = reviewViewModel,
                        planViewModel = planViewModel,
                    )
                }
                composable(Routes.Security) {
                    SecuritySettingsScreen(
                        onBack = { navController.popBackStack() },
                        onSetupPin = { navController.navigate(Routes.PinSetup) },
                        onChangePin = { navController.navigate(Routes.PinChange) },
                        viewModel = securityViewModel,
                    )
                }
                composable(Routes.PinSetup) {
                    PinSetupScreen(
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() },
                        viewModel = securityViewModel,
                    )
                }
                composable(Routes.PinChange) {
                    PinChangeScreen(
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() },
                        viewModel = securityViewModel,
                    )
                }
                composable(Routes.DataBackup) {
                    val security by securityViewModel.uiState.collectAsState()
                    DataBackupScreen(
                        onBack = { navController.popBackStack() },
                        onRestorePreview = { navController.navigate(Routes.RestorePreview) },
                        lockEnabled = security.lockEnabled,
                        viewModel = backupViewModel,
                        onImportCsv = { navController.navigate(Routes.CsvImport) },
                        onImportBookmarks = { navController.navigate(Routes.BookmarkImport) },
                        onImportCalendar = { navController.navigate(Routes.CalendarImport) },
                    )
                }
                composable(Routes.CalendarImport) {
                    com.example.timeapp.ui.backup.CalendarImportScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = planViewModel,
                    )
                }
                composable(Routes.BookmarkImport) {
                    com.example.timeapp.ui.backup.BookmarkImportScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = miscViewModel,
                    )
                }
                composable(Routes.CsvImport) {
                    com.example.timeapp.ui.backup.CsvImportScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = moneyViewModel,
                        moneyCategories = categoryViewModel.all.collectAsState().value
                            .filter { it.kind == com.example.timeapp.data.CategoryKind.MONEY && !it.archived },
                    )
                }
                composable(Routes.RestorePreview) {
                    val security by securityViewModel.uiState.collectAsState()
                    val lockedState by securityViewModel.locked.collectAsState()
                    RestorePreviewScreen(
                        onBack = { navController.popBackStack() },
                        lockEnabled = security.lockEnabled,
                        locked = lockedState ?: false,
                        onLockNow = { securityViewModel.lockNow() },
                        viewModel = backupViewModel,
                    )
                }
                composable(Routes.Notifications) {
                    NotificationsSettingsScreen(onBack = { navController.popBackStack() }, viewModel = appViewModel)
                }
                composable(Routes.Appearance) {
                    AppearanceSettingsScreen(onBack = { navController.popBackStack() }, viewModel = appViewModel)
                }
                composable(Routes.Profile) {
                    ProfileSettingsScreen(onBack = { navController.popBackStack() }, viewModel = appViewModel)
                }
                composable(Routes.Categories) {
                    com.example.timeapp.ui.me.CategoriesSettingsScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = categoryViewModel,
                        onOpenProject = { id -> navController.navigate(Routes.projectSummary(id)) },
                    )
                }
                composable(Routes.ProjectSummary, arguments = listOf(navArgument("projectId") { type = NavType.LongType })) { entry ->
                    val projectId = entry.arguments?.getLong("projectId") ?: 0L
                    val plan by planViewModel.uiState.collectAsState()
                    val moneyNow by moneyViewModel.uiState.collectAsState()
                    val sessions by statsViewModel.allSessions.collectAsState()
                    val all = plan.allEntries
                    val tasks = all.filter { it.projectId == projectId }
                    val ids = tasks.map { it.id }.toSet()
                    val money = (moneyNow.plans + moneyNow.history).filter { it.projectId == projectId || (it.taskId != null && it.taskId in ids) }
                    com.example.timeapp.ui.stats.ProjectSummaryScreen(
                        name = categoryViewModel.all.collectAsState().value.firstOrNull { it.id == projectId }?.name ?: L("Project"),
                        summary = com.example.timeapp.data.projectSummary(projectId, all, sessions, moneyNow.history, moneyNow.plans),
                        tasks = tasks,
                        money = money,
                        onBack = { navController.popBackStack() },
                        onOpenTask = { id -> navController.navigate(Routes.taskDetail(id)) },
                    )
                }
                composable(Routes.About) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    Routes.AddTask,
                    arguments = listOf(navArgument("entryId") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    val entryId = entry.arguments?.getLong("entryId") ?: -1L
                    val plan by planViewModel.uiState.collectAsState()
                    val existingEntry = if (entryId >= 0) plan.find(entryId) else null
                    val reminderOwners by planViewModel.reminderOwnerIds.collectAsState()
                    val workCategories by categoryViewModel.work.collectAsState()
                    val projects by categoryViewModel.projects.collectAsState()
                    val tagNames by planViewModel.tagNames.collectAsState()
                    AddTaskScreen(
                        onBack = { navController.popBackStack() },
                        existingEntry = existingEntry,
                        initialRemind = existingEntry != null && existingEntry.id in reminderOwners,
                        remindersMuted = !appSettings.notifyRemindersEnabled || !appSettings.notifyScheduleChannel,
                        categories = workCategories,
                        onCreateCategory = { name, color, onCreated ->
                            categoryViewModel.add(name, color, com.example.timeapp.data.CategoryKind.WORK, onCreated = onCreated)
                        },
                        projects = projects,
                        onCreateProject = { name, color, onCreated ->
                            categoryViewModel.add(name, color, com.example.timeapp.data.CategoryKind.PROJECT, onCreated = onCreated)
                        },
                        tagSuggestions = tagNames,
                        initialTiming = existingEntry?.let { planViewModel.reminderTimings.collectAsState().value[it.id] },
                        onSave = { title, categoryId, dueOn, blockStart, estimateMinutes, description, remind, recurrence, priority, scope, projectId, tags, timing ->
                            if (!isCurrent(entry)) return@AddTaskScreen
                            val estimateMs = estimateMinutes?.let { it * 60_000L }
                            val blockDurationSeconds = estimateMinutes?.let { it * 60L } ?: (45 * 60L)
                            if (existingEntry != null) {
                                // Booked time is left exactly as it is. This used to rebuild it as
                                // 09:00 on the due date (or drop it with no due date), so renaming a
                                // task silently moved or deleted its booking. It's changed from the
                                // task's page, where overlaps are named.
                                planViewModel.saveTaskEdit(
                                    existingEntry.copy(title = title, categoryId = categoryId, dueOn = dueOn, estimateMs = estimateMs, description = description, recurrence = recurrence, priority = priority, projectId = projectId),
                                    scope,
                                    remind,
                                    tags,
                                    timing,
                                )
                            } else if (blockStart != null && dueOn != null) {
                                // At the time chosen on the form — it was always 09:00.
                                val start = dueOn.atTime(blockStart).atZone(ZoneId.systemDefault()).toInstant()
                                val end = start.plusSeconds(blockDurationSeconds)
                                // Repeat and priority went nowhere on this path: a booked task saved as a one-off, Normal.
                                planViewModel.proposeSchedule(title, start, end, categoryId, dueOn, estimateMs, description = description, remind = remind, recurrence = recurrence, projectId = projectId, tags = tags, timing = timing, priority = priority)
                            } else {
                                planViewModel.addTask(title, categoryId, dueOn, estimateMs, description, remind, recurrence, priority, projectId, tags, timing)
                            }
                            navController.popBackStack()
                        },
                    )
                }
                composable(
                    Routes.AddCost,
                    arguments = listOf(navArgument("planId") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    val planId = entry.arguments?.getLong("planId") ?: -1L
                    val money by moneyViewModel.uiState.collectAsState()
                    val existingPlan = if (planId >= 0) money.plans.firstOrNull { it.id == planId } else null
                    val planReminderOwners by moneyViewModel.reminderOwnerIds.collectAsState()
                    val moneyCategories by categoryViewModel.money.collectAsState()
                    AddCostScreen(
                        onBack = { navController.popBackStack() },
                        existingPlan = existingPlan,
                        initialRemind = existingPlan != null && existingPlan.id in planReminderOwners,
                        remindersMuted = !appSettings.notifyRemindersEnabled || !appSettings.notifyMoneyChannel,
                        categories = moneyCategories,
                        onCreateCategory = { name, color, onCreated ->
                            categoryViewModel.add(name, color, com.example.timeapp.data.CategoryKind.MONEY, onCreated = onCreated)
                        },
                        onSave = { title, amountTaka, walletId, categoryId, dueOn, note, remind, recurrence, scope, timing, links ->
                            if (!isCurrent(entry)) return@AddCostScreen
                            val amountMinor = com.example.timeapp.data.parseTakaToMinor(amountTaka) ?: return@AddCostScreen
                            if (existingPlan != null) {
                                moneyViewModel.savePlanEdit(
                                    existingPlan.copy(
                                        title = title, amountMinor = amountMinor, categoryId = categoryId, dueOn = dueOn, walletId = walletId, note = note,
                                        recurrence = recurrence, priority = links.priority, taskId = links.taskId, projectId = links.projectId,
                                    ),
                                    scope,
                                    remind,
                                    timing,
                                )
                            } else {
                                moneyViewModel.addPlan(title, amountMinor, categoryId, dueOn, walletId, note, remind, recurrence, timing, links)
                            }
                            navController.popBackStack()
                        },
                        wallets = money.pickerWallets(existingPlan?.walletId).preferring(appSettings.defaultWalletId),
                        initialTiming = existingPlan?.let { moneyViewModel.reminderTimings.collectAsState().value[it.id] },
                        tasks = planViewModel.uiState.collectAsState().value.entries.filter { it.kind == EntryKind.TASK && it.status != EntryStatus.DONE },
                        projects = categoryViewModel.projects.collectAsState().value,
                    )
                }
                composable(Routes.AddWallet) { entry ->
                    AddWalletScreen(
                        onBack = { navController.popBackStack() },
                        onSave = { name, kind, openingBalanceTaka ->
                            if (!isCurrent(entry)) return@AddWalletScreen
                            moneyViewModel.addWallet(name, kind, com.example.timeapp.data.parseTakaToMinor(openingBalanceTaka) ?: 0L, java.time.LocalDate.now())
                            navController.popBackStack()
                        },
                    )
                }
                composable(Routes.Habits) {
                    com.example.timeapp.ui.habits.HabitsScreen(
                        onBack = { navController.popBackStack() },
                        onAdd = { navController.navigate(Routes.addHabit()) },
                        onOpen = { id -> navController.navigate(Routes.habitDetail(id)) },
                        viewModel = habitsViewModel,
                    )
                }
                composable(
                    Routes.HabitDetail,
                    arguments = listOf(navArgument("habitId") { type = NavType.LongType }),
                ) { entry ->
                    com.example.timeapp.ui.habits.HabitDetailScreen(
                        habitId = entry.arguments?.getLong("habitId") ?: -1L,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(Routes.addHabit(id)) },
                        viewModel = habitsViewModel,
                    )
                }
                composable(
                    Routes.AddHabit,
                    arguments = listOf(navArgument("habitId") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    val habitId = entry.arguments?.getLong("habitId") ?: -1L
                    val habits by habitsViewModel.uiState.collectAsState()
                    val existing = (habits.active + habits.archived).firstOrNull { it.habit.id == habitId }?.habit
                    val workCategories by categoryViewModel.work.collectAsState()
                    com.example.timeapp.ui.add.AddHabitScreen(
                        onBack = { navController.popBackStack() },
                        existing = existing,
                        remindersMuted = !appSettings.notifyRemindersEnabled || !appSettings.notifyScheduleChannel,
                        categories = workCategories,
                        onCreateCategory = { name, color, onCreated ->
                            categoryViewModel.add(name, color, com.example.timeapp.data.CategoryKind.WORK, onCreated = onCreated)
                        },
                        onSave = { habit ->
                            if (!isCurrent(entry)) return@AddHabitScreen
                            habitsViewModel.save(habit)
                            navController.popBackStack()
                        },
                    )
                }
                composable(
                    Routes.AddScheduleBlock,
                    arguments = listOf(
                        navArgument("startMs") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("entryId") { type = NavType.LongType; defaultValue = -1L },
                    ),
                ) { entry ->
                    val startMs = entry.arguments?.getLong("startMs") ?: -1L
                    val blockId = entry.arguments?.getLong("entryId") ?: -1L
                    val plan by planViewModel.uiState.collectAsState()
                    val existingBlock = if (blockId >= 0) plan.find(blockId) else null
                    val reminderOwners by planViewModel.reminderOwnerIds.collectAsState()
                    val timings by planViewModel.reminderTimings.collectAsState()
                    // The edit waits for the record, so the form never opens blank and saves a new one.
                    if (blockId >= 0 && existingBlock == null) return@composable
                    com.example.timeapp.ui.add.AddScheduleBlockScreen(
                        existing = existingBlock,
                        initialRemind = existingBlock != null && existingBlock.id in reminderOwners,
                        initialTiming = existingBlock?.let { timings[it.id] },
                        overlapFor = { s, e -> planViewModel.overlapFor(s, e, exceptEntryId = existingBlock?.id)?.title },
                        initialStart = startMs.takeIf { it >= 0 }?.let {
                            java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
                        },
                        onBack = { navController.popBackStack() },
                        // Was never passed, so the block form always promised a notification
                        // even with schedule reminders muted — the other two forms say so.
                        remindersMuted = !appSettings.notifyRemindersEnabled || !appSettings.notifyScheduleChannel,
                        onSave = { title, start, end, remind, recurrence, fixed, timing, scope ->
                            if (!isCurrent(entry)) return@AddScheduleBlockScreen
                            if (existingBlock != null) {
                                planViewModel.saveBlockEdit(
                                    existingBlock.copy(title = title, startAt = start, endAt = end, isFixed = fixed, recurrence = recurrence),
                                    scope, remind, timing,
                                )
                            } else {
                                planViewModel.proposeSchedule(title, start, end, categoryId = null, kind = EntryKind.EVENT, remind = remind, recurrence = recurrence, isFixed = fixed, timing = timing)
                            }
                            navController.popBackStack()
                        },
                    )
                }
                composable(
                    Routes.AddTransaction,
                    arguments = listOf(navArgument("recordId") { type = NavType.LongType; defaultValue = -1L }),
                ) { entry ->
                    val recordId = entry.arguments?.getLong("recordId") ?: -1L
                    val money by moneyViewModel.uiState.collectAsState()
                    // History's "Edit" navigates here with the ledger row's id. This registration
                    // used to ignore it entirely — no existingRecord, and a save that always called
                    // addTransaction — so "Edit" opened a blank form and saving it posted a
                    // *duplicate* transaction, double-counting the spend and double-debiting the
                    // wallet, while updateHistoryRecord (and its plan reconciliation) had no caller.
                    val existingRecord = if (recordId >= 0) money.history.firstOrNull { it.id == recordId } else null
                    com.example.timeapp.ui.add.AddTransactionScreen(
                        onBack = { navController.popBackStack() },
                        onSave = { direction, title, amountTaka, fromWalletId, toWalletId, note, feeTaka, links ->
                            if (!isCurrent(entry)) return@AddTransactionScreen
                            val amountMinor = com.example.timeapp.data.parseTakaToMinor(amountTaka) ?: return@AddTransactionScreen
                            when {
                                existingRecord != null -> {
                                    // A payment row keeps its plan's estimate in amountMinor for
                                    // variance reporting and its direction is fixed by the plan;
                                    // only the amount actually paid changes. A standalone
                                    // transaction mirrors paidMinor into amountMinor, as
                                    // addTransaction does when it creates one.
                                    val linked = existingRecord.linkedPlanId != null
                                    moneyViewModel.updateHistoryRecord(
                                        existingRecord.copy(
                                            direction = if (linked) existingRecord.direction else direction,
                                            title = title,
                                            amountMinor = if (linked) existingRecord.amountMinor else amountMinor,
                                            paidMinor = amountMinor,
                                            walletId = fromWalletId,
                                            note = note,
                                            categoryId = if (linked) existingRecord.categoryId else links.categoryId,
                                            taskId = links.taskId,
                                            projectId = links.projectId ?: existingRecord.projectId,
                                        ),
                                    )
                                }
                                direction == com.example.timeapp.data.Direction.TRANSFER && toWalletId != null ->
                                    moneyViewModel.transfer(fromWalletId, toWalletId, amountMinor, com.example.timeapp.data.parseTakaToMinor(feeTaka) ?: 0L)
                                else -> moneyViewModel.addTransaction(direction, title, amountMinor, fromWalletId, note, links)
                            }
                            navController.popBackStack()
                        },
                        wallets = money.pickerWallets(existingRecord?.walletId, existingRecord?.transferToWalletId).preferring(appSettings.defaultWalletId),
                        existingRecord = existingRecord,
                        categories = categoryViewModel.money.collectAsState().value,
                        onCreateCategory = { name, color, onCreated ->
                            categoryViewModel.add(name, color, com.example.timeapp.data.CategoryKind.MONEY, onCreated = onCreated)
                        },
                        tasks = planViewModel.uiState.collectAsState().value.entries.filter { it.kind == EntryKind.TASK && it.status != EntryStatus.DONE },
                    )
                }

                composable(Routes.Switch) {
                    val plan by planViewModel.uiState.collectAsState()
                    // The running task first: taking "the first paused or doing" could pick a paused
                    // one, so the switch paused nothing and the running task stayed Doing with its
                    // session closed underneath it.
                    val current = plan.entries.firstOrNull { it.status == EntryStatus.DOING }
                        ?: plan.entries.firstOrNull { it.status == EntryStatus.PAUSED }
                    val trackedMs by (
                        current?.let { planViewModel.trackedMs(it.id) } ?: kotlinx.coroutines.flow.flowOf(0L)
                        ).collectAsState(initial = 0L)
                    val candidates = plan.entries.filter {
                        it.id != current?.id &&
                            it.status in listOf(EntryStatus.PLANNED, EntryStatus.UNSCHEDULED, EntryStatus.OVERDUE)
                    }
                    SwitchWorkScreen(
                        currentEntryId = current?.id,
                        currentEntryTitle = current?.title ?: "",
                        trackedMs = trackedMs,
                        remainingMs = current?.estimateMs?.let { (it - trackedMs).coerceAtLeast(0) },
                        candidates = candidates,
                        onBack = { navController.popBackStack() },
                        onReschedule = {
                            current?.let { navController.navigate(Routes.reschedule(it.id)) }
                        },
                        onConfirm = { action, resumeNote, switchToId ->
                            when (action) {
                                "switch" -> if (switchToId != null) {
                                    planViewModel.switchTask(current?.id, switchToId, resumeNote.ifBlank { null })
                                }
                                "pause" -> current?.let { planViewModel.pauseTask(it.id, resumeNote.ifBlank { null }) }
                            }
                            navController.popBackStack()
                        },
                    )
                }
                composable(
                    Routes.Reschedule,
                    arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
                ) { entry ->
                    val entryId = entry.arguments?.getLong("entryId") ?: 0L
                    RescheduleScreen(
                        entryId = entryId,
                        workStart = com.example.timeapp.ui.plan.SchedulingMath.parseWorkTime(appSettings.workStart, java.time.LocalTime.of(9, 0)),
                        workEnd = com.example.timeapp.ui.plan.SchedulingMath.parseWorkTime(appSettings.workEnd, java.time.LocalTime.of(18, 0)),
                        onBack = { navController.popBackStack() },
                        onApply = { start, end ->
                            planViewModel.reschedule(entryId, start, end)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                        viewModel = planViewModel,
                    )
                }
            }
        }
        }
    }
    // Confetti and milestone banners over everything (goal reached, streak milestones).
    com.example.timeapp.ui.components.CelebrationHost()
    }
    }

    // Phase 19C — the first time each tab (and Habits) opens, its "how it works" sheet shows once
    // by itself. Seen-flags wait for DataStore's real value, so a returning user never sees a flash.
    val tipKey = tabOf(currentRoute) ?: currentRoute?.takeIf { it == Routes.Habits }
    val tip = com.example.timeapp.ui.components.TabHelps.forTip(tipKey)
    if (appSettings.loaded && tip != null && tipKey != null && tipKey !in appSettings.tipsSeen && !showAddSheet) {
        com.example.timeapp.ui.components.HelpSheet(tip, onDismiss = { appViewModel.markTipSeen(tipKey) }, firstVisit = true)
    }

    if (showAddSheet) {
        // Open all the way: half-open, Link and Wallet sat behind the navigation bar and only a
        // drag (nothing hinted at one) revealed them.
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, sheetState = sheetState) {
            AddSheetContent(
                onPick = { type ->
                    showAddSheet = false
                    when (type) {
                        RecordType.TASK -> navController.navigate(Routes.addTask())
                        RecordType.SCHEDULE_BLOCK -> navController.navigate(Routes.addScheduleBlock())
                        RecordType.HABIT -> navController.navigate(Routes.addHabit())
                        RecordType.COST -> navController.navigate(Routes.addCost())
                        RecordType.TRANSACTION -> navController.navigate(Routes.addTransaction())
                        RecordType.LINK -> navController.navigate(Routes.addLink())
                        RecordType.WALLET -> navController.navigate(Routes.AddWallet)
                    }
                },
            )
        }
    }
}
