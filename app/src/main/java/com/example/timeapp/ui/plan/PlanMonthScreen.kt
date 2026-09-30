package com.example.timeapp.ui.plan

import com.example.timeapp.data.L

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.foundation.selection.toggleable

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

import com.example.timeapp.data.displayPattern

import com.example.timeapp.data.withBlocksExpanded
import com.example.timeapp.data.calendarDate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn

import com.example.timeapp.data.weekStart

import com.example.timeapp.data.EntryStatus
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timeapp.data.EntryKind
import com.example.timeapp.data.toTaka
import com.example.timeapp.ui.components.RootTopBar
import com.example.timeapp.ui.components.RootTopBarTrailing
import com.example.timeapp.ui.money.MoneyViewModel
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TaskColor = com.example.timeapp.ui.theme.MarkerTask
private val EventColor = com.example.timeapp.ui.theme.MarkerEvent
private val MoneyColor = com.example.timeapp.ui.theme.MarkerMoney

/** C2 — Plan · Calendar Month: real month grid + selected-day agenda from live Entry/MoneyRecord data. */
@Composable
fun PlanMonthScreen(
    onAdd: () -> Unit,
    onOpenList: () -> Unit,
    onOpenBoard: () -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PlanViewModel,
    moneyViewModel: MoneyViewModel,
    onOpenTask: (Long) -> Unit = {},
    onNewBlockAt: (java.time.LocalDateTime) -> Unit = {},
    initialView: String = "MONTH",
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    val money by moneyViewModel.uiState.collectAsState()
    val zone = ZoneId.systemDefault()

    // Saveable: booking time from the Week view and saving came back to Month on today — the
    // view, month and day you were on were forgotten while the form was open.
    var displayedMonth by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(YearMonth.now()) }
    var selectedDate by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(LocalDate.now()) }
    // Opens on the view chosen in Settings › Workday & calendar.
    var mode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(CalendarMode.entries.firstOrNull { it.name == initialView } ?: CalendarMode.MONTH) }
    val today = LocalDate.now()

    val filter by viewModel.calendarFilter.collectAsState()
    // Every view draws from the filtered lists, so a hidden kind is hidden everywhere.
    // A task split into several blocks appears once per block.
    val visibleEntries = ui.entries.withBlocksExpanded().filter { filter.shows(it) }
    val visiblePlans = money.plans.filter { filter.shows(it) }

    fun entryDate(e: com.example.timeapp.data.Entry): LocalDate? = e.calendarDate(zone)

    val marksByDay: Map<LocalDate, List<Color>> = buildMap<LocalDate, MutableList<Color>> {
        visibleEntries.forEach { e ->
            val date = entryDate(e) ?: return@forEach
            val color = if (e.kind == EntryKind.EVENT) EventColor else TaskColor
            getOrPut(date) { mutableListOf() }.add(color)
        }
        visiblePlans.forEach { p ->
            val date = p.dueOn ?: return@forEach
            getOrPut(date) { mutableListOf() }.add(MoneyColor)
        }
    }

    Scaffold(
        topBar = {
            RootTopBar(
                help = com.example.timeapp.ui.components.TabHelps.plan,
                title = L("Plan"), onSearch = onSearch,
                onOpenSettings = onOpenSettings,
                trailing = RootTopBarTrailing.SETTINGS,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = colors.acc, shape = Shapes.lg) {
                Icon(Icons.Rounded.Add, contentDescription = L("Add"), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PlanBoardCalendarToggle(selectedIndex = 2, onOpenMonth = {}, onOpenList = onOpenList, onOpenBoard = onOpenBoard)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenHList, vertical = Dimens.s8)
                    .background(colors.accS2, Shapes.pill)
                    .padding(3.dp),
            ) {
                // Week and Day used to be drawn here with no click handler at all — Month was
                // the only view that existed. All three are real now.
                CalendarMode.entries.forEach { option ->
                    val selected = option == mode
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.accInk else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selected) colors.accC else Color.Transparent, Shapes.pill)
                            .semantics { this.selected = selected }.clickable { mode = option }
                            .padding(vertical = Dimens.s8),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            // The PDF's calendar filters. Tapping a chip hides or shows that kind in every view.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.s8),
            ) {
                FilterChipToggle(L("Tasks"), TaskColor, filter.tasks) { viewModel.setCalendarFilter(filter.copy(tasks = !filter.tasks)) }
                FilterChipToggle(L("Booked time"), EventColor, filter.appointments) { viewModel.setCalendarFilter(filter.copy(appointments = !filter.appointments)) }
                FilterChipToggle(L("Bills due"), MoneyColor, filter.costs) { viewModel.setCalendarFilter(filter.copy(costs = !filter.costs)) }
                FilterChipToggle(L("Done"), TimeThemeTokens.status.dim, filter.completed) { viewModel.setCalendarFilter(filter.copy(completed = !filter.completed)) }
            }
            if (mode == CalendarMode.AGENDA) {
                AgendaList(
                    agenda = com.example.timeapp.data.buildAgenda(ui.entries.withBlocksExpanded(), money.plans, today, AGENDA_DAYS, filter, zone),
                    today = today,
                    zone = zone,
                    onOpenTask = onOpenTask,
                    modifier = Modifier.weight(1f),
                )
            } else if (mode != CalendarMode.MONTH) {
                val weekStart = selectedDate.weekStart()
                val step = if (mode == CalendarMode.WEEK) 7L else 1L
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selectedDate = selectedDate.minusDays(step) }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = if (mode == CalendarMode.WEEK) L("Previous week") else L("Previous day"))
                    }
                    Text(
                        if (mode == CalendarMode.WEEK) {
                            weekStart.format(displayPattern("d MMM")) + " – " +
                                weekStart.plusDays(6).format(displayPattern("d MMM"))
                        } else {
                            selectedDate.format(displayPattern("EEEE d MMM"))
                        },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).clickable { selectedDate = today },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { selectedDate = selectedDate.plusDays(step) }) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = if (mode == CalendarMode.WEEK) L("Next week") else L("Next day"))
                    }
                }
                if (mode == CalendarMode.DAY) {
                    // The PDF's "Day shows the real schedule and unplaced work": what's due
                    // today with no time reserved sits above the grid, not lost off it.
                    val unplaced = visibleEntries.filter { it.startAt == null && it.dueOn == selectedDate && it.status != EntryStatus.DONE }
                    val costs = visiblePlans.filter { it.dueOn == selectedDate }
                    if (unplaced.isNotEmpty() || costs.isNotEmpty()) {
                        Text(
                            L("DUE, NO TIME RESERVED"),
                            style = MaterialTheme.typography.labelSmall,
                            color = TimeThemeTokens.status.neutralInk,
                            modifier = Modifier.padding(horizontal = Dimens.s12, vertical = Dimens.s4),
                        )
                        unplaced.take(3).forEach { e ->
                            Text(
                                e.title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().clickable { onOpenTask(e.id) }
                                    .padding(horizontal = Dimens.s12, vertical = Dimens.s6),
                            )
                        }
                        costs.take(2).forEach { p ->
                            Text(
                                L("%1\$s · %2\$s due", p.title, p.remainingMinor.toTaka()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TimeThemeTokens.status.warnInk,
                                modifier = Modifier.padding(horizontal = Dimens.s12, vertical = Dimens.s4),
                            )
                        }
                        val more = (unplaced.size - 3).coerceAtLeast(0) + (costs.size - 2).coerceAtLeast(0)
                        if (more > 0) {
                            Text(
                                L("+%1\$s more — see Month for the full agenda", more),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Dimens.s12, vertical = Dimens.s4),
                            )
                        }
                    }
                }
                PlanTimeGrid(
                    days = if (mode == CalendarMode.WEEK) (0L..6L).map { weekStart.plusDays(it) } else listOf(selectedDate),
                    entries = visibleEntries,
                    today = today,
                    onOpenEntry = onOpenTask,
                    onEmptySlot = onNewBlockAt,
                    onDayHeader = { selectedDate = it; mode = CalendarMode.DAY },
                    modifier = Modifier.weight(1f).padding(horizontal = Dimens.s8),
                )
            } else Column(
                // Month content scrolls: a busy day's agenda used to run off the bottom of a
                // plain Column with no way to reach it.
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s12, vertical = Dimens.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = L("Previous month"))
                }
                Text(
                    displayedMonth.format(displayPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = L("Next month"))
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                com.example.timeapp.data.weekdayInitials().forEach {
                    Text(
                        it, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f).padding(vertical = Dimens.s6),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            val firstOfMonth = displayedMonth.atDay(1)
            // Monday-start grid: how many days before the 1st to back-fill from the previous month.
            val leadingBlanks = com.example.timeapp.data.leadingBlanks(firstOfMonth)
            val daysInMonth = displayedMonth.lengthOfMonth()
            val totalCells = leadingBlanks + daysInMonth
            val weekRows = (totalCells + 6) / 7
            for (w in 0 until weekRows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (d in 0 until 7) {
                        val cellIndex = w * 7 + d
                        val dayOfMonth = cellIndex - leadingBlanks + 1
                        val inMonth = dayOfMonth in 1..daysInMonth
                        val date = if (inMonth) displayedMonth.atDay(dayOfMonth) else null
                        val selected = date == selectedDate
                        // The whole cell is the target (rule 6 — the 30dp circle alone was too small),
                        // and it reads as a full date with how much is on it.
                        Column(
                            modifier = Modifier.weight(1f).heightIn(min = Dimens.touchMin)
                                .then(
                                    if (date != null) {
                                        Modifier.semantics {
                                            this.selected = selected
                                            contentDescription = date.format(displayPattern("EEEE d MMMM")) +
                                                (marksByDay[date]?.size?.takeIf { it > 0 }?.let { L(", %1\$s on this day", it) } ?: "")
                                        }.clickable { selectedDate = date }
                                    } else Modifier,
                                )
                                .padding(vertical = Dimens.s4),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(if (selected) colors.acc else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                val label = when {
                                    inMonth -> dayOfMonth.toString()
                                    cellIndex < leadingBlanks -> displayedMonth.minusMonths(1).lengthOfMonth().let { (it + dayOfMonth).toString() }
                                    else -> (dayOfMonth - daysInMonth).toString()
                                }
                                Text(
                                    label,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else if (inMonth) MaterialTheme.colorScheme.onSurface else TimeThemeTokens.status.dim,
                                )
                            }
                            Row(modifier = Modifier.padding(top = 2.dp)) {
                                if (date != null) {
                                    marksByDay[date]?.forEach { c ->
                                        Box(modifier = Modifier.size(5.dp).padding(1.dp).background(c, CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(
                selectedDate.format(displayPattern("EEE d MMM")),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = Dimens.s12, vertical = Dimens.s8),
            )
            val agendaEntries = visibleEntries.filter { entryDate(it) == selectedDate }
            if (agendaEntries.isEmpty()) {
                Text(
                    if (filter.isDefault) L("Nothing scheduled or due this day.") else L("Nothing shown this day — some kinds are filtered out."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.s12, vertical = Dimens.s6),
                )
            }
            agendaEntries.forEach { e ->
                val color = if (e.kind == EntryKind.EVENT) EventColor else TaskColor
                val meta = if (e.startAt != null && e.endAt != null) {
                    val fmt = displayPattern("HH:mm")
                    "${e.startAt.atZone(zone).toLocalTime().format(fmt)} – ${e.endAt.atZone(zone).toLocalTime().format(fmt)}" +
                        (if (e.isFixed) L(" · fixed") else L(" · flexible"))
                } else {
                    L("due this day · no time booked")
                }
                AgendaRow(e.title, meta, color)
            }
            visiblePlans.filter { it.dueOn == selectedDate }.forEach { p ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = Dimens.s12, vertical = Dimens.s8)
                        .background(TimeThemeTokens.status.warn, Shapes.md)
                        .padding(Dimens.s10),
                ) {
                    Icon(Icons.Rounded.Upcoming, contentDescription = null, tint = TimeThemeTokens.status.warnInk)
                    Column(modifier = Modifier.padding(start = Dimens.s10)) {
                        Text(L("%1\$s due · %2\$s outstanding", p.title, p.remainingMinor.toTaka()), style = MaterialTheme.typography.bodyMedium, color = TimeThemeTokens.status.warnInk)
                        Text(L("A due-date marker. It reserves no working time."), style = MaterialTheme.typography.bodySmall, color = TimeThemeTokens.status.warnInk)
                    }
                }
            }
            }
        }

        ui.pendingConflict?.let { proposal ->
            ConflictDialog(proposal = proposal, onChoice = viewModel::resolveConflict)
        }
    }
}

/** A calendar filter chip: its colour dot doubles as the legend. At least 48dp tall (rule 6). */
@Composable
private fun FilterChipToggle(label: String, color: Color, on: Boolean, onToggle: () -> Unit) {
    val colors = TimeThemeTokens.colors
    Row(
        modifier = Modifier
            .padding(end = Dimens.s6)
            .heightIn(min = Dimens.touchMin)
            .padding(vertical = Dimens.s6)
            .background(if (on) colors.accS2 else Color.Transparent, Shapes.pill)
            .border(Dimens.hairline, if (on) Color.Transparent else colors.edge, Shapes.pill)
            // An on/off filter, so TalkBack says "Tasks, checked" rather than just "Tasks".
            .toggleable(value = on, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = Dimens.s10, vertical = Dimens.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(7.dp).background(if (on) color else Color.Transparent, CircleShape).border(1.dp, color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (on) null else androidx.compose.ui.text.style.TextDecoration.LineThrough,
            modifier = Modifier.padding(start = Dimens.s6),
        )
    }
}

private const val AGENDA_DAYS = 30

/** The Agenda view: the next 30 days that have something on them, plus anything overdue. */
@Composable
private fun AgendaList(
    agenda: com.example.timeapp.data.Agenda,
    today: LocalDate,
    zone: ZoneId,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fmt = displayPattern("HH:mm")
    androidx.compose.foundation.lazy.LazyColumn(modifier = modifier.fillMaxWidth()) {
        if (agenda.overdue.isEmpty() && agenda.days.isEmpty()) {
            item {
                Text(
                    L("Nothing in the next %1\$s days.", AGENDA_DAYS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Dimens.s16),
                )
            }
        }
        if (agenda.overdue.isNotEmpty()) {
            item { AgendaHeader(L("OVERDUE"), TimeThemeTokens.status.dangerInk) }
            agenda.overdue.forEach { item -> item { AgendaItemRow(item, zone, fmt, onOpenTask, showDate = true) } }
        }
        agenda.days.forEach { day ->
            item(key = "d-${day.date}") {
                val label = when (day.date) {
                    today -> L("TODAY · ") + day.date.format(displayPattern("EEE d MMM"))
                    today.plusDays(1) -> L("TOMORROW · ") + day.date.format(displayPattern("EEE d MMM"))
                    else -> day.date.format(displayPattern("EEEE d MMM")).uppercase()
                }
                AgendaHeader(label, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            day.items.forEach { item -> item { AgendaItemRow(item, zone, fmt, onOpenTask, showDate = false) } }
        }
        item { Box(Modifier.padding(bottom = Dimens.s34 * 2)) }
    }
}

@Composable
private fun AgendaHeader(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(start = Dimens.s16, top = Dimens.s14, bottom = Dimens.s4),
    )
}

@Composable
private fun AgendaItemRow(
    item: com.example.timeapp.data.AgendaItem,
    zone: ZoneId,
    fmt: DateTimeFormatter,
    onOpenTask: (Long) -> Unit,
    showDate: Boolean,
) {
    val dateFmt = displayPattern("d MMM")
    when (item) {
        is com.example.timeapp.data.AgendaItem.Work -> {
            val e = item.entry
            val start = e.startAt
            val end = e.endAt
            val time = if (start != null && end != null) {
                "${start.atZone(zone).toLocalTime().format(fmt)} – ${end.atZone(zone).toLocalTime().format(fmt)}"
            } else {
                L("due · no time booked")
            }
            val date = if (showDate) e.calendarDate(zone)?.format(dateFmt)?.let { "$it · " } ?: "" else ""
            val done = e.status == EntryStatus.DONE
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).clickable { onOpenTask(e.id) }
                    .padding(horizontal = Dimens.s16, vertical = Dimens.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(8.dp).background(if (e.kind == EntryKind.EVENT) EventColor else TaskColor, CircleShape))
                Column(modifier = Modifier.padding(start = Dimens.s12)) {
                    Text(
                        e.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(date + time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        is com.example.timeapp.data.AgendaItem.Due -> {
            val p = item.plan
            val date = if (showDate) p.dueOn?.format(dateFmt)?.let { "$it · " } ?: "" else ""
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin).padding(horizontal = Dimens.s16, vertical = Dimens.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(8.dp).background(MoneyColor, CircleShape))
                Column(modifier = Modifier.padding(start = Dimens.s12)) {
                    Text(p.title, style = MaterialTheme.typography.bodyLarge)
                    Text(L("%1\$s%2\$s due", date, p.remainingMinor.toTaka()), style = MaterialTheme.typography.bodySmall, color = TimeThemeTokens.status.warnInk)
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(title: String, meta: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s12, vertical = Dimens.s6)) {
        Box(modifier = Modifier.size(4.dp).background(color, Shapes.xs))
        Column(modifier = Modifier.padding(start = Dimens.s12)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(meta, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private enum class CalendarMode(val label: String) { MONTH(L("Month")), WEEK(L("Week")), DAY(L("Day")), AGENDA(L("Agenda")) }
