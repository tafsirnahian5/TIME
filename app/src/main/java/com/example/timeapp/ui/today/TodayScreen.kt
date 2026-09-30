package com.example.timeapp.ui.today

import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Close
import com.example.timeapp.data.showGettingStarted
import com.example.timeapp.data.isDone
import com.example.timeapp.data.doneCount
import com.example.timeapp.data.StartStep
import com.example.timeapp.data.StartFacts
import com.example.timeapp.data.L
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.HabitForToday
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.TimelineItem
import com.example.timeapp.data.WorkdaySegmentKind
import com.example.timeapp.data.carryover
import com.example.timeapp.data.commitmentSuggestions
import com.example.timeapp.data.displayPattern
import com.example.timeapp.data.netSpendMinor
import com.example.timeapp.data.outstandingThisMonth
import com.example.timeapp.data.restOfToday
import com.example.timeapp.data.startNext
import com.example.timeapp.data.tasksToday
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.IllustrationImage
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.TodayTopBar
import com.example.timeapp.ui.focus.formatElapsed
import com.example.timeapp.ui.money.MoneyViewModel
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.ui.stats.formatHm
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.MarkerEvent
import com.example.timeapp.ui.theme.MarkerMoney
import com.example.timeapp.ui.theme.MarkerTask
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import com.example.timeapp.ui.workday.WorkdayViewModel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today (Phase 18A, from the "T!ME — Today redesign" canvas): one glance, one next action.
 * Top to bottom: the date and workday, the day at a glance, the Now card (what's running and its
 * controls — or what to start), anything missed before today, Today's 3, the rest of the day in
 * time order, habits, money, and paused work / the inbox folded to a line each. A brand-new install
 * gets four starting points instead. It used to be eight stacked cards of text with no Add button.
 */
@Composable
fun TodayScreen(
    onAdd: () -> Unit,
    onOpenMe: () -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchWork: () -> Unit,
    onOpenMoney: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    viewModel: PlanViewModel,
    moneyViewModel: MoneyViewModel,
    habitsViewModel: com.example.timeapp.ui.habits.HabitsViewModel,
    workdayViewModel: WorkdayViewModel,
    userName: String = "",
    onOpenTask: (Long) -> Unit = {},
    onOpenFocus: () -> Unit = {},
    onOpenPlan: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenWorkday: () -> Unit = {},
    onPay: (Long) -> Unit = {},
    onAddTask: () -> Unit = onAdd,
    onAddHabit: () -> Unit = onAdd,
    onAddWallet: () -> Unit = onAdd,
    onAddCost: () -> Unit = onAdd,
    gettingStartedDismissed: Boolean = false,
    onDismissGettingStarted: () -> Unit = {},
    /** A glance number opens its report (they used to live only in Settings). */
    onOpenReport: (String) -> Unit = {},
) {
    val commitments by viewModel.todayCommitments.collectAsState()
    val entries by viewModel.uiState.collectAsState()
    val money by moneyViewModel.uiState.collectAsState()
    val habits by habitsViewModel.uiState.collectAsState()
    val workday by workdayViewModel.uiState.collectAsState()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    // A clock for everything live on this page — the running timer, "rest of today".
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); nowMs = System.currentTimeMillis() } }
    val now = Instant.ofEpochMilli(nowMs)

    val all = entries.entries
    val current = all.firstOrNull { it.status == EntryStatus.DOING } ?: all.firstOrNull { it.status == EntryStatus.PAUSED }
    val currentSessions by remember(current?.id) {
        current?.let { viewModel.sessions(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val openSession = currentSessions.firstOrNull { it.endedAt == null }
    val trackedOnCurrent = currentSessions.sumOf { it.durationMs ?: 0L } +
        (openSession?.let { (nowMs - it.startedAt.toEpochMilli()).coerceAtLeast(0) } ?: 0L)
    val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val runningToday = openSession?.let { (nowMs - maxOf(it.startedAt.toEpochMilli(), startOfDay)).coerceAtLeast(0) } ?: 0L
    val trackedToday = workday.summary.activeMs + workday.summary.outsideMs + runningToday

    // Everything below walks the whole task list and ledger. The page ticks once a second for
    // the live timer, and after two years of records that walk took ~24 ms — more than a frame,
    // every second (measured in LongTermDataTest). So it's recomputed only when the data changes
    // or the minute turns; only the timer above reads the per-second clock.
    val minute = nowMs / 60_000
    val (doneToday, onPlate) = remember(all, entries.archived, today) { tasksToday(all + entries.archived, today, zone) }
    val habitRows = habits.dueToday
    val habitsDone = habitRows.count { it.marks[habits.today] == HabitMark.DONE }
    val spentToday = remember(money.history, today) {
        money.history.filter { it.settledAt?.atZone(zone)?.toLocalDate() == today }.sumOf { it.netSpendMinor }
    }

    val activeCommitmentIds = commitments.filter { it.active }.map { it.entryId }
    val next = remember(all, activeCommitmentIds, today, current == null) {
        if (current == null) startNext(all, activeCommitmentIds, today, zone) else null
    }
    val missed = remember(all, money.plans, today) { carryover(all, money.plans, today) }
    val timeline = remember(all, money.plans, habitRows, habits.today, minute) {
        restOfToday(
            all, money.plans,
            habitRows.map { HabitForToday(it.habit.id, it.habit.name, it.habit.remindAt, it.marks[habits.today] == HabitMark.DONE) },
            Instant.ofEpochMilli(minute * 60_000), zone,
        )
    }
    val paused = remember(all, current?.id) { all.filter { it.status == EntryStatus.PAUSED && it.id != current?.id } }
    val inbox = remember(all) { all.filter { it.status == EntryStatus.UNSCHEDULED } }
    // Starting work posts the running-timer notification; ask the way the Focus screens do.
    val askNotificationPermission = com.example.timeapp.ui.components.rememberNotificationPermissionRequest()
    val hasTrackedTime by viewModel.hasTrackedTime.collectAsState(initial = true)
    val startFacts = StartFacts(
        hasTask = all.any { it.kind == com.example.timeapp.data.EntryKind.TASK } ||
            entries.archived.any { it.kind == com.example.timeapp.data.EntryKind.TASK },
        hasTrackedTime = hasTrackedTime,
        hasWallet = money.wallets.isNotEmpty(),
        hasPlannedCost = money.plans.isNotEmpty() || money.history.any { it.linkedPlanId != null },
        hasHabit = habits.active.isNotEmpty(),
    )
    val startActions: (StartStep) -> Unit = { step ->
        when (step) {
            StartStep.TASK -> onAddTask()
            StartStep.TIMER -> onOpenFocus()
            StartStep.WALLET -> onAddWallet()
            StartStep.BILL -> onAddCost()
            StartStep.HABIT -> onAddHabit()
        }
    }
    val firstRun = all.isEmpty() && entries.archived.isEmpty() && habits.active.isEmpty() &&
        money.wallets.isEmpty() && money.plans.isEmpty() && money.history.isEmpty()

    Scaffold(
        topBar = { TodayTopBar(onSearch = onSearch, onAvatar = onOpenMe, onOpenSettings = onOpenSettings, initial = userName.trim().take(1)) },
        floatingActionButton = {
            // Today had no way to add anything: the Add sheet was reachable only from other tabs.
            FloatingActionButton(
                onClick = onAdd,
                containerColor = TimeThemeTokens.colors.accC,
                contentColor = TimeThemeTokens.colors.accInk,
            ) { Icon(Icons.Rounded.Add, contentDescription = L("Add")) }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Dimens.s16, end = Dimens.s16, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.s16),
        ) {
            item { DateHeader(today, workday.activeKind, workday.segments.firstOrNull()?.startedAt, firstRun, workdayViewModel, onOpenWorkday, taskTitle = { id -> entries.find(id)?.title }) }
            if (firstRun) {
                item { FirstRunCard(startFacts, startActions) }
                return@LazyColumn
            }
            item { DayGlance(trackedToday, doneToday, onPlate, habitsDone, habitRows.size, spentToday, onOpenReport) }
            if (showGettingStarted(startFacts, gettingStartedDismissed)) {
                item { GettingStartedCard(startFacts, startActions, onDismiss = onDismissGettingStarted) }
            }
            item {
                NowCard(
                    current = current, tracked = trackedOnCurrent, next = next,
                    isCommitment = next != null && next.id in activeCommitmentIds,
                    onPause = { current?.let { viewModel.pauseTask(it.id) } },
                    onSwitch = onSwitchWork,
                    onDone = { current?.let { viewModel.toggleDone(it) } },
                    onResume = { askNotificationPermission(); current?.let { viewModel.resumeTask(it.id) } },
                    onStart = { askNotificationPermission(); next?.let { viewModel.startTask(it.id) } },
                    onFocus = onOpenFocus,
                    onAddTask = onAddTask,
                    onOpenTask = onOpenTask,
                )
            }
            if (!missed.isEmpty) {
                item {
                    CarryoverCard(
                        missed,
                        onToday = { e -> viewModel.updateTask(e.copy(dueOn = today)) },
                        onPay = onPay,
                        onOpenTask = onOpenTask,
                    )
                }
            }
            item {
                CommitmentsCard(
                    commitments, all, entries.archived, viewModel, onOpenTask,
                    suggestions = remember(all, activeCommitmentIds, today) { commitmentSuggestions(all, today, activeCommitmentIds.toSet(), zone) },
                )
            }
            item { RestOfTodayCard(timeline, now, zone, onOpenTask, onOpenCalendar, onOpenMoney) }
            item {
                TodayHabitsCard(
                    habits = habits,
                    onOpenHabits = onOpenHabits,
                    onOpenHabit = onOpenHabit,
                    onToggle = { row, mark -> habitsViewModel.toggleDone(row.habit.id, habits.today, mark) },
                    onSetDay = { row, mark, value, note -> habitsViewModel.setDay(row.habit.id, habits.today, mark, value, note) },
                    onStartTimer = { row -> habitsViewModel.startTimer(row.habit) },
                    onAddHabit = onAddHabit,
                )
            }
            item { MoneyDueSoonCard(money.plans, today, money.plans.outstandingThisMonth(), onOpenMoney) }
            if (paused.isNotEmpty() || inbox.isNotEmpty()) {
                item { PausedAndInboxCard(paused, inbox, viewModel, onOpenTask, onOpenPlan) }
            }
        }
    }
}

/** The date, and the workday as one tappable pill with its next action beside it. */
@Composable
private fun DateHeader(
    today: LocalDate,
    activeKind: WorkdaySegmentKind?,
    dayStartedAt: Instant?,
    firstRun: Boolean,
    workday: WorkdayViewModel,
    onOpenWorkday: () -> Unit,
    taskTitle: (Long) -> String? = { null },
) {
    val status = TimeThemeTokens.status
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // The same offer as My Workday: Today's Resume day used to go straight back on duty and
    // leave the task the break had paused sitting there paused.
    var resumeOffer by remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    resumeOffer?.let { taskId ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { resumeOffer = null },
            title = { Text(L("Back to %1\$s?", (taskTitle(taskId) ?: L("that task")))) },
            text = { Text(L("Your break paused it. Pick it up again as you go back on duty?")) },
            confirmButton = { TextButton(onClick = { resumeOffer = null; workday.resumeDay(taskId) }) { Text(L("Resume it")) } },
            dismissButton = { TextButton(onClick = { resumeOffer = null; workday.resumeDay(null) }) { Text(L("Not now")) } },
        )
    }
    Column(modifier = Modifier.padding(top = Dimens.s4), verticalArrangement = Arrangement.spacedBy(Dimens.s10)) {
        Text(
            today.format(displayPattern("EEE d MMM")),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (firstRun) return@Column
        val hm = displayPattern("HH:mm")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.s8)) {
            val (label, bg, ink) = when (activeKind) {
                WorkdaySegmentKind.ON_DUTY -> Triple(
                    dayStartedAt?.let { L("On duty since %1\$s", it.atZone(ZoneId.systemDefault()).format(hm)) } ?: L("On duty"),
                    status.ok, status.okInk,
                )
                WorkdaySegmentKind.BREAK -> Triple(L("On a break"), status.warn, status.warnInk)
                else -> Triple(if (dayStartedAt != null) L("Day ended") else L("Workday not started"), status.neutral, status.neutralInk)
            }
            Row(
                modifier = Modifier.heightIn(min = Dimens.touchMin).clickable(onClick = onOpenWorkday),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.background(bg, Shapes.pill).padding(horizontal = Dimens.s12, vertical = Dimens.s6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(ink, CircleShape))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = ink, modifier = Modifier.padding(start = Dimens.s6))
                }
            }
            when (activeKind) {
                WorkdaySegmentKind.ON_DUTY -> PillAction(L("Take a break")) { workday.takeBreak() }
                WorkdaySegmentKind.BREAK -> PillAction(L("Resume day")) {
                    scope.launch { val task = workday.interruptedTask(); if (task != null) resumeOffer = task else workday.resumeDay(null) }
                }
                else -> if (dayStartedAt == null) PillAction(L("Start my day")) { workday.startDay() }
            }
        }
    }
}

@Composable
private fun PillAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label) }
}

/** Four numbers for the day so far — each one a count or amount, never a score. */
@Composable
private fun DayGlance(trackedMs: Long, done: Int, onPlate: Int, habitsDone: Int, habitsDue: Int, spentMinor: Long, onOpenReport: (String) -> Unit) {
    val r = com.example.timeapp.ui.Routes
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s8)) {
        GlanceCell(Icons.Rounded.Timer, formatHm(trackedMs), L("tracked"), null, Modifier.weight(1f)) { onOpenReport(r.TimeStats) }
        GlanceCell(
            Icons.Rounded.CheckCircle, if (onPlate == 0) "—" else L("%1\$s/%2\$s", done, onPlate), L("tasks done"),
            if (onPlate == 0) null else done / onPlate.toFloat(), Modifier.weight(1f),
        ) { onOpenReport(r.TimeStats) }
        GlanceCell(
            Icons.Rounded.EventRepeat, if (habitsDue == 0) "—" else L("%1\$s/%2\$s", habitsDone, habitsDue), L("habits"),
            if (habitsDue == 0) null else habitsDone / habitsDue.toFloat(), Modifier.weight(1f),
        ) { onOpenReport(r.HabitStats) }
        GlanceCell(Icons.Rounded.Payments, spentMinor.coerceAtLeast(0).toTaka(), L("spent today"), null, Modifier.weight(1f)) { onOpenReport(r.MoneyStats) }
    }
}

/** One glance number: an icon, the value, what it is — and, where it's a share of a whole, a small bar (Phase 20C). */
// Tiles show "2/4": it fits a quarter-width tile in every language — "2 of 4" became
// "৪-এর মধ্যে ২" in Bangla and was cut off with the counted number last.
@Composable
private fun GlanceCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    progress: Float?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = TimeThemeTokens.colors
    Column(
        modifier = modifier.background(colors.card, Shapes.lg).clickable(onClick = onClick)
            .heightIn(min = Dimens.touchMin).padding(horizontal = Dimens.s10, vertical = Dimens.s12),
        verticalArrangement = Arrangement.spacedBy(Dimens.s2),
    ) {
        Icon(icon, contentDescription = null, tint = colors.acc, modifier = Modifier.size(Dimens.s16))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (progress != null) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.s4),
                color = TimeThemeTokens.status.okInk, trackColor = colors.accS,
                drawStopIndicator = {},
            )
        }
    }
}

/**
 * The one thing happening, with its controls: running → live time, estimate progress, Pause /
 * Switch / Done; paused → Resume; nothing → what to start (an open commitment first), or Focus
 * with no task. It used to show a title and a single icon that opened Switch work.
 */
@Composable
private fun NowCard(
    current: Entry?,
    tracked: Long,
    next: Entry?,
    isCommitment: Boolean,
    onPause: () -> Unit,
    onSwitch: () -> Unit,
    onDone: () -> Unit,
    onResume: () -> Unit,
    onStart: () -> Unit,
    onFocus: () -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (Long) -> Unit,
) {
    val colors = TimeThemeTokens.colors
    val ink = colors.accInk
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.accC2, Shapes.xl).padding(Dimens.s20),
        verticalArrangement = Arrangement.spacedBy(Dimens.s12),
    ) {
        when {
            current != null -> {
                val running = current.status == EntryStatus.DOING
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (running) L("TRACKING NOW") else L("PAUSED"),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ink,
                        modifier = Modifier.weight(1f),
                    )
                    current.category?.let { c ->
                        Row(
                            Modifier.background(colors.accC, Shapes.pill).padding(horizontal = Dimens.s10, vertical = Dimens.s4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(Color(c.color), CircleShape))
                            Text(c.name, style = MaterialTheme.typography.labelMedium, color = ink, modifier = Modifier.padding(start = Dimens.s6))
                        }
                    }
                }
                Text(
                    current.title, style = MaterialTheme.typography.titleLarge, color = ink,
                    modifier = Modifier.clickable { onOpenTask(current.id) },
                )
                Text(formatElapsed(tracked), style = MaterialTheme.typography.displaySmall, color = ink)
                current.estimateMs?.takeIf { it > 0 }?.let { est ->
                    val fraction = (tracked.toFloat() / est).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(6.dp).background(colors.accC, Shapes.pill)) {
                        Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(colors.acc, Shapes.pill))
                    }
                    val left = est - tracked
                    Text(
                        if (left >= 0) L("%1\$s of %2\$s estimate · %3\$s left", formatHm(tracked), formatHm(est), formatHm(left))
                        else L("%1\$s over the %2\$s estimate", formatHm(-left), formatHm(est)),
                        style = MaterialTheme.typography.bodySmall, color = ink,
                    )
                }
                if (!running) current.resumeNote?.let { Text(L("Resume: %1\$s", it), style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 2) }
                if (running) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s8)) {
                        NowButton(L("Pause"), Icons.Rounded.Pause, primary = true, Modifier.weight(1f), onPause)
                        NowButton(L("Switch"), Icons.Rounded.SwapHoriz, primary = false, Modifier.weight(1f), onSwitch)
                        NowButton(L("Done"), Icons.Rounded.Check, primary = false, Modifier.weight(1f), onDone)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s8)) {
                        NowButton(L("Resume"), Icons.Rounded.PlayArrow, primary = true, Modifier.weight(1f), onResume)
                        NowButton(L("Done"), Icons.Rounded.Check, primary = false, Modifier.weight(1f), onDone)
                    }
                }
            }
            next != null -> {
                Text(L("NOTHING RUNNING"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ink)
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.s2)) {
                    Text(L("Start with"), style = MaterialTheme.typography.bodyMedium, color = ink)
                    Text(next.title, style = MaterialTheme.typography.titleLarge, color = ink, modifier = Modifier.clickable { onOpenTask(next.id) })
                    Text(
                        listOfNotNull(
                            if (isCommitment) L("One of Today's 3") else null,
                            next.estimateMs?.let { L("%1\$s estimate", formatHm(it)) },
                            next.category?.name,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = ink,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s8)) {
                    NowButton(L("Start"), Icons.Rounded.PlayArrow, primary = true, Modifier.weight(1f), onStart)
                    NowButton(L("Just focus"), Icons.Rounded.Timer, primary = false, Modifier.weight(1f), onFocus)
                }
            }
            else -> {
                Text(L("NOTHING RUNNING"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ink)
                Text(L("Nothing is due today. Add a task, or start a focus timer and name it later."), style = MaterialTheme.typography.bodyMedium, color = ink)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s8)) {
                    NowButton(L("Add a task"), Icons.Rounded.Add, primary = true, Modifier.weight(1f), onAddTask)
                    NowButton(L("Focus"), Icons.Rounded.Timer, primary = false, Modifier.weight(1f), onFocus)
                }
            }
        }
    }
}

@Composable
private fun NowButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = TimeThemeTokens.colors
    if (primary) {
        androidx.compose.material3.Button(
            onClick = onClick, modifier = modifier.heightIn(min = Dimens.touchMin),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.acc, contentColor = MaterialTheme.colorScheme.onPrimary),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.s8),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Dimens.s6))
            Text(label, maxLines = 1)
        }
    } else {
        androidx.compose.material3.OutlinedButton(
            onClick = onClick, modifier = modifier.heightIn(min = Dimens.touchMin),
            border = androidx.compose.foundation.BorderStroke(Dimens.hairline, colors.accInk),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = colors.accInk),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.s8),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Dimens.s6))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Only when something was missed: open tasks and bills past their date, each with one fix. */
@Composable
private fun CarryoverCard(
    missed: com.example.timeapp.data.Carryover,
    onToday: (Entry) -> Unit,
    onPay: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
) {
    val status = TimeThemeTokens.status
    val fmt = displayPattern("EEE d MMM")
    Column(
        modifier = Modifier.fillMaxWidth().background(status.warn, Shapes.lg).padding(Dimens.s16),
        verticalArrangement = Arrangement.spacedBy(Dimens.s4),
    ) {
        Text(L("From before today"), style = MaterialTheme.typography.titleSmall, color = status.warnInk, modifier = Modifier.semantics { heading() })
        missed.tasks.take(3).forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.touchMin)) {
                Text(
                    L("%1\$s · was due %2\$s", e.title, e.dueOn!!.format(fmt)),
                    style = MaterialTheme.typography.bodyMedium, color = status.warnInk,
                    modifier = Modifier.weight(1f).clickable { onOpenTask(e.id) },
                )
                TextButton(onClick = { onToday(e) }) { Text(L("Move to today"), color = status.warnInk) }
            }
        }
        missed.plans.take(3).forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.touchMin)) {
                Text(
                    L("%1\$s · %2\$s left · was due %3\$s", p.title, p.remainingMinor.toTaka(), p.dueOn!!.format(fmt)),
                    style = MaterialTheme.typography.bodyMedium, color = status.warnInk, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onPay(p.id) }) { Text(L("Pay"), color = status.warnInk) }
            }
        }
        val more = (missed.tasks.size - 3).coerceAtLeast(0) + (missed.plans.size - 3).coerceAtLeast(0)
        if (more > 0) Text(L("and %1\$s more", more), style = MaterialTheme.typography.bodySmall, color = status.warnInk)
    }
}

/** Everything still to come today in one time-ordered line: blocks, due work, habits, bills. */
@Composable
private fun RestOfTodayCard(
    items: List<TimelineItem>,
    now: Instant,
    zone: ZoneId,
    onOpenTask: (Long) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenMoney: () -> Unit,
) {
    val colors = TimeThemeTokens.colors
    val hm = displayPattern("HH:mm")
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.card, Shapes.lg).padding(Dimens.s16),
        verticalArrangement = Arrangement.spacedBy(Dimens.s6),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L("Rest of today"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onOpenCalendar) { Text(L("Calendar")) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(now.atZone(zone).format(hm), style = MaterialTheme.typography.labelMedium, color = colors.acc, modifier = Modifier.width(56.dp))
            Box(Modifier.size(8.dp).background(colors.acc, CircleShape))
            Box(Modifier.weight(1f).height(2.dp).background(colors.acc))
        }
        if (items.isEmpty()) {
            Text(
                L("Nothing else booked today. Book time for a task from Plan, or tap + to book time."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items.forEach { item ->
            val bar = when (item.kind) {
                TimelineItem.Kind.EVENT -> MarkerEvent
                TimelineItem.Kind.TASK_BLOCK, TimelineItem.Kind.TASK_DUE -> MarkerTask
                TimelineItem.Kind.HABIT -> TimeThemeTokens.status.okInk
                TimelineItem.Kind.BILL -> MarkerMoney
            }
            val detail = when (item.kind) {
                TimelineItem.Kind.EVENT, TimelineItem.Kind.TASK_BLOCK -> {
                    val span = listOfNotNull(item.at?.atZone(zone)?.format(hm), item.endAt?.atZone(zone)?.format(hm)).joinToString("–")
                    span + " · " + if (item.fixed) L("fixed") else L("flexible")
                }
                TimelineItem.Kind.TASK_DUE -> L("due today, no time booked")
                TimelineItem.Kind.HABIT -> L("habit reminder")
                TimelineItem.Kind.BILL -> L("due today")
            }
            Row(verticalAlignment = Alignment.Top) {
                // Under way already: "Now", not its start time — "11:00" under an 11:27 now-line
                // read as out of order. The detail line still gives the whole span.
                val underWay = item.at?.isBefore(now) == true
                Text(
                    if (underWay) L("Now") else item.at?.atZone(zone)?.format(hm) ?: L("Any time"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (underWay) colors.acc else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp).padding(top = Dimens.s10),
                )
                Row(
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.touchMin)
                        .background(colors.accS, Shapes.md)
                        .clickable {
                            when {
                                item.entryId != null -> onOpenTask(item.entryId)
                                item.planId != null -> onOpenMoney()
                            }
                        }
                        .padding(horizontal = Dimens.s12, vertical = Dimens.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(4.dp).height(32.dp).background(bar, Shapes.pill))
                    Column(Modifier.weight(1f).padding(start = Dimens.s10)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item.amountMinor?.let { Text(it.toTaka(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

/** Paused work and the inbox, folded to a line each so they don't crowd out today. */
@Composable
private fun PausedAndInboxCard(paused: List<Entry>, inbox: List<Entry>, viewModel: PlanViewModel, onOpenTask: (Long) -> Unit, onOpenPlan: () -> Unit) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    Column(Modifier.fillMaxWidth().background(colors.card, Shapes.lg)) {
        paused.forEach { e ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpenTask(e.id) }.padding(start = Dimens.s16, end = Dimens.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).background(status.warn, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Pause, contentDescription = null, tint = status.warnInk, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f).padding(start = Dimens.s12)) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        e.resumeNote?.let { L("Paused · “%1\$s”", it) } ?: L("Paused"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { viewModel.resumeTask(e.id) }) { Text(L("Resume")) }
            }
        }
        if (paused.isNotEmpty() && inbox.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(horizontal = Dimens.s16).height(Dimens.hairline).background(colors.line))
        if (inbox.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onOpenPlan).padding(horizontal = Dimens.s16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).background(colors.accS, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f).padding(start = Dimens.s12)) {
                    Text(
                        if (inbox.size == 1) L("Inbox · 1 not scheduled") else L("Inbox · %1\$s not scheduled", inbox.size),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(inbox.take(3).joinToString(", ") { it.title }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(L("Plan them"), style = MaterialTheme.typography.labelLarge, color = colors.acc)
            }
        }
    }
}

/** A brand-new install: four clear starting points instead of empty cards. */
@Composable
private fun FirstRunCard(facts: StartFacts, onStep: (StartStep) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.s16)) {
        IllustrationImage(Illustration.WELCOME, Modifier.align(Alignment.CenterHorizontally), width = 220.dp)
        Text(
            L("This page fills in as you use T!ME: what you're working on, your day in order, your habits and what's due."),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GettingStartedCard(facts, onStep, onDismiss = null)
    }
}

/**
 * Phase 19B — five first steps, each ticking itself from real records (`data/GettingStarted.kt`).
 * A done step stays visible, ticked, so progress shows; tapping an open one goes straight to it.
 */
@Composable
private fun GettingStartedCard(facts: StartFacts, onStep: (StartStep) -> Unit, onDismiss: (() -> Unit)?) {
    val colors = TimeThemeTokens.colors
    val status = TimeThemeTokens.status
    Column(Modifier.fillMaxWidth().background(colors.card, Shapes.lg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Dimens.s16, top = Dimens.s12, end = Dimens.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(L("Getting started"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    L("%1\$s of %2\$s done", facts.doneCount(), StartStep.entries.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = L("Hide getting started")) }
            }
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = { facts.doneCount() / StartStep.entries.size.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s16, vertical = Dimens.s8),
            color = status.okInk, trackColor = colors.accS,
            drawStopIndicator = {},
        )
        StartStep.entries.forEach { step ->
            val done = facts.isDone(step)
            val (icon, title, subtitle) = when (step) {
                StartStep.TASK -> Triple(Icons.Rounded.CheckCircle, L("Add a task"), L("Something to do, with or without a date"))
                StartStep.TIMER -> Triple(Icons.Rounded.Timer, L("Track some time"), L("Start a timer now, name it later"))
                StartStep.WALLET -> Triple(Icons.Rounded.AccountBalanceWallet, L("Add a wallet"), L("Cash, bank or bKash — with what's in it now"))
                StartStep.BILL -> Triple(Icons.Rounded.Payments, L("Plan a bill"), L("Rent, a phone bill — anything you expect to pay"))
                StartStep.HABIT -> Triple(Icons.Rounded.EventRepeat, L("Start a habit"), L("Check in each day and keep a streak"))
            }
            StartRow(
                icon = if (done) Icons.Rounded.Check else icon,
                bg = if (done) status.ok else colors.accC,
                ink = if (done) status.okInk else colors.accInk,
                title = title,
                subtitle = if (done) L("Done") else subtitle,
                done = done,
                onClick = { onStep(step) },
            )
        }
    }
}

@Composable
private fun StartRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: Color,
    ink: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    done: Boolean = false,
) {
    val state = if (done) L("Done") else L("Not done yet")
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = state }
            .padding(horizontal = Dimens.s16, vertical = Dimens.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = ink)
        }
        Column(Modifier.weight(1f).padding(start = Dimens.s14)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Today's habits, as the refined canvas draws them: one card, a ring for how many of today's
 * are done, then each habit with an animated check and its streak chip. A first run with no
 * habits gets the "Start a habit" invitation instead.
 */
@Composable
private fun TodayHabitsCard(
    habits: com.example.timeapp.ui.habits.HabitsUiState,
    onOpenHabits: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onToggle: (com.example.timeapp.ui.habits.HabitRow, com.example.timeapp.data.HabitMark?) -> Unit,
    onSetDay: (com.example.timeapp.ui.habits.HabitRow, com.example.timeapp.data.HabitMark?, Double?, String?) -> Unit = { _, _, _, _ -> },
    onStartTimer: (com.example.timeapp.ui.habits.HabitRow) -> Unit = {},
    onAddHabit: () -> Unit = onOpenHabits,
) {
    val colors = TimeThemeTokens.colors
    val rows = habits.dueToday
    val doneCount = rows.count { it.marks[habits.today] == com.example.timeapp.data.HabitMark.DONE }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, Shapes.lg)
            .padding(start = Dimens.s16, end = Dimens.s8, top = Dimens.s12, bottom = Dimens.s6),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Dimens.s6)) {
            if (rows.isNotEmpty()) {
                com.example.timeapp.ui.components.ProgressRing(
                    fraction = doneCount.toFloat() / rows.size,
                    color = colors.acc, track = colors.ring, size = 40.dp, stroke = 4.dp,
                ) {
                    // A full ring has no gap, so on its own it read as an empty circle.
                    if (doneCount == rows.size) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.acc, modifier = Modifier.size(20.dp))
                }
            } else {
                Icon(Icons.Rounded.EventRepeat, contentDescription = null, tint = colors.acc, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = Dimens.s12)) {
                Text(L("Habits"), style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        habits.active.isEmpty() -> L("Check in each day and keep a streak going")
                        rows.isEmpty() -> L("Nothing due today — done or resting")
                        else -> L("%1\$s of %2\$s done today", doneCount, rows.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // "Start one" opens the form itself — it used to open the empty Habits list first.
            androidx.compose.material3.TextButton(onClick = if (habits.active.isEmpty()) onAddHabit else onOpenHabits) {
                Text(if (habits.active.isEmpty()) L("Start one") else L("All habits"))
            }
        }
        var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.example.timeapp.ui.habits.HabitRow?>(null) }
        editing?.let { row ->
            com.example.timeapp.ui.habits.HabitDayDialog(
                row, habits.today,
                onSave = { m, v, n -> onSetDay(row, m, v, n); editing = null },
                onDismiss = { editing = null },
            )
        }
        rows.forEach { row ->
            val mark = row.marks[habits.today]
            val done = mark == com.example.timeapp.data.HabitMark.DONE
            Box(Modifier.fillMaxWidth().height(Dimens.hairline).background(colors.line2))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenHabit(row.habit.id) }.padding(vertical = Dimens.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.example.timeapp.ui.components.AnimatedCheckCircle(
                    checked = done,
                    // A measurable habit asks how much; a plain one just checks.
                    onClick = { if (row.habit.target != null) editing = row else onToggle(row, mark) },
                    contentDescription = if (done) L("Mark %1\$s not done", row.habit.name) else L("Mark %1\$s done", row.habit.name),
                    modifier = Modifier.padding(end = Dimens.s4),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.habit.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    )
                    if (!done) com.example.timeapp.ui.habits.HabitStreakText(row)
                }
                val minutes = row.habit.timerMinutes
                val ringsAt = habits.runningTimers[row.habit.id]
                if (minutes != null && !done) {
                    if (ringsAt != null) {
                        Text(
                            L("until %1\$s", ringsAt.atZone(java.time.ZoneId.systemDefault()).format(displayPattern("HH:mm"))),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.acc,
                            modifier = Modifier.padding(end = Dimens.s8),
                        )
                    } else {
                        androidx.compose.material3.TextButton(onClick = { onStartTimer(row) }) { Text(L("Start %1\$s min", minutes)) }
                    }
                } else {
                    com.example.timeapp.ui.habits.StreakChip(row, modifier = Modifier.padding(end = Dimens.s8))
                }
            }
        }
    }
}
