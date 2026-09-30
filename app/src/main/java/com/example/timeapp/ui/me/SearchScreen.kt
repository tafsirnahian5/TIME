package com.example.timeapp.ui.me

import com.example.timeapp.data.L
import androidx.compose.ui.focus.focusRequester

import androidx.compose.material.icons.rounded.EventRepeat
import com.example.timeapp.data.label
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.timeapp.ui.MiscViewModel
import com.example.timeapp.ui.ideas.IdeasViewModel
import com.example.timeapp.ui.money.MoneyViewModel
import com.example.timeapp.ui.plan.PlanViewModel
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** G2 — Global search: real grouped results across tasks/plans/ideas/links, every result
 * navigates to its source record. Session search stays out of scope — no session/timer
 * data model exists yet to search over. */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenMoney: () -> Unit,
    onOpenIdea: (Long) -> Unit,
    onOpenLinks: () -> Unit,
    planViewModel: PlanViewModel,
    moneyViewModel: MoneyViewModel,
    ideasViewModel: IdeasViewModel,
    miscViewModel: MiscViewModel,
    habitsViewModel: com.example.timeapp.ui.habits.HabitsViewModel? = null,
    onOpenHabit: (Long) -> Unit = {},
) {
    val colors = TimeThemeTokens.colors
    // Saveable: opening a result and coming back used to find the box empty.
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    // The keyboard comes up with the screen — typing straight away went nowhere.
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
    val plan by planViewModel.uiState.collectAsState()
    val money by moneyViewModel.uiState.collectAsState()
    val habitState = habitsViewModel?.uiState?.collectAsState()?.value
    val allIdeas by ideasViewModel.allIdeas.collectAsState()
    val bookmarks by miscViewModel.bookmarks.collectAsState()

    // A task also matches on its project's name or a tag, so "#android" finds everything tagged.
    val tagQuery = query.trim().trimStart('#')
    // Archived tasks too — Search is how an archived task is found again.
    val tasks = if (query.isBlank()) emptyList() else plan.allEntries.filter { e ->
        e.title.contains(query, ignoreCase = true) ||
            e.project?.name?.contains(query, ignoreCase = true) == true ||
            (tagQuery.isNotEmpty() && e.tags.any { it.contains(tagQuery, ignoreCase = true) })
    }
    val moneyResults = if (query.isBlank()) emptyList() else (money.plans + money.history).filter { it.title.contains(query, ignoreCase = true) }
    val ideaResults = if (query.isBlank()) emptyList() else allIdeas.filter { it.title.contains(query, ignoreCase = true) }
    val habitResults = if (query.isBlank() || habitState == null) emptyList()
    else (habitState.active + habitState.archived).filter { it.habit.name.contains(query, ignoreCase = true) }
    val linkResults = if (query.isBlank()) emptyList() else bookmarks.filter { it.title.contains(query, ignoreCase = true) }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.s12)
                    .background(colors.accS2, Shapes.pill)
                    .padding(horizontal = Dimens.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Real 48dp buttons: a bare clickable icon was a small target and drew a grey
                // focus square.
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = L("Back"))
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(L("Search everything")) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                if (query.isNotEmpty()) {
                    androidx.compose.material3.IconButton(onClick = { query = ""; focus.requestFocus() }) {
                        Icon(Icons.Rounded.Close, contentDescription = L("Clear"))
                    }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.s16)) {
                if (query.isBlank()) {
                    item {
                        Text(
                            L("Search your tasks, booked time, habits, bills and payments, ideas and links."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Dimens.s20),
                        )
                    }
                }
                if (tasks.isNotEmpty()) {
                    item { GroupLabel(L("TASKS"), tasks.size) }
                    items(tasks) { e ->
                        // A filled tick on every task read as "done" — the icon now says what it is.
                        ResultRow(
                            when {
                                e.kind == com.example.timeapp.data.EntryKind.EVENT -> Icons.Rounded.Event
                                e.status == com.example.timeapp.data.EntryStatus.DONE -> Icons.Rounded.CheckCircle
                                else -> Icons.Rounded.RadioButtonUnchecked
                            },
                            e.title,
                            if (e.isArchived) L("Archived") else listOfNotNull(e.category?.name, e.status.label()).joinToString(" · "),
                            onClick = { onOpenTask(e.id) },
                        )
                    }
                }
                if (habitResults.isNotEmpty()) {
                    item { GroupLabel(L("HABITS"), habitResults.size) }
                    items(habitResults) { h ->
                        ResultRow(
                            Icons.Rounded.EventRepeat, h.habit.name,
                            if (h.habit.archived) L("Archived") else h.habit.schedule.label(),
                            onClick = { onOpenHabit(h.habit.id) },
                        )
                    }
                }
                if (moneyResults.isNotEmpty()) {
                    item { GroupLabel(L("MONEY"), moneyResults.size) }
                    items(moneyResults) { m ->
                        ResultRow(
                            Icons.Rounded.AccountBalanceWallet, m.title,
                            m.status?.name?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: L("Settled"),
                            onClick = onOpenMoney,
                        )
                    }
                }
                if (ideaResults.isNotEmpty()) {
                    item { GroupLabel(L("IDEAS"), ideaResults.size) }
                    items(ideaResults) { i ->
                        ResultRow(Icons.AutoMirrored.Rounded.MenuBook, i.title, if (i.isDone) L("Done") else L("Open"), onClick = { onOpenIdea(i.id) })
                    }
                }
                if (linkResults.isNotEmpty()) {
                    item { GroupLabel(L("LINKS"), linkResults.size) }
                    items(linkResults) { b ->
                        ResultRow(Icons.Rounded.Link, b.title, "${com.example.timeapp.data.displayUrl(b.url)} · ${b.folder}", onClick = onOpenLinks)
                    }
                }
                if (query.isNotBlank() && tasks.isEmpty() && habitResults.isEmpty() && moneyResults.isEmpty() && ideaResults.isEmpty() && linkResults.isEmpty()) {
                    item {
                        Text(
                            L("No matches for \"%1\$s\".", query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Dimens.s20),
                        )
                    }
                }
                item {
                    Row(modifier = Modifier.padding(vertical = Dimens.s14)) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                        Text(
                            L("Every result opens the original record, not a copy."),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = Dimens.s10),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(label: String, count: Int) {
    val colors = TimeThemeTokens.colors
    Row(modifier = Modifier.fillMaxWidth().padding(top = Dimens.s14, bottom = Dimens.s4)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.acc, modifier = Modifier.weight(1f))
        Text(L("%1\$s", count), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Dimens.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.padding(start = Dimens.s14)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
