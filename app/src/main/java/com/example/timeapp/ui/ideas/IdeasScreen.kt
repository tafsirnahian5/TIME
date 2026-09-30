package com.example.timeapp.ui.ideas

import com.example.timeapp.data.displayName
import com.example.timeapp.data.L

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.timeapp.data.CollectionKind
import com.example.timeapp.data.Idea
import com.example.timeapp.data.IdeaCollection
import com.example.timeapp.ui.components.Illustration
import com.example.timeapp.ui.components.EmptyState
import androidx.compose.material.icons.rounded.Lightbulb
import com.example.timeapp.ui.theme.Dimens
import com.example.timeapp.ui.theme.Shapes
import com.example.timeapp.ui.theme.TimeThemeTokens

/** I1 — Ideas: collections index. No due date, estimate, timer or XP in this tab, on purpose. */
@Composable
fun IdeasScreen(
    onOpenBucket: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onQuickCapture: () -> Unit,
    onOpenCollection: (Long) -> Unit,
    onSearch: () -> Unit,
    viewModel: IdeasViewModel,
) {
    val colors = TimeThemeTokens.colors
    val ui by viewModel.uiState.collectAsState()
    var showNewCollection by remember { mutableStateOf(false) }
    // No content insets: the header lives in the content, not a topBar, so the Scaffold padded
    // the status bar a second time (the root already does) — the title sat ~40dp lower than on
    // every other tab.
    Scaffold(contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.s12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.example.timeapp.ui.components.TabTitle(L("Ideas"), com.example.timeapp.ui.components.TabHelps.ideas, Modifier.weight(1f))
                    IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = L("Search")) }
                    com.example.timeapp.ui.components.InboxBellButton()
                    // No further Ideas-scoped actions exist yet beyond what's already reachable
                    // (quick capture, collections) — no destination to wire this menu to.
                }
                LazyColumn(
                    // Bottom padding on the list's own bounds (not contentPadding, which only
                    // reserves space past the last item and does nothing when the list is
                    // shorter than the viewport) so the last row isn't hidden under the FAB.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimens.s16)
                        .padding(bottom = Dimens.s34 + Dimens.s34 + Dimens.s20),
                ) {
                    // (The "Nothing here is scheduled or due. Ever." banner is now the tab's purpose line and ? sheet.)
                    val bucket = ui.collections.firstOrNull { it.kind == CollectionKind.BUCKET }
                    if (bucket != null) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = Dimens.s12)
                                    .background(colors.acc, Shapes.xl)
                                    .clickable(onClick = onOpenBucket)
                                    .padding(Dimens.s20),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                Text(L("PINNED"), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { heading() }.padding(top = Dimens.s8))
                                Text(L("Bucket list"), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Dimens.s8, bottom = Dimens.s4))
                                Text(if (bucket.count == 0) L("Nothing on it yet — places to go, things to try") else L("%1\$s of %2\$s done", bucket.doneCount, bucket.count), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    // A plain chunked grid, not LazyVerticalGrid — this collections list is small
                    // and fixed-size, and nesting a second lazy scrollable inside this LazyColumn
                    // item crashes with "measured with an infinity maximum height constraints".
                    // `null` stands in for the trailing "New collection" tile.
                    val collectionCells: List<IdeaCollection?> =
                        ui.collections.filter { it.kind != CollectionKind.BUCKET } + listOf(null)
                    items(collectionCells.chunked(2)) { rowCells ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s12),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.s12),
                        ) {
                            rowCells.forEach { cell ->
                                Box(modifier = Modifier.weight(1f)) {
                                    if (cell != null) {
                                        CollectionCard(cell, onClick = { onOpenCollection(cell.id) })
                                    } else {
                                        NewCollectionCard(onClick = { showNewCollection = true })
                                    }
                                }
                            }
                            if (rowCells.size == 1) Box(modifier = Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.s8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(L("Recently added"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                L("All notes"), style = MaterialTheme.typography.labelLarge, color = colors.acc,
                                modifier = Modifier.clickable(onClick = onSearch),
                            )
                        }
                    }
                    if (ui.recentlyTouched.isEmpty()) {
                        // One compact line: a full illustrated empty state under the collections was
                        // cut off mid-sentence above the New idea button.
                        item {
                            Text(
                                L("No ideas yet. Tap New idea to catch your first thought — it stays out of your schedule until you decide otherwise."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Dimens.s8),
                            )
                        }
                    }
                    items(ui.recentlyTouched) { idea -> RecentIdeaRow(idea, onClick = { onOpenNote(idea.id) }) }
                }
            }
            // A real FAB: white text on the light dark-mode accent was nearly invisible.
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onQuickCapture,
                containerColor = colors.accC,
                contentColor = colors.accInk,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(L("New idea")) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = Dimens.s16, bottom = Dimens.s16),
            )
        }
    }

    if (showNewCollection) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewCollection = false },
            title = { Text(L("New collection")) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(L("Name")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.addCollection(name); showNewCollection = false },
                    enabled = name.isNotBlank(),
                ) { Text(L("Create")) }
            },
            dismissButton = { TextButton(onClick = { showNewCollection = false }) { Text(L("Cancel")) } },
        )
    }
}

@Composable
private fun CollectionCard(c: IdeaCollection, onClick: () -> Unit) {
    val icon = when (c.icon) {
        "bolt" -> Icons.Rounded.Bolt
        "auto_stories" -> Icons.Rounded.AutoStories
        "travel_explore" -> Icons.Rounded.TravelExplore
        else -> Icons.AutoMirrored.Rounded.MenuBook
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TimeThemeTokens.colors.card, Shapes.xl)
            .border(1.dp, TimeThemeTokens.colors.line, Shapes.xl)
            .clickable(onClick = onClick)
            .padding(Dimens.s16),
    ) {
        Icon(icon, contentDescription = null, tint = Color(c.iconColorArgb))
        Text(c.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Dimens.s12, bottom = Dimens.s2))
        Text(
            when {
                c.kind == CollectionKind.SPARKS && c.count == 1 -> L("1 quick note")
                c.kind == CollectionKind.SPARKS -> if (c.count == 1) L("1 quick note") else L("%1\$s quick notes", c.count)
                c.count == 1 -> L("1 idea")
                else -> L("%1\$s ideas", c.count)
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NewCollectionCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TimeThemeTokens.colors.edge, Shapes.xl)
            .clickable(onClick = onClick)
            .padding(Dimens.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(L("New collection"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Dimens.s8))
    }
}

@Composable
private fun RecentIdeaRow(idea: Idea, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Dimens.s10)) {
        Icon(Icons.Rounded.Description, contentDescription = null, tint = TimeThemeTokens.colors.acc)
        Column(modifier = Modifier.weight(1f).padding(start = Dimens.s14)) {
            Text(idea.title, style = MaterialTheme.typography.bodyLarge)
            Text(idea.body ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (idea.promotedToPlan) Text(L("In Plan"), style = MaterialTheme.typography.labelSmall, color = TimeThemeTokens.colors.acc)
    }
}
